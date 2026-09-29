#!/usr/bin/env bash
# Creates the per-service databases, writes the runtime env file on EC2 and deploys the jars.
source "$(dirname "$0")/lib.sh"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}

log "Waiting for RDS"
aws rds wait db-instance-available --db-instance-identifier $PROJECT-db
save RDS_HOST "$(aws rds describe-db-instances --db-instance-identifier $PROJECT-db \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
echo "RDS_HOST=$RDS_HOST"

SSH=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new "ec2-user@$EC2_IP")

log "Database 'pedidos' (the 'productos' one is created with the instance)"
"${SSH[@]}" "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U $DB_USER -d productos -tAc \
  \"SELECT 1 FROM pg_database WHERE datname='pedidos'\" | grep -q 1 || \
  PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U $DB_USER -d productos -c 'CREATE DATABASE pedidos'"

log "Runtime env file on EC2"
"${SSH[@]}" "sudo mkdir -p /opt/pedidos360 && sudo chown ec2-user: /opt/pedidos360 && umask 077 && cat > /opt/pedidos360/pedidos360.env" <<ENV
COGNITO_ISSUER_URI=$COGNITO_ISSUER_URI
COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
PRODUCTOS_URL=http://localhost:8081
PEDIDOS_URL=http://localhost:8082
FRONTEND_ORIGINS=$FRONTEND_URL,https://preview.$AMPLIFY_APP_ID.amplifyapp.com,http://localhost:4200
WEBPAY_RETURN_URL=$API_URL/api/pagos/webpay/retorno
ENV

log "Build + deploy"
EC2_HOST="ec2-user@$EC2_IP" SSH_KEY="$KEY_FILE" RDS_HOST="$RDS_HOST" "$INFRA_DIR/../deploy/ec2/deploy.sh"

log "Health"
for i in $(seq 1 30); do
  up=$("${SSH[@]}" 'for p in 8080 8081 8082; do curl -sf localhost:$p/actuator/health >/dev/null && echo up; done | wc -l')
  [ "$up" -eq 3 ] && break
  sleep 3
done
"${SSH[@]}" 'for p in 8080 8081 8082; do echo "$p $(curl -s localhost:$p/actuator/health)"; done'
