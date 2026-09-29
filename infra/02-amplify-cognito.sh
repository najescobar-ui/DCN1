#!/usr/bin/env bash
# Amplify Hosting app (HTTPS URL for the SPA) and the Cognito tenant:
# user pool, groups, resource server + scopes, app client (Auth Code + PKCE), domain, test users.
source "$(dirname "$0")/lib.sh"

log "Amplify app (frontend hosting over HTTPS; CloudFront is not available in the lab)"
if [ -z "${AMPLIFY_APP_ID:-}" ]; then
  SPA_RULE='[{"source":"</^[^.]+$|\\.(?!(css|gif|ico|jpg|js|png|txt|svg|woff|woff2|ttf|map|json|webp)$)([^.]+$)/>","target":"/index.html","status":"200"}]'
  AMPLIFY_APP_ID=$(aws amplify create-app --name $PROJECT-frontend --platform WEB \
    --custom-rules "$SPA_RULE" --query app.appId --output text)
  save AMPLIFY_APP_ID "$AMPLIFY_APP_ID"
  aws amplify create-branch --app-id "$AMPLIFY_APP_ID" --branch-name main --stage PRODUCTION >/dev/null
fi
save FRONTEND_URL "https://main.$AMPLIFY_APP_ID.amplifyapp.com"
echo "FRONTEND_URL=$FRONTEND_URL"

log "User pool"
if [ -z "${USER_POOL_ID:-}" ]; then
  USER_POOL_ID=$(aws cognito-idp create-user-pool --pool-name $PROJECT-users \
    --alias-attributes email \
    --auto-verified-attributes email \
    --username-configuration CaseSensitive=false \
    --schema Name=email,Required=true,Mutable=true \
    --policies 'PasswordPolicy={MinimumLength=8,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
    --account-recovery-setting 'RecoveryMechanisms=[{Priority=1,Name=verified_email}]' \
    --user-pool-tags Project=$PROJECT \
    --query UserPool.Id --output text)
  save USER_POOL_ID "$USER_POOL_ID"
fi
save COGNITO_ISSUER_URI "https://cognito-idp.$AWS_REGION.amazonaws.com/$USER_POOL_ID"
echo "USER_POOL_ID=$USER_POOL_ID"

log "Groups (roles)"
for g in ADMIN:1:"Administradores de Pedidos360" CLIENTE:10:"Clientes de Pedidos360"; do
  IFS=: read -r name prec desc <<<"$g"
  aws cognito-idp create-group --user-pool-id "$USER_POOL_ID" --group-name "$name" \
    --precedence "$prec" --description "$desc" >/dev/null 2>&1 || true
done

log "Resource server + scopes"
aws cognito-idp create-resource-server --user-pool-id "$USER_POOL_ID" --identifier pedidos360 --name "Pedidos360 API" \
  --scopes ScopeName=productos.read,ScopeDescription="Leer catalogo" \
           ScopeName=productos.write,ScopeDescription="Administrar catalogo" \
           ScopeName=pedidos.read,ScopeDescription="Leer pedidos" \
           ScopeName=pedidos.write,ScopeDescription="Crear y modificar pedidos" >/dev/null 2>&1 || true

log "App client (public SPA, Authorization Code + PKCE)"
SCOPES="openid email profile pedidos360/productos.read pedidos360/productos.write pedidos360/pedidos.read pedidos360/pedidos.write"
PREVIEW_URL="https://preview.$AMPLIFY_APP_ID.amplifyapp.com"
# Full client settings: update-user-pool-client resets any field that is not passed.
CLIENT_SETTINGS=(
  --allowed-o-auth-flows code
  --allowed-o-auth-flows-user-pool-client
  --allowed-o-auth-scopes $SCOPES
  --supported-identity-providers COGNITO
  --callback-urls "http://localhost:4200/callback" "$FRONTEND_URL/callback" "$PREVIEW_URL/callback"
  --logout-urls "http://localhost:4200" "$FRONTEND_URL" "$PREVIEW_URL"
  --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_PASSWORD_AUTH
  --access-token-validity 60 --id-token-validity 60 --refresh-token-validity 1
  --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days
  --prevent-user-existence-errors ENABLED
  --enable-token-revocation
)
if [ -z "${COGNITO_CLIENT_ID:-}" ]; then
  COGNITO_CLIENT_ID=$(aws cognito-idp create-user-pool-client --user-pool-id "$USER_POOL_ID" \
    --client-name $PROJECT-spa --no-generate-secret "${CLIENT_SETTINGS[@]}" \
    --query UserPoolClient.ClientId --output text)
  save COGNITO_CLIENT_ID "$COGNITO_CLIENT_ID"
else
  aws cognito-idp update-user-pool-client --user-pool-id "$USER_POOL_ID" --client-id "$COGNITO_CLIENT_ID" \
    --client-name $PROJECT-spa "${CLIENT_SETTINGS[@]}" >/dev/null
fi
echo "COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID"

log "Domain (classic hosted UI; the local AWS CLI predates managed login v2)"
if [ -z "${COGNITO_DOMAIN:-}" ]; then
  prefix="$PROJECT-$(openssl rand -hex 3)"
  aws cognito-idp create-user-pool-domain --user-pool-id "$USER_POOL_ID" --domain "$prefix" >/dev/null
  save COGNITO_DOMAIN "https://$prefix.auth.$AWS_REGION.amazoncognito.com"
fi
echo "COGNITO_DOMAIN=$COGNITO_DOMAIN"

log "Test users"
create_user() {  # user email password group
  aws cognito-idp admin-create-user --user-pool-id "$USER_POOL_ID" --username "$1" \
    --user-attributes Name=email,Value="$2" Name=email_verified,Value=true \
    --message-action SUPPRESS >/dev/null 2>&1 || true
  aws cognito-idp admin-set-user-password --user-pool-id "$USER_POOL_ID" --username "$1" \
    --password "$3" --permanent
  aws cognito-idp admin-add-user-to-group --user-pool-id "$USER_POOL_ID" --username "$1" --group-name "$4"
  echo "  $1 ($4)"
}
[ -z "${ADMIN_PASSWORD:-}" ] && save ADMIN_PASSWORD "Admin$(openssl rand -hex 3)!"
[ -z "${CLIENTE_PASSWORD:-}" ] && save CLIENTE_PASSWORD "Cliente$(openssl rand -hex 3)!"
create_user admin admin@pedidos360.test "$ADMIN_PASSWORD" ADMIN
create_user cliente1 cliente1@pedidos360.test "$CLIENTE_PASSWORD" CLIENTE
