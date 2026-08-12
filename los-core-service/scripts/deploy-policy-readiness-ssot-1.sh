#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_policy_readiness_ssot_1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_ssot.dump
docker cp billiontech-postgres:/tmp/pre_ssot.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

cp -a ui-dist "ui-dist.prev.policy-readiness-ssot-1.$TS"
rm -rf ui-dist.new && mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-policy-readiness-ssot-1.tar.gz -C ui-dist.new
rm -rf ui-dist && mv ui-dist.new ui-dist
echo "policy-readiness-ssot-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/policy-readiness-ssot-1-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/parameters/PolicyExecutionReadiness.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "policy-readiness-ssot-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done
curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 200; echo
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
