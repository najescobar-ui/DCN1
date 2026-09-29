#!/usr/bin/env bash
# End-to-end evidence through API Gateway: every route with no token, bad token, CLIENTE and ADMIN.
source "$(dirname "$0")/lib.sh" >/dev/null
DIR="$(dirname "$0")"
CLIENTE=$("$DIR/get-token.sh" cliente1 "$CLIENTE_PASSWORD")
ADMIN=$("$DIR/get-token.sh" admin "$ADMIN_PASSWORD")
TAMPERED="${CLIENTE%?}$([ "${CLIENTE: -1}" = A ] && echo B || echo A)"

claims() { python3 -c 'import sys,json,base64;p=sys.argv[1].split(".")[1];p+="="*(-len(p)%4);d=json.loads(base64.urlsafe_b64decode(p));print(json.dumps({k:d.get(k) for k in ("token_use","client_id","username","cognito:groups","scope")}))' "$1"; }
echo "cliente1 access token: $(claims "$CLIENTE")"
echo "admin    access token: $(claims "$ADMIN")"

PASS=0; FAIL=0
check() {  # expected label method path token [body]
  local args=(-s -o /tmp/pedidos360-body -w '%{http_code}' -X "$3" "$API_URL$4")
  [ -n "$5" ] && args+=(-H "Authorization: Bearer $5")
  [ -n "${6:-}" ] && args+=(-H 'Content-Type: application/json' -d "$6")
  local got; got=$(curl "${args[@]}")
  local body; body=$(head -c 110 /tmp/pedidos360-body | tr '\n' ' ')
  if [ "$got" = "$1" ]; then PASS=$((PASS+1)); mark=OK; else FAIL=$((FAIL+1)); mark=XX; fi
  printf '%s %s (esperado %s) %-9s %-7s %-32s %s\n' "$mark" "$got" "$1" "$2" "$3" "$4" "$body"
}

echo; echo "--- Sin token / token invalido (rechaza API Gateway)"
check 401 sin-token GET /api/productos ""
check 401 sin-token GET /api/pedidos ""
check 401 sin-token GET /api/bff/me ""
check 401 alterado  GET /api/productos "$TAMPERED"

echo; echo "--- CLIENTE"
check 200 cliente GET    /api/bff/me "$CLIENTE"
check 200 cliente GET    /api/productos "$CLIENTE"
check 200 cliente GET    /api/productos/1 "$CLIENTE"
check 403 cliente POST   /api/productos "$CLIENTE" '{"nombre":"X","precio":1000,"stock":1}'
check 403 cliente DELETE /api/productos/1 "$CLIENTE"
NUEVO=$(curl -s -X POST "$API_URL/api/pedidos" -H "Authorization: Bearer $CLIENTE" -H 'Content-Type: application/json' \
  -d '{"items":[{"productoId":1,"cantidad":2},{"productoId":4,"cantidad":1}],"direccionEntrega":"Av. Providencia 1234"}')
PEDIDO_ID=$(python3 -c 'import sys,json;print(json.loads(sys.argv[1])["id"])' "$NUEVO")
echo "OK 201 pedido creado #$PEDIDO_ID: $(head -c 110 <<<"$NUEVO")"
check 200 cliente GET    /api/pedidos "$CLIENTE"
check 200 cliente GET    "/api/pedidos/$PEDIDO_ID" "$CLIENTE"
check 403 cliente PATCH  "/api/pedidos/$PEDIDO_ID/estado" "$CLIENTE" '{"estado":"DESPACHADO"}'
check 200 cliente GET    /api/bff/resumen "$CLIENTE"
check 400 cliente POST   /api/pedidos "$CLIENTE" '{"items":[]}'

echo; echo "--- ADMIN"
check 200 admin GET    /api/pedidos "$ADMIN"
check 200 admin PATCH  "/api/pedidos/$PEDIDO_ID/estado" "$ADMIN" '{"estado":"CONFIRMADO"}'
check 201 admin POST   /api/productos "$ADMIN" '{"nombre":"Empanada de pino","descripcion":"Horneada","precio":2500,"stock":30}'
check 200 admin PUT    /api/productos/5 "$ADMIN" '{"nombre":"Brownie","descripcion":"Brownie con nueces","precio":3190,"stock":60}'
check 404 admin GET    /api/productos/9999 "$ADMIN"

echo; echo "Resultado: $PASS OK, $FAIL con diferencias"
