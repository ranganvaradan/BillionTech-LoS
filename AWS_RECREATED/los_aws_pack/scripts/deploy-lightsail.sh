#!/usr/bin/env bash
set -euo pipefail
if [ ! -f .env.prod ]; then
  echo "Missing .env.prod. Copy .env.prod.template to .env.prod and edit it first."
  exit 1
fi
docker compose -f docker-compose.prod.yml --env-file .env.prod pull || true
docker compose -f docker-compose.prod.yml --env-file .env.prod up --build -d
docker compose -f docker-compose.prod.yml ps
