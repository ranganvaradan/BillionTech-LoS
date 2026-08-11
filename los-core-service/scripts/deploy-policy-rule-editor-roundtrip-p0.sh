#!/bin/bash
set -eu
APP=/opt/billiontech/apps/billiontechlos
TS=$(date -u +%Y%m%dT%H%M%SZ)
cd "$APP"

BACKUP=/opt/billiontech/backups/los_core_staging_pre_policy_rule_editor_roundtrip_p0_${TS}.dump
mkdir -p /opt/billiontech/backups
docker exec billiontech-postgres pg_dump -U los_app -d los_core_staging -Fc -f /tmp/pre_prerp0.dump
docker cp billiontech-postgres:/tmp/pre_prerp0.dump "$BACKUP"
BYTES=$(stat -c%s "$BACKUP")
SHA=$(sha256sum "$BACKUP" | awk '{print $1}')
echo "BACKUP=$BACKUP BYTES=$BYTES SHA256=$SHA"
test "$BYTES" -gt 0

cp -a ui-dist "ui-dist.prev.policy-rule-editor-roundtrip-p0.$TS"
rm -rf ui-dist.new && mkdir -p ui-dist.new
tar -xzf /tmp/ui-dist-policy-rule-editor-roundtrip-p0.tar.gz -C ui-dist.new
rm -rf ui-dist && mv ui-dist.new ui-dist
echo "policy-rule-editor-roundtrip-p0 $TS" > UI_DEPLOYED_AT.txt
date -u +%Y-%m-%dT%H:%M:%SZ >> UI_DEPLOYED_AT.txt

tar -xzf /tmp/policy-rule-editor-roundtrip-p0-src.tar.gz -C los-core-service
touch los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/parameters/InwardReturnCompoundSupport.java
docker compose build --no-cache los-core
docker compose up -d los-core
date -u +%Y-%m-%dT%H:%M:%SZ > DEPLOYED_AT.txt
echo "policy-rule-editor-roundtrip-p0 $TS" >> DEPLOYED_AT.txt

for i in $(seq 1 48); do
  st=$(docker inspect -f '{{.State.Health.Status}}' billiontechlos-core 2>/dev/null || echo starting)
  echo "health=$st try=$i"
  [ "$st" = healthy ] && break
  sleep 5
done

curl -sS -m 20 'http://127.0.0.1:8083/actuator/health' | head -c 300; echo
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'
curl -sS -m 90 -X POST "$BASE/policy-studio/banking/reset" -H "X-Internal-Token: $TOKEN" -o /tmp/bank_prerp0.json
python3 - <<'PY'
import json,os
d=json.load(open("/tmp/bank_prerp0.json"))
print("allowCanonicalAuthority", d.get("allowCanonicalAuthority"))
uw=d.get("underwritingRules") or d.get("ruleCards") or []
# find inward
hits=[]
for c in uw:
  title=str(c.get("ruleName") or c.get("businessTitle") or "")
  br=str(c.get("businessRule") or "")
  if "inward" in title.lower() or "inward" in br.lower() or "ENACH" in title:
    hits.append(c)
print("INWARD_CARDS", len(hits))
if hits:
  c=hits[0]
  print("businessRule", (c.get("businessRule") or "")[:120])
  print("visualKind", (c.get("visualLogic") or {}).get("kind"))
  print("compoundEditable", c.get("compoundEditable"))
  print("reviewBadge", c.get("reviewBadge"))
  print("boundaryIncomplete", c.get("boundaryIncomplete"))
  em=c.get("editableModel") or {}
  print("branches", len(em.get("branches") or []))
# blockers
life=d.get("lifecycleSettings") or d.get("lifecycle") or {}
# also check readiness from document view fields
blockers=d.get("executionBlockers") or life.get("blockerDetails") or []
print("BLOCKERS", len(blockers))
for b in blockers:
  r=str(b.get("reason") or b.get("message") or "")
  if "100" in r or "Bulk" in r or "10" in r or "boundary" in r.lower():
    print("BLOCKER", r[:100], "action=", b.get("action"), "defineBoundary=", b.get("defineBoundary"))
# preview preserve
expr=None
if hits and hits[0].get("technicalExpression"):
  expr=hits[0]["technicalExpression"]
print("HAS_EXPR", bool(expr))
js_dir="/opt/billiontech/apps/billiontechlos/ui-dist/assets"
blob=""
for f in os.listdir(js_dir):
  if f.endswith(".js"):
    blob += open(os.path.join(js_dir,f),encoding="utf-8",errors="ignore").read()
for n in ["compound-rule-editor","Define boundary","boundary-resolver-panel","Accepted · Boundary incomplete","COMPOUND"]:
  print(n, n in blob)
print("BACKUP_RECORD BYTES=%s SHA256=%s" % (os.environ.get("BYTES",""), os.environ.get("SHA","")))
PY
echo "BACKUP_RECORD BYTES=$BYTES SHA256=$SHA FILE=$BACKUP"
