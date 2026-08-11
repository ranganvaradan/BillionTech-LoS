#!/bin/bash
set -eu
BASE=http://127.0.0.1:8083
IDA=0d792e8b-8f4a-486d-b749-b1b2115df2fb
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT status, loan_product, intake_segment, requested_amount, borrower_type FROM loan_applications WHERE id='$IDA';"
curl -sS -o /tmp/g_uw.json -w "g_uw=%{http_code}\n" -X POST "$BASE/api/v1/flow/$IDA/underwrite" \
  -H 'X-User-Role: CREDIT_MANAGER' -H 'X-User-Id: a1000000-0000-0000-0000-000000000002' \
  -H 'Content-Type: application/json'
head -c 500 /tmp/g_uw.json; echo
# also try anchor underwriting approve path is later — do not disburse
