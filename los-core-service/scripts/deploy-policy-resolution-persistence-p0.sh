#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_persist_p0_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_persist_p0.dump
docker cp billiontech-postgres:/tmp/pre_persist_p0.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

# Host-persisted durable resolution store (survives container recreate)
mkdir -p /opt/billiontech/data/billiontechlos/policy-studio-resolutions
# Container runs as uid 999 (app) — must be writable
chown -R 999:999 /opt/billiontech/data/billiontechlos/policy-studio-resolutions || true
chmod 775 /opt/billiontech/data/billiontechlos/policy-studio-resolutions || true
if ! grep -q 'policy-studio-resolutions' docker-compose.yml; then
  python3 - <<'PY'
from pathlib import Path
p = Path("docker-compose.yml")
t = p.read_text()
needle = "      - /opt/billiontech/data/billiontechlos/uploads:/app/uploads\n"
insert = needle + "      - /opt/billiontech/data/billiontechlos/policy-studio-resolutions:/app/data/policy-studio-resolutions\n"
if needle in t and "policy-studio-resolutions" not in t:
    p.write_text(t.replace(needle, insert, 1))
    print("compose volume added")
else:
    print("compose volume already present or needle missing")
PY
fi

# Ensure env points durable dir (Spring relaxed binding)
if ! grep -q 'LOS_POLICY_STUDIO_DURABLE_DIR' .env; then
  echo 'LOS_POLICY_STUDIO_DURABLE_DIR=/app/data/policy-studio-resolutions' >> .env
fi

tar -xzf /tmp/persist-p0-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/parameters/PolicyResolutionIdentity.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "policy-resolution-persistence-p0 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done
curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 200; echo
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
