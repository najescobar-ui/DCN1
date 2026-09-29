#!/usr/bin/env bash
# Applies the Pedidos360 look (colors + logo) to the Cognito hosted UI.
source "$(dirname "$0")/lib.sh"
log "Hosted UI customization"
aws cognito-idp set-ui-customization --user-pool-id "$USER_POOL_ID" --client-id "$COGNITO_CLIENT_ID" \
  --css "$(tr '\n' ' ' < "$INFRA_DIR/cognito-ui/hosted-ui.css")" \
  --image-file "fileb://$INFRA_DIR/cognito-ui/logo.png" --query 'UICustomization.LastModifiedDate' --output text
