#!/usr/bin/env bash
# Escribe frontend/.env.production y frontend/.env.development con los valores de Cognito y API Gateway
# guardados en state.env. Se corre después de 02 y 04, y cada vez que cambie el user pool o la API.
source "$(dirname "$0")/lib.sh"
FRONT="$INFRA_DIR/../frontend"
SCOPE="openid email profile pedidos360/productos.read pedidos360/productos.write pedidos360/pedidos.read pedidos360/pedidos.write"

escribir() {  # archivo comentario
  cat > "$FRONT/$1" <<ENV
# $2
VITE_API_URL=$API_URL
VITE_COGNITO_AUTHORITY=$COGNITO_ISSUER_URI
VITE_COGNITO_DOMAIN=$COGNITO_DOMAIN
VITE_COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
VITE_COGNITO_SCOPE=$SCOPE
ENV
  echo "  $1"
}

log "Variables del frontend"
escribir .env.production "Public client settings (no secrets). Production build."
escribir .env.development "Public client settings (no secrets). Local dev against the AWS API; to use local backends create .env.development.local with VITE_API_URL= (empty, Vite proxy)."
echo "El token de Mapbox (opcional) va aparte en frontend/.env.production.local y .env.development.local: VITE_MAPBOX_TOKEN=pk...."
