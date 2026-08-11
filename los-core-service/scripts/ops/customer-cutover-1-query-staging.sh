#!/bin/bash
# Read-only staging probes for LOS-CUSTOMER-CUTOVER-1 (examples only — not customer config).
set -eu
BASE=${BASE:-http://127.0.0.1:8083}
HDR=(-H 'X-User-Role: CREDIT_MANAGER' -H 'X-User-Id: a1000000-0000-0000-0000-000000000002')

echo '=== GOLDEN COMPOSE (staging example) ==='
curl -sS -m 60 "$BASE/api/v1/admin/live-readiness/product-configuration/golden" "${HDR[@]}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(json.dumps({
    "borrowerType": d.get("borrowerType"),
    "loanProduct": d.get("loanProduct"),
    "workflow": (d.get("workflow") or {}),
    "liveRuleSet": (d.get("liveRuleSet") or d.get("ruleSet") or {}),
    "scorecard": (d.get("scorecard") or {}),
    "lms": (d.get("lms") or {}),
    "plp": (d.get("plp") or {}),
    "goLiveBlockers": d.get("goLiveBlockers") or d.get("blockers"),
    "readiness": d.get("readiness") or d.get("status"),
    "policyStudio": d.get("policyStudio"),
    "customerConfigTemplate": d.get("customerConfigTemplate"),
  }, indent=2)[:8000])'

echo
echo '=== CUSTOMER GO-LIVE (customerConfigSupplied=false) ==='
curl -sS -m 60 -X POST "$BASE/api/v1/admin/live-readiness/customer-go-live" "${HDR[@]}" \
  -H 'Content-Type: application/json' \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("safe",d.get("safeToGoLiveAnswer"),"failed",d.get("failedCheckCount"));
checks=d.get("checks") or [];
[print(c.get("id"), c.get("pass"), c.get("detail") or c.get("message") or "") for c in checks]'

echo
echo '=== FLYWAY HEAD ==='
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT version||' '||description FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 3;"
