#!/usr/bin/env bash
# EC2 de streaming: Amazon Linux 2023 + Docker + docker compose. Ahí corre deploy/streaming/docker-compose.yml
# (3 Zookeeper, 3 brokers Kafka, Kafka UI, ms-analitica y ms-streaming-admin). Es una t3.large (8 GiB): siete JVM
# no caben en la t3.medium de RabbitMQ. IP elástica para que la consola y el SSH sobrevivan a los apagados del lab.
source "$(dirname "$0")/lib.sh"

log "Security group de streaming"
if [ -z "${KAFKA_SG_ID:-}" ]; then
  KAFKA_SG_ID=$(aws ec2 create-security-group --group-name $PROJECT-kafka-sg --vpc-id "$VPC_ID" \
    --description "Pedidos360 streaming (Zookeeper + Kafka + Kafka UI + microservicios de streaming)" \
    --tag-specifications "ResourceType=security-group,Tags=[{Key=Project,Value=$PROJECT}]" \
    --query GroupId --output text)
  save KAFKA_SG_ID "$KAFKA_SG_ID"
fi
regla() {  # descripción + argumentos de authorize-security-group-ingress
  local desc=$1; shift
  if aws ec2 authorize-security-group-ingress --group-id "$KAFKA_SG_ID" "$@" >/dev/null 2>&1; then
    echo "  + $desc"
  else
    echo "  = $desc"
  fi
}
MI_IP=$(curl -s https://checkip.amazonaws.com)
regla "SSH desde $MI_IP"                         --protocol tcp --port 22 --cidr "$MI_IP/32"
regla "Kafka UI (8090) desde $MI_IP"             --protocol tcp --port 8090 --cidr "$MI_IP/32"
# Listener EXTERNAL de los tres brokers: solo el productor ms-pedidos (EC2 backend); nunca abierto a internet.
regla "Kafka (9092-9094) desde la EC2 backend"   --protocol tcp --port 9092-9094 --source-group "$EC2_SG_ID"
# ms-analitica y ms-streaming-admin solo los llama el BFF (EC2 backend) por IP privada; no se exponen.
regla "APIs 8085-8086 desde la EC2 backend (BFF)" --protocol tcp --port 8085-8086 --source-group "$EC2_SG_ID"

log "Instancia"
if [ -z "${KAFKA_INSTANCE_ID:-}" ]; then
  AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
    --query Parameter.Value --output text)
  USER_DATA=$(cat <<'UD'
#!/bin/bash
dnf install -y docker
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
mkdir -p /opt/pedidos360-kafka && chown ec2-user: /opt/pedidos360-kafka
UD
)
  SUBNET_ID=$(aws ec2 describe-instances --instance-ids "$EC2_INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].SubnetId' --output text)
  KAFKA_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.large \
    --key-name $PROJECT-key --security-group-ids "$KAFKA_SG_ID" --subnet-id "$SUBNET_ID" \
    --iam-instance-profile Name=LabInstanceProfile \
    --user-data "$USER_DATA" \
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=24,VolumeType=gp3}' \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$PROJECT-streaming},{Key=Project,Value=$PROJECT}]" \
    --query 'Instances[0].InstanceId' --output text)
  save KAFKA_INSTANCE_ID "$KAFKA_INSTANCE_ID"
fi
aws ec2 wait instance-running --instance-ids "$KAFKA_INSTANCE_ID"
echo "KAFKA_INSTANCE_ID=$KAFKA_INSTANCE_ID"
save KAFKA_PRIVATE_IP "$(aws ec2 describe-instances --instance-ids "$KAFKA_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"
echo "KAFKA_PRIVATE_IP=$KAFKA_PRIVATE_IP"

log "IP elástica"
if [ -z "${KAFKA_EIP_ALLOC:-}" ]; then
  KAFKA_EIP_ALLOC=$(aws ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Project,Value=$PROJECT}]" \
    --query AllocationId --output text)
  save KAFKA_EIP_ALLOC "$KAFKA_EIP_ALLOC"
  aws ec2 associate-address --instance-id "$KAFKA_INSTANCE_ID" --allocation-id "$KAFKA_EIP_ALLOC" >/dev/null
fi
save KAFKA_IP "$(aws ec2 describe-addresses --allocation-ids "$KAFKA_EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)"
echo "KAFKA_IP=$KAFKA_IP"

log "Credenciales de Kafka UI (solo en state.env, nunca en git)"
[ -n "${KAFKA_UI_USER:-}" ] || save KAFKA_UI_USER pedidos360
[ -n "${KAFKA_UI_PASSWORD:-}" ] || save KAFKA_UI_PASSWORD "$(openssl rand -hex 12)"
echo "usuario: $KAFKA_UI_USER"
echo "Kafka UI: http://$KAFKA_IP:8090"
