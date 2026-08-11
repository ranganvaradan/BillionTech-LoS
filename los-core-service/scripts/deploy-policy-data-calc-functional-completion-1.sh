#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_policy_data_calc_functional_1_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_pdc.dump
docker cp billiontech-postgres:/tmp/pre_pdc.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

cp -a ui-dist "ui-dist.prev.policy-data-calc-functional-1.$TS"
rm -rf ui-dist.new
mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-policy-data-calc-functional-1.tar.gz -C ui-dist.new
rm -rf ui-dist
mv ui-dist.new ui-dist
echo "policy-data-calc-functional-completion-1 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/policy-data-calc-functional-completion-1-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/metrics/EmiBounceCountCalculator.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "policy-data-calc-functional-completion-1 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 300; echo
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT version, success FROM flyway_schema_history WHERE version='119';" || true
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT canonical_id, implemented, production_ready, implementation_binding
   FROM ci_gacat_canonical_parameter p
   LEFT JOIN ci_gacat_canonical_parameter_version v ON v.parameter_id=p.id AND v.version_no=1
   WHERE canonical_id='banking.emi_bounce_count_3m';" || true

CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'
# Smoke: reset banking demo session and look for EMI bounce configure path in UI + allowCanonicalAuthority
curl -sS -m 90 -X POST "$BASE/policy-studio/banking/reset" -H "X-Internal-Token: $TOKEN" -o /tmp/bank_pdc.json || true
python3 - <<'PY'
import json,os,re
d=json.load(open("/tmp/bank_pdc.json"))
print("allowCanonicalAuthority", d.get("allowCanonicalAuthority"))
dc=d.get("dataAndCalculations") or []
emi=[c for c in dc if "emi bounce" in str(c.get("sourceClause","")).lower() or "emi_bounce" in str(c.get("canonicalParameterId","")).lower()]
print("EMI_CARDS", len(emi))
if emi:
  print("EMI_PARAM", emi[0].get("canonicalParameterId"), emi[0].get("missingDefinition"))
js_dir="/opt/billiontech/apps/billiontechlos/ui-dist/assets"
js=[f for f in os.listdir(js_dir) if f.endswith(".js")]
blob=""
for f in js[:5]:
  blob += open(os.path.join(js_dir,f),encoding="utf-8",errors="ignore").read()
for needle in ["emi-bounce-config","EmiBounceCountCalculator","previewDataCalculation","Save configuration","data-calculations/preview"]:
  print(needle, needle in blob)
print("BACKUP_RECORD BYTES=%s SHA256=%s FILE=%s" % (
  os.environ.get("BYTES",""), os.environ.get("SHA",""), os.environ.get("BACKUP","")))
PY
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
