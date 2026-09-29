#!/usr/bin/env bash
# EC2 host for the three microservices (Amazon Linux 2023 + Corretto 21) with an Elastic IP,
# so API Gateway integrations survive the lab stopping/starting the instance.
source "$(dirname "$0")/lib.sh"

KEY_FILE="$HOME/.ssh/$PROJECT-dce1.pem"
log "Key pair"
if [ ! -f "$KEY_FILE" ]; then
  aws ec2 create-key-pair --key-name $PROJECT-key --query KeyMaterial --output text > "$KEY_FILE"
  chmod 400 "$KEY_FILE"
fi
save KEY_FILE "$KEY_FILE"

log "Instance"
if [ -z "${EC2_INSTANCE_ID:-}" ]; then
  AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
    --query Parameter.Value --output text)
  USER_DATA=$(cat <<'UD'
#!/bin/bash
dnf install -y java-21-amazon-corretto-headless postgresql17
mkdir -p /opt/pedidos360 && chown ec2-user: /opt/pedidos360
UD
)
  EC2_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.small \
    --key-name $PROJECT-key --security-group-ids "$EC2_SG_ID" \
    --iam-instance-profile Name=LabInstanceProfile \
    --user-data "$USER_DATA" \
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=10,VolumeType=gp3}' \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$PROJECT-backend},{Key=Project,Value=$PROJECT}]" \
    --query 'Instances[0].InstanceId' --output text)
  save EC2_INSTANCE_ID "$EC2_INSTANCE_ID"
fi
aws ec2 wait instance-running --instance-ids "$EC2_INSTANCE_ID"
echo "EC2_INSTANCE_ID=$EC2_INSTANCE_ID"

log "Elastic IP"
if [ -z "${EC2_EIP_ALLOC:-}" ]; then
  EC2_EIP_ALLOC=$(aws ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Project,Value=$PROJECT}]" \
    --query AllocationId --output text)
  save EC2_EIP_ALLOC "$EC2_EIP_ALLOC"
  aws ec2 associate-address --instance-id "$EC2_INSTANCE_ID" --allocation-id "$EC2_EIP_ALLOC" >/dev/null
fi
save EC2_IP "$(aws ec2 describe-addresses --allocation-ids "$EC2_EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)"
echo "EC2_IP=$EC2_IP"
