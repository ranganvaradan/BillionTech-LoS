#!/bin/bash
# POLICY-SIMPLE-FLOW-INTEGRITY-1 — deploy + staging API verification
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

if [ -f /tmp/ui-dist-simple-flow-integrity-1.tar.gz ]; then
  cp -a ui-dist "ui-dist.prev.simple-flow-integrity-1.$TS"
  rm -rf ui-dist.new && mkdir -p ui-dist.new
  tar -xzf /tmp/ui-dist-simple-flow-integrity-1.tar.gz -C ui-dist.new
  rm -rf ui-dist && mv ui-dist.new ui-dist
  echo "simple-flow-integrity-1 $TS" > UI_DEPLOYED_AT.txt
  date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt
fi

tar -xzf /tmp/simple-flow-integrity-1-src.tar.gz -C los-core-service
docker compose build los-core
docker compose up -d los-core
echo "simple-flow-integrity-1 $TS" > DEPLOYED_AT.txt
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

curl -sS -m 60 -X POST "$BASE/policy-studio/create" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"policyName":"Simple Flow Integrity Four-Rule"}' -o /tmp/sfi1-create.json
DOC=$(python3 -c 'import json;d=json.load(open("/tmp/sfi1-create.json"));print((d.get("policyHeader") or {}).get("documentId") or "")')
echo "DOC=$DOC"
test -n "$DOC"

add_rule() {
  curl -sS -m 60 -X POST "$BASE/policy-studio/documents/$DOC/rules/add-plain-english" \
    -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
    -d "$1" -o /tmp/sfi1-add.json
  python3 -c 'import json;d=json.load(open("/tmp/sfi1-add.json")); assert d.get("confirmed") is True, d; print("ADD", d.get("ruleId"), d.get("allowCanonicalAuthority"))'
}

add_rule '{"confirm":true,"mode":"BUILD","parameterId":"banking.monthly_credits_3m","operator":">=","value":50000,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"bureau.score","operator":">=","value":650,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"banking.cheque_return_count_3m","operator":"<=","value":0,"treatment":"Reject"}'
add_rule '{"confirm":true,"mode":"BUILD","parameterId":"banking.settlement.count_monthly_avg_3m","operator":">=","value":20,"treatment":"Reject"}'

curl -sS -m 30 -X POST "$BASE/policy-studio/documents/$DOC/lifecycle/save-draft" \
  -H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"products":["DIGILEAP"],"borrowerType":"COMPANY","reasonForChange":"SFI1"}' -o /tmp/sfi1-save.json || true

curl -sS -m 30 "$BASE/policy-studio/documents/$DOC" -H "X-Internal-Token: $TOKEN" -o /tmp/sfi1-session.json
curl -sS -m 30 "$BASE/policy-studio/documents/$DOC/lifecycle/settings" -H "X-Internal-Token: $TOKEN" -o /tmp/sfi1-life.json
curl -sS -m 30 "$BASE/policy-studio/documents/$DOC/approvals" -H "X-Internal-Token: $TOKEN" -o /tmp/sfi1-appr.json

python3 <<'PY'
import json
s=json.load(open("/tmp/sfi1-session.json"))
life=json.load(open("/tmp/sfi1-life.json"))
appr=json.load(open("/tmp/sfi1-appr.json"))
uw=s.get("underwritingRules") or []
print("UW_COUNT", len(uw))
assert len(uw) >= 4, uw
contam=[]
for c in uw:
    ops=c.get("operands") or []
    title=str(c.get("ruleName") or c.get("businessRule") or c.get("parameterName") or "")
    sys=str(c.get("systemRuleId") or "")
    for op in ops:
        key=str(op.get("operandKey") or "")
        pid=str(op.get("parameterId") or op.get("suggestedParameterId") or "")
        name=str(op.get("businessName") or op.get("label") or "")
        if "proposed_edi" in key or "proposed_edi" in pid or "proposed edi" in name.lower():
            contam.append((title, sys, key, pid, name))
    if "monthly" in title.lower() or "MONTHLY_CREDITS" in sys.upper():
        print("MONTHLY_CARD", title, sys, "ops=", [(o.get("operandKey"), o.get("parameterId"), o.get("businessName")) for o in ops])
assert not contam, ("EDI_CONTAMINATION", contam)
print("NO_EDI_CONTAMINATION_OK")

exec_blockers=life.get("executionBlockers") or []
crit=life.get("criticalDataGap")
pct=life.get("dataReadinessPercent")
print("LIFE blockers", len(exec_blockers), "criticalDataGap", crit, "dataReadiness%", pct, "status", life.get("businessStatus"))
assert len(exec_blockers)==0, exec_blockers
assert crit in (False, None, 0) or crit is False
checklist=appr.get("readinessChecklist") or []
for row in checklist:
    disp=str(row.get("display") or row.get("label") or "")
    if "critical rule data gap" in disp.lower():
        raise SystemExit("CRITICAL_GAP_ON_APPROVALS: "+disp)
    print("CHECK", row.get("tone"), disp[:120])
print("blockingItems", len(appr.get("blockingItems") or []))
assert len(appr.get("blockingItems") or [])==0
assert appr.get("allowCanonicalAuthority") is False or s.get("allowCanonicalAuthority") is False
assert appr.get("primaryLifecycleAuthority")=="VERSIONS_BUSINESS_LIFECYCLE" or appr.get("draftPackageRole")=="ADVANCED_DEVELOPMENT_ONLY"
print("APPROVALS_ALIGNED_OK")
print("allowCanonicalAuthority", s.get("allowCanonicalAuthority"), appr.get("allowCanonicalAuthority"))
print("SFI1_STAGING_OK DOC", s.get("policyHeader",{}).get("documentId") or open("/tmp/sfi1-create.json").read()[:80])
PY

echo "POLICY-SIMPLE-FLOW-INTEGRITY-1 deploy+verify done DOC=$DOC"
