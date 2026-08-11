#!/bin/bash
set -eu
echo '=== PROGRAM / PLP LINKS ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c '\dt *program*'
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name='program_masters' ORDER BY 1;" 2>/dev/null || true
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT id::text, name FROM program_masters LIMIT 15;" 2>/dev/null || true

echo '=== LOS USERS BY ROLE ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT role, count(*) FROM los_users GROUP BY role ORDER BY 1;" 2>/dev/null || \
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c '\d los_users' | head -30

echo '=== PROPRIETOR TERM dual rule sets ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT name, priority, active, id::text FROM underwriting_rule_sets WHERE borrower_type='PROPRIETOR' AND loan_product='TERM_LOAN' AND active=true;"

python3 - <<'PY'
import json, os
p='/tmp/cutover_full/COMPANY_BUSINESS_TERM_LOAN_BORROWER.json'
if os.path.exists(p):
  d=json.load(open(p))
  r=(d.get('response') or {}).get('readiness') or {}
  c=(d.get('response') or {}).get('conflicts') or {}
  print('COMPANY BUSINESS_TERM ready', r.get('ready'), r.get('status'))
  print('gaps', r.get('gaps'))
  print('messages', c.get('messages'))
  print('ambiguous', c.get('ambiguous'))
PY
