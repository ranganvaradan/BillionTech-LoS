#!/bin/bash
# POLICY-READINESS-SSOT-1 — full prove on staging Banking BRE demo (browser path)
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
export TOKEN
python3 <<'PY'
import json, urllib.request, os, sys
BASE="http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo"
H={"X-Internal-Token":os.environ["TOKEN"],"Content-Type":"application/json",
   "X-User-Role":"ADMINISTRATOR","X-User-Id":"c1000000-0000-0000-0000-000000000001","X-User-Name":"admin"}

def req(method, path, body=None, timeout=180):
    data=None if body is None else json.dumps(body).encode()
    r=urllib.request.Request(BASE+path, data=data, method=method, headers=H)
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return json.load(resp)

print("=== 1) Open Banking BRE (demo path browsers use) ===")
d=req("POST", "/policy-studio/banking/reset", {})
DOC=str((d.get("policyHeader") or {}).get("documentId") or d.get("documentId"))
print("DOC", DOC, "name", (d.get("policyHeader") or {}).get("policyName"), "status", d.get("businessLifecycleStatus"))
print("allowCanonicalAuthority", d.get("allowCanonicalAuthority"))

def snap(label):
    view=req("GET", f"/policy-studio/documents/{DOC}")
    life=view.get("lifecycle") or {}
    er=view.get("executionReadiness") or {}
    items=life.get("readinessItems") or []
    primary=life.get("primaryAction") or {}
    print(f"--- {label} ---")
    print("execReadyRules", er.get("executionReadyRules"), "/", er.get("includedExecutableRules"),
          "execBlockers", er.get("executionBlockerCount"))
    print("readinessItems", [(i.get("ok"), i.get("label") or i.get("whenMissing")) for i in items])
    print("readyForNextStep", life.get("readyForNextStep"), "executionReadinessOk", life.get("executionReadinessOk"))
    print("submitBlockers", life.get("submitBlockers"))
    print("primary", primary.get("code"), "enabled=", primary.get("enabled"), "reason=", primary.get("disabledReason"))
    checklist=all(i.get("ok") for i in items) if items else False
    submit_blocked=bool(life.get("submitBlockers"))
    print("CONTRADICTION", checklist and submit_blocked)
    return view, life, er, items, primary, checklist, submit_blocked

view, life, er, items, primary, checklist, submit_blocked = snap("BEFORE boundary resolve (architecture proof)")
# After SSOT fix: checklist must NOT be all green when submit blocked
print("REPRODUCED_ON_ACTUAL_STAGING_POLICY", "YES" if submit_blocked else "NO")
print("CHECKLIST_HONEST_WITH_SUBMIT", "YES" if (not checklist and submit_blocked) or (checklist and not submit_blocked) else "NO")

# Resolve boundary + EDI etc needed for execution ready
for a in (view.get("ambiguityCards") or []):
    if str(a.get("resolutionStatus")) != "OPEN":
        continue
    term=str(a.get("unclearTerm") or "").lower()
    aid=a.get("id")
    if "100" in term:
        opt="treat_100_as_ratio_branch"
    elif term.strip()=="edi":
        opt="application.proposed_edi"
    elif "average monthly" in term:
        opt="banking.transaction_count.average_monthly_3m"
    elif "gaming" in term:
        opt="banking.narration_taxonomy.ONLINE_GAMING"
    elif "deposition" in term or "deposit" in term:
        opt="banking.avg_daily_balance_3m"
    else:
        continue
    try:
        out=req("POST", f"/policy-studio/documents/{DOC}/ambiguities/{aid}/resolve", {
            "uiAction":"ACCEPT_RECOMMENDATION","resolvedOption":opt,"resolvedBy":"credit_manager",
            "unclearTerm":a.get("unclearTerm"),"notes":"SSOT-1","rememberDefinition":False})
        print("RESOLVED", term[:40], opt, "superseded", ((out.get("lastResolution") or {}).get("extras") or out.get("lastResolution") or {}).get("relatedAmbiguitiesSuperseded"))
    except Exception as e:
        print("RESOLVE_ERR", term, e)

# Map EDI + bulk adj if needed
view=req("GET", f"/policy-studio/documents/{DOC}")
for r in (view.get("underwritingRules") or []):
    if r.get("systemRuleId")=="BANK_DIGILEAP_ADB_DIV5_GTE_EDI" and r.get("id"):
        req("POST", f"/policy-studio/documents/{DOC}/rules/{r['id']}/review", {
            "uiAction":"RESOLVE_PARAMETER_MAP","operandKey":"proposed_edi",
            "parameterId":"application.proposed_edi","originalTerm":"EDI","reviewer":"credit_manager"})
        print("EDI mapped")
        break
for c in (view.get("dataAndCalculations") or []):
    blob=json.dumps(c).lower()
    if c.get("metricAdjustment") and ("bulk" in blob or "10 times" in blob or "10×" in blob) and c.get("id"):
        req("POST", f"/policy-studio/documents/{DOC}/rules/{c['id']}/review", {
            "uiAction":"RESOLVE_DATA_ADJUSTMENT","dataItemId":"banking.adb_bulk_deposit_adjustment",
            "multiple":"10","periodMonths":3,"strictGreaterThan":True,"confirmExecutable":True,
            "saveMode":"SAVE_EXECUTABLE","actor":"credit_manager"})
        print("BULK saved")
        break

# Ensure policy test stamped
try:
    req("POST", f"/policy-studio/documents/{DOC}/test/quick", {
        "product":"DIGILEAP","reviewer":"credit_manager",
        "testValues":{"proposed_edi":15000,"application.proposed_edi":15000}})
    print("TEST ran")
except Exception as e:
    print("TEST_ERR", e)

view, life, er, items, primary, checklist, submit_blocked = snap("AFTER repair")
# Inward proof
for r in (view.get("underwritingRules") or []):
    if "INWARD" in str(r.get("systemRuleId") or ""):
        print("INWARD ready", r.get("executionReady"), "incomplete", r.get("boundaryIncomplete"))
        print("INWARD", str(r.get("businessRule") or "")[:160])
        cond=((r.get("technicalExpression") or {}).get("condition") or {})
        print("INWARD op", cond.get("op"), cond.get("right"))
        br=(r.get("editableModel") or {}).get("branches") or []
        print("BRANCH1", br[0].get("whenOperator") if br else None, br[0].get("whenValue") if br else None)
        print("BRANCH2", br[1].get("whenOperator") if len(br)>1 else None, br[1].get("whenValue") if len(br)>1 else None)

print("EXECUTION_BLOCKERS", er.get("executionBlockerCount"))
print("READY_FOR_NEXT_STEP", life.get("readyForNextStep"))
print("SUBMIT_ENABLED", (life.get("primaryAction") or {}).get("enabled"))
print("READY_SUBMIT_CONTRADICTION", checklist and submit_blocked)

if (life.get("primaryAction") or {}).get("enabled") and (life.get("primaryAction") or {}).get("code")=="SUBMIT_FOR_REVIEW":
    out=req("POST", f"/policy-studio/documents/{DOC}/lifecycle/submit-review", {"actor":"credit_manager"})
    print("SUBMIT_OK", out.get("businessLifecycleStatus") or (out.get("lifecycle") or {}).get("businessStatus"),
          "allowCanonicalAuthority", out.get("allowCanonicalAuthority"))
else:
    print("SUBMIT_NOT_ENABLED", (life.get("primaryAction") or {}).get("disabledReason"))

# Reload persistence proof — re-GET
view2=req("GET", f"/policy-studio/documents/{DOC}")
life2=view2.get("lifecycle") or {}
print("RELOAD execBlockers", len(view2.get("executionBlockers") or []),
      "readyForNext", life2.get("readyForNextStep"),
      "submitBlockers", life2.get("submitBlockers"))
print("DOC", DOC)
print("allowCanonicalAuthority", view2.get("allowCanonicalAuthority"))
PY
