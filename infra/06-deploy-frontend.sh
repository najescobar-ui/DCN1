#!/usr/bin/env bash
# Builds the React app and publishes it to Amplify Hosting (manual zip deployment).
# Usage: [BRANCH=preview] ./06-deploy-frontend.sh   (main = production, preview = review before merging)
source "$(dirname "$0")/lib.sh"
BRANCH=${BRANCH:-main}

log "Build"
(cd "$INFRA_DIR/../frontend" && npm ci --silent && npm run build >/dev/null)
DIST="$INFRA_DIR/../frontend/dist"
ZIP="$(mktemp -d)/frontend.zip"
(cd "$DIST" && zip -qr "$ZIP" .)

log "Deploy to Amplify ($BRANCH)"
if ! aws amplify get-branch --app-id "$AMPLIFY_APP_ID" --branch-name "$BRANCH" >/dev/null 2>&1; then
  aws amplify create-branch --app-id "$AMPLIFY_APP_ID" --branch-name "$BRANCH" --stage "$([ "$BRANCH" = main ] && echo PRODUCTION || echo DEVELOPMENT)" >/dev/null
fi
read -r JOB_ID UPLOAD_URL < <(aws amplify create-deployment --app-id "$AMPLIFY_APP_ID" --branch-name "$BRANCH" \
  --query '[jobId,zipUploadUrl]' --output text)
curl -sf -X PUT -T "$ZIP" -H 'Content-Type: application/zip' "$UPLOAD_URL"
aws amplify start-deployment --app-id "$AMPLIFY_APP_ID" --branch-name "$BRANCH" --job-id "$JOB_ID" >/dev/null
for i in $(seq 1 40); do
  status=$(aws amplify get-job --app-id "$AMPLIFY_APP_ID" --branch-name "$BRANCH" --job-id "$JOB_ID" \
    --query job.summary.status --output text)
  [[ "$status" =~ SUCCEED|FAILED|CANCELLED ]] && break
  sleep 3
done
echo "Job $JOB_ID: $status -> https://$BRANCH.$AMPLIFY_APP_ID.amplifyapp.com"
