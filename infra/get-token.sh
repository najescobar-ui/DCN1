#!/usr/bin/env bash
# Gets an access token through the real Authorization Code + PKCE flow of the Cognito hosted UI.
# Usage: [SCOPE="openid ..."] ./get-token.sh <username> <password>   -> prints the access token
source "$(dirname "$0")/lib.sh" >/dev/null
USERNAME=${1:?username}; PASSWORD=${2:?password}
REDIRECT="http://localhost:4200/callback"
SCOPE=${SCOPE:-"openid email profile pedidos360/productos.read pedidos360/productos.write pedidos360/pedidos.read pedidos360/pedidos.write"}

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
VERIFIER=$(openssl rand 32 | b64url)
CHALLENGE=$(printf '%s' "$VERIFIER" | openssl dgst -sha256 -binary | b64url)
STATE=$(openssl rand -hex 16)
JAR=$(mktemp)
trap 'rm -f "$JAR"' EXIT

QUERY=$(python3 -c 'import sys,urllib.parse as u;print(u.urlencode(dict(response_type="code",client_id=sys.argv[1],redirect_uri=sys.argv[2],scope=sys.argv[3],state=sys.argv[4],code_challenge=sys.argv[5],code_challenge_method="S256")))' \
  "$COGNITO_CLIENT_ID" "$REDIRECT" "$SCOPE" "$STATE" "$CHALLENGE")

# 1) authorize -> login page (sets the XSRF cookie)
curl -s -c "$JAR" -b "$JAR" -L -o /dev/null "$COGNITO_DOMAIN/oauth2/authorize?$QUERY"
CSRF=$(awk '$6=="XSRF-TOKEN"{print $7}' "$JAR")

# 2) submit credentials -> 302 to redirect_uri?code=...&state=...
LOCATION=$(curl -s -c "$JAR" -b "$JAR" -o /dev/null -w '%{redirect_url}' \
  --data-urlencode "_csrf=$CSRF" --data-urlencode "username=$USERNAME" --data-urlencode "password=$PASSWORD" \
  "$COGNITO_DOMAIN/login?$QUERY")
CODE=$(python3 -c 'import sys,urllib.parse as u;q=u.parse_qs(u.urlparse(sys.argv[1]).query);assert q.get("state",[""])[0]==sys.argv[2],"state mismatch";print(q["code"][0])' "$LOCATION" "$STATE") \
  || { echo "Login fallido (redirect: $LOCATION)" >&2; exit 1; }

# 3) exchange code + code_verifier for tokens
curl -s "$COGNITO_DOMAIN/oauth2/token" \
  --data-urlencode grant_type=authorization_code --data-urlencode "client_id=$COGNITO_CLIENT_ID" \
  --data-urlencode "code=$CODE" --data-urlencode "redirect_uri=$REDIRECT" --data-urlencode "code_verifier=$VERIFIER" \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
