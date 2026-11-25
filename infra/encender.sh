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

log "EC2 de streaming (Kafka)"
if [ -n "${KAFKA_INSTANCE_ID:-}" ]; then
  estado=$(aws ec2 describe-instances --instance-ids "$KAFKA_INSTANCE_ID" --query 'Reservations[0].Instances[0].State.Name' --output text)
  echo "estado: $estado"
  if [ "$estado" = stopped ]; then
    aws ec2 start-instances --instance-ids "$KAFKA_INSTANCE_ID" >/dev/null
    aws ec2 wait instance-running --instance-ids "$KAFKA_INSTANCE_ID"
  fi
fi

log "Servicios (systemd y docker los arrancan solos al encender las EC2)"
# Solo el BFF (8080) está expuesto: el estado de cada microservicio se consulta dentro de su EC2 por SSH.
ssh_en() { ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o LogLevel=ERROR -o ConnectTimeout=5 "ec2-user@$1" "$2"; }
salud() {  # host puertos...
  local host=$1; shift
  ssh_en "$host" "for p in $*; do echo \"\$p: \$(curl -s -m 3 localhost:\$p/actuator/health || echo sin respuesta)\"; done" 2>/dev/null
}
esperar() {  # host cantidad puertos...
  local host=$1 n=$2; shift 2
  for i in $(seq 1 40); do
    [ "$(salud "$host" "$@" | grep -c '"UP"')" -eq "$n" ] && return
    sleep 5
  done
}
esperar "$EC2_IP" 3 8080 8081 8082
salud "$EC2_IP" 8080 8081 8082 || echo "sin SSH a la EC2 backend: infra/ssh-mi-ip.sh"
if [ -n "${MQ_IP:-}" ]; then
  esperar "$MQ_IP" 2 8083 8084
  salud "$MQ_IP" 8083 8084 || echo "sin SSH a la EC2 de mensajería: infra/10-rabbitmq-ec2.sh"
fi
if [ -n "${KAFKA_IP:-}" ]; then
  # Los brokers esperan a los tres Zookeeper y los microservicios a los tres brokers: tarda un par de minutos.
  esperar "$KAFKA_IP" 2 8085 8086
  salud "$KAFKA_IP" 8085 8086 || echo "sin SSH a la EC2 de streaming: infra/12-kafka-ec2.sh"
  ssh_en "$KAFKA_IP" "docker exec pedidos360-kafka-zookeeper1-1 zookeeper-shell localhost:2181 ls /brokers/ids 2>/dev/null | tail -1 | sed 's/^/brokers: /'" || true
fi
