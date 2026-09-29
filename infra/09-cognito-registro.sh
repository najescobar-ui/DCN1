#!/usr/bin/env bash
# Post confirmation Lambda that adds self-registered users to the CLIENTE group.
# Uses the lab's LabRole (the Learner Lab does not allow creating IAM roles).
source "$(dirname "$0")/lib.sh"
FUNCTION=$PROJECT-post-confirmation

log "Lambda $FUNCTION"
ZIP="$(mktemp -d)/function.zip"
(cd "$INFRA_DIR/lambda/post-confirmation" && zip -q "$ZIP" index.py)
ROLE_ARN=$(aws iam get-role --role-name LabRole --query Role.Arn --output text)
if aws lambda get-function --function-name "$FUNCTION" >/dev/null 2>&1; then
  aws lambda update-function-code --function-name "$FUNCTION" --zip-file "fileb://$ZIP" >/dev/null
else
  aws lambda create-function --function-name "$FUNCTION" --runtime python3.12 --handler index.handler \
    --role "$ROLE_ARN" --zip-file "fileb://$ZIP" --timeout 10 \
    --environment "Variables={DEFAULT_GROUP=CLIENTE}" --tags Project=$PROJECT >/dev/null
  aws lambda wait function-active-v2 --function-name "$FUNCTION"
  aws lambda add-permission --function-name "$FUNCTION" --statement-id cognito-post-confirmation \
    --action lambda:InvokeFunction --principal cognito-idp.amazonaws.com \
    --source-arn "arn:aws:cognito-idp:$AWS_REGION:$(aws sts get-caller-identity --query Account --output text):userpool/$USER_POOL_ID" >/dev/null
fi
LAMBDA_ARN=$(aws lambda get-function --function-name "$FUNCTION" --query Configuration.FunctionArn --output text)
save POST_CONFIRMATION_LAMBDA "$LAMBDA_ARN"

log "Attach trigger to the user pool"
# update-user-pool resets every setting it is not given, so the current configuration is sent back
# with only LambdaConfig changed.
python3 - "$USER_POOL_ID" "$LAMBDA_ARN" <<'PY'
import json, subprocess, sys
pool_id, lambda_arn = sys.argv[1], sys.argv[2]
pool = json.loads(subprocess.check_output(
    ["aws", "cognito-idp", "describe-user-pool", "--user-pool-id", pool_id, "--output", "json"]))["UserPool"]
keys = ["Policies", "DeletionProtection", "AutoVerifiedAttributes", "VerificationMessageTemplate",
        "UserAttributeUpdateSettings", "MfaConfiguration", "DeviceConfiguration", "EmailConfiguration",
        "SmsConfiguration", "UserPoolTags", "AdminCreateUserConfig", "UserPoolAddOns", "AccountRecoverySetting"]
body = {k: pool[k] for k in keys if k in pool}
body["UserPoolId"] = pool_id
body["LambdaConfig"] = {**pool.get("LambdaConfig", {}), "PostConfirmation": lambda_arn}
# Fields describe returns but update does not accept inside nested objects.
body.get("AdminCreateUserConfig", {}).pop("UnusedAccountValidityDays", None)
if body.get("MfaConfiguration") == "OFF":
    body.pop("MfaConfiguration")
subprocess.check_call(["aws", "cognito-idp", "update-user-pool", "--cli-input-json", json.dumps(body)])
PY
aws cognito-idp describe-user-pool --user-pool-id "$USER_POOL_ID" \
  --query 'UserPool.{trigger:LambdaConfig.PostConfirmation,verificados:AutoVerifiedAttributes,obligatorios:SchemaAttributes[?Required].Name}' --output json
