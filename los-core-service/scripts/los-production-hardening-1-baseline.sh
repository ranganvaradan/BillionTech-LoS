#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"
mkdir -p /opt/billiontech/backups /tmp/los-production-hardening-1

BACKUP=/opt/billiontech/backups/los_core_staging_pre_los_production_hardening_1_${TS}.dump
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_ph1.dump
docker cp billiontech-postgres:/tmp/pre_ph1.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP"
echo "BYTES=$BYTES"
echo "SHA256=$SHA"
test "$BYTES" -gt 0
echo "RESTORE_CMD=docker exec -i billiontech-postgres pg_restore -U los_app -d los_core_staging_restore_test --clean --if-exists < $BACKUP"

{
  echo "=== BASELINE $TS ==="
  echo "CORE_IMAGE=$(docker inspect -f '{{.Image}}' billiontechlos-core 2>/dev/null || true)"
  echo "CORE_HEALTH=$(curl -sS -m 10 http://127.0.0.1:8083/actuator/health | head -c 300)"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
    "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
    SELECT (SELECT COUNT(*) FROM loan_applications) apps,
           (SELECT COUNT(*) FROM workflow_configs) workflows,
           (SELECT COUNT(*) FROM underwriting_rule_sets WHERE active) active_rs,
           (SELECT COUNT(*) FROM underwriting_scorecards WHERE active) active_sc,
           (SELECT COUNT(*) FROM ci_gacat_canonical_parameter) gacat;
  "
  echo "--- ENV FLAGS ---"
  grep -E 'SPRING_PROFILES|ENCORE_BASE|PLP_|LOS_|PROVIDER_GAP|DEMO_|JWT_|INTERNAL|CANONICAL' .env 2>/dev/null \
    | sed 's/PASSWORD=.*/PASSWORD=***/;s/TOKEN=.*/TOKEN=***/;s/SECRET=.*/SECRET=***/;s/KEY=.*/KEY=***/' || true
  docker exec billiontechlos-core printenv SPRING_PROFILES_ACTIVE || true
  echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
} | tee /tmp/los-production-hardening-1/baseline_${TS}.txt
echo "BASELINE_OK TS=$TS"
