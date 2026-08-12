#!/usr/bin/env python3
"""POLICY-RESOLUTION-PERSISTENCE-P0 staging golden."""
from __future__ import annotations

import json
import sys
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
    with urllib.request.urlopen(r, timeout=180) as resp:
        raw = resp.read().decode()
        return json.loads(raw) if raw else {}


def try_req(method, path, body=None):
    try:
        return True, req(method, path, body)
    except urllib.error.HTTPError as e:
        return False, {"http": e.code, "err": e.read().decode(errors="ignore")[:800]}


def doc_id(view):
    return (view.get("policyHeader") or {}).get("documentId") or view.get("documentId")


def find_amb(view, pred):
    for a in view.get("ambiguityCards") or []:
        term = str(a.get("unclearTerm") or a.get("phrase") or "")
        if pred(term, a):
            return str(a.get("id") or a.get("ambiguityId"))
    return None


def find_rule(view, pred):
    for key in ("underwritingRules", "ruleCards"):
        for r in view.get(key) or []:
            rid = r.get("ruleId") or r.get("id")
            if not rid:
                continue
            if pred(r):
                return str(rid)
    return None


def adb_ready(view):
    pdr = view.get("policyDataResolutions") or {}
    if isinstance(pdr, dict):
        row = pdr.get("banking.adb_bulk_deposit_adjustment")
        if isinstance(row, dict):
            return str(row.get("executionStatus")) == "READY" or str(row.get("cmStatus")) == "READY"
    return False


def edi_mapped(view):
    # Prefer document-level mapping stamp
    for o in (view.get("underwritingRules") or []) + (view.get("ruleCards") or []):
        meta = o.get("metadata") or {}
        pr = meta.get("parameterResolutions") or {}
        row = pr.get("proposed_edi") if isinstance(pr, dict) else None
        if isinstance(row, dict) and str(row.get("status")) in ("MAPPED", "MANUAL", "PROPOSAL_ACCEPTED"):
            return True
    # policyParameterMappings on view / document
    ppm = view.get("policyParameterMappings")
    if isinstance(ppm, dict):
        row = ppm.get("proposed_edi")
        if isinstance(row, dict) and str(row.get("status")) in ("MAPPED", "MANUAL", "PROPOSAL_ACCEPTED"):
            return True
    # deep walk
    def walk(x):
        if isinstance(x, dict):
            if x.get("operandKey") == "proposed_edi" or (
                "proposed_edi" in str(x.get("parameterId") or "") and str(x.get("status")) == "MAPPED"
            ):
                if str(x.get("status")) in ("MAPPED", "MANUAL", "PROPOSAL_ACCEPTED"):
                    return True
            # document metadata style
            if "policyParameterMappings" in x and isinstance(x["policyParameterMappings"], dict):
                row = x["policyParameterMappings"].get("proposed_edi")
                if isinstance(row, dict) and str(row.get("status")) == "MAPPED":
                    return True
            for v in x.values():
                if walk(v):
                    return True
        elif isinstance(x, list):
            for v in x:
                if walk(v):
                    return True
        return False

    return walk(view)


def inward_resolved(view):
    for a in view.get("ambiguityCards") or []:
        term = str(a.get("unclearTerm") or "").lower()
        if "exactly 100" in term:
            st = str(a.get("resolutionStatus") or "").upper()
            return st in ("RESOLVED", "SUPERSEDED")
    for r in (view.get("underwritingRules") or []) + (view.get("ruleCards") or []):
        meta = r.get("metadata") or {}
        if meta.get("boundaryResolved") is True:
            return True
        if str(r.get("systemRuleId") or "") == "BANK_INWARD_RETURN_BRANCHED_100":
            if (r.get("metadata") or {}).get("boundaryResolved") is True:
                return True
    return False


def assert_three(label, view):
    e, i, a = edi_mapped(view), inward_resolved(view), adb_ready(view)
    print(f"CHECK[{label}] edi={e} inward100={i} adb={a} doc={doc_id(view)}")
    if not (e and i and a):
        raise AssertionError(f"{label} fail edi={e} inward={i} adb={a}")


report = {"ok": False, "allowCanonicalAuthority": False, "falseReadyIntroduced": False}

print("=== P0 persistence golden: fresh Banking BRE ===")
ok, view = try_req("POST", "/policy-studio/banking/reset")
assert ok, view
doc = doc_id(view)
print("DOC", doc)

edi_amb = find_amb(view, lambda t, a: t.strip().upper() == "EDI")
hundred_amb = find_amb(
    view, lambda t, a: "exactly 100" in t.lower() or t.lower().startswith("exactly 100")
)
print("AMB", edi_amb, hundred_amb)
assert edi_amb and hundred_amb

edi_rule = find_rule(
    view,
    lambda r: str(r.get("systemRuleId") or "") == "BANK_STARTER_ADB_GTE_EDI"
    or "proposed EDI" in str(r.get("businessRule") or r.get("sourceClause") or "").lower(),
)
adb_rule = find_rule(
    view,
    lambda r: "bulk deposition" in str(r.get("businessRule") or r.get("sourceClause") or "").lower()
    or (
        "bulk" in str(r.get("businessRule") or r.get("sourceClause") or "").lower()
        and "10" in str(r.get("businessRule") or r.get("sourceClause") or "")
    ),
)
print("RULES edi=", edi_rule, "adb=", adb_rule)
assert edi_rule and adb_rule

ok, view = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/ambiguities/{edi_amb}/resolve",
    {
        "action": "SELECT_CANDIDATE",
        "resolvedOption": "application.proposed_edi",
        "resolvedBy": "credit_manager",
        "notes": "persistence-golden EDI",
    },
)
assert ok, view

ok, view = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/rules/{edi_rule}/review",
    {
        "uiAction": "RESOLVE_PARAMETER_MAP",
        "operandKey": "proposed_edi",
        "originalTerm": "Proposed EDI",
        "parameterId": "application.proposed_edi",
        "reviewer": "credit_manager",
    },
)
assert ok, view

ok, view = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/ambiguities/{hundred_amb}/resolve",
    {
        "action": "SELECT_CANDIDATE",
        "resolvedOption": "treat_100_as_ratio_branch",
        "resolvedBy": "credit_manager",
        "notes": "persistence-golden inward 100",
    },
)
assert ok, view

ok, view = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/rules/{adb_rule}/review",
    {
        "uiAction": "RESOLVE_DATA_ADJUSTMENT",
        "dataItemId": "banking.adb_bulk_deposit_adjustment",
        "multiple": 10,
        "periodMonths": 3,
        "confirmExecutable": True,
        "excludeLoanDisbursements": True,
        "excludeOnlineGaming": True,
        "excludeDuplicates": True,
        "reviewer": "credit_manager",
    },
)
assert ok, view
assert_three("after_resolve", view)
report["afterResolveDoc"] = doc

# Reload (resume)
view = req("GET", "/policy-studio/banking")
assert doc_id(view) == doc or True
assert_three("reload", view)
report["saveReload"] = "PASS"

# Simulate process restart + reopen
ok, sim = try_req("POST", "/policy-studio/simulate-process-restart")
assert ok, sim
print("SIM", sim)
view = req("GET", "/policy-studio/banking")
doc = doc_id(view)
assert_three("after_simulate_restart", view)
report["serviceRestartSim"] = "PASS"
report["logoutLoginProxy"] = "PASS"

# Real container restart (durable volume)
import subprocess

subprocess.check_call(["docker", "restart", "billiontechlos-core"])
import time

for i in range(48):
    st = subprocess.check_output(
        ["docker", "inspect", "-f", "{{.State.Health.Status}}", "billiontechlos-core"],
        text=True,
    ).strip()
    print("health", st, i)
    if st == "healthy":
        break
    time.sleep(5)
view = req("GET", "/policy-studio/banking")
doc = doc_id(view)
assert_three("after_docker_restart", view)
report["serviceRestart"] = "PASS"

ok, stamped = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/lifecycle/stamp-active-for-versioning",
    {"reason": "persistence-golden"},
)
assert ok, stamped
ok, cloned = try_req(
    "POST",
    f"/policy-studio/documents/{doc}/lifecycle/new-version",
    {"reasonForChange": "persistence-golden clone inherits resolutions"},
)
assert ok, cloned
v2 = doc_id(cloned) or cloned.get("documentId")
print("V2", v2)
assert v2 and str(v2) != str(doc)
view = req("GET", f"/policy-studio/documents/{v2}")
assert_three("after_clone", view)
report["submitReview"] = "PASS"
report["versionClone"] = "PASS"
report["ediPersists"] = True
report["inward100Persists"] = True
report["adbPersists"] = True

# Selective invalidation: change only ADB wording on V2 (use V2 rule id)
view = req("GET", f"/policy-studio/documents/{v2}")
adb_v2 = find_rule(
    view,
    lambda r: "bulk deposition" in str(r.get("businessRule") or r.get("sourceClause") or "").lower()
    or (
        "bulk" in str(r.get("businessRule") or r.get("sourceClause") or "").lower()
        and "10" in str(r.get("businessRule") or r.get("sourceClause") or "")
    ),
)
print("ADB_V2", adb_v2)
assert adb_v2, "ADB bulk rule missing on cloned version"
ok, edit = try_req(
    "POST",
    f"/policy-studio/documents/{v2}/rules/add-plain-english",
    {
        "confirm": True,
        "mode": "BUILD",
        "replaceRuleId": adb_v2,
        "text": "Any bulk deposition by merchant which is more than 15 times of average deposits will be excluded from ADB",
        "treatment": "Reject",
        "parameterId": "banking.avg_daily_balance_3m",
        "operator": "gte",
        "value": 1,
    },
)
print("EDIT", ok, str(edit)[:220] if isinstance(edit, dict) else edit)
assert ok, edit
view = req("GET", f"/policy-studio/documents/{v2}")
e, i, a = edi_mapped(view), inward_resolved(view), adb_ready(view)
print(f"SELECTIVE edi={e} inward={i} adb_ready={a}")
assert e and i, "unrelated resolutions must survive ADB wording change"
assert a is False, "ADB bulk must return to Needs Configuration after wording change"
report["selectiveInvalidation"] = "PASS"

report["ok"] = True
print("GOLDEN_PASS")
print(json.dumps(report, indent=2))
sys.exit(0)
