#!/usr/bin/env bash
# Builds the three services and installs them on the EC2 host as systemd units.
# Usage: EC2_HOST=ec2-user@<public-dns> SSH_KEY=~/.ssh/labsuser.pem RDS_HOST=<endpoint> ./deploy/ec2/deploy.sh
set -euo pipefail

: "${EC2_HOST:?EC2_HOST is required}"
: "${SSH_KEY:?SSH_KEY is required}"
: "${RDS_HOST:?RDS_HOST is required}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERVICES=(ms-productos ms-pedidos bff)
SSH=(ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new "$EC2_HOST")

for svc in "${SERVICES[@]}"; do
  (cd "$ROOT/backend/$svc" && ./mvnw -q -B -DskipTests package)
done

"${SSH[@]}" "sudo mkdir -p /opt/pedidos360 && sudo chown ec2-user: /opt/pedidos360"
for svc in "${SERVICES[@]}"; do
  scp -i "$SSH_KEY" "$ROOT/backend/$svc/target/$svc-0.0.1-SNAPSHOT.jar" "$EC2_HOST:/opt/pedidos360/$svc.jar"
  scp -i "$SSH_KEY" "$ROOT/deploy/ec2/pedidos360-$svc.service" "$EC2_HOST:/tmp/"
done

"${SSH[@]}" bash -s <<REMOTE
set -euo pipefail
for svc in ${SERVICES[*]}; do
  sudo mv /tmp/pedidos360-\$svc.service /etc/systemd/system/
done
# Per-service database URL
for pair in ms-productos:productos ms-pedidos:pedidos; do
  svc=\${pair%%:*}; db=\${pair##*:}
  sudo mkdir -p /etc/systemd/system/pedidos360-\$svc.service.d
  printf '[Service]\nEnvironment=DB_URL=jdbc:postgresql://${RDS_HOST}:5432/%s\n' "\$db" \
    | sudo tee /etc/systemd/system/pedidos360-\$svc.service.d/db.conf >/dev/null
done
sudo systemctl daemon-reload
for svc in ${SERVICES[*]}; do
  sudo systemctl enable pedidos360-\$svc >/dev/null
  sudo systemctl restart pedidos360-\$svc
done
REMOTE

echo "Deployed. Check with: ssh ... 'curl -s localhost:8080/actuator/health'"
