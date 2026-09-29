"""Cognito post confirmation trigger: every self-registered user joins the CLIENTE group,
so their tokens carry cognito:groups like the users created by an admin."""
import os

import boto3

cognito = boto3.client("cognito-idp")
GROUP = os.environ.get("DEFAULT_GROUP", "CLIENTE")


def handler(event, context):
    # Only for sign-up confirmations, not for "forgot password" confirmations.
    if event.get("triggerSource") == "PostConfirmation_ConfirmSignUp":
        cognito.admin_add_user_to_group(
            UserPoolId=event["userPoolId"],
            Username=event["userName"],
            GroupName=GROUP,
        )
    return event
