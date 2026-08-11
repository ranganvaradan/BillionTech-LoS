#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_V117_product_config_routing_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_v117.dump
docker cp billiontech-postgres:/tmp/pre_v117.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

cp -a ui-dist "ui-dist.prev.product-config-routing-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-product-config-routing-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "product-config-routing-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/product-config-routing-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "product-config-routing-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success, installed_on FROM flyway_schema_history WHERE version IN ('117','116') ORDER BY installed_rank;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='ci_policy_applicability' AND column_name='borrower_types';"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 400; echo
curl -sS -m 30 'http://127.0.0.1:8083/api/v1/admin/live-readiness/product-configuration/golden' | head -c 1200; echo
grep -oE 'Runtime Workflow|Runtime Rule Set|Governance only|borrowerTypes|PRODUCT CONFIG' ui-dist/assets/*.js | head -20 || true
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
