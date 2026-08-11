#!/bin/bash
set -eu
TS=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP=/opt/billiontech/backups/los_core_staging_pre_los_production_p0_closure_1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_p0c.dump
docker cp billiontech-postgres:/tmp/pre_p0c.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP"
echo "BYTES=$BYTES"
echo "SHA256=$SHA"
test "$BYTES" -gt 0
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
docker inspect -f '{{.State.Health.Status}}' billiontechlos-core
echo "BASELINE_OK"
