#!/usr/bin/env bash
# Creates .env from .env.example with freshly generated secrets. Safe to re-run: never overwrites an existing .env.
set -euo pipefail

cd "$(dirname "$0")/.."

if [[ -f .env ]]; then
  echo ".env already exists; leaving it untouched."
  exit 0
fi

random() { openssl rand -base64 "$1" | tr -d '\n'; }

jwt_secret="$(random 48)"
# Alphanumeric only, so the password is safe inside a JDBC URL and YAML without quoting.
db_password="$(openssl rand -hex 24)"

sed -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=${db_password}|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=${jwt_secret}|" \
    .env.example > .env
chmod 600 .env

echo "Created .env with a generated database password and JWT signing key."
