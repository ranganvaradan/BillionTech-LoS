#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_deploy_los_production_hardening_1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_ph1_deploy.dump
docker cp billiontech-postgres:/tmp/pre_ph1_deploy.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

tar -xzf /tmp/los-production-hardening-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "los-production-hardening-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 800; echo
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='underwriting_evaluations' AND column_name='decision_snapshot_json';"
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
