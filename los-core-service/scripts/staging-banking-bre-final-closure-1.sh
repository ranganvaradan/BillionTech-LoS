#!/bin/bash
# BANKING-BRE-FINAL-CLOSURE-1 — staging golden on disposable Banking BRE copy
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
export TOKEN
python3 <<'PY'
import json, urllib.request, os, sys

BASE = "http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo"
H = {
    "X-User-Role": "ADMINISTRATOR",
    "X-User-Id": "c1000000-0000-0000-0000-000000000001",
    "X-User-Name": "admin",
    "Content-Type": "application/json",
    "X-Internal-Token": os.environ.get("TOKEN", ""),
}

def req(method, path, body=None, timeout=180):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=H)
    with urllib.request.urlopen(r, timeout=timeout) as resp:
        return json.load(resp)

print("=== 1) Reset Banking BRE demo (disposable) ===")
reset = req("POST", "/policy-studio/banking/reset", {})
doc = str((reset.get("policyHeader") or {}).get("documentId") or reset.get("documentId"))
print("DOC", doc)
print("allowCanonicalAuthority", reset.get("allowCanonicalAuthority"))
assert doc and doc != "None"

def blockers(sess):
    ready = sess.get("executionReadiness") or {}
    return ready.get("executionBlockers") or sess.get("executionBlockers") or []

print("=== 2) Resolve 100-boundary + EDI mapping ===")
sess = req("GET", f"/policy-studio/documents/{doc}")
cards = sess.get("ambiguityCards") or sess.get("ambiguities") or []
for a in cards:
    term = str(a.get("unclearTerm") or a.get("phrase") or a.get("term") or "").lower()
    aid = a.get("id") or a.get("ambiguityId")
    if not aid or str(a.get("resolutionStatus")) != "OPEN":
        continue
    if "exactly 100" in term or "more than 100" in term or "less than 100" in term:
        out = req("POST", f"/policy-studio/documents/{doc}/ambiguities/{aid}/resolve", {
            "uiAction": "ACCEPT_RECOMMENDATION",
            "resolvedOption": "treat_100_as_ratio_branch",
            "resolvedBy": "credit_manager",
            "unclearTerm": a.get("unclearTerm"),
            "notes": "BANKING-BRE-FINAL-CLOSURE-1: >=100 ratio branch; <100 count branch",
            "rememberDefinition": False,
        })
        lr = out.get("lastResolution") or {}
        print("BOUND_RESOLVE", term[:40], lr.get("resolvedOption") or lr.get("status"),
              "patched", (lr.get("extras") or lr).get("inwardReturnBoundaryPatched"))
    elif term.strip() == "edi" or term == "proposed edi":
        out = req("POST", f"/policy-studio/documents/{doc}/ambiguities/{aid}/resolve", {
            "uiAction": "ACCEPT_RECOMMENDATION",
            "resolvedOption": "application.proposed_edi",
            "resolvedBy": "credit_manager",
            "unclearTerm": a.get("unclearTerm"),
            "notes": "Map EDI → application.proposed_edi",
            "rememberDefinition": False,
        })
        print("EDI_AMB_RESOLVE", (out.get("lastResolution") or {}).get("resolvedOption"))

# Also stamp operand resolution on a capacity rule (covers sibling rules)
sess = req("GET", f"/policy-studio/documents/{doc}")
cap = None
for r in (sess.get("underwritingRules") or []):
    if r.get("systemRuleId") == "BANK_DIGILEAP_ADB_DIV5_GTE_EDI":
        cap = r
        break
if cap and cap.get("id"):
    req("POST", f"/policy-studio/documents/{doc}/rules/{cap['id']}/review", {
        "uiAction": "RESOLVE_PARAMETER_MAP",
        "operandKey": "proposed_edi",
        "parameterId": "application.proposed_edi",
        "originalTerm": "EDI",
        "reason": "Map Proposed EDI to application.proposed_edi",
        "reviewer": "credit_manager",
    })
    print("EDI_PARAM_MAPPED on", cap.get("systemRuleId"))

print("=== 3) Configure ADB bulk >10× adjustment (executable) ===")
sess = req("GET", f"/policy-studio/documents/{doc}")
bulk_rule = None
for c in (sess.get("dataAndCalculations") or []):
    blob = json.dumps(c).lower()
    if c.get("metricAdjustment") and ("bulk" in blob or "10 times" in blob or "10×" in blob or "10x" in blob):
        bulk_rule = c
        break
print("BULK_RULE", None if not bulk_rule else bulk_rule.get("id"))
if bulk_rule and bulk_rule.get("id"):
    prev = req("POST", f"/policy-studio/documents/{doc}/data-calculations/preview", {
        "dataItemId": "banking.adb_bulk_deposit_adjustment",
        "multiple": "10",
        "periodMonths": 3,
        "strictGreaterThan": True,
    })
    p = prev.get("preview") or {}
    print("PREVIEW avg", p.get("averageDepositAmount"), "threshold", p.get("bulkThreshold"),
          "excluded", len(p.get("excludedCredits") or []), "base", p.get("baseAdb"), "adj", p.get("adjustedAdb"),
          "outcome", p.get("outcome"))
    assert float(p.get("averageDepositAmount") or 0) == 9800.0
    assert float(p.get("bulkThreshold") or 0) == 98000.0
    assert len(p.get("excludedCredits") or []) == 1
    assert float((p.get("excludedCredits") or [{}])[0].get("amount") or 0) == 120000.0
    assert float(p.get("adjustedAdb") or 0) < float(p.get("baseAdb") or 0)
    # exactly 10x not excluded
    excl_amts = [float(x.get("amount") or 0) for x in (p.get("excludedCredits") or [])]
    assert 98000.0 not in excl_amts
    req("POST", f"/policy-studio/documents/{doc}/rules/{bulk_rule['id']}/review", {
        "uiAction": "RESOLVE_DATA_ADJUSTMENT",
        "dataItemId": "banking.adb_bulk_deposit_adjustment",
        "parameterId": "banking.adb_bulk_deposit_adjustment",
        "multiple": "10",
        "periodMonths": 3,
        "strictGreaterThan": True,
        "excludeLoanDisbursements": True,
        "excludeOnlineGaming": True,
        "excludeDuplicates": True,
        "confirmExecutable": True,
        "saveMode": "SAVE_EXECUTABLE",
        "reason": "BANKING-BRE-FINAL-CLOSURE-1 executable bulk ADB",
        "actor": "credit_manager",
    })
    print("ADJ_SAVE ok")

print("=== 4) Readiness ===")
sess = req("GET", f"/policy-studio/documents/{doc}")
bl = blockers(sess)
print("executionBlockers", len(bl))
for b in bl:
    print("  BLOCKER", b.get("blockerType"), b.get("reason"))
has100 = any("100" in str(b.get("reason","")).lower() and "not defined" in str(b.get("reason","")).lower() for b in bl)
has_bulk = any("bulk" in str(b.get("reason","")).lower() for b in bl)
print("HAS_100_BOUNDARY_BLOCKER", has100)
print("HAS_BULK_BLOCKER", has_bulk)

print("=== 5) Rules inventory ===")
ready_n = 0
for r in (sess.get("underwritingRules") or []):
    er = bool(r.get("executionReady"))
    if er: ready_n += 1
    print("RULE", r.get("systemRuleId"), "execReady=", er)
print("UW_READY", ready_n, "/", len(sess.get("underwritingRules") or []))

print("=== 6) Policy Quick Test ===")
test = req("POST", f"/policy-studio/documents/{doc}/test/quick", {
    "product": "DIGILEAP",
    "reviewer": "credit_manager",
    "testValues": {"proposed_edi": 15000, "application.proposed_edi": 15000},
}, timeout=180)
print("TEST summary", test.get("summary") or test.get("overallOutcome") or test.get("result"))
print("TEST allowCanonicalAuthority", test.get("allowCanonicalAuthority"))
for rr in (test.get("ruleResults") or test.get("results") or [])[:15]:
    print("  RR", rr.get("systemRuleId") or rr.get("ruleName"), rr.get("outcome") or rr.get("result"))
for p in (test.get("valueProvenance") or []):
    if "AdbBulk" in str(p.get("sourceLabel") or "") or "avg_daily" in str(p.get("parameterKey") or ""):
        print("ADB_PROV", p.get("sourceLabel"), "base", p.get("baseAdb"), "adj", p.get("adjustedAdb") or p.get("value"))

print("=== 7) Adj Ready + inward summary ===")
adj = (sess.get("policyDataResolutions") or {}).get("banking.adb_bulk_deposit_adjustment") or {}
print("ADJ_STATUS", adj.get("cmStatus"), adj.get("executionStatus"), (adj.get("definition") or {}).get("binding"))
inward = next((r for r in (sess.get("underwritingRules") or []) if "INWARD" in str(r.get("systemRuleId") or "")), None)
if inward:
    print("INWARD summary", (inward.get("businessRule") or inward.get("businessSummary") or "")[:160])
    em = inward.get("editableModel") or {}
    print("INWARD branches", em.get("branches"))

print("=== 8) Lifecycle (shadow) — submit / CM / checker / approve ===")
life = sess.get("lifecycle") or {}
actions = life.get("actions") or {}
print("life businessStatus", life.get("businessStatus"), "readyToSchedule", life.get("readyToSchedule"))
print("submitBlockers", len(life.get("submitBlockers") or []))
print("approveBlockers", len(life.get("approveBlockers") or []))
# try existing lifecycle endpoints if present
for path, body in [
    (f"/policy-studio/documents/{doc}/lifecycle/submit-review", {"actor": "credit_manager", "comments": "Submit for review shadow"}),
    (f"/policy-studio/documents/{doc}/lifecycle/approve", {"actor": "credit_manager", "role": "CREDIT_MANAGER", "comments": "CM approve shadow"}),
    (f"/policy-studio/documents/{doc}/lifecycle/approve", {"actor": "policy_checker", "role": "POLICY_CHECKER", "comments": "Checker approve shadow"}),
    (f"/policy-studio/documents/{doc}/lifecycle/schedule", {"actor": "credit_manager", "comments": "Schedule shadow only"}),
]:
    try:
        out = req("POST", path, body)
        print("LIFE", path.split("/")[-1], out.get("businessStatus") or out.get("businessLifecycleStatus") or out.get("status") or "ok",
              "allowCanonicalAuthority", out.get("allowCanonicalAuthority"),
              "productionActive", out.get("productionActive"))
    except Exception as e:
        print("LIFE_SKIP", path.split("/")[-1], type(e).__name__, str(e)[:160])

sess = req("GET", f"/policy-studio/documents/{doc}")
bl = blockers(sess)
print("FINAL_EXEC_BLOCKERS", len(bl))
for b in bl:
    print("  BLOCKER", b.get("blockerType"), b.get("reason"))
print("FINAL_UW_READY", sum(1 for r in (sess.get("underwritingRules") or []) if r.get("executionReady")),
      "/", len(sess.get("underwritingRules") or []))
print("allowCanonicalAuthority", sess.get("allowCanonicalAuthority"))
print("productionActive", sess.get("productionActive"))
print("DOC", doc)
ok = (len(bl) == 0
      and sum(1 for r in (sess.get("underwritingRules") or []) if r.get("executionReady")) == 8
      and str(adj.get("cmStatus")) == "READY"
      and sess.get("allowCanonicalAuthority") is False)
print("CLOSURE_GOLDEN_OK", ok)
sys.exit(0 if ok else 2)
PY
