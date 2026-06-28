#!/usr/bin/env bash
set -euo pipefail
source .env.prod
mkdir -p backups
TS=$(date +%Y%m%d_%H%M%S)
docker exec los_postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" > "backups/losdb_$TS.sql"
echo "Created backups/losdb_$TS.sql"
