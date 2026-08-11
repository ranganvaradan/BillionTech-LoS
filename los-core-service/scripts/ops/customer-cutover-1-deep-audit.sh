#!/bin/bash
# Deep audit for final business decisions — read-only, no deactivation.
set -eu
OUT=/tmp/cutover_deep
mkdir -p "$OUT"
BASE=${BASE:-http://127.0.0.1:8083}
HDR=(-H 'X-User-Role: CREDIT_MANAGER' -H 'X-User-Id: a1000000-0000-0000-0000-000000000002' -H 'Content-Type: application/json')

echo '=== 1. PROPRIETOR TERM_LOAN RULE SETS (full) ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT id::text, name, priority, active, borrower_type, loan_product,
       COALESCE(description,'') AS description,
       pg_column_size(rules_json) AS rules_bytes
FROM underwriting_rule_sets
WHERE borrower_type='PROPRIETOR' AND loan_product='TERM_LOAN'
ORDER BY active DESC, priority DESC NULLS LAST;
" 2>/dev/null || docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT column_name FROM information_schema.columns WHERE table_name='underwriting_rule_sets' ORDER BY 1;
"

echo '=== 1b. PROPRIETOR TERM rule JSON peek ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -Atc "
SELECT id::text||'|'||name||'|'||priority::text||'|'||left(COALESCE(rules_json::text, rules::text, '{}'), 800)
FROM underwriting_rule_sets
WHERE borrower_type='PROPRIETOR' AND loan_product='TERM_LOAN' AND active=true
ORDER BY priority DESC;
" > "$OUT/proprietor_term_rules.txt" 2>/dev/null || \
docker exec billiontech-postgres psql -U los_app -d los_core_staging -Atc "
SELECT id::text||'|'||name||'|'||priority::text
FROM underwriting_rule_sets
WHERE borrower_type='PROPRIETOR' AND loan_product='TERM_LOAN' AND active=true
ORDER BY priority DESC;
" > "$OUT/proprietor_term_rules.txt"
cat "$OUT/proprietor_term_rules.txt"

echo '=== 2. COMPANY BUSINESS_TERM SCORECARDS ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT id::text, name, version, status, priority, min_amount, max_amount, active,
       governance_status, approved_at IS NOT NULL AS approved
FROM underwriting_scorecards
WHERE borrower_type='COMPANY' AND loan_product='BUSINESS_TERM_LOAN'
ORDER BY status, priority DESC NULLS LAST;
" 2>/dev/null || docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT id::text, name, version, status, priority, min_amount, max_amount
FROM underwriting_scorecards
WHERE borrower_type='COMPANY' AND loan_product='BUSINESS_TERM_LOAN'
ORDER BY status, priority DESC NULLS LAST;
"

echo '=== 3. ALL INVOICE DISCOUNTING WORKFLOWS (active+inactive) ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, intake_segment, active, version, name, id::text, COALESCE(lms_product_code,'') AS lms
FROM workflow_configs
WHERE loan_product='BUSINESS_WC_INVOICE_DISCOUNTING'
ORDER BY borrower_type, intake_segment, active DESC, version DESC;
"

echo '=== 4. INDIVIDUAL × ID × ANCHOR specifically ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT active, version, name, id::text, intake_segment
FROM workflow_configs
WHERE borrower_type='INDIVIDUAL'
  AND loan_product='BUSINESS_WC_INVOICE_DISCOUNTING'
ORDER BY intake_segment, active DESC, version DESC;
"

echo '=== 5. RISK_MANAGER users ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT id::text, email, primary_los_role, active
FROM los_users
WHERE primary_los_role ILIKE '%RISK%' OR primary_los_role ILIKE '%SANCTION%'
ORDER BY email;
"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT column_name FROM information_schema.columns WHERE table_name='user_role_mappings' ORDER BY 1;
"
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT * FROM user_role_mappings WHERE role::text ILIKE '%RISK%' OR los_role::text ILIKE '%RISK%' LIMIT 20;
" 2>/dev/null || docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT * FROM user_role_mappings LIMIT 5;
"

echo '=== 6. LMS codes per launch WF ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, loan_product, intake_segment, name, version, lms_product_code, id::text
FROM workflow_configs
WHERE active=true
  AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
  AND borrower_type IN ('INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY')
ORDER BY loan_product, borrower_type, intake_segment;
"

echo '=== 7. PROGRAM MASTERS PLP for ID ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT program_name, product_type, plp_program_id IS NOT NULL AS has_plp_id,
       plp_operational_status, encore_product_code, workflow_config_id::text
FROM program_masters
WHERE product_type ILIKE '%INVOICE%' OR program_name ILIKE '%Invoice%';
"

echo '=== 8. Scorecard amount bands all Day-1 ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c "
SELECT borrower_type, loan_product, name, priority, min_amount, max_amount, version, id::text
FROM underwriting_scorecards
WHERE status='ACTIVE'
  AND loan_product IN ('TERM_LOAN','BUSINESS_TERM_LOAN','BUSINESS_WC_INVOICE_DISCOUNTING')
  AND borrower_type IN ('INDIVIDUAL','PROPRIETOR','PARTNERSHIP','COMPANY')
ORDER BY borrower_type, loan_product, priority DESC;
"
