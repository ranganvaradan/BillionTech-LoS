#!/bin/bash
set -eu
BASE=${1:-http://127.0.0.1:8083}

echo "=== GOLDEN A — Company Term Loan compose ==="
GOLDEN=$(curl -sS -m 40 "$BASE/api/v1/admin/live-readiness/product-configuration/golden")
echo "$GOLDEN" | head -c 2000; echo
echo "$GOLDEN" | grep -q 'allowCanonicalAuthority.:false' || echo "$GOLDEN" | grep -q '"allowCanonicalAuthority":false'
echo "$GOLDEN" | grep -q 'Runtime' || true
MATCH=$(echo "$GOLDEN" | grep -o '"productConfigMatchesRuntime":[^,]*' | head -1 || true)
echo "MATCH_FIELD=$MATCH"

echo "=== Flyway V117 ==="
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version, success FROM flyway_schema_history WHERE version='117';"

echo "=== borrower_types column ==="
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT column_name, data_type FROM information_schema.columns WHERE table_name='ci_policy_applicability' AND column_name IN ('borrower_type','borrower_types');"

echo "=== OK ==="
