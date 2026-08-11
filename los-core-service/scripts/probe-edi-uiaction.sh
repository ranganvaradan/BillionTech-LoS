#!/bin/bash
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'
curl -sS -m 90 -X POST "$BASE/policy-studio/banking/reset" -H "X-Internal-Token: $TOKEN" -o /tmp/b.json
DOC=$(python3 -c 'import json;d=json.load(open("/tmp/b.json"));print((d.get("policyHeader") or {}).get("documentId"))')
RID=$(python3 -c 'import json;d=json.load(open("/tmp/b.json"));
for c in d.get("underwritingRules") or []:
  if "BANK_DIGILEAP_ADB" in str(c.get("systemRuleId")):
    print(c.get("ruleId") or c.get("id")); break')
echo "DOC=$DOC RID=$RID"
curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$DOC/rules/$RID/review" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"uiAction":"RESOLVE_PARAMETER_MANUAL","operandKey":"proposed_edi","originalTerm":"Proposed EDI","manualInputLabel":"Proposed EDI","manualInputType":"Money","unit":"INR","requiredActor":"Credit Analyst","reviewer":"credit_manager"}' \
  -o /tmp/res.json
curl -sS -m 30 "$BASE/policy-studio/documents/$DOC" -H "X-Internal-Token: $TOKEN" -o /tmp/b2.json
curl -sS -m 30 "$BASE/policy-studio/documents/$DOC/lifecycle" -H "X-Internal-Token: $TOKEN" -o /tmp/l2.json
python3 <<'PY'
import json
d=json.load(open("/tmp/b2.json"))
life=json.load(open("/tmp/l2.json"))
edi_left=[]
for c in d.get("underwritingRules") or []:
  if "EDI" in str(c.get("systemRuleId") or "").upper():
    print(c.get("systemRuleId"), "status=", c.get("status"), "execReady=", c.get("executionReady"))
    if c.get("executionReady") is not True:
      edi_left.append(c.get("systemRuleId"))
print("EDI not ready:", edi_left)
rb=set(str(b.get("blockerKey")) for b in (d.get("executionBlockers") or []) if isinstance(b,dict))
vb=set(str(b.get("blockerKey")) for b in (life.get("executionBlockers") or []) if isinstance(b,dict))
print("blockerEqual", rb==vb)
print("EDI blockers left", [k for k in rb if "edi" in k.lower() or "EDI" in k])
print("exactly100", [k for k in rb if "100" in k])
print("bulk", [k for k in rb if "bulk" in k.lower() or "10" in k])
# inward return card
for c in d.get("underwritingRules") or []:
  if "INWARD" in str(c.get("systemRuleId") or "").upper():
    print("INWARD status", c.get("status"), "execReady", c.get("executionReady"), "blockers", c.get("executionBlockers"))
PY
