#!/usr/bin/env bash
# Demo y evidencia de las pruebas de streaming contra el clúster real (guía EP3, sección 6):
#   1 plataforma · 2 tópicos · 3 misma key → misma partición · 4 dos grupos con offsets independientes
#   5 consumidor detenido: el lag sube y se pone al día · 6 rebalance · 7 caída de un broker · 8 retención
#   9 RabbitMQ sigue igual
# Uso: infra/demo-kafka.sh [paso...]   (sin argumentos corre todos; PAUSA=1 espera Enter entre pasos)
source "$(dirname "$0")/lib.sh" >/dev/null
set +e
DIR="$(dirname "$0")"
PASOS=${*:-1 2 3 4 5 6 7 8 9}
KF="openid email profile pedidos360/pedidos.read pedidos360/streaming.admin"
TOKEN=$(SCOPE="$KF" "$DIR/get-token.sh" admin "$ADMIN_PASSWORD")
H=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
SSH=(ssh -i "$KEY_FILE" -o LogLevel=ERROR "ec2-user@$KAFKA_IP")
K="docker exec pedidos360-kafka-kafka1-1"
BS="--bootstrap-server kafka1:29092,kafka2:29092,kafka3:29092"

titulo() { printf '\n\033[1;33m== %s\033[0m\n' "$*"; [ "${PAUSA:-0}" = 1 ] && read -r -p "(Enter)"; }
remoto() { "${SSH[@]}" "$@"; }
api() { curl -s "${H[@]}" "$@"; }
grupo() { remoto "$K kafka-consumer-groups $BS --describe --group $1 2>/dev/null" | awk 'NF && $1!="" {print}' | cut -c1-130; }
publicar() { api -X POST "$API_URL/api/streaming/events" -d "{\"escenario\":\"$1\"${2:+,\"cantidad\":$2}}"; }
consumidores() { api -X PUT "$API_URL/api/analitica/consumidores/$1" -d "{\"consumidores\":$2}" | jq -c '{grupo, consumidores, asignaciones}'; }

for paso in $PASOS; do
  case $paso in
  1)
    titulo "1 · Plataforma: docker compose en la EC2 de streaming"
    remoto "cd /opt/pedidos360-kafka && docker compose ps --format 'table {{.Service}}\t{{.Image}}\t{{.Status}}'"
    echo; echo "Ensemble de Zookeeper (un líder, dos seguidores):"
    for z in 1 2 3; do
      printf '  zookeeper%s: ' "$z"
      remoto "docker exec pedidos360-kafka-zookeeper$z-1 bash -c 'exec 3<>/dev/tcp/localhost/2181; echo srvr >&3; grep Mode <&3'"
    done
    printf 'Brokers registrados en Zookeeper: '
    remoto "docker exec pedidos360-kafka-zookeeper1-1 zookeeper-shell localhost:2181 ls /brokers/ids 2>/dev/null | tail -1"
    printf 'Controlador: '
    remoto "docker exec pedidos360-kafka-zookeeper1-1 zookeeper-shell localhost:2181 get /controller 2>/dev/null | tail -1"
    ;;
  2)
    titulo "2 · Los dos tópicos: 3 particiones, 3 réplicas, líderes repartidos e ISR completo"
    for t in pedidos.actividad ventas.metricas; do
      remoto "$K kafka-topics $BS --describe --topic $t" | sed 's/\tElr: N\/A\tLastKnownElr: N\/A//'
      echo
    done
    ;;
  3)
    titulo "3 · Misma key, misma partición: los eventos de un pedido llegan en orden"
    publicar VALIDO | jq -r '.[] | "  key=\(.key)  partición=\(.particion)  offset=\(.offset)  \(.descripcion)"'
    publicar VALIDO | jq -r '.[] | "  key=\(.key)  partición=\(.particion)  offset=\(.offset)  \(.descripcion)"'
    ;;
  4)
    titulo "4 · Dos grupos sobre el mismo tópico: cada uno con su propio offset"
    sleep 3
    grupo analitica-ventas
    echo
    grupo seguimiento-pedidos
    ;;
  5)
    titulo "5 · Consumidor detenido: el lag sube; al volver, se pone al día sin perder eventos"
    echo "Detengo seguimiento-pedidos:"; consumidores seguimiento-pedidos 0
    echo "Publico un lote de 20 pedidos (60 eventos):"
    publicar LOTE 20 | jq -r 'group_by(.particion) | map("  partición \(.[0].particion): \(length) eventos") | .[]'
    sleep 3
    echo "Lag con el grupo detenido:"; grupo seguimiento-pedidos
    echo; echo "Vuelvo a encender el grupo con 3 consumidores:"; consumidores seguimiento-pedidos 3
    sleep 12
    echo "Lag después de ponerse al día:"; grupo seguimiento-pedidos
    ;;
  6)
    titulo "6 · Rebalance: con 1 consumidor, uno solo atiende las 3 particiones"
    consumidores analitica-ventas 1
    sleep 8
    remoto "$K kafka-consumer-groups $BS --describe --group analitica-ventas --members --verbose 2>/dev/null" | cut -c1-150
    echo; echo "Vuelvo a 3 consumidores (uno por partición):"; consumidores analitica-ventas 3
    sleep 8
    remoto "$K kafka-consumer-groups $BS --describe --group analitica-ventas --members --verbose 2>/dev/null" | cut -c1-150
    ;;
  7)
    titulo "7 · Caída de un broker: se reelige el líder y el sistema sigue operando"
    echo "Antes:"; remoto "$K kafka-topics $BS --describe --topic pedidos.actividad" | grep Partition: | sed 's/\tElr.*//'
    echo; echo "docker stop kafka2"; remoto "docker stop pedidos360-kafka-kafka2-1 >/dev/null"
    sleep 8
    echo "Con kafka2 caído (líder reelegido, ISR con 2 réplicas):"
    remoto "$K kafka-topics --bootstrap-server kafka1:29092 --describe --topic pedidos.actividad" | grep Partition: | sed 's/\tElr.*//'
    echo; echo "Publicar sigue funcionando (acks=all con min.insync.replicas=2):"
    publicar VALIDO | jq -r '.[] | "  key=\(.key)  partición=\(.particion)  offset=\(.offset)  \(.descripcion)"'
    echo; echo "docker start kafka2"; remoto "docker start pedidos360-kafka-kafka2-1 >/dev/null"
    for i in $(seq 1 24); do
      isr=$(remoto "$K kafka-topics --bootstrap-server kafka1:29092 --describe --topic pedidos.actividad --under-replicated-partitions" | grep -c Partition:)
      [ "$isr" = 0 ] && break
      sleep 5
    done
    echo "Después (la réplica se sincronizó y volvió al ISR):"
    remoto "$K kafka-topics $BS --describe --topic pedidos.actividad" | grep Partition: | sed 's/\tElr.*//'
    ;;
  8)
    titulo "8 · Retención de cada tópico"
    for t in pedidos.actividad ventas.metricas pedidos.actividad.DLT; do
      echo "$t:"
      # Solo las configuraciones propias del tópico (la primera palabra de cada línea), no los valores por defecto.
      remoto "$K kafka-configs $BS --describe --entity-type topics --entity-name $t" | awk '/^  /{print "  " $1}'
    done
    ;;
  9)
    titulo "9 · RabbitMQ sigue funcionando igual (EC2 de mensajería)"
    ssh -i "$KEY_FILE" -o LogLevel=ERROR "ec2-user@$MQ_IP" \
      'docker exec pedidos360-mq-rabbit1-1 rabbitmqctl -q cluster_status | grep -A3 "Running Nodes"; docker exec pedidos360-mq-rabbit1-1 rabbitmqctl -q list_queues name type messages consumers'
    ;;
  esac
done
