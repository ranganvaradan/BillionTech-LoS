#!/bin/bash
# Resolve Product Configuration for Billionloans reference cutover combinations.
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
HDR=(-H 'X-User-Role: CREDIT_MANAGER' -H 'X-User-Id: a1000000-0000-0000-0000-000000000002' -H 'Content-Type: application/json')
OUT=/tmp/cutover_resolve
mkdir -p "$OUT"

echo '=== OPTIONS ==='
curl -sS -m 60 "$BASE/api/v1/admin/live-readiness/product-configuration/options" "${HDR[@]}" > "$OUT/options.json"
python3 - <<'PY'
import json
d=json.load(open('/tmp/cutover_resolve/options.json'))
print(json.dumps({k:d.get(k) for k in d if k in (
  'borrowerTypes','loanProducts','intakeSegments','workflows','liveRuleSets','scorecards','products'
) or True}, indent=2)[:12000])
# print top-level keys
print('KEYS', list(d.keys())[:40])
PY

# Also dump workflow/rules/scorecards tables
echo '=== DB WORKFLOWS ACTIVE ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT borrower_type, loan_product, intake_segment, name, version, status, id::text, lms_product_code
   FROM workflow_configs WHERE COALESCE(active,true) OR status ILIKE '%ACTIVE%'
   ORDER BY borrower_type, loan_product, intake_segment, version DESC;" 2>/dev/null || \
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "\d workflow_configs" | head -40

echo '=== DB RULE SETS ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT borrower_type, loan_product, name, version, active, priority, id::text
   FROM underwriting_rule_sets ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;" 2>/dev/null || \
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "\dt *rule*"

echo '=== DB SCORECARDS ACTIVE ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT borrower_type, loan_product, name, version, status, priority, min_amount, max_amount, id::text
   FROM underwriting_scorecards WHERE status='ACTIVE'
   ORDER BY borrower_type, loan_product, priority DESC NULLS LAST;" 2>/dev/null || true
