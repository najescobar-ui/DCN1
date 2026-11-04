#!/usr/bin/env bash
# API Gateway HTTP API: CORS, Cognito JWT authorizer and one explicit route per endpoint, each requiring the
# OAuth scope that the microservice also enforces. Todas las rutas se integran con el BFF, que valida el JWT
# otra vez, aplica la autorización por rol y reenvía al microservicio correspondiente.
source "$(dirname "$0")/lib.sh"

log "HTTP API + CORS"
if [ -z "${API_ID:-}" ]; then
  API_ID=$(aws apigatewayv2 create-api --name $PROJECT-api --protocol-type HTTP \
    --tags Project=$PROJECT --query ApiId --output text)
  save API_ID "$API_ID"
fi
# Only the frontend origins, only the methods/headers the SPA uses, no credentials (Bearer header, not cookies).
# Production: only the main site. CORS_DEV=1 also allows the preview and local development.
ORIGINS="$FRONTEND_URL"
[ "${CORS_DEV:-0}" = 1 ] && ORIGINS="$ORIGINS,https://preview.$AMPLIFY_APP_ID.amplifyapp.com,http://localhost:4200"
aws apigatewayv2 update-api --api-id "$API_ID" --cors-configuration \
  "AllowOrigins=$ORIGINS,AllowMethods=GET,POST,PUT,PATCH,DELETE,AllowHeaders=authorization,content-type,MaxAge=3600,AllowCredentials=false" >/dev/null
echo "CORS origins: $ORIGINS"
save API_URL "https://$API_ID.execute-api.$AWS_REGION.amazonaws.com"
echo "API_URL=$API_URL"

log "JWT authorizer (Cognito issuer + app client as audience)"
if [ -z "${AUTHORIZER_ID:-}" ]; then
  AUTHORIZER_ID=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name cognito-jwt \
    --authorizer-type JWT --identity-source '$request.header.Authorization' \
    --jwt-configuration "Audience=$COGNITO_CLIENT_ID,Issuer=$COGNITO_ISSUER_URI" \
    --query AuthorizerId --output text)
  save AUTHORIZER_ID "$AUTHORIZER_ID"
else
  # Keeps the authorizer in sync if the user pool or app client changed.
  aws apigatewayv2 update-authorizer --api-id "$API_ID" --authorizer-id "$AUTHORIZER_ID" \
    --jwt-configuration "Audience=$COGNITO_CLIENT_ID,Issuer=$COGNITO_ISSUER_URI" >/dev/null
fi

log "Integración: todo entra por el BFF (EC2 backend, puerto 8080), que reenvía a cada microservicio"
integration() {  # name port [host]
  local var="INTEG_$1" host=${3:-$EC2_IP}
  if [ -z "${!var:-}" ]; then
    local id
    id=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type HTTP_PROXY \
      --integration-method ANY --integration-uri "http://$host:$2" \
      --payload-format-version 1.0 --timeout-in-millis 15000 \
      --request-parameters 'overwrite:path=$request.path' \
      --description "$1 on $host:$2" --query IntegrationId --output text)
    save "$var" "$id"
  fi
}
integration bff 8080

log "Routes"
EXISTING=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query 'Items[].[RouteKey,RouteId,Target]' --output text)
route() {  # "METHOD /path" scope|PUBLIC   (todas apuntan a la integración del BFF)
  local target="integrations/$INTEG_bff" fila id actual
  fila=$(awk -F'\t' -v k="$1" '$1==k' <<<"$EXISTING")
  if [ -n "$fila" ]; then
    id=$(cut -f2 <<<"$fila"); actual=$(cut -f3 <<<"$fila")
    if [ "$actual" != "$target" ]; then
      aws apigatewayv2 update-route --api-id "$API_ID" --route-id "$id" --target "$target" >/dev/null
      echo "  ~ $1 -> bff"
    else
      echo "  = $1"
    fi
    return
  fi
  local auth_args=(--authorization-type JWT --authorizer-id "$AUTHORIZER_ID")
  if [ "$2" = PUBLIC ]; then
    auth_args=(--authorization-type NONE)
  elif [ -n "$2" ]; then
    auth_args+=(--authorization-scopes "$2")
  fi
  aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$1" --target "$target" "${auth_args[@]}" >/dev/null
  echo "  + $1 -> bff ${2:-(solo token valido)}"
}
route "GET /api/bff/me"                   ""
route "GET /api/bff/resumen"              "pedidos360/pedidos.read"
route "GET /api/productos"                "pedidos360/productos.read"
route "GET /api/productos/{id}"           "pedidos360/productos.read"
route "POST /api/productos"               "pedidos360/productos.write"
route "PUT /api/productos/{id}"           "pedidos360/productos.write"
route "DELETE /api/productos/{id}"        "pedidos360/productos.write"
route "GET /api/pedidos"                  "pedidos360/pedidos.read"
route "GET /api/pedidos/{id}"             "pedidos360/pedidos.read"
route "POST /api/pedidos"                 "pedidos360/pedidos.write"
route "PATCH /api/pedidos/{id}/estado"    "pedidos360/pedidos.write"
route "POST /api/pedidos/{id}/cancelar"   "pedidos360/pedidos.write"
route "PATCH /api/pedidos/{id}/pago"      "pedidos360/pedidos.write"
route "POST /api/pedidos/{id}/pago/webpay" "pedidos360/pedidos.write"
# Lo que generan los consumidores de RabbitMQ (el CLIENTE ve lo suyo, el ADMIN todo; auditoría solo ADMIN).
route "GET /api/notificaciones"           "pedidos360/pedidos.read"
route "GET /api/comprobantes"             "pedidos360/pedidos.read"
route "GET /api/auditoria"                "pedidos360/pedidos.read"
# Administración de RabbitMQ: además del scope, ms-mensajeria-admin exige el grupo ADMIN.
MQ_SCOPE="pedidos360/mensajeria.admin"
route "GET /api/mensajeria/cluster"                     "$MQ_SCOPE"
route "GET /api/mensajeria/queues"                      "$MQ_SCOPE"
route "POST /api/mensajeria/queues"                     "$MQ_SCOPE"
route "GET /api/mensajeria/queues/{nombre}"             "$MQ_SCOPE"
route "DELETE /api/mensajeria/queues/{nombre}"          "$MQ_SCOPE"
route "DELETE /api/mensajeria/queues/{nombre}/messages" "$MQ_SCOPE"
route "POST /api/mensajeria/queues/{nombre}/reprocess"  "$MQ_SCOPE"
route "GET /api/mensajeria/exchanges"                   "$MQ_SCOPE"
route "POST /api/mensajeria/exchanges"                  "$MQ_SCOPE"
route "GET /api/mensajeria/exchanges/{nombre}"          "$MQ_SCOPE"
route "DELETE /api/mensajeria/exchanges/{nombre}"       "$MQ_SCOPE"
route "GET /api/mensajeria/bindings"                    "$MQ_SCOPE"
route "POST /api/mensajeria/bindings"                   "$MQ_SCOPE"
route "DELETE /api/mensajeria/bindings"                 "$MQ_SCOPE"
route "POST /api/mensajeria/messages"                   "$MQ_SCOPE"
# Mensajes no entregados que registraron los listeners de las DLQ (ms-notificaciones; exige ADMIN).
route "GET /api/mensajes-fallidos"                      "$MQ_SCOPE"
route "POST /api/mensajes-fallidos/{id}/reprocesar"     "$MQ_SCOPE"
# Webpay sends the customer's browser back here (GET or form POST) without a bearer token;
# ms-pedidos validates the transaction token directly with Transbank.
route "GET /api/pagos/webpay/retorno"     PUBLIC
route "POST /api/pagos/webpay/retorno"    PUBLIC

log "Integraciones directas a los microservicios (de la versión anterior): se eliminan"
for nombre in productos pedidos notificaciones mensajeria; do
  var="INTEG_$nombre"
  if [ -n "${!var:-}" ]; then
    if aws apigatewayv2 delete-integration --api-id "$API_ID" --integration-id "${!var}" >/dev/null 2>&1; then
      echo "  - $nombre"
    fi
    grep -v "^$var=" "$STATE_FILE" > "$STATE_FILE.tmp" && mv "$STATE_FILE.tmp" "$STATE_FILE"
  fi
done

log "Stage \$default (auto deploy, throttling)"
if ! aws apigatewayv2 get-stage --api-id "$API_ID" --stage-name '$default' >/dev/null 2>&1; then
  aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy \
    --default-route-settings ThrottlingBurstLimit=50,ThrottlingRateLimit=20 >/dev/null
fi
echo "Listo: $API_URL"
