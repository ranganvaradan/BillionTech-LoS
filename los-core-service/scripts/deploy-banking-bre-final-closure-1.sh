#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_banking_bre_final_closure_1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_bbfc1.dump
docker cp billiontech-postgres:/tmp/pre_bbfc1.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

cp -a ui-dist "ui-dist.prev.banking-bre-final-closure-1.$TS"
rm -rf ui-dist.new && mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-banking-bre-final-closure-1.tar.gz -C ui-dist.new
rm -rf ui-dist && mv ui-dist.new ui-dist
echo "banking-bre-final-closure-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/banking-bre-final-closure-1-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/metrics/AdbBulkDepositAdjustmentCalculator.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "banking-bre-final-closure-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 300; echo
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'

# Run closure golden
bash "$APP/los-core-service/scripts/staging-banking-bre-final-closure-1.sh" || true

# UI needles
js_dir="$APP/ui-dist/assets"
blob=""
for f in "$js_dir"/*.js; do
  blob+=$(cat "$f" 2>/dev/null || true)
done
for n in adb-bulk-config AdbBulkDepositAdjustmentCalculator Preview calculation with sample treat_100_as_ratio_branch; do
  echo "UI_NEEDLE $n $(echo "$blob" | grep -c -F "$n" || true)"
done

echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
