#!/usr/bin/env bash
# Shared helpers: AWS profile and a local state file with the created resource ids.
set -euo pipefail

source "$HOME/.aws/dce1-env.sh"
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
