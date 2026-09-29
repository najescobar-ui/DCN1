#!/usr/bin/env bash
# Security groups (EC2, RDS) and the PostgreSQL RDS instance.
source "$(dirname "$0")/lib.sh"

VPC_ID=${VPC_ID:-$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)}
save VPC_ID "$VPC_ID"

log "Security group EC2 (SSH from my IP, 8080-8082 for API Gateway)"
if [ -z "${EC2_SG_ID:-}" ]; then
  EC2_SG_ID=$(aws ec2 create-security-group --group-name $PROJECT-ec2-sg --vpc-id "$VPC_ID" \
    --description "Pedidos360 microservices" --query GroupId --output text)
  save EC2_SG_ID "$EC2_SG_ID"
  MY_IP=$(curl -s https://checkip.amazonaws.com)
  aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 22 --cidr "$MY_IP/32" >/dev/null
  # API Gateway HTTP API has no fixed source IPs; the services validate the JWT themselves.
  aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 8080-8082 --cidr 0.0.0.0/0 >/dev/null
fi
echo "EC2_SG_ID=$EC2_SG_ID"

log "Security group RDS (5432 only from the EC2 security group)"
if [ -z "${RDS_SG_ID:-}" ]; then
  RDS_SG_ID=$(aws ec2 create-security-group --group-name $PROJECT-rds-sg --vpc-id "$VPC_ID" \
    --description "Pedidos360 PostgreSQL" --query GroupId --output text)
  save RDS_SG_ID "$RDS_SG_ID"
  aws ec2 authorize-security-group-ingress --group-id "$RDS_SG_ID" --protocol tcp --port 5432 \
    --source-group "$EC2_SG_ID" >/dev/null
fi
echo "RDS_SG_ID=$RDS_SG_ID"

log "RDS PostgreSQL (db.t3.micro, private)"
if [ -z "${DB_PASSWORD:-}" ]; then
  save DB_USER pedidos360
  save DB_PASSWORD "$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 24)"
fi
if ! aws rds describe-db-instances --db-instance-identifier $PROJECT-db >/dev/null 2>&1; then
  aws rds create-db-instance \
    --db-instance-identifier $PROJECT-db \
    --engine postgres \
    --db-instance-class db.t3.micro \
    --allocated-storage 20 \
    --storage-type gp2 \
    --master-username "$DB_USER" \
    --master-user-password "$DB_PASSWORD" \
    --db-name productos \
    --vpc-security-group-ids "$RDS_SG_ID" \
    --no-publicly-accessible \
    --backup-retention-period 0 \
    --no-multi-az \
    --tags Key=Project,Value=$PROJECT \
    --query 'DBInstance.DBInstanceStatus' --output text
fi
echo "RDS en creacion; el endpoint se obtiene con 02 cuando quede 'available'."
