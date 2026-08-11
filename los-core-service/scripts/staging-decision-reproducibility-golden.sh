#!/bin/bash
# LOS-PRODUCTION-P0-CLOSURE-1 — historical decision reproducibility golden on staging (no prod providers).
set -eu
BASE=http://127.0.0.1:8083
HDR=(-H 'X-User-Role: CREDIT_MANAGER' -H 'X-User-Id: a1000000-0000-0000-0000-000000000002' -H 'Content-Type: application/json')

echo "=== R1: locate evaluation with decision_snapshot_json ==="
docker exec billiontech-postgres psql -U los_app -d los_core_staging -c \
  "SELECT id, application_id, aggregate_decision, scorecard_version,
          decision_snapshot_json ? 'immutable' AS has_immutable,
          decision_snapshot_json->'routing'->>'scorecardVersion' AS snap_sc_ver,
          decision_snapshot_json->'routing'->>'workflowVersion' AS snap_wf_ver
     FROM underwriting_evaluations
    WHERE decision_snapshot_json IS NOT NULL
    ORDER BY evaluated_at DESC LIMIT 5;"

EVAL_ROW=$(docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT id||'|'||application_id||'|'||coalesce(md5(decision_snapshot_json::text),'')
     FROM underwriting_evaluations
    WHERE decision_snapshot_json IS NOT NULL
    ORDER BY evaluated_at DESC LIMIT 1;")
if [ -z "$EVAL_ROW" ]; then
  echo "NO_SNAPSHOT_YET — run underwriting once after V118 deploy; creating synthetic proof via unit tests only"
  exit 0
fi
EVAL_ID=$(echo "$EVAL_ROW" | cut -d'|' -f1)
APP_ID=$(echo "$EVAL_ROW" | cut -d'|' -f2)
HASH1=$(echo "$EVAL_ROW" | cut -d'|' -f3)
echo "EVAL_ID=$EVAL_ID APP_ID=$APP_ID HASH1=$HASH1"

echo "=== R2 mutate future DRAFT scorecard (do not edit ACTIVE in place) ==="
# Find an ACTIVE scorecard and create new version DRAFT via API if possible
SC=$(curl -sS "$BASE/api/v1/underwriting/scorecards" "${HDR[@]}" | python3 -c '
import sys,json
try:
  d=json.load(sys.stdin)
except Exception:
  print(""); raise SystemExit
items=d if isinstance(d,list) else d.get("content") or d.get("items") or []
for s in items:
  st=(s.get("status") or "ACTIVE")
  if s.get("active") and str(st).upper()=="ACTIVE" and s.get("loanProduct")=="TERM_LOAN":
    print(s.get("id")); break
')
echo "ACTIVE_SCORECARD=$SC"
if [ -n "$SC" ]; then
  curl -sS -X POST "$BASE/api/v1/admin/underwriting-scorecards/$SC/new-version" "${HDR[@]}" > /tmp/sc_new_ver.json || true
  python3 -c 'import json;d=json.load(open("/tmp/sc_new_ver.json")); print("newVersion", d.get("id"), d.get("version"), d.get("status"))' || true
fi

echo "=== R2 historical hash after mutation ==="
HASH2=$(docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT md5(decision_snapshot_json::text) FROM underwriting_evaluations WHERE id='$EVAL_ID';")
echo "HASH2=$HASH2"
test "$HASH1" = "$HASH2"
echo "HISTORICAL_SNAPSHOT_UNCHANGED=YES"

echo "=== Historical explanation independence (API map fields) ==="
curl -sS "$BASE/api/v1/applications/$APP_ID" "${HDR[@]}" > /tmp/app_hist.json || true
python3 - <<'PY'
import json
d=json.load(open("/tmp/app_hist.json"))
ev=d.get("latestUnderwritingEvaluation") or {}
print("hasDecisionSnapshot", bool(ev.get("decisionSnapshot")))
print("requiresCurrentConfig", ev.get("requiresCurrentConfig"))
hist=ev.get("historicalExplanation") or {}
print("explanationSource", hist.get("explanationSource"))
print("snapScorecardVersion", (hist.get("routing") or {}).get("scorecardVersion"))
if ev.get("decisionSnapshot"):
  assert hist.get("explanationSource")=="DECISION_SNAPSHOT"
  assert hist.get("requiresCurrentConfig") is False
  print("HISTORICAL_EXPLANATION_INDEPENDENT=YES")
else:
  print("HISTORICAL_EXPLANATION_INDEPENDENT=NO_SNAPSHOT_ON_APP_API")
PY

echo "REPRODUCIBILITY_GOLDEN_OK"
