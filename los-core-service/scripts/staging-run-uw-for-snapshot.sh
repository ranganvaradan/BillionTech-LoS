#!/bin/bash
set -eu
BASE=http://127.0.0.1:8083
APP=$(docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT id FROM loan_applications WHERE loan_product='TERM_LOAN' AND borrower_type='COMPANY' ORDER BY created_at DESC LIMIT 1;")
APP=$(echo "$APP" | tr -d '[:space:]')
echo "APP=$APP"
test -n "$APP"

code=$(curl -sS -o /tmp/uw_out.json -w "%{http_code}" -X POST \
  "$BASE/api/v1/flow/$APP/underwrite" \
  -H "X-User-Role: CREDIT_MANAGER" \
  -H "X-User-Id: a1000000-0000-0000-0000-000000000002" \
  -H "Content-Type: application/json")
echo "underwrite=$code"
head -c 600 /tmp/uw_out.json; echo

docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT id, aggregate_decision, decision_snapshot_json IS NOT NULL AS has_snap,
          left(decision_snapshot_json::text, 120) AS snap_prefix
     FROM underwriting_evaluations
    WHERE application_id='$APP'
    ORDER BY evaluated_at DESC LIMIT 3;"
