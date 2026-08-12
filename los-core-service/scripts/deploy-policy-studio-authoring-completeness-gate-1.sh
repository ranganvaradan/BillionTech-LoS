#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_authoring_completeness_gate1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_acg1.dump
docker cp billiontech-postgres:/tmp/pre_acg1.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

cp -a ui-dist "ui-dist.prev.authoring-completeness-gate1.$TS"
rm -rf ui-dist.new && mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-authoring-completeness-gate1.tar.gz -C ui-dist.new
rm -rf ui-dist && mv ui-dist.new ui-dist
echo "authoring-completeness-gate1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/authoring-completeness-gate1-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/parameters/CompoundPlainEnglishParser.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "authoring-completeness-gate1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done
curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 200; echo
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
grep -E 'allowCanonicalAuthority|ALLOW_CANONICAL' .env || true
