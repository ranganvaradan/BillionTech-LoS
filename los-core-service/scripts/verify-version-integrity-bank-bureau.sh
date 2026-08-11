#!/bin/bash
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'

curl -sS -m 90 -X POST "$BASE/policy-studio/banking/reset" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bank.json
BANK=$(python3 -c 'import json;d=json.load(open("/tmp/vi1-bank.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "BANK=$BANK"
test -n "$BANK"
curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$BANK/lifecycle/stamp-active-for-versioning" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"products":["DIGILEAP"]}' -o /tmp/vi1-bank-stamp.json
curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$BANK/lifecycle/new-version" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"reasonForChange":"VI1 bank"}' -o /tmp/vi1-bank-nv.json
python3 <<'PY'
import json
d=json.load(open("/tmp/vi1-bank-nv.json"))
print("BANK_NV", d.get("structuredClone"), d.get("reingested"), d.get("underwritingRuleCount"))
assert d.get("structuredClone") is True and d.get("reingested") is False
v2=(d.get("policyHeader") or {}).get("documentId") or d.get("documentId")
assert v2
open("/tmp/vi1-bank-v2.txt","w").write(str(v2))
print("BANK_V2", v2)
PY
curl -sS -m 30 "$BASE/policy-studio/documents/$(cat /tmp/vi1-bank-v2.txt)" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bank-v2.json
python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-bank.json"))
v2=json.load(open("/tmp/vi1-bank-v2.json"))
u1=len(v1.get("underwritingRules") or [])
u2=len(v2.get("underwritingRules") or [])
print("BANK_COMPARE", u1, u2)
assert u1 == u2 and u1 > 0
print("BANKING_WALKTHROUGH_OK")
print("allowCanonical", v2.get("allowCanonicalAuthority"))
PY

curl -sS -m 90 -X POST "$BASE/policy-studio/bureau/reset" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bur.json
BUR=$(python3 -c 'import json;d=json.load(open("/tmp/vi1-bur.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "BUR=$BUR"
test -n "$BUR"
curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$BUR/lifecycle/stamp-active-for-versioning" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" -d '{}' -o /tmp/vi1-bur-stamp.json
curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$BUR/lifecycle/new-version" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"reasonForChange":"VI1 bureau"}' -o /tmp/vi1-bur-nv.json
python3 <<'PY'
import json
d=json.load(open("/tmp/vi1-bur-nv.json"))
print("BUREAU_NV", d.get("structuredClone"), d.get("reingested"), d.get("underwritingRuleCount"))
assert d.get("structuredClone") is True and d.get("reingested") is False
v2=(d.get("policyHeader") or {}).get("documentId") or d.get("documentId")
open("/tmp/vi1-bur-v2.txt","w").write(str(v2))
print("BUREAU_V2", v2)
PY
curl -sS -m 30 "$BASE/policy-studio/documents/$(cat /tmp/vi1-bur-v2.txt)" \
  -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bur-v2.json
python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-bur.json"))
v2=json.load(open("/tmp/vi1-bur-v2.json"))
u1=len(v1.get("underwritingRules") or [])
u2=len(v2.get("underwritingRules") or [])
print("BUREAU_COMPARE", u1, u2)
assert u1 == u2 and u1 > 0
comp=sum(1 for c in (v2.get("underwritingRules") or [])
         if c.get("compoundParent") or "overdue" in str(c).lower() or "compound" in str(c).lower())
print("BUREAU compound/overdue", comp)
print("BUREAU_WALKTHROUGH_OK")
print("allowCanonical", v2.get("allowCanonicalAuthority"))
PY
echo DONE_BANK_BUREAU
