#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_V116_scorecard_governance_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_v116.dump
docker cp billiontech-postgres:/tmp/pre_v116.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS scorecards, COUNT(*) FILTER (WHERE active) AS active FROM underwriting_scorecards;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

cp -a ui-dist "ui-dist.prev.scorecard-governance-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-scorecard-governance-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "scorecard-governance-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/scorecard-governance-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "scorecard-governance-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success, installed_on FROM flyway_schema_history WHERE version IN ('116','115') ORDER BY installed_rank;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='underwriting_scorecards' AND column_name='governance_json';"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT status, COUNT(*) FROM underwriting_scorecards GROUP BY status ORDER BY 1;"

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 400; echo
grep -oE 'Submit for Review|IN REVIEW|governanceJson|allowCanonicalAuthority' ui-dist/assets/*.js | head -20 || true
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
