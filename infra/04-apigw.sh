#!/usr/bin/env bash
# API Gateway HTTP API: CORS, Cognito JWT authorizer and one explicit route per endpoint,
# each requiring the OAuth scope that the microservice also enforces.
source "$(dirname "$0")/lib.sh"

log "HTTP API + CORS"
if [ -z "${API_ID:-}" ]; then
  API_ID=$(aws apigatewayv2 create-api --name $PROJECT-api --protocol-type HTTP \
    --tags Project=$PROJECT --query ApiId --output text)
  save API_ID "$API_ID"
fi
# Only the frontend origins, only the methods/headers the SPA uses, no credentials (Bearer header, not cookies).
aws apigatewayv2 update-api --api-id "$API_ID" --cors-configuration \
  "AllowOrigins=$FRONTEND_URL,http://localhost:4200,AllowMethods=GET,POST,PUT,PATCH,DELETE,AllowHeaders=authorization,content-type,MaxAge=3600,AllowCredentials=false" >/dev/null
save API_URL "https://$API_ID.execute-api.$AWS_REGION.amazonaws.com"
echo "API_URL=$API_URL"

log "JWT authorizer (Cognito issuer + app client as audience)"
if [ -z "${AUTHORIZER_ID:-}" ]; then
  AUTHORIZER_ID=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name cognito-jwt \
    --authorizer-type JWT --identity-source '$request.header.Authorization' \
    --jwt-configuration "Audience=$COGNITO_CLIENT_ID,Issuer=$COGNITO_ISSUER_URI" \
    --query AuthorizerId --output text)
  save AUTHORIZER_ID "$AUTHORIZER_ID"
fi

log "Integrations (one per microservice, original path forwarded)"
integration() {  # name port
  local var="INTEG_$1"
  if [ -z "${!var:-}" ]; then
    local id
    id=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type HTTP_PROXY \
      --integration-method ANY --integration-uri "http://$EC2_IP:$2" \
      --payload-format-version 1.0 --timeout-in-millis 15000 \
      --request-parameters 'overwrite:path=$request.path' \
      --description "$1 on EC2 :$2" --query IntegrationId --output text)
    save "$var" "$id"
  fi
}
integration bff 8080
integration productos 8081
integration pedidos 8082

log "Routes"
EXISTING=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query 'Items[].RouteKey' --output text)
route() {  # "METHOD /path" integration scope
  if grep -qxF "$1" <<<"$(tr '\t' '\n' <<<"$EXISTING")"; then
    echo "  = $1"; return
  fi
  local integ="INTEG_$2" scope_args=()
  [ -n "$3" ] && scope_args=(--authorization-scopes "$3")
  aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$1" \
    --target "integrations/${!integ}" --authorization-type JWT --authorizer-id "$AUTHORIZER_ID" \
    "${scope_args[@]}" >/dev/null
  echo "  + $1 -> $2 ${3:-(solo token valido)}"
}
route "GET /api/bff/me"                   bff       ""
route "GET /api/bff/resumen"              bff       "pedidos360/pedidos.read"
route "GET /api/productos"                productos "pedidos360/productos.read"
route "GET /api/productos/{id}"           productos "pedidos360/productos.read"
route "POST /api/productos"               productos "pedidos360/productos.write"
route "PUT /api/productos/{id}"           productos "pedidos360/productos.write"
route "DELETE /api/productos/{id}"        productos "pedidos360/productos.write"
route "GET /api/pedidos"                  pedidos   "pedidos360/pedidos.read"
route "GET /api/pedidos/{id}"             pedidos   "pedidos360/pedidos.read"
route "POST /api/pedidos"                 pedidos   "pedidos360/pedidos.write"
route "PATCH /api/pedidos/{id}/estado"    pedidos   "pedidos360/pedidos.write"
route "POST /api/pedidos/{id}/cancelar"   pedidos   "pedidos360/pedidos.write"

log "Stage \$default (auto deploy, throttling)"
if ! aws apigatewayv2 get-stage --api-id "$API_ID" --stage-name '$default' >/dev/null 2>&1; then
  aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy \
    --default-route-settings ThrottlingBurstLimit=50,ThrottlingRateLimit=20 >/dev/null
fi
echo "Listo: $API_URL"
