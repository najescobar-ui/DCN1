#!/usr/bin/env bash
# Builds the Angular app and publishes it to Amplify Hosting (manual zip deployment).
source "$(dirname "$0")/lib.sh"

log "Build"
(cd "$INFRA_DIR/../frontend" && npx ng build --configuration production >/dev/null)
DIST="$INFRA_DIR/../frontend/dist/frontend/browser"
ZIP="$(mktemp -d)/frontend.zip"
(cd "$DIST" && zip -qr "$ZIP" .)

log "Deploy to Amplify"
read -r JOB_ID UPLOAD_URL < <(aws amplify create-deployment --app-id "$AMPLIFY_APP_ID" --branch-name main \
  --query '[jobId,zipUploadUrl]' --output text)
curl -sf -X PUT -T "$ZIP" -H 'Content-Type: application/zip' "$UPLOAD_URL"
aws amplify start-deployment --app-id "$AMPLIFY_APP_ID" --branch-name main --job-id "$JOB_ID" >/dev/null
for i in $(seq 1 40); do
  status=$(aws amplify get-job --app-id "$AMPLIFY_APP_ID" --branch-name main --job-id "$JOB_ID" \
    --query job.summary.status --output text)
  [[ "$status" =~ SUCCEED|FAILED|CANCELLED ]] && break
  sleep 3
done
echo "Job $JOB_ID: $status -> $FRONTEND_URL"
