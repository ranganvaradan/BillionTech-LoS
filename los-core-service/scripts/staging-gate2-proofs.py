#!/usr/bin/env python3
"""POLICY-STUDIO-GATE2 remaining proofs — confirm:True + Policy Test + version isolation."""
from __future__ import annotations

import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo"
ENV = "/opt/billiontech/apps/billiontechlos/.env"
TOKEN = open(ENV, encoding="utf-8").read().split("CREDIT_INTELLIGENCE_INTERNAL_TOKEN=")[-1].splitlines()[0]
H = {"Content-Type": "application/json", "X-Internal-Token": TOKEN}


def req(method, path, body=None):
    r = urllib.request.Request(
        BASE + path,
        data=None if body is None else json.dumps(body).encode(),
        headers=H,
        method=method,
    )
    with urllib.request.urlopen(r, timeout=120) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def try_req(method, path, body=None):
    try:
        return True, req(method, path, body)
    except urllib.error.HTTPError as e:
        return False, {"http": e.code, "err": e.read().decode(errors="ignore")[:800]}
    except Exception as e:
        return False, {"err": str(e)}


def create(name):
    d = req(
        "POST",
        "/policy-studio/create",
        {"policyName": name, "product": "Business Term Loan", "borrowerType": "Company"},
    )
    return (d.get("policyHeader") or {}).get("documentId") or d.get("documentId")


def confirm(doc, text):
    return req(
        "POST",
        f"/policy-studio/documents/{doc}/rules/add-plain-english",
        {"confirm": True, "mode": "DESCRIBE", "text": text, "treatment": "Reject"},
    )


def decision_of(pt):
    return str(
        pt.get("simulatedDecisionCode")
        or pt.get("overallDecision")
        or pt.get("decision")
        or pt.get("outcome")
        or ""
    ).upper()


def normalize(d):
    d = d.replace(" ", "_")
    if d in ("PASS", "ALLOW", "APPROVE"):
        return "ALLOW"
    if d in ("FAIL", "REJECT", "DECLINE"):
        return "REJECT"
    return d


out = {"ok": True}

# G6 write-off save + policy test
doc6 = create("GATE2 G6 writeoff parity")
text_wo = "No Loan Write-Offs are allowed, except for Credit Cards"
c = confirm(doc6, text_wo)
assert c.get("confirmed") is True, c
assert (c.get("expression") or {}).get("left", {}).get("metric") == "bureau.accounts.writeoff_non_cc" or (
    "writeoff_non_cc" in json.dumps(c)
), c
reload6 = req("GET", f"/policy-studio/documents/{doc6}")
assert len(reload6.get("underwritingRules") or []) >= 1
out["g6_save_reload"] = {
    "confirmed": True,
    "ruleCount": len(reload6.get("underwritingRules") or []),
    "paramInSession": "bureau.accounts.writeoff_non_cc" in json.dumps(reload6),
}
cases = []
for vals, expect in (
    ({"bureau.accounts.writeoff_non_cc": 0}, "ALLOW"),
    ({"bureau.accounts.writeoff_non_cc": 2}, "REJECT"),
):
    ok, pt = try_req(
        "POST",
        f"/policy-studio/documents/{doc6}/test/quick",
        {"testValues": vals, "useFixtureDefaults": False},
    )
    actual = normalize(decision_of(pt)) if ok else "ERR"
    cases.append({"ok": ok, "input": vals, "expect": expect, "actual": actual, "raw": decision_of(pt) if ok else pt})
out["g6_policy_test"] = cases
assert all(x["ok"] and x["actual"] == x["expect"] for x in cases), cases
print("G6_OK", out["g6_save_reload"], cases)

# G7 missing data
doc7 = create("GATE2 G7 missing")
confirm(doc7, "Bureau score must be 650 or above")
ok, pt7 = try_req(
    "POST",
    f"/policy-studio/documents/{doc7}/test/quick",
    {"testValues": {}, "useFixtureDefaults": False},
)
blob = json.dumps(pt7).upper()
insufficient = any(
    k in blob
    for k in (
        "DATA_INSUFFICIENT",
        "MISSING",
        "INSUFFICIENT",
        "UNAVAILABLE",
        "NOT_AVAILABLE",
        "MISSING_FACT",
        "NO_VALUE",
        "ABSENT",
    )
)
# Must not invent zero as a real bureau score that PASSes
dec = normalize(decision_of(pt7)) if ok else "ERR"
invented_pass = (":0" in blob or "SCORE\":0" in blob) and dec == "ALLOW" and not insufficient
out["g7"] = {
    "ok": ok,
    "decision": dec,
    "insufficientSignal": insufficient,
    "inventedZeroPass": invented_pass,
    "keys": list(pt7.keys())[:20] if isinstance(pt7, dict) else None,
}
assert ok and not invented_pass, out["g7"]
print("G7_OK", out["g7"])

# Human recovery + version isolation
docH = create("GATE2 human recovery v")
amb = req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "weird blorp metric zzz"})
assert amb.get("resolutionState") == "NEEDS_CLARIFICATION"
src = req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "write-offs", "preferredSource": "Bureau"},
)
assert src.get("resolutionState") in ("READY_DERIVED", "NEEDS_PARAMETER_SELECTION", "READY_EXISTING")
browse = req("GET", "/policy-studio/parameters?source=Bureau")
assert (browse.get("count") or 0) > 0
cH = confirm(docH, text_wo)
assert cH.get("confirmed") is True
reloadH = req("GET", f"/policy-studio/documents/{docH}")
assert "bureau.accounts.writeoff_non_cc" in json.dumps(reloadH)
req(
    "POST",
    f"/policy-studio/documents/{docH}/lifecycle/stamp-active-for-versioning",
    {"reason": "GATE2"},
)
v2 = req("POST", f"/policy-studio/documents/{docH}/lifecycle/new-version", {})
v2_doc = (v2.get("policyHeader") or {}).get("documentId") or v2.get("documentId")
assert v2_doc and v2_doc != docH
# edit v2: add bureau score rule
confirm(v2_doc, "Bureau score must be 650 or above")
v1_after = req("GET", f"/policy-studio/documents/{docH}")
v2_after = req("GET", f"/policy-studio/documents/{v2_doc}")
v1_rules = json.dumps(v1_after.get("underwritingRules") or [])
v2_rules = json.dumps(v2_after.get("underwritingRules") or [])
out["human_recovery"] = {
    "ambiguous": amb.get("resolutionState"),
    "bureauOverride": src.get("resolutionState"),
    "browseCount": browse.get("count"),
    "savedParam": "bureau.accounts.writeoff_non_cc" in json.dumps(reloadH),
    "v1": docH,
    "v2": v2_doc,
    "v1RuleCount": len(v1_after.get("underwritingRules") or []),
    "v2RuleCount": len(v2_after.get("underwritingRules") or []),
    "v1UnchangedAfterV2Edit": len(v1_after.get("underwritingRules") or [])
    == len(reloadH.get("underwritingRules") or []),
    "v2HasExtraBureauScore": "bureau.score" in v2_rules and "bureau.score" not in v1_rules,
}
assert out["human_recovery"]["v1UnchangedAfterV2Edit"]
assert out["human_recovery"]["v2HasExtraBureauScore"]
print("HUMAN_VERSION_OK", out["human_recovery"])

# G8 structural
c8 = req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "Bureau score"})
cands = [x.get("parameterId") or x.get("id") for x in (c8.get("candidates") or [])]
out["g8"] = {
    "state": c8.get("resolutionState"),
    "candidates": cands,
    "providerLeak": any(x in json.dumps(c8).lower() for x in ("equifax", "karza", "perfios")),
}
assert "bureau.score" in cands
assert not out["g8"]["providerLeak"]
print("G8_OK", out["g8"])

# G10 manual proposed EDI
c10 = req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "proposed EDI"})
out["g10"] = {
    "state": c10.get("resolutionState"),
    "param": c10.get("canonicalParameter"),
    "source": c10.get("source"),
    "resolutionType": c10.get("resolutionType"),
}
assert c10.get("canonicalParameter") == "application.proposed_edi"
print("G10_OK", out["g10"])

# Banking
banking = req("GET", "/policy-studio/banking")
blockers = (banking.get("executionReadiness") or {}).get("executionBlockers") or banking.get(
    "executionBlockers"
) or []
out["banking"] = {
    "blockerCount": len(blockers),
    "types": [b.get("blockerType") for b in blockers],
    "allowCanonicalAuthority": banking.get("allowCanonicalAuthority"),
}
print("BANKING", out["banking"])

open("/tmp/gate2-proofs.json", "w", encoding="utf-8").write(json.dumps(out, indent=2))
print("REPORT /tmp/gate2-proofs.json")
print("GATE2_PROOFS_OK")
