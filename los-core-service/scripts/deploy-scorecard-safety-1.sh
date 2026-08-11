#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

# UI
cp -a ui-dist "ui-dist.prev.scorecard-safety-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-scorecard-safety-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "scorecard-safety-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

# Core source overlay
tar -xzf /tmp/scorecard-safety-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "scorecard-safety-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success, installed_on FROM flyway_schema_history WHERE version IN ('114','113','112') ORDER BY installed_rank;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='underwriting_scorecards' AND column_name IN ('status','lineage_id','safety_json') ORDER BY 1;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='underwriting_evaluations' AND column_name IN ('scorecard_version','scorecard_evidence_json') ORDER BY 1;"

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE active) AS active, COUNT(*) FILTER (WHERE status='ACTIVE') AS status_active FROM underwriting_scorecards;"

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 400; echo
grep -o 'allowCanonicalAuthority\|Create new version\|METADATA_ONLY\|exclusive-band' ui-dist/assets/*.js | head -10 || true
