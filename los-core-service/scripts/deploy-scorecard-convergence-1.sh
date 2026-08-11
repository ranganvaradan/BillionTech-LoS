#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

# Pre-migration backup (must be >0 bytes)
BACKUP=/opt/billiontech/backups/los_core_staging_pre_V115_scorecard_convergence_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc \
  -f /tmp/pre_v115.dump
docker cp billiontech-postgres:/tmp/pre_v115.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

# Pre counts
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS scorecards, COUNT(*) FILTER (WHERE active) AS active FROM underwriting_scorecards;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3;"

# UI
cp -a ui-dist "ui-dist.prev.scorecard-convergence-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-scorecard-convergence-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "scorecard-convergence-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

# Core source overlay
tar -xzf /tmp/scorecard-convergence-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "scorecard-convergence-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success, installed_on FROM flyway_schema_history WHERE version IN ('115','114') ORDER BY installed_rank;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS apps FROM loan_applications;"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS scorecards, COUNT(*) FILTER (WHERE active) AS active FROM underwriting_scorecards;"

# Sample EXACT stamp on company term loan
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT name, version, active,
          (scorecard_json::text LIKE '%bureau.score%') AS has_bureau_canonical,
          (scorecard_json::text LIKE '%canonicalDefinitionVersion%') AS has_def_ver
   FROM underwriting_scorecards
   WHERE borrower_type='COMPANY' AND loan_product='TERM_LOAN'
   ORDER BY active DESC, version DESC LIMIT 5;"

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 400; echo
grep -oE 'gacat-factor-picker|Suggested from policy|canonicalParameterId|Missing data|allowCanonicalAuthority' ui-dist/assets/*.js | head -20 || true

echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
