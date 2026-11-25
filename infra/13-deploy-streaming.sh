#!/usr/bin/env bash
# Despliega deploy/streaming en la EC2 de streaming: compila los dos microservicios, copia el compose, escribe el
# .env (fuera de git) y ejecuta docker compose up.
# SOLO_KAFKA=1 levanta únicamente Zookeeper, los brokers y Kafka UI (sin compilar Java).
source "$(dirname "$0")/lib.sh"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}
ROOT="$INFRA_DIR/.."
SRC="$ROOT/deploy/streaming"
DEST=/opt/pedidos360-kafka
SERVICIOS=(ms-analitica ms-streaming-admin)
SSH=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o LogLevel=ERROR "ec2-user@$KAFKA_IP")

log "Esperando Docker en la EC2 (user data)"
for i in $(seq 1 40); do
  "${SSH[@]}" 'docker compose version' >/dev/null 2>&1 && break
  sleep 5
done
"${SSH[@]}" 'docker compose version'

if [ "${SOLO_KAFKA:-0}" != 1 ]; then
  log "Compilación"
  for svc in "${SERVICIOS[@]}"; do
    (cd "$ROOT/backend/$svc" && ./mvnw -q -B -DskipTests package)
  done
fi

log "Archivos"
"${SSH[@]}" "mkdir -p $DEST/jars"
scp -q -i "$KEY_FILE" "$SRC/docker-compose.yml" "$SRC/Dockerfile.java" "ec2-user@$KAFKA_IP:$DEST/"
if [ "${SOLO_KAFKA:-0}" != 1 ]; then
  for svc in "${SERVICIOS[@]}"; do
    scp -q -i "$KEY_FILE" "$ROOT/backend/$svc/target/$svc-0.0.1-SNAPSHOT.jar" "ec2-user@$KAFKA_IP:$DEST/jars/$svc.jar"
  done
fi
# KAFKA_HOST es lo que anuncia el listener EXTERNAL: la IP privada, que es la que alcanza ms-pedidos.
"${SSH[@]}" "umask 077 && cat > $DEST/.env" <<ENV
KAFKA_HOST=$KAFKA_PRIVATE_IP
KAFKA_UI_USER=$KAFKA_UI_USER
KAFKA_UI_PASSWORD=$KAFKA_UI_PASSWORD
COGNITO_ISSUER_URI=$COGNITO_ISSUER_URI
COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
ENV

log "docker compose up"
if [ "${SOLO_KAFKA:-0}" = 1 ]; then
  "${SSH[@]}" "cd $DEST && docker compose up -d zookeeper1 zookeeper2 zookeeper3 kafka1 kafka2 kafka3 kafka-ui"
else
  "${SSH[@]}" "cd $DEST && docker compose up -d --build --remove-orphans"
fi

log "Brokers registrados en Zookeeper"
for i in $(seq 1 40); do
  ids=$("${SSH[@]}" "docker exec pedidos360-kafka-zookeeper1-1 zookeeper-shell localhost:2181 ls /brokers/ids 2>/dev/null | tail -1")
  [ "$ids" = "[1, 2, 3]" ] && break
  sleep 5
done
echo "brokers: $ids"
"${SSH[@]}" "cd $DEST && docker compose ps --format 'table {{.Service}}\t{{.Status}}'"
echo "Kafka UI: http://$KAFKA_IP:8090 (usuario $KAFKA_UI_USER, clave en state.env)"
