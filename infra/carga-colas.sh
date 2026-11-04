#!/usr/bin/env bash
# Pruebas de carga sobre la mensajería. Tres modos:
#
#   ./carga-colas.sh perftest [segundos] [msg/s por productor]
#       PerfTest (herramienta oficial de RabbitMQ) corre como contenedor en la EC2 de mensajería, dentro de la
#       red de docker compose. Publica y consume en una cola quorum de prueba (carga.perftest), conectado a
#       los dos nodos. No toca las colas del sistema ni la base de datos. Mientras corre, la consola de
#       RabbitMQ (puerto 15672) muestra la tasa en Overview y en Queues.
#
#   ./carga-colas.sh api [mensajes] [paralelo]
#       Camino completo: API Gateway (JWT con scope mensajeria.admin) → ms-mensajeria-admin → RabbitMQ.
#       Crea con la API un exchange direct, una cola quorum con DLQ y su binding (carga.*), publica N
#       mensajes en paralelo, muestra cuántos llegaron y deja todo eliminado al final.
#
#   ./carga-colas.sh dlq [mensajes]
#       Publica N mensajes con JSON inválido en pedidos.eventos (routing key pedido.carga). Los consumidores
#       de notificaciones y auditoría los rechazan sin reintento y terminan en sus DLQ, sin crear registros en
#       la base. Sirve para ver la DLQ llenarse; después se vacía o reprocesa desde Admin → Mensajería.
source "$(dirname "$0")/lib.sh" >/dev/null
DIR="$(dirname "$0")"
MODO=${1:-}
PERFTEST_IMAGEN=pivotalrabbitmq/perf-test:2.23.0
SSH=(ssh -i "$KEY_FILE" -o StrictHostKeyChecking=accept-new -o LogLevel=ERROR "ec2-user@$MQ_IP")

token_admin() {
  SCOPE="openid pedidos360/mensajeria.admin" "$DIR/get-token.sh" admin "$ADMIN_PASSWORD"
}

api() {  # método ruta [body] -> imprime "código cuerpo"
  local args=(-s -w '\n%{http_code}' -X "$1" "$API_URL$2" -H "Authorization: Bearer $TOKEN")
  [ -n "${3:-}" ] && args+=(-H 'Content-Type: application/json' -d "$3")
  local salida; salida=$(curl "${args[@]}")
  echo "$(tail -n1 <<<"$salida") $(sed '$d' <<<"$salida" | head -c 160)"
}

contar() {  # cola -> mensajes según la API de management (los contadores tardan ~5 s en actualizarse)
  curl -s "$API_URL/api/mensajeria/queues/$1" -H "Authorization: Bearer $TOKEN" \
    | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("mensajes", d))'
}

case "$MODO" in
perftest)
  SEGUNDOS=${2:-60}; TASA=${3:-500}
  log "PerfTest: 2 productores x $TASA msg/s, 2 consumidores, $SEGUNDOS s, cola quorum carga.perftest"
  # -f persistent + -c confirms: el mismo trato que reciben los eventos reales (persistentes y confirmados).
  "${SSH[@]}" "docker run --rm --network pedidos360-mq_default $PERFTEST_IMAGEN \
    --uris amqp://$MQ_USER:$MQ_PASSWORD@rabbit1:5672,amqp://$MQ_USER:$MQ_PASSWORD@rabbit2:5672 \
    --queue carga.perftest --quorum-queue --routing-key carga.perftest \
    --producers 2 --consumers 2 --rate $TASA --size 512 --flag persistent --confirm 100 \
    --time $SEGUNDOS --interval 5 --id carga"
  log "Limpieza de la cola de prueba"
  "${SSH[@]}" 'docker exec pedidos360-mq-rabbit1-1 rabbitmqctl -q delete_queue carga.perftest' || true
  ;;
api)
  N=${2:-200}; PARALELO=${3:-10}
  TOKEN=$(token_admin)
  log "Topología de prueba creada con la API"
  echo "exchange  $(api POST /api/mensajeria/exchanges '{"nombre":"carga.directo","tipo":"DIRECT"}')"
  echo "cola      $(api POST /api/mensajeria/queues '{"nombre":"carga.api","tipo":"QUORUM","conDlq":true}')"
  echo "binding   $(api POST /api/mensajeria/bindings '{"exchange":"carga.directo","cola":"carga.api","routingKey":"carga.api"}')"

  log "Publicando $N mensajes ($PARALELO en paralelo) a través de API Gateway"
  export API_URL TOKEN
  INICIO=$(python3 -c 'import time;print(time.time())')
  # xargs -n1 (no -I: en macOS -I limita el largo del comando) pasa cada número como $1 al worker.
  seq 1 "$N" | xargs -n1 -P "$PARALELO" sh -c 'curl -s -o /dev/null -w "%{http_code}\n" -X POST "$API_URL/api/mensajeria/messages" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"exchange\":\"carga.directo\",\"routingKey\":\"carga.api\",\"cuerpo\":\"{\\\"n\\\":$1}\"}"' _ \
    | sort | uniq -c | sed 's/^/  HTTP /'
  python3 -c 'import sys,time;s=time.time()-float(sys.argv[1]);print(f"  {int(sys.argv[2])} mensajes en {s:.1f} s ({int(sys.argv[2])/s:.0f} req/s)")' "$INICIO" "$N"

  sleep 6
  echo "  mensajes en carga.api: $(contar carga.api)"

  log "Limpieza con la API"
  echo "binding   $(api DELETE /api/mensajeria/bindings '{"exchange":"carga.directo","cola":"carga.api","routingKey":"carga.api"}')"
  echo "vaciar    $(api DELETE /api/mensajeria/queues/carga.api/messages)"
  echo "cola      $(api DELETE "/api/mensajeria/queues/carga.api?soloSiVacia=false")"
  echo "dlq       $(api DELETE /api/mensajeria/queues/carga.api.dlq)"
  echo "exchange  $(api DELETE /api/mensajeria/exchanges/carga.directo)"
  ;;
dlq)
  N=${2:-20}
  TOKEN=$(token_admin)
  log "Publicando $N mensajes inválidos en pedidos.eventos (pedido.carga)"
  for i in $(seq 1 "$N"); do
    api POST /api/mensajeria/messages \
      "{\"exchange\":\"pedidos.eventos\",\"routingKey\":\"pedido.carga\",\"cuerpo\":\"esto no es un evento $i\"}" >/dev/null
  done
  sleep 6
  echo "  notificaciones.pedidos.dlq: $(contar notificaciones.pedidos.dlq)"
  echo "  auditoria.eventos.dlq:      $(contar auditoria.eventos.dlq)"
  echo "  Se vacían o reprocesan desde Admin → Mensajería, o con DELETE /api/mensajeria/queues/<dlq>/messages"
  ;;
*)
  sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
  ;;
esac
