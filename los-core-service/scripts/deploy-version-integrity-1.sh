#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

tar -xzf /tmp/version-integrity-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
echo "version-integrity-1 $TS" > DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> DEPLOYED_AT.txt

CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' "$CORE" 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'

stamp_and_version() {
  local DOC="$1"
  local LABEL="$2"
  curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/stamp-active-for-versioning" \
    -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
    -d '{"products":["DIGILEAP"],"reasonForChange":"VI1 stamp"}' -o "/tmp/vi1-${LABEL}-stamp.json"
  python3 -c "import json;d=json.load(open('/tmp/vi1-${LABEL}-stamp.json')); print('STAMP', '${LABEL}', (d.get('lifecycle') or d).get('businessStatus'), d.get('allowCanonicalAuthority'))"
  curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/new-version" \
    -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
    -d "{\"reasonForChange\":\"VI1 ${LABEL} structured clone\"}" -o "/tmp/vi1-${LABEL}-nv.json"
  python3 <<PY
import json
d=json.load(open("/tmp/vi1-${LABEL}-nv.json"))
print("${LABEL}_NV structuredClone", d.get("structuredClone"), "reingested", d.get("reingested"), "uw", d.get("underwritingRuleCount"))
print("${LABEL}_NV msg", str(d.get("message"))[:240])
assert d.get("structuredClone") is True, d
assert d.get("reingested") is False, d
header=d.get("policyHeader") or {}
v2=header.get("documentId") or d.get("documentId")
assert v2, d
open("/tmp/vi1-${LABEL}-v2.txt","w").write(str(v2))
life=d.get("lifecycle") or {}
assert (life.get("businessStatus") or d.get("businessStatus")) == "DRAFT" or str(life.get("businessStatus"))=="DRAFT"
print("${LABEL}_V2", v2)
PY
}

# A — simple 5-rule policy
curl -sS -m 60 -X POST "$BASE/policy-studio/create" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"policyName":"SME Term Loan Policy VI1"}' -o /tmp/vi1-scratch.json
DOC=$(python3 -c 'import json;d=json.load(open("/tmp/vi1-scratch.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "V1_DOC=$DOC"

add_rule() {
  curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$DOC/rules/add-plain-english" \
    -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
    -d "$1" -o /tmp/vi1-add.json
  python3 -c 'import json;d=json.load(open("/tmp/vi1-add.json")); assert d.get("confirmed") is True, d; print("ADD", d.get("ruleId"))'
}

add_rule '{"confirm":true,"mode":"BUILD","parameterId":"kyc.pan.verified","operator":"is","value":true,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"bureau.score","operator":">=","value":650,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"obligation.ratio","operator":"<=","value":50,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"application.business_vintage_months","operator":">=","value":24,"durationUnit":"Months","treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"application.requested_amount","operator":"<=","value":1000000,"treatment":"Reject"}'

curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/save-draft" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"products":["BUSINESS_TERM_LOAN"],"borrowerType":"COMPANY","reasonForChange":"VI1"}' -o /tmp/vi1-save.json || true

stamp_and_version "$DOC" "simple"
V2=$(cat /tmp/vi1-simple-v2.txt)
curl -sS -m 30 "$BASE/policy-studio/documents/$DOC" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-simple-v1.json
curl -sS -m 30 "$BASE/policy-studio/documents/$V2" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-simple-v2.json
python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-simple-v1.json"))
v2=json.load(open("/tmp/vi1-simple-v2.json"))
u1=v1.get("underwritingRules") or []
u2=v2.get("underwritingRules") or []
print("SIMPLE UW v1", len(u1), "v2", len(u2))
assert len(u2) >= 5, (len(u1), len(u2))
# typed value spot-check bureau 650 on both before edit
def bureau_const(sess):
  for c in (sess.get("underwritingRules") or []):
    blob=str(c).lower()
    if "bureau" in blob and "score" in blob:
      return c.get("thresholdValue") or c.get("authoringThreshold") or (c.get("metadata") or {}).get("threshold") or c.get("value")
  return None
print("SIMPLE bureau v1", bureau_const(v1), "v2", bureau_const(v2))
print("SIMPLE_WALKTHROUGH_OK")
print("allowCanonical v2", v2.get("allowCanonicalAuthority"))
PY

# Edit v2 bureau via replace if ruleId available
python3 <<'PY'
import json,urllib.request,os
v2=json.load(open("/tmp/vi1-simple-v2.json"))
doc=v2.get("policyHeader",{}).get("documentId")
rule=None
for c in (v2.get("underwritingRules") or []):
  if "bureau" in str(c).lower() and "score" in str(c).lower():
    rule=c.get("ruleId") or c.get("id")
    break
open("/tmp/vi1-bureau-rule.txt","w").write(str(rule or ""))
open("/tmp/vi1-v2-doc.txt","w").write(str(doc or ""))
print("edit target", doc, rule)
PY
RULE=$(cat /tmp/vi1-bureau-rule.txt)
V2DOC=$(cat /tmp/vi1-v2-doc.txt)
if [ -n "$RULE" ] && [ "$RULE" != "" ]; then
  curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$V2DOC/rules/add-plain-english" \
    -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
    -d "{\"confirm\":true,\"mode\":\"BUILD\",\"replaceRuleId\":\"$RULE\",\"parameterId\":\"bureau.score\",\"operator\":\">=\",\"value\":675,\"treatment\":\"Reject\"}" \
    -o /tmp/vi1-edit.json
  curl -sS -m 30 "$BASE/policy-studio/documents/$DOC" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-simple-v1b.json
  curl -sS -m 30 "$BASE/policy-studio/documents/$V2DOC" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-simple-v2b.json
  python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-simple-v1b.json"))
v2=json.load(open("/tmp/vi1-simple-v2b.json"))
def find_bureau(sess):
  for c in (sess.get("underwritingRules") or []):
    if "bureau" in str(c).lower() and "score" in str(c).lower():
      return c.get("thresholdValue") or c.get("authoringThreshold") or (c.get("metadata") or {}).get("threshold") or c.get("value")
  return None
b1,b2=find_bureau(v1),find_bureau(v2)
print("IMMUTABILITY v1", b1, "v2", b2)
assert str(b1) in ("650","650.0","650.00") or b1==650, b1
assert str(b2) in ("675","675.0","675.00") or b2==675, b2
print("IMMUTABILITY_OK")
PY
fi

# B — Banking BRE
curl -sS -m 90 -X POST "$BASE/policy-studio/reset-demo" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"kind":"banking"}' -o /tmp/vi1-bank.json
BANK=$(python3 -c 'import json;d=json.load(open("/tmp/vi1-bank.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "BANK_DOC=$BANK"
curl -sS -m 30 "$BASE/policy-studio/documents/$BANK" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bank-v1.json
stamp_and_version "$BANK" "bank"
BANKV2=$(cat /tmp/vi1-bank-v2.txt)
curl -sS -m 30 "$BASE/policy-studio/documents/$BANKV2" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bank-v2.json
python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-bank-v1.json"))
v2=json.load(open("/tmp/vi1-bank-v2.json"))
u1=len(v1.get("underwritingRules") or [])
u2=len(v2.get("underwritingRules") or [])
print("BANK_COMPARE UW v1", u1, "v2", u2)
assert u2 == u1 and u2 > 0, (u1, u2)
# Should not invent a flood of clarification-only stubs as UW rules
print("BANKING_WALKTHROUGH_OK")
print("allowCanonical", v2.get("allowCanonicalAuthority"))
PY

# C — Bureau BRE
curl -sS -m 90 -X POST "$BASE/policy-studio/reset-demo" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"kind":"bureau"}' -o /tmp/vi1-bureau.json
BUR=$(python3 -c 'import json;d=json.load(open("/tmp/vi1-bureau.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "BUREAU_DOC=$BUR"
curl -sS -m 30 "$BASE/policy-studio/documents/$BUR" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bur-v1.json
stamp_and_version "$BUR" "bureau"
BURV2=$(cat /tmp/vi1-bureau-v2.txt)
curl -sS -m 30 "$BASE/policy-studio/documents/$BURV2" -H "X-Internal-Token: $TOKEN" -o /tmp/vi1-bur-v2.json
python3 <<'PY'
import json
v1=json.load(open("/tmp/vi1-bur-v1.json"))
v2=json.load(open("/tmp/vi1-bur-v2.json"))
u1=len(v1.get("underwritingRules") or [])
u2=len(v2.get("underwritingRules") or [])
print("BUREAU_COMPARE UW v1", u1, "v2", u2)
assert u2 == u1 and u2 > 0, (u1, u2)
comp=sum(1 for c in (v2.get("underwritingRules") or []) if c.get("compoundParent") or "compound" in str(c).lower() or "overdue" in str(c).lower())
print("BUREAU compound/overdue cards", comp)
print("BUREAU_WALKTHROUGH_OK")
PY

echo "DONE version-integrity-1 $TS"
