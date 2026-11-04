#!/usr/bin/env bash
# Enciende la EC2 y RDS si el lab las dejó apagadas y espera a que los tres servicios respondan.
source "$(dirname "$0")/lib.sh"

log "Credenciales"
aws sts get-caller-identity --query Arn --output text

log "RDS"
estado=$(aws rds describe-db-instances --db-instance-identifier $PROJECT-db --query 'DBInstances[0].DBInstanceStatus' --output text)
echo "estado: $estado"
if [ "$estado" = stopped ]; then
  aws rds start-db-instance --db-instance-identifier $PROJECT-db >/dev/null
  echo "encendiendo (tarda unos minutos)..."
  aws rds wait db-instance-available --db-instance-identifier $PROJECT-db
fi

log "EC2"
estado=$(aws ec2 describe-instances --instance-ids "$EC2_INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)
echo "estado: $estado"
if [ "$estado" = stopped ]; then
  aws ec2 start-instances --instance-ids "$EC2_INSTANCE_ID" >/dev/null
  aws ec2 wait instance-running --instance-ids "$EC2_INSTANCE_ID"
fi

log "EC2 de mensajería (RabbitMQ)"
if [ -n "${MQ_INSTANCE_ID:-}" ]; then
  estado=$(aws ec2 describe-instances --instance-ids "$MQ_INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)
  echo "estado: $estado"
  if [ "$estado" = stopped ]; then
    aws ec2 start-instances --instance-ids "$MQ_INSTANCE_ID" >/dev/null
    aws ec2 wait instance-running --instance-ids "$MQ_INSTANCE_ID"
  fi
fi

log "Servicios (systemd y docker los arrancan solos al encender las EC2)"
for i in $(seq 1 40); do
  arriba=0
  for p in 8080 8081 8082; do
    curl -sf -m 3 "http://$EC2_IP:$p/actuator/health" >/dev/null && arriba=$((arriba + 1))
  done
  [ "$arriba" -eq 3 ] && break
  sleep 5
done
for p in 8080 8081 8082; do echo "$p: $(curl -s -m 3 "http://$EC2_IP:$p/actuator/health" || echo 'sin respuesta')"; done
if [ -n "${MQ_IP:-}" ]; then
  for i in $(seq 1 40); do
    curl -sf -m 3 "http://$MQ_IP:8083/actuator/health" >/dev/null && curl -sf -m 3 "http://$MQ_IP:8084/actuator/health" >/dev/null && break
    sleep 5
  done
  for p in 8083 8084; do echo "$p: $(curl -s -m 3 "http://$MQ_IP:$p/actuator/health" || echo 'sin respuesta')"; done
fi
