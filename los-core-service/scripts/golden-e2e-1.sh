#!/bin/bash
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
TOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo'
H=(-H "X-Internal-Token: $TOKEN" -H "Content-Type: application/json")
REPORT=/tmp/golden-e2e-1-report.json
echo "$BASE" > /tmp/golden-base.txt
echo "$TOKEN" > /tmp/golden-token.txt
cat > /tmp/golden-e2e-1.py <<'PY'
import json, urllib.request, ssl, sys, traceback
from collections import Counter

BASE = open("/tmp/golden-base.txt").read().strip()
TOKEN = open("/tmp/golden-token.txt").read().strip()

def req(method, path, body=None, timeout=90):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"X-Internal-Token": TOKEN, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            j = json.loads(raw)
        except Exception:
            j = {"raw": raw[:500]}
        return e.code, j

def keys(blockers):
    out = set()
    for b in blockers or []:
        if isinstance(b, dict):
            out.add(str(b.get("blockerKey") or b.get("reason") or b))
        else:
            out.add(str(b))
    return out

report = {
    "allowCanonical": None,
    "simple": {}, "banking": {}, "bureau": {},
    "defects": [], "pass": {}
}

# Landing
code, landing = req("GET", "/policy-studio")
report["landing"] = {
    "title": landing.get("title"),
    "primaryAction": landing.get("primaryAction"),
    "createPaths": landing.get("createPaths"),
    "allowCanonical": landing.get("allowCanonicalAuthority"),
    "existingCount": len(landing.get("existingPolicies") or []),
}
report["allowCanonical"] = landing.get("allowCanonicalAuthority")
if landing.get("allowCanonicalAuthority") is not False:
    report["defects"].append({"sev":"P0","area":"safety","msg":"allowCanonicalAuthority not false on landing"})

# ═══════════════ A. SIMPLE SME ═══════════════
print("=== GOLDEN A SIMPLE ===")
code, created = req("POST", "/policy-studio/create", {"policyName": "SME Term Loan Policy – Golden E2E"})
doc = (created.get("policyHeader") or {}).get("documentId")
assert doc, created
report["simple"]["doc"] = doc
report["simple"]["enterWorkspace"] = created.get("enterWorkspace")

# Scope save
code, sc = req("POST", f"/policy-studio/documents/{doc}/lifecycle/save-draft", {
    "products": ["BUSINESS_TERM_LOAN"],
    "borrowerType": "COMPANY",
    "minLoanAmount": 100000,
    "maxLoanAmount": 1000000,
    "reasonForChange": "Golden E2E scope"
})
report["simple"]["scopeSave"] = {"http": code, "status": (sc.get("lifecycle") or sc).get("businessStatus"), "msg": sc.get("message")}

# Mix Build + Describe rules
rules = [
    {"confirm": True, "mode": "BUILD", "parameterId": "kyc.pan.verified", "operator": "is", "value": "Yes", "treatment": "Reject"},
    {"confirm": True, "mode": "BUILD", "parameterId": "bureau.score", "operator": ">=", "value": 650, "treatment": "Reject"},
    {"confirm": True, "mode": "DESCRIBE", "text": "FOIR should not exceed 50%", "treatment": "Reject"},
    {"confirm": True, "mode": "BUILD", "parameterId": "application.business_vintage_months", "operator": ">=", "value": 24, "durationUnit": "Months", "treatment": "Reject"},
    {"confirm": True, "mode": "BUILD", "parameterId": "application.requested_amount", "operator": "<=", "value": "₹10,00,000", "treatment": "Reject"},
]
for body in rules:
    code, r = req("POST", f"/policy-studio/documents/{doc}/rules/add-plain-english", body)
    ok = r.get("confirmed") is True
    print("ADD", ok, r.get("ruleId"), (r.get("preview") or {}).get("ruleDisplay") or r.get("message"))
    if not ok:
        report["defects"].append({"sev":"P0","area":"authoring","msg": str(r.get("message") or r)[:240]})

# PAN preview regression (Yes string)
code, panp = req("POST", f"/policy-studio/documents/{doc}/rules/preview", {
    "mode": "BUILD", "parameterId": "kyc.pan.verified", "operator": "=", "value": "Yes", "treatment": "Reject"})
prev = panp.get("preview") or panp
report["simple"]["panPreview"] = {
    "complete": prev.get("complete"), "value": prev.get("value"),
    "display": prev.get("ruleDisplay"), "msg": prev.get("message")}
if prev.get("complete") is not True or prev.get("value") is not True:
    report["defects"].append({"sev":"P0","area":"pan","msg":"PAN Yes preview failed"})

code, sess = req("GET", f"/policy-studio/documents/{doc}")
uw = sess.get("underwritingRules") or []
other = sess.get("otherContent") or sess.get("otherPolicyContent") or []
data = sess.get("dataAndCalculations") or []
classif = [c for c in uw if "classification" in str(c).lower() and not c.get("cmAuthored")]
report["simple"]["counts"] = {
    "uw": len(uw), "dataCalc": len(data), "other": len(other) if isinstance(other, list) else other,
    "classifInUw": len(classif), "allowCanonical": sess.get("allowCanonicalAuthority")}
print("SIMPLE counts", report["simple"]["counts"])
if len(uw) != 5:
    report["defects"].append({"sev":"P0","area":"simple","msg": f"expected 5 UW got {len(uw)}"})

# Rules vs Versions blockers
life = sess.get("lifecycle") or {}
code, lifeView = req("GET", f"/policy-studio/documents/{doc}/lifecycle")
rb = keys(sess.get("executionBlockers"))
vb = keys(lifeView.get("executionBlockers"))
report["simple"]["blockerCompare"] = {"rules": sorted(rb), "versions": sorted(vb), "equal": rb == vb}
if rb != vb:
    report["defects"].append({"sev":"P0","area":"readiness","msg": f"simple blocker mismatch {rb ^ vb}"})

# Edit bureau 650 -> 675 then back for test? Keep 650 for pass test; edit after versioning
bureau_rule = None
for c in uw:
    if "bureau" in str(c).lower() and "score" in str(c).lower():
        bureau_rule = c.get("ruleId") or c.get("id")
        break

# Quick test pass
code, t1 = req("POST", f"/policy-studio/documents/{doc}/test/quick", {
    "product": "BUSINESS_TERM_LOAN",
    "testValues": {
        "kyc.pan.verified": True,
        "bureau.score": 720,
        "obligation.ratio": 45,
        "application.business_vintage_months": 36,
        "application.requested_amount": 800000,
        "application.foir": 45,
        "foir": 45,
    }
})
report["simple"]["testPass"] = {
    "http": code,
    "decision": t1.get("simulatedDecision") or t1.get("simulatedDecisionCode"),
    "policyMutated": t1.get("policyMutated"),
    "lifecycleEvidenceStamped": t1.get("lifecycleEvidenceStamped"),
    "ruleResults": len(t1.get("ruleResults") or t1.get("rules") or []),
    "msg": str(t1.get("message") or "")[:160],
}
print("TEST PASS", report["simple"]["testPass"])

# FOIR fail
code, t2 = req("POST", f"/policy-studio/documents/{doc}/test/quick", {
    "product": "BUSINESS_TERM_LOAN",
    "testValues": {
        "kyc.pan.verified": True,
        "bureau.score": 720,
        "obligation.ratio": 60,
        "application.business_vintage_months": 36,
        "application.requested_amount": 800000,
        "foir": 60,
    }
})
# Find FOIR fail in results
fail_foir = False
for rr in (t2.get("ruleResults") or t2.get("rules") or t2.get("ruleOutcomes") or []):
    blob = str(rr).lower()
    if "foir" in blob or "obligation" in blob:
        if any(x in blob for x in ("fail", "reject", "false", "below")):
            fail_foir = True
dec = str(t2.get("simulatedDecisionCode") or t2.get("simulatedDecision") or "").upper()
report["simple"]["testFoirFail"] = {
    "http": code, "decision": dec, "foirFailSeen": fail_foir,
    "policyMutated": t2.get("policyMutated"),
    "snippet": str(t2)[:400]
}
print("TEST FOIR", report["simple"]["testFoirFail"]["decision"], "foirFail", fail_foir)

# Accept CM-authored rules (dispositions)
for c in uw:
    rid = c.get("ruleId") or c.get("id")
    if not rid: continue
    req("POST", f"/policy-studio/documents/{doc}/rules/{rid}/review", {
        "action": "ACCEPT", "reviewer": "credit_manager"})

# Approvals
code, cm = req("POST", f"/policy-studio/documents/{doc}/approvals/credit-manager", {
    "reviewer": "credit_manager", "comments": "Golden E2E CM"})
code, ck = req("POST", f"/policy-studio/documents/{doc}/approvals/checker", {
    "reviewer": "policy_checker", "comments": "Golden E2E Checker"})
report["simple"]["approvals"] = {
    "cmHttp": code if False else None,  # placeholder fixed below
}
# re-fetch properly
code_cm, cm = req("POST", f"/policy-studio/documents/{doc}/approvals/credit-manager", {
    "reviewer": "credit_manager", "comments": "Golden E2E CM"})
code_ck, ck = req("POST", f"/policy-studio/documents/{doc}/approvals/checker", {
    "reviewer": "policy_checker", "comments": "Golden E2E Checker"})
report["simple"]["approvals"] = {
    "cm": {"http": code_cm, "msg": str(cm.get("message") or cm.get("error") or "")[:200],
           "cmApproved": cm.get("creditManagerApproved") or (cm.get("approvals") or {}).get("creditManagerApproved")},
    "checker": {"http": code_ck, "msg": str(ck.get("message") or ck.get("error") or "")[:200],
                "checkerApproved": ck.get("checkerApproved") or (ck.get("approvals") or {}).get("checkerApproved")},
}
print("APPROVALS", report["simple"]["approvals"])

code, sub = req("POST", f"/policy-studio/documents/{doc}/lifecycle/submit-review", {})
code, appr = req("POST", f"/policy-studio/documents/{doc}/lifecycle/approve", {"reviewer": "credit_manager", "checker": "policy_checker"})
report["simple"]["lifecycle"] = {
    "submit": {"http": code, "status": (sub.get("lifecycle") or sub).get("businessStatus"), "msg": str(sub.get("message") or "")[:200]},
    "approve": {"http": code, "status": (appr.get("lifecycle") or appr).get("businessStatus") or appr.get("businessStatus"),
                "msg": str(appr.get("message") or appr.get("error") or "")[:300],
                "blockers": appr.get("approveBlockers") or (appr.get("lifecycle") or {}).get("approveBlockers")},
}
# if approve failed, capture lifecycle view
code, life2 = req("GET", f"/policy-studio/documents/{doc}/lifecycle")
report["simple"]["lifecycleView"] = {
    "businessStatus": life2.get("businessStatus"),
    "primaryAction": life2.get("primaryAction"),
    "executionBlockers": life2.get("executionBlockers"),
    "governanceBlockers": life2.get("governanceBlockers"),
    "approveBlockers": life2.get("approveBlockers"),
    "submitBlockers": life2.get("submitBlockers"),
}
print("LIFECYCLE", report["simple"]["lifecycle"], "VIEW", report["simple"]["lifecycleView"]["businessStatus"], report["simple"]["lifecycleView"].get("primaryAction"))

# Schedule if approved
st = str(report["simple"]["lifecycleView"].get("businessStatus") or "")
if st in ("APPROVED", "SCHEDULED"):
    code, sch = req("POST", f"/policy-studio/documents/{doc}/lifecycle/schedule", {
        "products": ["BUSINESS_TERM_LOAN"],
        "effectiveFrom": "2026-08-01",
        "businessDate": "2026-08-11",
    })
    report["simple"]["schedule"] = {"http": code, "status": (sch.get("lifecycle") or sch).get("businessStatus") or sch.get("businessStatus"),
                                    "msg": str(sch.get("message") or "")[:200]}
else:
    # stamp active for versioning path if approve blocked by honest reasons - still attempt new version after stamp
    report["simple"]["schedule"] = {"skipped": True, "reason": f"status={st}"}
    req("POST", f"/policy-studio/documents/{doc}/lifecycle/stamp-active-for-versioning", {
        "products": ["BUSINESS_TERM_LOAN"], "borrowerType": "COMPANY"})

code, nv = req("POST", f"/policy-studio/documents/{doc}/lifecycle/new-version", {"reasonForChange": "Golden E2E version"})
v2 = (nv.get("policyHeader") or {}).get("documentId") or nv.get("documentId")
report["simple"]["version"] = {
    "structuredClone": nv.get("structuredClone"), "reingested": nv.get("reingested"),
    "uwCount": nv.get("underwritingRuleCount"), "v2": v2,
    "msg": str(nv.get("message") or "")[:200]}
print("VERSION", report["simple"]["version"])
if nv.get("structuredClone") is True and v2:
    code, v2s = req("GET", f"/policy-studio/documents/{v2}")
    u2 = v2s.get("underwritingRules") or []
    # edit bureau on v2
    br = None
    for c in u2:
        if "bureau" in str(c).lower() and "score" in str(c).lower():
            br = c.get("ruleId") or c.get("id")
            break
    if br:
        req("POST", f"/policy-studio/documents/{v2}/rules/add-plain-english", {
            "confirm": True, "mode": "BUILD", "replaceRuleId": br,
            "parameterId": "bureau.score", "operator": ">=", "value": 675, "treatment": "Reject"})
    code, v1r = req("GET", f"/policy-studio/documents/{doc}")
    code, v2r = req("GET", f"/policy-studio/documents/{v2}")
    def bureau_val(s):
        for c in (s.get("underwritingRules") or []):
            if "bureau" in str(c).lower() and "score" in str(c).lower():
                return c.get("thresholdValue") or c.get("authoringThreshold") or (c.get("metadata") or {}).get("threshold") or c.get("value")
        return None
    report["simple"]["immutability"] = {"v1": bureau_val(v1r), "v2": bureau_val(v2r), "v2uw": len(v2r.get("underwritingRules") or [])}
    print("IMMUTABILITY", report["simple"]["immutability"])

# Reload invariant
code, reload = req("GET", f"/policy-studio/documents/{doc}")
report["simple"]["reload"] = {"uw": len(reload.get("underwritingRules") or []), "allowCanonical": reload.get("allowCanonicalAuthority")}

# ═══════════════ B. BANKING ═══════════════
print("=== GOLDEN B BANKING ===")
code, bank = req("POST", "/policy-studio/banking/reset")
bdoc = (bank.get("policyHeader") or {}).get("documentId")
assert bdoc, bank
code, bs = req("GET", f"/policy-studio/documents/{bdoc}")
code, blife = req("GET", f"/policy-studio/documents/{bdoc}/lifecycle")
uwb = bs.get("underwritingRules") or []
datab = bs.get("dataAndCalculations") or []
# parameter groups approximated from data items
param_names = []
adj = 0
obj_obj = 0
for d in datab:
    blob = str(d)
    if "[object Object]" in blob:
        obj_obj += 1
    if isinstance(d, dict):
        param_names.append(d.get("parameterName") or d.get("businessName") or d.get("name") or d.get("title"))
        if d.get("metricAdjustment") or d.get("policyAdjustment") or "adjustment" in str(d.get("kind","")).lower():
            adj += 1
        if "bulk" in blob.lower() or "10" in blob and "adb" in blob.lower():
            adj += 0  # counted via kind
report["banking"]["baseline"] = {
    "doc": bdoc,
    "uw": len(uwb),
    "dataCalc": len(datab),
    "ready": sum(1 for c in uwb if c.get("executionReady") is True or str(c.get("status","")).upper()=="READY"),
    "needsInput": sum(1 for c in uwb if "INPUT" in str(c.get("status","")).upper() or c.get("executionReady") is False),
    "ignored": sum(1 for c in uwb if str(c.get("disposition") or c.get("reviewDisposition") or "").upper()=="IGNORED"),
    "execBlockersRules": sorted(keys(bs.get("executionBlockers"))),
    "execBlockersVersions": sorted(keys(blife.get("executionBlockers"))),
    "govBlockers": blife.get("governanceBlockers"),
    "objectObject": obj_obj,
    "allowCanonical": bs.get("allowCanonicalAuthority"),
}
report["banking"]["blockerEqual"] = keys(bs.get("executionBlockers")) == keys(blife.get("executionBlockers"))
print("BANK baseline", report["banking"]["baseline"]["uw"], "blockersEqual", report["banking"]["blockerEqual"])
if not report["banking"]["blockerEqual"]:
    report["defects"].append({"sev":"P0","area":"banking-readiness","msg":"Rules/Versions blocker keys differ",
                              "delta": sorted(keys(bs.get("executionBlockers")) ^ keys(blife.get("executionBlockers")))})

# Find EDI / ADB rule and exactly-100 / bulk blockers
edi_rules = [c for c in uwb if "edi" in str(c).lower()]
report["banking"]["edi"] = {"count": len(edi_rules), "sample": [{
    "status": c.get("status"), "executionReady": c.get("executionReady"),
    "disposition": c.get("disposition") or c.get("reviewDisposition"),
    "name": c.get("businessRule") or c.get("ruleName") or c.get("systemRuleId"),
    "blockers": c.get("executionBlockers")} for c in edi_rules[:3]]}

# Resolve EDI manual on first unresolved EDI operand rule
resolved_edi = False
for c in edi_rules:
    rid = c.get("ruleId") or c.get("id")
    if not rid: continue
    code, res = req("POST", f"/policy-studio/documents/{bdoc}/rules/{rid}/review", {
        "action": "RESOLVE_PARAMETER_MANUAL",
        "operandKey": "proposed_edi",
        "originalTerm": "Proposed EDI",
        "manualInputLabel": "Proposed EDI",
        "manualInputType": "Money",
        "unit": "INR",
        "requiredActor": "Credit Analyst",
        "guidance": "Capture proposed EDI from CAM",
        "reviewer": "credit_manager",
    })
    resolved_edi = code < 400
    report["banking"]["ediResolve"] = {"http": code, "msg": str(res.get("message") or "")[:200]}
    break

code, bs2 = req("GET", f"/policy-studio/documents/{bdoc}")
code, blife2 = req("GET", f"/policy-studio/documents/{bdoc}/lifecycle")
report["banking"]["afterEdi"] = {
    "execRules": sorted(keys(bs2.get("executionBlockers"))),
    "execVersions": sorted(keys(blife2.get("executionBlockers"))),
    "equal": keys(bs2.get("executionBlockers")) == keys(blife2.get("executionBlockers")),
    "ediCards": [{
        "status": c.get("status"), "executionReady": c.get("executionReady"),
        "name": c.get("businessRule") or c.get("ruleName")}
        for c in (bs2.get("underwritingRules") or []) if "edi" in str(c).lower()][:3]
}
print("AFTER EDI", report["banking"]["afterEdi"])

# exactly 100
exact = [b for b in (bs2.get("executionBlockers") or []) if isinstance(b, dict) and ("100" in str(b).lower() or "boundary" in str(b).lower() or "exactly" in str(b).lower())]
report["banking"]["exactly100"] = exact or [b for b in (bs2.get("executionBlockers") or []) if "100" in str(b).lower()]

# ADB adjustment blockers
adb_adj = [b for b in (bs2.get("executionBlockers") or []) if isinstance(b, dict) and ("bulk" in str(b).lower() or "adjustment" in str(b).lower() or "adb" in str(b).lower())]
report["banking"]["adbAdjustBlockers"] = adb_adj[:8]

# Test banking (temporary EDI value)
code, bt = req("POST", f"/policy-studio/documents/{bdoc}/test/quick", {
    "product": "DIGILEAP",
    "testValues": {"application.proposed_edi": 50000, "proposed_edi": 50000,
                   "banking.avg_daily_balance_3m": 200000}
})
code, bs3 = req("GET", f"/policy-studio/documents/{bdoc}")
# Prove test value did not heal readiness
report["banking"]["test"] = {
    "http": code, "policyMutated": bt.get("policyMutated"),
    "decision": bt.get("simulatedDecisionCode") or bt.get("simulatedDecision"),
    "execBlockersAfterTest": sorted(keys(bs3.get("executionBlockers"))),
    "lifecycleEvidenceStamped": bt.get("lifecycleEvidenceStamped"),
}
print("BANK TEST", report["banking"]["test"]["decision"], "mutated", report["banking"]["test"]["policyMutated"])

# Version banking (stamp if needed)
req("POST", f"/policy-studio/documents/{bdoc}/lifecycle/stamp-active-for-versioning", {"products": ["DIGILEAP"]})
code, bnv = req("POST", f"/policy-studio/documents/{bdoc}/lifecycle/new-version", {"reasonForChange": "Golden banking"})
bv2 = (bnv.get("policyHeader") or {}).get("documentId") or bnv.get("documentId")
code, bv2s = req("GET", f"/policy-studio/documents/{bv2}") if bv2 else (0, {})
report["banking"]["version"] = {
    "structuredClone": bnv.get("structuredClone"), "reingested": bnv.get("reingested"),
    "uwApi": bnv.get("underwritingRuleCount"),
    "uwV1": len(bs3.get("underwritingRules") or []),
    "uwV2": len((bv2s.get("underwritingRules") or [])),
    "dataV1": len(bs3.get("dataAndCalculations") or []),
    "dataV2": len((bv2s.get("dataAndCalculations") or [])),
}
print("BANK VERSION", report["banking"]["version"])

# ═══════════════ C. BUREAU ═══════════════
print("=== GOLDEN C BUREAU ===")
code, bur = req("POST", "/policy-studio/bureau/reset")
udoc = (bur.get("policyHeader") or {}).get("documentId")
code, us = req("GET", f"/policy-studio/documents/{udoc}")
code, ulife = req("GET", f"/policy-studio/documents/{udoc}/lifecycle")
uwu = us.get("underwritingRules") or []
report["bureau"]["baseline"] = {
    "doc": udoc, "uw": len(uwu),
    "execEqual": keys(us.get("executionBlockers")) == keys(ulife.get("executionBlockers")),
    "execRules": sorted(keys(us.get("executionBlockers"))),
    "execVersions": sorted(keys(ulife.get("executionBlockers"))),
    "compound": sum(1 for c in uwu if c.get("compoundParent") or "overdue" in str(c).lower()),
    "cleanUnresolved": any("clean" in str(b).lower() for b in (us.get("executionBlockers") or [])),
    "falseIgnored": [c.get("systemRuleId") or c.get("ruleName") for c in uwu
                     if str(c.get("disposition") or "").upper()=="IGNORED" and not c.get("cmAuthored")],
}
print("BUREAU", report["bureau"]["baseline"])
if not report["bureau"]["baseline"]["execEqual"]:
    report["defects"].append({"sev":"P0","area":"bureau-readiness","msg":"Rules/Versions blocker keys differ"})

# Max DPD lineage card in data
dpd_cards = [d for d in (us.get("dataAndCalculations") or []) if "dpd" in str(d).lower()]
report["bureau"]["maxDpd"] = dpd_cards[:2]

req("POST", f"/policy-studio/documents/{udoc}/lifecycle/stamp-active-for-versioning", {})
code, unv = req("POST", f"/policy-studio/documents/{udoc}/lifecycle/new-version", {"reasonForChange": "Golden bureau"})
uv2 = (unv.get("policyHeader") or {}).get("documentId") or unv.get("documentId")
code, uv2s = req("GET", f"/policy-studio/documents/{uv2}") if uv2 else (0, {})
report["bureau"]["version"] = {
    "structuredClone": unv.get("structuredClone"), "reingested": unv.get("reingested"),
    "uwV1": len(uwu), "uwV2": len(uv2s.get("underwritingRules") or []),
    "compoundV2": sum(1 for c in (uv2s.get("underwritingRules") or [])
                      if c.get("compoundParent") or "overdue" in str(c).lower()),
}
print("BUREAU VERSION", report["bureau"]["version"])

# Pass flags
report["pass"]["simpleAuthoring"] = report["simple"]["counts"].get("uw") == 5 and report["simple"]["panPreview"].get("complete") is True
report["pass"]["simpleVersion"] = report["simple"].get("version", {}).get("structuredClone") is True and report["simple"].get("immutability", {}).get("v2uw") == 5
report["pass"]["bankingVersion"] = report["banking"]["version"].get("structuredClone") is True and report["banking"]["version"].get("uwV1") == report["banking"]["version"].get("uwV2")
report["pass"]["bureauVersion"] = report["bureau"]["version"].get("structuredClone") is True and report["bureau"]["version"].get("uwV1") == report["bureau"]["version"].get("uwV2")
report["pass"]["readinessConsistent"] = report["simple"]["blockerCompare"]["equal"] and report["banking"]["blockerEqual"] and report["bureau"]["baseline"]["execEqual"]
report["pass"]["testSafe"] = (report["simple"].get("testPass", {}).get("policyMutated") is False
                              and report["banking"].get("test", {}).get("policyMutated") is False)
report["pass"]["allowCanonicalFalse"] = report["allowCanonical"] is False

json.dump(report, open("/tmp/golden-e2e-1-report.json","w"), indent=2, default=str)
print("REPORT_WRITTEN defects", len(report["defects"]))
print(json.dumps(report["pass"], indent=2))
PY

python3 /tmp/golden-e2e-1.py
echo "==== REPORT SUMMARY ===="
python3 - <<'PY'
import json
r=json.load(open("/tmp/golden-e2e-1-report.json"))
print(json.dumps({
  "pass": r.get("pass"),
  "simple_counts": r.get("simple",{}).get("counts"),
  "simple_lifecycle": r.get("simple",{}).get("lifecycleView"),
  "simple_version": r.get("simple",{}).get("version"),
  "simple_immutability": r.get("simple",{}).get("immutability"),
  "simple_testPass": r.get("simple",{}).get("testPass"),
  "simple_testFoir": {k:r.get("simple",{}).get("testFoirFail",{}).get(k) for k in ("decision","foirFailSeen","policyMutated")},
  "banking_baseline": {k:r.get("banking",{}).get("baseline",{}).get(k) for k in ("uw","dataCalc","ready","needsInput","objectObject")},
  "banking_edi": r.get("banking",{}).get("edi"),
  "banking_afterEdi": r.get("banking",{}).get("afterEdi"),
  "banking_exactly100": r.get("banking",{}).get("exactly100"),
  "banking_version": r.get("banking",{}).get("version"),
  "bureau_baseline": r.get("bureau",{}).get("baseline"),
  "bureau_version": r.get("bureau",{}).get("version"),
  "defects": r.get("defects"),
  "landing": r.get("landing"),
}, indent=2, default=str))
PY
