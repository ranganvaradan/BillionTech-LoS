#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

cp -a ui-dist "ui-dist.prev.scorecard-safety-closure-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-scorecard-safety-closure-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "scorecard-safety-closure-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/scorecard-safety-closure-1-src.tar.gz -C los-core-service
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "scorecard-safety-closure-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success FROM flyway_schema_history WHERE version='114';"

sed -i 's/\r$//' /tmp/scorecard-safety-closure-inventory.py 2>/dev/null || true
python3 /tmp/scorecard-safety-closure-inventory.py

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 300; echo
