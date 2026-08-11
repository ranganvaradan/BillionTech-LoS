#!/usr/bin/env bash
set -euo pipefail
BASE=http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo
CT="Content-Type: application/json"
TN="X-Tenant-Id: staging"

CREATE=$(curl -sS -X POST "$BASE/policy-studio/create" -H "$CT" -H "$TN" \
  -d "{\"policyName\":\"Lifecycle Golden v2 $(date -u +%H%M%S)\",\"description\":\"POLICY-LIFECYCLE-FIX-1\"}")
DOC=$(echo "$CREATE" | python3 -c "import json,sys; d=json.load(sys.stdin); print((d.get('policyHeader') or {}).get('documentId') or '')")
echo "DOC=$DOC"
test -n "$DOC"

curl -sS "$BASE/policy-studio/capabilities" -H "$TN" | python3 -c "
import json,sys
d=json.load(sys.stdin)
ids=[]
for g,arr in (d.get('groups') or {}).items():
  for c in arr:
    cid=c.get('businessCapabilityId') or c.get('id')
    if cid and cid not in ids:
      ids.append(str(cid))
    if len(ids)>=4: break
  if len(ids)>=4: break
open('/tmp/caps4.txt','w').write('\n'.join(ids[:4]))
print('caps', ids[:4])
"

while IFS= read -r CAP; do
  [ -z "$CAP" ] && continue
  BODY=$(python3 -c "import json,sys; print(json.dumps({'businessCapabilityId':sys.argv[1],'parameters':{}}))" "$CAP")
  CODE=$(curl -sS -o /tmp/add.json -w "%{http_code}" -X POST \
    "$BASE/policy-studio/documents/$DOC/rules/add-catalogue-capability" -H "$CT" -H "$TN" -d "$BODY")
  MSG=$(python3 -c "import json; d=json.load(open('/tmp/add.json')); print(d.get('message') or d.get('error') or 'ok')")
  echo "add $CAP -> $CODE $MSG"
done < /tmp/caps4.txt

curl -sS -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/save-draft" -H "$CT" -H "$TN" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","reasonForChange":"Golden walk v2"}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); life=d.get('lifecycle') or d; print('save', life.get('businessStatus'))"

curl -sS -o /tmp/qt.json -w "quick=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/test/quick" -H "$CT" -H "$TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/qt.json')); print('test evidence', d.get('lifecycleEvidenceStamped'), d.get('runId'))"

curl -sS -o /tmp/sub.json -w "submit=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/submit-review" -H "$CT" -H "$TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/sub.json')); life=d.get('lifecycle') or d; print('submit', d.get('status'), d.get('message') or life.get('message'), life.get('businessStatus') or d.get('businessStatus'))"

curl -sS -o /tmp/cm.json -w "cm=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/approvals/credit-manager" -H "$CT" -H "$TN" \
  -d '{"reviewer":"credit_manager","comments":"CM ok"}'
python3 -c "import json; d=json.load(open('/tmp/cm.json')); print('cm', d.get('status'), d.get('message') or d.get('error'))"

curl -sS -o /tmp/ck.json -w "ck=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/approvals/checker" -H "$CT" -H "$TN" \
  -d '{"reviewer":"policy_checker","comments":"Checker ok"}'
python3 -c "import json; d=json.load(open('/tmp/ck.json')); print('ck', d.get('status'), d.get('message') or d.get('error'))"

curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "$TN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('mid', d.get('businessStatus')); print('primary', d.get('primaryAction')); print('approvals', d.get('approvals')); print('approveBlockers', d.get('approveBlockers'))"

curl -sS -o /tmp/ap.json -w "approve=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/approve" -H "$CT" -H "$TN" -d '{}'
python3 -c "import json; d=json.load(open('/tmp/ap.json')); life=d.get('lifecycle') or d; print('approve', d.get('status'), d.get('message') or life.get('message'), life.get('businessStatus') or d.get('businessStatus'))"

curl -sS -o /tmp/sc.json -w "sched=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/schedule" -H "$CT" -H "$TN" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","businessDate":"2026-09-15"}'
python3 -c "import json; d=json.load(open('/tmp/sc.json')); life=d.get('lifecycle') or d; print('sched', d.get('status'), d.get('message') or life.get('message'), life.get('businessStatus') or d.get('businessStatus'), 'allowCanonical', life.get('allowCanonicalAuthority'), 'prod', life.get('productionAuthority'))"

curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "$TN" \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('FINAL', d.get('businessStatus')); print('primary', d.get('primaryAction')); print('shadow', d.get('shadowBoundary')); print('allowCanonical', d.get('allowCanonicalAuthority'))"
echo DONE DOC=$DOC
