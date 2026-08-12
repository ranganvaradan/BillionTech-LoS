#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_actuator_ui_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_actuator_ui.dump
docker cp billiontech-postgres:/tmp/pre_actuator_ui.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

# --- UI ---
cp -a ui-dist "ui-dist.prev.actuator-info-ui.$TS"
rm -rf ui-dist.new && mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-actuator-info-ui.tar.gz -C ui-dist.new
# support either flat dist contents or nested dist/
if [ -f ui-dist.new/index.html ]; then
  :
elif [ -f ui-dist.new/dist/index.html ]; then
  mv ui-dist.new/dist/* ui-dist.new/ && rmdir ui-dist.new/dist
fi
test -f ui-dist.new/index.html
rm -rf ui-dist && mv ui-dist.new ui-dist
{
  echo "actuator-info-ui-redeploy $TS"
  date -u +%Y-%m-%dT%H:%M:%SZ
} > UI_DEPLOYED_AT.txt
echo "UI_ASSET=$(ls ui-dist/assets/index-*.js | head -1)"

# --- CORE ---
tar -xzf /tmp/actuator-info-ui-core-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/parameters/CompoundPlainEnglishParser.java
touch los-core-service/src/main/resources/application.yml
touch los-core-service/pom.xml
docker compose build --no-cache los-core
docker compose up -d los-core
{
  echo "actuator-info-ui-redeploy $TS"
  date -u +%Y-%m-%dT%H:%M:%SZ
} > DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done
curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 200; echo
echo "=== actuator/info ==="
curl -sS -m 20 'http://127.0.0.1:8083/actuator/info' | head -c 800; echo
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
grep -E 'allowCanonicalAuthority|ALLOW_CANONICAL' .env || true
