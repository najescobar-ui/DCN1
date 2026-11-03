#!/usr/bin/env bash
# EC2 de mensajería: Amazon Linux 2023 + Docker + docker compose. Ahí corre deploy/mensajeria/docker-compose.yml
# (dos nodos RabbitMQ en clúster, ms-notificaciones y ms-mensajeria-admin). IP elástica para que las
# integraciones de API Gateway sobrevivan a los apagados del lab.
source "$(dirname "$0")/lib.sh"

log "Security group de mensajería"
if [ -z "${MQ_SG_ID:-}" ]; then
  MQ_SG_ID=$(aws ec2 create-security-group --group-name $PROJECT-mq-sg --vpc-id "$VPC_ID" \
    --description "Pedidos360 mensajeria (RabbitMQ + microservicios de mensajeria)" \
    --tag-specifications "ResourceType=security-group,Tags=[{Key=Project,Value=$PROJECT}]" \
    --query GroupId --output text)
  save MQ_SG_ID "$MQ_SG_ID"
fi
regla() {  # descripción + argumentos de authorize-security-group-ingress
  local desc=$1; shift
  if aws ec2 authorize-security-group-ingress --group-id "$MQ_SG_ID" "$@" >/dev/null 2>&1; then
    echo "  + $desc"
  else
    echo "  = $desc"
  fi
}
MI_IP=$(curl -s https://checkip.amazonaws.com)
regla "SSH desde $MI_IP"                       --protocol tcp --port 22 --cidr "$MI_IP/32"
regla "Consola RabbitMQ (15672-15673) desde $MI_IP" --protocol tcp --port 15672-15673 --cidr "$MI_IP/32"
# AMQP solo desde la EC2 de los microservicios (productor ms-pedidos); nunca abierto a internet.
regla "AMQP (5672-5673) desde la EC2 backend"  --protocol tcp --port 5672-5673 --source-group "$EC2_SG_ID"
# API Gateway llama por IP pública (HTTP_PROXY); los microservicios validan el JWT igual que en la EC2 backend.
regla "APIs 8083-8084 para API Gateway"        --protocol tcp --port 8083-8084 --cidr 0.0.0.0/0
# ms-notificaciones guarda notificaciones, comprobantes y auditoría en la misma RDS.
if aws ec2 authorize-security-group-ingress --group-id "$RDS_SG_ID" --protocol tcp --port 5432 \
  --source-group "$MQ_SG_ID" >/dev/null 2>&1; then echo "  + RDS 5432 desde mensajería"; else echo "  = RDS 5432 desde mensajería"; fi

log "Instancia"
if [ -z "${MQ_INSTANCE_ID:-}" ]; then
  AMI=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
    --query Parameter.Value --output text)
  USER_DATA=$(cat <<'UD'
#!/bin/bash
dnf install -y docker postgresql17
systemctl enable --now docker
usermod -aG docker ec2-user
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
mkdir -p /opt/pedidos360-mq && chown ec2-user: /opt/pedidos360-mq
UD
)
  SUBNET_ID=$(aws ec2 describe-instances --instance-ids "$EC2_INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].SubnetId' --output text)
  MQ_INSTANCE_ID=$(aws ec2 run-instances --image-id "$AMI" --instance-type t3.medium \
    --key-name $PROJECT-key --security-group-ids "$MQ_SG_ID" --subnet-id "$SUBNET_ID" \
    --iam-instance-profile Name=LabInstanceProfile \
    --user-data "$USER_DATA" \
    --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=16,VolumeType=gp3}' \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$PROJECT-mensajeria},{Key=Project,Value=$PROJECT}]" \
    --query 'Instances[0].InstanceId' --output text)
  save MQ_INSTANCE_ID "$MQ_INSTANCE_ID"
fi
aws ec2 wait instance-running --instance-ids "$MQ_INSTANCE_ID"
echo "MQ_INSTANCE_ID=$MQ_INSTANCE_ID"
save MQ_PRIVATE_IP "$(aws ec2 describe-instances --instance-ids "$MQ_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)"
echo "MQ_PRIVATE_IP=$MQ_PRIVATE_IP"

log "IP elástica"
if [ -z "${MQ_EIP_ALLOC:-}" ]; then
  MQ_EIP_ALLOC=$(aws ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Project,Value=$PROJECT}]" \
    --query AllocationId --output text)
  save MQ_EIP_ALLOC "$MQ_EIP_ALLOC"
  aws ec2 associate-address --instance-id "$MQ_INSTANCE_ID" --allocation-id "$MQ_EIP_ALLOC" >/dev/null
fi
save MQ_IP "$(aws ec2 describe-addresses --allocation-ids "$MQ_EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)"
echo "MQ_IP=$MQ_IP"

log "Credenciales de RabbitMQ (solo en state.env, nunca en git)"
[ -n "${MQ_USER:-}" ] || save MQ_USER pedidos360
[ -n "${MQ_PASSWORD:-}" ] || save MQ_PASSWORD "$(openssl rand -hex 16)"
[ -n "${MQ_ERLANG_COOKIE:-}" ] || save MQ_ERLANG_COOKIE "$(openssl rand -hex 24)"
echo "usuario: $MQ_USER"
