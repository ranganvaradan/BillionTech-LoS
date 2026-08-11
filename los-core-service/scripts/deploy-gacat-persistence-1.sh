#!/bin/bash
# GACAT-PERSISTENCE-1 — backup, migrate, redeploy, verify DB catalogue + restart survival
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

echo "=== PREBACKUP $TS ==="
mkdir -p /opt/billiontech/backups/gacat-persistence-1
# Postgres container name may vary
PG=$(docker ps --format '{{.Names}}' | grep -E 'postgres|billiontechlos-db' | head -1 || true)
if [ -n "$PG" ]; then
  docker exec "$PG" pg_dump -U "${POSTGRES_USER:-los}" "${POSTGRES_DB:-los}" \
    > "/opt/billiontech/backups/gacat-persistence-1/pre-${TS}.sql" || true
  ls -la "/opt/billiontech/backups/gacat-persistence-1/pre-${TS}.sql" || true
fi

CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence'

# Pre inventory via Java seed path still works until migrate
curl -sS -m 30 "$BASE/staging-demo/status" -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-pre-status.json || true
python3 - <<'PY'
import json
try:
  d=json.load(open("/tmp/gacat-pre-status.json"))
  print("PRE allowCanonical", d.get("allowCanonicalAuthority"), "flywayHint", d.get("flyway") or d.get("schemaVersion"))
except Exception as e:
  print("PRE status skip", e)
PY

if [ -f /tmp/ui-dist-gacat-persistence-1.tar.gz ]; then
  cp -a ui-dist "ui-dist.prev.gacat-persistence-1.$TS"
  rm -rf ui-dist.new && mkdir -p ui-dist.new
  tar -xzf /tmp/ui-dist-gacat-persistence-1.tar.gz -C ui-dist.new
  rm -rf ui-dist && mv ui-dist.new ui-dist
fi

tar -xzf /tmp/gacat-persistence-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
echo "gacat-persistence-1 $TS" > DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> DEPLOYED_AT.txt

CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
for i in $(seq 1 60); do
  st=$(docker inspect -f '{{.State.Health.Status}}' "$CORE" 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)

# Admin overview
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-admin.json || \
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters/overview" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-admin.json

# Search Maximum DPD
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters/search?q=Maximum%20DPD" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-dpd.json

# Detail
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters/parameters/bureau.max_dpd_6m" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-dpd-detail.json || \
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters/bureau.max_dpd_6m" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-dpd-detail.json

python3 <<'PY'
import json,sys
admin=json.load(open("/tmp/gacat-admin.json"))
print("ADMIN authority", admin.get("catalogueAuthority"), "inventory", admin.get("inventoryVersion"),
      "javaSeed", admin.get("javaSeedIsRuntimeAuthority"), "count",
      (admin.get("totals") or {}).get("registryCount"), "allowCanonical", admin.get("allowCanonicalAuthority"))
assert admin.get("allowCanonicalAuthority") is False
assert admin.get("catalogueAuthority") == "DATABASE", admin
assert admin.get("javaSeedIsRuntimeAuthority") is False
assert (admin.get("totals") or {}).get("registryCount", 0) >= 160

dpd=json.load(open("/tmp/gacat-dpd.json"))
results=dpd.get("results") or []
ids=[str(r.get("id")) for r in results]
print("DPD search ids", ids[:5])
assert any("bureau.max_dpd_6m" == i for i in ids), ids

detail=json.load(open("/tmp/gacat-dpd-detail.json"))
param=detail.get("parameter") or detail
print("DPD detail", param.get("id") or param.get("canonicalId"), "v", param.get("definitionVersion"),
      "impl", (param.get("advanced") or {}).get("existingImplementationBinding"))
assert (param.get("id") or param.get("canonicalId")) == "bureau.max_dpd_6m"
assert param.get("definitionVersion") in (1, "1", 1.0)
print("CHECK1_2_OK")
PY

# Restart survival
docker compose restart los-core
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' "$CORE" 2>/dev/null || echo starting)
  echo "rehealth=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-admin2.json || \
curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters/overview" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/gacat-admin2.json
python3 <<'PY'
import json
admin=json.load(open("/tmp/gacat-admin2.json"))
assert admin.get("catalogueAuthority")=="DATABASE"
assert (admin.get("totals") or {}).get("registryCount",0) >= 160
print("RESTART_SURVIVAL_OK", admin.get("totals"))
print("GACAT_PERSISTENCE_1_STAGING_OK")
PY
