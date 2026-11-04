#!/usr/bin/env bash
# Despliega deploy/mensajeria en la EC2 de mensajería: compila los dos microservicios, copia el compose,
# escribe el .env y la cookie de Erlang (fuera de git) y ejecuta docker compose up.
# SOLO_RABBIT=1 levanta únicamente los dos nodos RabbitMQ (sin compilar Java).
source "$(dirname "$0")/lib.sh"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
ROOT="$INFRA_DIR/.."
SRC="$ROOT/deploy/mensajeria"
SERVICIOS=(ms-notificaciones ms-mensajeria-admin)
SSH=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o LogLevel=ERROR "ec2-user@$MQ_IP")

log "Esperando Docker en la EC2 (user data)"
for i in $(seq 1 40); do
  "${SSH[@]}" 'docker compose version' >/dev/null 2>&1 && break
  sleep 5
done
"${SSH[@]}" 'docker compose version'

log "Base de datos 'mensajeria'"
"${SSH[@]}" "PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U $DB_USER -d productos -tAc \
  \"SELECT 1 FROM pg_database WHERE datname='mensajeria'\" | grep -q 1 || \
  PGPASSWORD='$DB_PASSWORD' psql -h $RDS_HOST -U $DB_USER -d productos -c 'CREATE DATABASE mensajeria'"

if [ "${SOLO_RABBIT:-0}" != 1 ]; then
  log "Compilación"
  for svc in "${SERVICIOS[@]}"; do
    (cd "$ROOT/backend/$svc" && ./mvnw -q -B -DskipTests package)
  done
fi

log "Archivos"
"${SSH[@]}" 'mkdir -p /opt/pedidos360-mq/rabbitmq /opt/pedidos360-mq/jars /opt/pedidos360-mq/secrets'
scp -q -i "$KEY_FILE" "$SRC/docker-compose.yml" "$SRC/Dockerfile.java" "ec2-user@$MQ_IP:/opt/pedidos360-mq/"
scp -q -i "$KEY_FILE" "$SRC/rabbitmq/rabbitmq.conf" "$SRC/rabbitmq/enabled_plugins" "ec2-user@$MQ_IP:/opt/pedidos360-mq/rabbitmq/"
if [ "${SOLO_RABBIT:-0}" != 1 ]; then
  for svc in "${SERVICIOS[@]}"; do
    scp -q -i "$KEY_FILE" "$ROOT/backend/$svc/target/$svc-0.0.1-SNAPSHOT.jar" "ec2-user@$MQ_IP:/opt/pedidos360-mq/jars/$svc.jar"
  done
fi
"${SSH[@]}" "umask 077 && cat > /opt/pedidos360-mq/.env" <<ENV
MQ_USER=$MQ_USER
MQ_PASSWORD=$MQ_PASSWORD
COGNITO_ISSUER_URI=$COGNITO_ISSUER_URI
COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
DB_URL=jdbc:postgresql://$RDS_HOST:5432/mensajeria
DB_USER=$DB_USER
DB_PASSWORD=$DB_PASSWORD
ENV
# La cookie es el secreto compartido que permite a los dos nodos unirse al mismo clúster.
# RabbitMQ exige que pertenezca a su usuario (uid 999) y que nadie más pueda leerla.
"${SSH[@]}" "sudo sh -c 'printf %s $MQ_ERLANG_COOKIE > /opt/pedidos360-mq/secrets/erlang.cookie && \
  chown 999:999 /opt/pedidos360-mq/secrets/erlang.cookie && chmod 400 /opt/pedidos360-mq/secrets/erlang.cookie'"

log "docker compose up"
if [ "${SOLO_RABBIT:-0}" = 1 ]; then
  "${SSH[@]}" 'cd /opt/pedidos360-mq && docker compose up -d rabbit1 rabbit2'
else
  "${SSH[@]}" 'cd /opt/pedidos360-mq && docker compose up -d --build --remove-orphans'
fi

log "Estado del clúster"
for i in $(seq 1 30); do
  "${SSH[@]}" 'docker exec pedidos360-mq-rabbit1-1 rabbitmqctl -q cluster_status --formatter json 2>/dev/null' \
    | grep -q 'rabbit@rabbit2' && break
  sleep 5
done
"${SSH[@]}" 'docker exec pedidos360-mq-rabbit1-1 rabbitmqctl cluster_status | grep -A3 Running.Nodes'
"${SSH[@]}" 'cd /opt/pedidos360-mq && docker compose ps --format "table {{.Service}}\t{{.Status}}"'
