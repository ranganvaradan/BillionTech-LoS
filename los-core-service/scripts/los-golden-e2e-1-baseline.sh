#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"
mkdir -p /opt/billiontech/backups /tmp/los-golden-e2e-1

BACKUP=/opt/billiontech/backups/los_core_staging_pre_los_golden_e2e_1_${TS}.dump
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_e2e.dump
docker cp billiontech-postgres:/tmp/pre_e2e.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP"
echo "BYTES=$BYTES"
echo "SHA256=$SHA"
test "$BYTES" -gt 0
echo "RESTORE_CMD=docker exec -i billiontech-postgres pg_restore -U los_app -d los_core_staging --clean --if-exists < $BACKUP"

{
  echo "=== BASELINE $TS ==="
  echo "CORE_HEALTH=$(curl -sS -m 10 http://127.0.0.1:8083/actuator/health | head -c 200)"
  echo "--- FLYWAY ---"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
    "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
  echo "--- COUNTS ---"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
    SELECT
      (SELECT COUNT(*) FROM loan_applications) AS applications,
      (SELECT COUNT(*) FROM workflow_configs) AS workflows,
      (SELECT COUNT(*) FROM workflow_configs WHERE active) AS active_workflows,
      (SELECT COUNT(*) FROM underwriting_rule_sets WHERE active) AS active_rule_sets,
      (SELECT COUNT(*) FROM underwriting_scorecards WHERE active) AS active_scorecards,
      (SELECT COUNT(*) FROM canonical_parameters WHERE active) AS gacat_active;
  "
  echo "--- ACTIVE COMPANY TERM_LOAN WORKFLOWS ---"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
    SELECT id, name, version, active, lms_product_code, intake_segment
    FROM workflow_configs
    WHERE UPPER(borrower_type)='COMPANY' AND UPPER(loan_product)='TERM_LOAN'
    ORDER BY active DESC, version DESC;
  "
  echo "--- ACTIVE RULE SETS ---"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
    SELECT id, name, priority, active, borrower_type, loan_product
    FROM underwriting_rule_sets
    WHERE UPPER(borrower_type)='COMPANY' AND UPPER(REPLACE(loan_product,' ','_'))='TERM_LOAN'
    ORDER BY active DESC, priority DESC;
  "
  echo "--- ACTIVE SCORECARDS ---"
  docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
    SELECT id, name, version, priority, active, status, borrower_type, loan_product
    FROM underwriting_scorecards
    WHERE UPPER(borrower_type)='COMPANY' AND UPPER(REPLACE(loan_product,' ','_'))='TERM_LOAN'
    ORDER BY active DESC, priority DESC NULLS LAST;
  "
  echo "--- ENV FLAGS (redacted) ---"
  grep -E 'ENCORE_|LMS_|PROVIDER_GAP|ALLOW_CANONICAL|GAP_DEFAULT|DEMO_|SPRING_PROFILES' .env 2>/dev/null \
    | sed 's/PASSWORD=.*/PASSWORD=***/;s/TOKEN=.*/TOKEN=***/;s/SECRET=.*/SECRET=***/' || true
  echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
} | tee /tmp/los-golden-e2e-1/baseline_${TS}.txt

# Product Config golden
TOKEN=$(grep CREDIT_INTELLIGENCE_INTERNAL_TOKEN .env | cut -d= -f2- | tr -d '\r')
curl -sS -m 40 -H "X-Internal-Token: $TOKEN" -H "X-User-Role: ADMINISTRATOR" \
  -H "X-User-Id: c1000000-0000-0000-0000-000000000001" -H "X-User-Name: admin" \
  'http://127.0.0.1:8083/api/v1/admin/live-readiness/product-configuration/golden' \
  | tee /tmp/los-golden-e2e-1/product_config_golden.json | head -c 2500
echo
echo "BASELINE_OK TS=$TS"