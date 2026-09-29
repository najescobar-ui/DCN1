#!/usr/bin/env bash
# Autoriza SSH (puerto 22) desde tu IP pública actual en el security group de la EC2.
# Hace falta cuando cambias de red: sin esto, 05-deploy-backend.sh no puede conectarse.
source "$(dirname "$0")/lib.sh"
MI_IP=$(curl -s https://checkip.amazonaws.com)
log "SSH desde $MI_IP/32"
if aws ec2 authorize-security-group-ingress --group-id "$EC2_SG_ID" --protocol tcp --port 22 --cidr "$MI_IP/32" >/dev/null 2>&1; then
  echo "regla agregada"
else
  echo "la regla ya existía"
fi
echo "Reglas SSH actuales:"
aws ec2 describe-security-groups --group-ids "$EC2_SG_ID" \
  --query 'SecurityGroups[0].IpPermissions[?FromPort==`22`].IpRanges[].CidrIp' --output text
