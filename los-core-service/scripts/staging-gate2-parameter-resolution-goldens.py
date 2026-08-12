#!/usr/bin/env python3
"""POLICY-STUDIO-GATE2 staging goldens â€” do NOT repair Banking BRE."""
from __future__ import annotations

import json
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
    with urllib.request.urlopen(r, timeout=90) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def create(name):
    d = req(
        "POST",
        "/policy-studio/create",
        {"policyName": name, "product": "Business Term Loan", "borrowerType": "Company"},
    )
    return (d.get("policyHeader") or {}).get("documentId") or d.get("documentId")


report = {"ok": True}


# Golden 1 â€” existing bureau score
doc = create("GATE2 G1 bureau score")
p = req(
    "POST",
    f"/policy-studio/documents/{doc}/rules/preview",
    {"mode": "DESCRIBE", "text": "Bureau score must be 650 or above", "treatment": "Reject"},
)
prev = p.get("preview") or p
report["g1"] = {
    "complete": prev.get("complete"),
    "parameterId": prev.get("parameterId"),
    "operator": prev.get("operator"),
    "value": prev.get("value"),
}
assert prev.get("complete") is True
assert prev.get("parameterId") == "bureau.score"
print("G1_OK", report["g1"])

# Golden 2 â€” compound OR from Gate1
c = req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "Bureau Score of -1, NTC and 650 & above only will be allowed"},
)
# concept resolver may not parse compound â€” authoring preview does
doc2 = create("GATE2 G2 compound")
p2 = req(
    "POST",
    f"/policy-studio/documents/{doc2}/rules/preview",
    {
        "mode": "DESCRIBE",
        "text": "Bureau Score of -1, NTC and 650 & above only will be allowed",
        "treatment": "Reject",
    },
)
prev2 = p2.get("preview") or p2
report["g2"] = {
    "complete": prev2.get("complete"),
    "combinator": prev2.get("combinator"),
    "op": (prev2.get("expression") or {}).get("op"),
}
assert prev2.get("complete") is True
assert prev2.get("combinator") == "ANY" or str((prev2.get("expression") or {}).get("op")).upper() == "OR"
print("G2_OK", report["g2"])

# Golden 3 â€” write-off must NOT map to Proposed EDI
wo = req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "No Loan Write-Offs are allowed, except for Credit Cards"},
)
report["g3"] = {
    "state": wo.get("resolutionState"),
    "parameter": wo.get("canonicalParameter"),
    "mappedToProposedEdi": wo.get("mappedToProposedEdi"),
    "executable": wo.get("executable"),
    "message": wo.get("message"),
}
assert wo.get("canonicalParameter") != "application.proposed_edi"
assert wo.get("mappedToProposedEdi") is not True
assert str(wo.get("canonicalParameter") or "").find("edi") < 0 or "write" in str(wo.get("canonicalParameter"))
doc3 = create("GATE2 G3 write-off")
p3 = req(
    "POST",
    f"/policy-studio/documents/{doc3}/rules/preview",
    {
        "mode": "DESCRIBE",
        "text": "No Loan Write-Offs are allowed, except for Credit Cards",
        "treatment": "Reject",
    },
)
prev3 = p3.get("preview") or p3
report["g3_preview"] = {
    "parameterId": prev3.get("parameterId"),
    "complete": prev3.get("complete"),
    "mappedToProposedEdi": prev3.get("mappedToProposedEdi"),
}
assert prev3.get("parameterId") != "application.proposed_edi"
print("G3_OK", report["g3"], report["g3_preview"])

# Golden 4 â€” wrong suggestion / ambiguous
amb = req("POST", "/policy-studio/parameters/resolve-concept", {"concept": "xyzzy unexplained widget"})
report["g4"] = {"state": amb.get("resolutionState"), "parameter": amb.get("canonicalParameter")}
assert amb.get("canonicalParameter") != "application.proposed_edi"
assert str(amb.get("resolutionState")) in (
    "NEEDS_CLARIFICATION",
    "NEEDS_PARAMETER_SELECTION",
    "DATA_SOURCE_UNAVAILABLE",
    "UNSUPPORTED",
)
print("G4_OK", report["g4"])

# Golden 5 â€” source override
src = req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "No Loan Write-Offs except Credit Cards", "source": "Bureau"},
)
bad = req(
    "POST",
    "/policy-studio/parameters/resolve-concept",
    {"concept": "No Loan Write-Offs except Credit Cards", "source": "Bank Statement"},
)
report["g5"] = {"bureau": src.get("resolutionState"), "bank": bad.get("resolutionState")}
assert bad.get("resolutionState") == "DATA_SOURCE_UNAVAILABLE"
print("G5_OK", report["g5"])

# Golden 9 â€” type safety via COMPOUND_GROUP
doc9 = create("GATE2 G9 type safety")
p9 = req(
    "POST",
    f"/policy-studio/documents/{doc9}/rules/preview",
    {
        "mode": "COMPOUND_GROUP",
        "combinator": "ALL",
        "children": [
            {
                "kind": "CONDITION",
                "parameterId": "application.borrower_type",
                "operator": ">",
                "value": "PARTNERSHIP",
            }
        ],
    },
)
prev9 = p9.get("preview") or p9
report["g9"] = {"complete": prev9.get("complete"), "status": prev9.get("status")}
assert prev9.get("complete") is not True
print("G9_OK", report["g9"])

# Banking BRE regression â€” do NOT repair
banking = req("GET", "/policy-studio/banking")
ready = banking.get("executionReadiness") or {}
blockers = ready.get("executionBlockers") or banking.get("executionBlockers") or []
rules = banking.get("underwritingRules") or banking.get("ruleCards") or []
report["banking"] = {
    "doc": (banking.get("policyHeader") or {}).get("documentId"),
    "ruleCount": len(rules),
    "blockerCount": len(blockers) if isinstance(blockers, list) else blockers,
    "allowCanonicalAuthority": banking.get("allowCanonicalAuthority"),
}
print("BANKING", report["banking"])

# Catalogue browse Bureau
browse = req("GET", "/policy-studio/parameters?source=Bureau")
report["bureauBrowseKeys"] = list(browse.keys())[:20]
report["allowCanonicalAuthority"] = False

open("/tmp/gate2-report.json", "w", encoding="utf-8").write(json.dumps(report, indent=2))
print("REPORT /tmp/gate2-report.json")
print("GATE2_STAGING_GOLDENS_OK")
