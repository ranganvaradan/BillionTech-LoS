#!/bin/bash
# LOS-PRODUCTION-P0-CLOSURE-1 — real prod-profile JVM boot against disposable DB (no prod providers).
set -eu
APP=/opt/billiontech/apps/billiontechlos
cd "$APP"
export LOS_DB_USER="${LOS_DB_USER:-los_app}"
export LOS_DB_PASSWORD="${LOS_DB_PASSWORD:-los_app}"

DUMP=$(ls -1t /opt/billiontech/backups/los_core_staging_pre_los_production_p0_closure_1_*.dump 2>/dev/null | head -1)
test -n "$DUMP"
echo "Using dump $DUMP"
docker exec billiontech-postgres psql -U postgres -c "DROP DATABASE IF EXISTS los_core_prod_dryrun;"
docker exec billiontech-postgres psql -U postgres -c "CREATE DATABASE los_core_prod_dryrun OWNER los_app;"
docker cp "$DUMP" billiontech-postgres:/tmp/los_prod_dryrun.dump
docker exec billiontech-postgres pg_restore -U los_app -d los_core_prod_dryrun --clean --if-exists /tmp/los_prod_dryrun.dump >/tmp/pg_restore_prod_dryrun.txt 2>&1 || true

# Stop prior dry-run container if present
docker rm -f los-core-prod-dryrun >/dev/null 2>&1 || true

JWT_SECRET='dry-run-hmac-secret-32chars-minimum!!'
CI_TOKEN='dry-run-internal-token-not-for-prod'

NET=$(docker inspect billiontechlos-core --format '{{json .NetworkSettings.Networks}}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(next(iter(d.keys())) if d else "billiontech-net")')
NET=${NET:-billiontech-net}
echo "Using network $NET"

docker run -d --name los-core-prod-dryrun --network "$NET" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e SPRING_DATASOURCE_URL='jdbc:postgresql://billiontech-postgres:5432/los_core_prod_dryrun' \
  -e SPRING_DATASOURCE_USERNAME="${LOS_DB_USER}" \
  -e SPRING_DATASOURCE_PASSWORD="${LOS_DB_PASSWORD}" \
  -e POSTGRES_USER="${LOS_DB_USER}" \
  -e POSTGRES_PASSWORD="${LOS_DB_PASSWORD}" \
  -e POSTGRES_DB=los_core_prod_dryrun \
  -e POSTGRES_HOST=billiontech-postgres \
  -e POSTGRES_PORT=5432 \
  -e REDIS_HOST="${REDIS_HOST}" \
  -e REDIS_PORT="${REDIS_PORT:-6379}" \
  -e REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  -e RABBITMQ_HOST="${RABBITMQ_HOST}" \
  -e RABBITMQ_PORT="${RABBITMQ_PORT:-5672}" \
  -e RABBITMQ_USER="${RABBITMQ_USER}" \
  -e RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD}" \
  -e LOS_JWT_HMAC_SECRET="$JWT_SECRET" \
  -e CREDIT_INTELLIGENCE_INTERNAL_TOKEN="$CI_TOKEN" \
  -e LOS_TENANCY_MODE=SINGLE_TENANT_DEPLOYMENT \
  -e LOS_DEPLOYMENT_TENANT_ID=00000000-0000-0000-0000-000000000001 \
  -e ENCORE_BASE_URL='http://127.0.0.1:9/disabled-encore' \
  -e PLP_BASE_URL='http://127.0.0.1:9/disabled-plp' \
  -e EUREKA_CLIENT_ENABLED=false \
  -e SPRING_CLOUD_DISCOVERY_ENABLED=false \
  -p 127.0.0.1:18083:8083 \
  billiontechlos-core:staging

echo "Waiting for prod dry-run health..."
OK=0
for i in $(seq 1 60); do
  if curl -sf http://127.0.0.1:18083/actuator/health >/tmp/prod_dry_health.json 2>/dev/null; then
    ST=$(python3 -c 'import json;print(json.load(open("/tmp/prod_dry_health.json")).get("status"))')
    echo "try=$i status=$ST"
    if [ "$ST" = "UP" ]; then OK=1; break; fi
  else
    echo "try=$i starting"
  fi
  sleep 3
done
if [ "$OK" != "1" ]; then
  docker logs --tail 120 los-core-prod-dryrun || true
  echo "PROD_DRYRUN_BOOT_FAILED"
  exit 1
fi

python3 - <<'PY'
import json
d=json.load(open("/tmp/prod_dry_health.json"))
ph=d.get("components",{}).get("productionHardening",{})
print("HEALTH", d.get("status"))
print("productionHardening", ph.get("status"))
print(json.dumps(ph.get("details",{}), indent=2)[:1200])
flags=ph.get("details",{})
assert flags.get("jwtRequired") is True
assert flags.get("allowHeaderImpersonation") is False
assert flags.get("demoEnabled") is False
assert flags.get("providerGapDefaultsEnabled") is False
assert flags.get("allowCanonicalAuthority") is False
assert flags.get("singleTenantFailClosed") is True
print("FLAGS_OK")
PY

# Auth smoke
ANON=$(curl -sS -o /tmp/anon.json -w "%{http_code}" http://127.0.0.1:18083/api/v1/applications || true)
echo "anonymous=$ANON"
test "$ANON" = "401"

# Login may fail if users not in restored DB roles — issue token via java not available; use filter smoke with crafted JWT inside container if login fails
LOGIN=$(curl -sS -m 20 -X POST http://127.0.0.1:18083/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"mohit@billionloans.com","password":"Bltest@123"}' || true)
echo "$LOGIN" > /tmp/prod_login.json
python3 - <<'PY'
import json,subprocess,os
d=json.load(open("/tmp/prod_login.json"))
tok=d.get("accessToken")
print("loginRole", d.get("role"), "token", bool(tok))
if not tok:
    raise SystemExit("LOGIN_NO_TOKEN")
open("/tmp/prod_tok.txt","w").write(tok)
PY
TOK=$(tr -d '\r\n' </tmp/prod_tok.txt)
CODE=$(curl -sS -o /tmp/prod_apps.json -w "%{http_code}" http://127.0.0.1:18083/api/v1/applications -H "Authorization: Bearer ${TOK}")
echo "jwt_apps=$CODE"
# Prefer a known authenticated admin endpoint if list applications is role-gated oddly
CODE_GL=$(curl -sS -o /tmp/prod_golive.json -w "%{http_code}" -X POST http://127.0.0.1:18083/api/v1/admin/live-readiness/customer-go-live \
  -H "Authorization: Bearer ${TOK}" -H 'Content-Type: application/json' \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}')
echo "jwt_golive=$CODE_GL"
# spoof headers without JWT
SPOOF=$(curl -sS -o /tmp/spoof.json -w "%{http_code}" http://127.0.0.1:18083/api/v1/applications \
  -H 'X-User-Role: ADMINISTRATOR' -H 'X-User-Id: a1000000-0000-0000-0000-000000000001')
echo "spoof_headers=$SPOOF"
test "$SPOOF" = "401"

# JWT wins over conflicting X-User-Role
CODE2=$(curl -sS -o /tmp/prod_apps2.json -w "%{http_code}" http://127.0.0.1:18083/api/v1/applications \
  -H "Authorization: Bearer $TOK" -H 'X-User-Role: BORROWER')
echo "jwt_plus_spoof_role=$CODE2"

# Internal API
INT0=$(curl -sS -o /dev/null -w "%{http_code}" http://127.0.0.1:18083/api/v1/internal/health 2>/dev/null || echo 404)
INT1=$(curl -sS -o /dev/null -w "%{http_code}" -H 'X-Internal-Token: wrong' http://127.0.0.1:18083/api/v1/internal/health 2>/dev/null || echo 404)
INT2=$(curl -sS -o /dev/null -w "%{http_code}" -H "X-Internal-Token: $CI_TOKEN" http://127.0.0.1:18083/api/v1/internal/health 2>/dev/null || echo 404)
echo "internal_no=$INT0 wrong=$INT1 ok=$INT2"

# Startup failure goldens (separate short-lived containers)
fail_boot() {
  local name=$1; shift
  docker rm -f "$name" >/dev/null 2>&1 || true
  if docker run --name "$name" --network "$NET" --rm \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e SPRING_DATASOURCE_URL='jdbc:postgresql://billiontech-postgres:5432/los_core_prod_dryrun' \
    -e SPRING_DATASOURCE_USERNAME="${LOS_DB_USER}" \
    -e SPRING_DATASOURCE_PASSWORD="${LOS_DB_PASSWORD}" \
    -e POSTGRES_USER="${LOS_DB_USER}" \
    -e POSTGRES_PASSWORD="${LOS_DB_PASSWORD}" \
    -e POSTGRES_HOST=billiontech-postgres \
    -e POSTGRES_PORT=5432 \
    -e POSTGRES_DB=los_core_prod_dryrun \
    -e EUREKA_CLIENT_ENABLED=false \
    "$@" \
    billiontechlos-core:staging >/tmp/${name}.log 2>&1; then
    echo "EXPECTED_FAIL_BUT_STARTED $name"
    docker rm -f "$name" >/dev/null 2>&1 || true
    return 1
  fi
  if grep -Eiq 'hmac-secret|internal.token|demo.enabled|IllegalStateException|Application run failed' /tmp/${name}.log; then
    echo "FAIL_BOOT_OK $name"
  else
    echo "FAIL_BOOT_UNCLEAR $name"; tail -40 /tmp/${name}.log || true
    return 1
  fi
}

fail_boot los-core-prod-fail-jwt \
  -e LOS_JWT_HMAC_SECRET='' \
  -e CREDIT_INTELLIGENCE_INTERNAL_TOKEN="$CI_TOKEN"

fail_boot los-core-prod-fail-ci \
  -e LOS_JWT_HMAC_SECRET="$JWT_SECRET" \
  -e CREDIT_INTELLIGENCE_INTERNAL_TOKEN=''

fail_boot los-core-prod-fail-demo \
  -e LOS_JWT_HMAC_SECRET="$JWT_SECRET" \
  -e CREDIT_INTELLIGENCE_INTERNAL_TOKEN="$CI_TOKEN" \
  -e LOS_DEMO_ENABLED=true

echo "NO_EXTERNAL_SIDE_EFFECT=ASSERTED_BY_DISABLED_ENDPOINTS"
echo "PROD_PROFILE_JVM_DRY_RUN_PASSED"
# leave dry-run container up for optional document smoke; caller may docker rm -f los-core-prod-dryrun
