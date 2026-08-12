#!/usr/bin/env python3
"""POLICY-STUDIO-GATE2 extended goldens — do NOT repair Banking BRE."""
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
        err = e.read().decode(errors="ignore")[:500]
        return False, {"http": e.code, "err": err}
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
        {"text": text, "treatment": "Reject", "mode": "DESCRIBE"},
    )


out = {"ok": True}

# --- G6 raw -> derived ---
doc6 = create("GATE2 G6 raw-derived writeoff")
ok, c = try_req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "Number of loan write-offs excluding credit cards", "preferredSource": "Bureau"},
)
out["g6_resolve"] = c if ok else c
assert ok and c.get("resolutionState") in ("READY_DERIVED", "READY_EXISTING")
assert c.get("canonicalParameter") == "bureau.accounts.writeoff_non_cc"
ok, saved = try_req(
    "POST",
    f"/policy-studio/documents/{doc6}/rules/add-plain-english",
    {
        "text": "No Loan Write-Offs are allowed, except for Credit Cards",
        "treatment": "Reject",
        "mode": "DESCRIBE",
    },
)
out["g6_save"] = {"ok": ok, "keys": list(saved.keys())[:12] if isinstance(saved, dict) else saved}
# Policy Test with fixture writeoff=0 should allow; writeoff=1 reject
for vals, expect in (({"bureau.accounts.writeoff_non_cc": 0}, "ALLOW"), ({"bureau.accounts.writeoff_non_cc": 2}, "REJECT")):
    okpt, pt = try_req(
        "POST",
        f"/policy-studio/documents/{doc6}/test/quick",
        {"testValues": vals, "useFixtureDefaults": False},
    )
    if not okpt:
        okpt, pt = try_req(
            "POST",
            f"/policy-studio/documents/{doc6}/policy-test/run",
            {"testValues": vals, "useFixtureDefaults": False},
        )
    decision = str(
        pt.get("simulatedDecisionCode")
        or pt.get("overallDecision")
        or pt.get("decision")
        or pt.get("outcome")
        or ""
    ).upper()
    out.setdefault("g6_policy_test", []).append(
        {"ok": okpt, "input": vals, "expect": expect, "decision": decision, "keys": list(pt.keys())[:12] if isinstance(pt, dict) else None}
    )
print("G6", out["g6_resolve"].get("resolutionState"), out["g6_save"].get("ok"), out.get("g6_policy_test"))

# --- G7 missing data ---
doc7 = create("GATE2 G7 missing data")
confirm(doc7, "Bureau score must be 650 or above")
ok, pt7 = try_req(
    "POST",
    f"/policy-studio/documents/{doc7}/test/quick",
    {"testValues": {}, "useFixtureDefaults": False},
)
if not ok:
    ok, pt7 = try_req(
        "POST",
        f"/policy-studio/documents/{doc7}/policy-test/run",
        {"testValues": {}, "useFixtureDefaults": False},
    )
blob = json.dumps(pt7).upper()
invented_zero_pass = ("BUREAU.SCORE" in blob and ":0" in blob and "PASS" in blob and "INSUFFICIENT" not in blob and "MISSING" not in blob)
# Prefer detecting explicit insufficient/missing
insufficient = any(
    k in blob
    for k in (
        "DATA_INSUFFICIENT",
        "MISSING",
        "NOT_AVAILABLE",
        "INSUFFICIENT",
        "UNAVAILABLE",
        "DATA_UNAVAILABLE",
        "MISSING_FACT",
        "NOT_REQUIRED",
    )
)
decision7 = str(
    pt7.get("simulatedDecisionCode") or pt7.get("overallDecision") or pt7.get("decision") or pt7.get("outcome") or ""
).upper()
out["g7"] = {
    "ok": ok,
    "decision": decision7,
    "insufficientSignal": insufficient,
    "inventedZeroPassHeuristic": invented_zero_pass,
    "keys": list(pt7.keys())[:20] if isinstance(pt7, dict) else None,
    "sample": {k: pt7.get(k) for k in list(pt7.keys())[:8]} if isinstance(pt7, dict) else pt7,
}
print("G7", out["g7"])

# --- G8 provider independence ---
ok, c8 = try_req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "bureau score"})
leak = any(x in json.dumps(c8).lower() for x in ("equifax", "karza", "perfios", "surepass"))
out["g8"] = {
    "ok": ok,
    "param": c8.get("canonicalParameter"),
    "providerFieldLeak": leak,
    "state": c8.get("resolutionState"),
}
print("G8", out["g8"])

# --- G10 manual ---
ok, c10 = try_req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "relationship manager override score", "preferredSource": "Manual"},
)
# also try known manual param
ok2, c10b = try_req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "proposed EDI", "preferredSource": "Application"},
)
out["g10"] = {
    "ambiguousManual": c10 if ok else c10,
    "proposedEdi": {
        "state": c10b.get("resolutionState"),
        "param": c10b.get("canonicalParameter"),
        "source": c10b.get("source"),
        "message": c10b.get("message"),
    },
}
print("G10", out["g10"])

# --- Human recovery ---
docH = create("GATE2 human recovery")
ok, amb = try_req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "weird blorp metric zzz"})
assert ok and amb.get("resolutionState") in (
    "NEEDS_CLARIFICATION",
    "UNSUPPORTED",
    "NEEDS_PARAMETER_SELECTION",
)
ok, src = try_req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "write-offs", "preferredSource": "Bureau"},
)
assert ok and src.get("resolutionState") in (
    "READY_DERIVED",
    "READY_EXISTING",
    "NEEDS_PARAMETER_SELECTION",
)
ok, browse = try_req("GET", "/policy-studio/parameters?source=Bureau")
confirm(
    docH,
    "No Loan Write-Offs are allowed, except for Credit Cards",
)
reload = req("GET", f"/policy-studio/documents/{docH}")
rules = reload.get("underwritingRules") or []
writeoff_rules = [
    r
    for r in rules
    if "writeoff" in json.dumps(r).lower() or "write-off" in json.dumps(r).lower()
]
out["human_recovery"] = {
    "ambiguousState": amb.get("resolutionState"),
    "bureauOverrideState": src.get("resolutionState"),
    "bureauParam": src.get("canonicalParameter"),
    "browseCounts": {
        "count": browse.get("count"),
        "rawCount": browse.get("rawCount"),
        "derivedCount": browse.get("derivedCount"),
    },
    "reloadRuleCount": len(rules),
    "writeoffRulePresent": len(writeoff_rules) > 0,
}
print("HUMAN", out["human_recovery"])

# --- Version clone ---
ok, stamp = try_req(
    "POST",
    f"/policy-studio/documents/{docH}/lifecycle/stamp-active-for-versioning",
    {"reason": "GATE2 version isolation"},
)
ok2, v2 = try_req(
    "POST",
    f"/policy-studio/documents/{docH}/lifecycle/new-version",
    {},
)
v2_doc = (v2.get("policyHeader") or {}).get("documentId") or v2.get("documentId")
out["version"] = {
    "stampOk": ok,
    "newVersionOk": ok2,
    "v1": docH,
    "v2": v2_doc,
}
print("VERSION", out["version"])

# --- Banking blockers ---
banking = req("GET", "/policy-studio/banking")
ready = banking.get("executionReadiness") or {}
blockers = ready.get("executionBlockers") or banking.get("executionBlockers") or []
out["banking"] = {
    "blockerCount": len(blockers),
    "types": [b.get("blockerType") for b in blockers],
    "params": [b.get("parameterId") for b in blockers],
    "reasons": [b.get("reason") for b in blockers],
    "allowCanonicalAuthority": banking.get("allowCanonicalAuthority"),
}
print("BANKING", out["banking"])

open("/tmp/gate2-extended.json", "w", encoding="utf-8").write(json.dumps(out, indent=2))
print("REPORT /tmp/gate2-extended.json")
print("GATE2_EXTENDED_OK")
