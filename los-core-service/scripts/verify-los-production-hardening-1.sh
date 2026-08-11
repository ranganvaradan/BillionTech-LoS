#!/bin/bash
set -eu
echo "=== HEALTH ==="
curl -sS -m 15 http://127.0.0.1:8083/actuator/health > /tmp/ph1_health.json
python3 -c 'import json; d=json.load(open("/tmp/ph1_health.json")); print(d.get("status")); ph=d.get("components",{}).get("productionHardening",{}); print("productionHardening", ph.get("status")); print(json.dumps(ph.get("details",{}), indent=2)[:900])'
echo "=== FLYWAY ==="
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1;"
echo "=== GO LIVE API ==="
curl -sS -m 60 -X POST http://127.0.0.1:8083/api/v1/admin/live-readiness/customer-go-live \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMINISTRATOR" \
  -H "X-User-Id: a1000000-0000-0000-0000-000000000001" \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}' \
  > /tmp/ph1_golive.json
python3 -c 'import json; d=json.load(open("/tmp/ph1_golive.json")); print("safe", d.get("safeToGoLiveAnswer")); print("failed", d.get("failedCheckCount")); print([c["id"] for c in d.get("checks",[]) if not c.get("pass")][:20])'
echo "=== JWT LOGIN ==="
curl -sS -m 20 -X POST http://127.0.0.1:8083/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"mohit@billionloans.com","password":"Bltest@123"}' \
  > /tmp/ph1_login.json
python3 -c 'import json; d=json.load(open("/tmp/ph1_login.json")); print("role", d.get("role")); print("tokenPresent", bool(d.get("accessToken"))); print("tokenType", d.get("tokenType")); open("/tmp/ph1_token.txt","w").write(d.get("accessToken") or "")'
TOKEN=$(cat /tmp/ph1_token.txt || true)
if [ -n "$TOKEN" ]; then
  echo "=== BEARER SMOKE ==="
  code=$(curl -sS -o /tmp/ph1_apps.json -w "%{http_code}" -m 20 \
    http://127.0.0.1:8083/api/v1/applications \
    -H "Authorization: Bearer $TOKEN")
  echo "applications_with_bearer=$code"
fi
echo "=== RESTORE DISPOSABLE ==="
DUMP=$(ls -1t /opt/billiontech/backups/los_core_staging_pre_los_production_hardening_1_*.dump 2>/dev/null | head -1)
echo "DUMP=$DUMP"
BYTES=$(stat -c%s "$DUMP")
SHA=$(sha256sum "$DUMP" | awk '{print $1}')
echo "BYTES=$BYTES"
echo "SHA256=$SHA"
test "$BYTES" -gt 0
docker exec billiontech-postgres psql -U postgres -c "DROP DATABASE IF EXISTS los_core_restore_test;"
docker exec billiontech-postgres psql -U postgres -c "CREATE DATABASE los_core_restore_test OWNER los_app;"
docker cp "$DUMP" billiontech-postgres:/tmp/los_restore_ph1.dump
docker exec billiontech-postgres pg_restore -U los_app -d los_core_restore_test --clean --if-exists /tmp/los_restore_ph1.dump >/tmp/pg_restore_out.txt 2>&1 || true
docker exec billiontech-postgres psql -U los_app -d los_core_restore_test -c \
  "SELECT (SELECT count(*) FROM loan_applications) apps, (SELECT count(*) FROM workflow_configs) workflows;"
echo "RESTORE_OK"
if [ -f /tmp/ui-dist-los-production-hardening-1.tar.gz ]; then
  UI_DIR=/opt/billiontech/apps/billiontechlos/ui-dist
  mkdir -p "$UI_DIR"
  tar -xzf /tmp/ui-dist-los-production-hardening-1.tar.gz -C "$UI_DIR"
  echo "UI_DIST_DEPLOYED=$UI_DIR"
fi
echo "VERIFY_DONE"
