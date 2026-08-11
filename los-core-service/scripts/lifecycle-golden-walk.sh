#!/usr/bin/env bash
set -euo pipefail
BASE=http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo
HDR_CT="Content-Type: application/json"
HDR_TN="X-Tenant-Id: staging"

CREATE=$(curl -sS -X POST "$BASE/policy-studio/create" \
  -H "$HDR_CT" -H "$HDR_TN" \
  -d "{\"policyName\":\"Lifecycle Golden Walk $(date -u +%H%M%S)\",\"description\":\"POLICY-LIFECYCLE-FIX-1\"}")
DOC=$(echo "$CREATE" | python3 -c "import json,sys; d=json.load(sys.stdin); print((d.get('policyHeader') or {}).get('documentId') or '')")
echo "DOC=$DOC"
if [ -z "$DOC" ]; then echo "$CREATE" | head -c 800; exit 1; fi

add_cap() {
  local body="$1"
  local code
  code=$(curl -sS -o /tmp/addcap.json -w "%{http_code}" -X POST \
    "$BASE/policy-studio/documents/$DOC/rules/add-catalogue-capability" \
    -H "$HDR_CT" -H "$HDR_TN" -d "$body")
  echo "add_cap http=$code $(python3 -c "import json; d=json.load(open('/tmp/addcap.json')); print(d.get('message') or d.get('error') or 'ok')")"
}

add_cap '{"businessCapabilityId":"ELIG.BUREAU_SCORE_MIN","parameters":{"minimumScore":650}}'
add_cap '{"businessCapabilityId":"ELIG.REQUIRE_KYC_PASS","parameters":{}}'
add_cap '{"businessCapabilityId":"FOIR.MAX_PERCENT","parameters":{"maximumPercentage":50}}'
add_cap '{"businessCapabilityId":"AGE.MIN_YEARS","parameters":{"minimumYears":21}}'

curl -sS -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/save-draft" \
  -H "$HDR_CT" -H "$HDR_TN" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","reasonForChange":"Golden walk"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); life=d.get('lifecycle') or d; print('save', life.get('businessStatus'), d.get('message') or life.get('message'))"

curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "$HDR_TN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('afterSave status',d.get('businessStatus')); print('primary',d.get('primaryAction')); print('submitBlockers',d.get('submitBlockers')); print('readiness',[(i.get('label'),i.get('ok')) for i in d.get('readinessItems') or []])"

curl -sS -o /tmp/qt.json -w "quick=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/test/quick" -H "$HDR_CT" -H "$HDR_TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/qt.json')); print('test', d.get('simulatedDecision'), 'evidence', d.get('lifecycleEvidenceStamped'), 'run', d.get('runId'))"

curl -sS -o /tmp/sub.json -w "submit=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/submit-review" -H "$HDR_CT" -H "$HDR_TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/sub.json')); life=d.get('lifecycle') or d; print('submit', d.get('message') or life.get('message') or d.get('error'), life.get('businessStatus') or d.get('businessStatus') or d.get('status'))"

curl -sS -o /tmp/cm.json -w "cm=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/approvals/credit-manager" -H "$HDR_CT" -H "$HDR_TN" \
  -d '{"reviewer":"credit_manager","comment":"CM ok"}'
python3 -c "import json; d=json.load(open('/tmp/cm.json')); print('cm', d.get('message') or d.get('error') or list(d.keys())[:6])"

curl -sS -o /tmp/ck.json -w "ck=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/approvals/checker" -H "$HDR_CT" -H "$HDR_TN" \
  -d '{"reviewer":"policy_checker","comment":"Checker ok"}'
python3 -c "import json; d=json.load(open('/tmp/ck.json')); print('ck', d.get('message') or d.get('error') or list(d.keys())[:6])"

curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "$HDR_TN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('mid status',d.get('businessStatus')); print('primary',d.get('primaryAction')); print('approvals',d.get('approvals')); print('approveBlockers',d.get('approveBlockers'))"

curl -sS -o /tmp/ap.json -w "approve=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/approve" -H "$HDR_CT" -H "$HDR_TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/ap.json')); life=d.get('lifecycle') or d; print('approve', d.get('message') or life.get('message') or d.get('error'), life.get('businessStatus') or d.get('businessStatus') or d.get('status'))"

curl -sS -o /tmp/sc.json -w "sched=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/schedule" -H "$HDR_CT" -H "$HDR_TN" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","businessDate":"2026-09-15"}'
python3 -c "import json; d=json.load(open('/tmp/sc.json')); life=d.get('lifecycle') or d; print('sched', d.get('message') or life.get('message') or d.get('error'), life.get('businessStatus') or d.get('businessStatus'), 'allowCanonical', life.get('allowCanonicalAuthority'), 'prodAuth', life.get('productionAuthority'))"

curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "$HDR_TN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('FINAL', d.get('businessStatus')); print('primary', d.get('primaryAction')); print('shadow', d.get('shadowBoundary'))"
echo "DONE DOC=$DOC"
