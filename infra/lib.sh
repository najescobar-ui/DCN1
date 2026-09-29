#!/usr/bin/env bash
# Shared helpers: AWS profile and a local state file with the created resource ids.
set -euo pipefail

# Perfil de AWS: por defecto el del lab (~/.aws/dce1-env.sh). Para otra cuenta o lab, apuntar
# AWS_ENV_FILE a otro archivo con AWS_CONFIG_FILE, AWS_SHARED_CREDENTIALS_FILE, AWS_PROFILE y AWS_REGION.
AWS_ENV_FILE=${AWS_ENV_FILE:-$HOME/.aws/dce1-env.sh}
if [ -f "$AWS_ENV_FILE" ]; then
  # shellcheck disable=SC1090
  source "$AWS_ENV_FILE"
fi
export AWS_REGION=${AWS_REGION:-us-east-1}
INFRA_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STATE_FILE="$INFRA_DIR/state.env"   # ignored by git (*.env): contains ids and the DB password
PROJECT=pedidos360
touch "$STATE_FILE"
# shellcheck disable=SC1090
source "$STATE_FILE"

save() {  # save KEY VALUE
  grep -v "^$1=" "$STATE_FILE" > "$STATE_FILE.tmp" || true
  printf '%s=%q\n' "$1" "$2" >> "$STATE_FILE.tmp"
  mv "$STATE_FILE.tmp" "$STATE_FILE"
  export "$1=$2"
}

log() { printf '\n== %s\n' "$*"; }
