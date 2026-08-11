#!/usr/bin/env python3
"""LMS-PRODUCT-MAPPING-P0 staging verification — Product Config vs openLoanAccount authority (no Encore open)."""
import json
import urllib.request

ENV = open("/opt/billiontech/apps/billiontechlos/.env").read()
TOKEN = ENV.split("CREDIT_INTELLIGENCE_INTERNAL_TOKEN=")[1].splitlines()[0].strip()
# Prefer admin JWT path used by LiveReadiness; fall back to internal if present
BASE = "http://127.0.0.1:8083/api/v1"
H = {
    "Content-Type": "application/json",
    "X-Internal-Token": TOKEN,
    "X-User-Role": "ADMINISTRATOR",
    "X-User-Id": "c1000000-0000-0000-0000-000000000001",
    "X-User-Name": "admin",
}

def req(method, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=H)
    with urllib.request.urlopen(r, timeout=60) as resp:
        return json.load(resp)

# Golden A — COMPANY / TERM_LOAN / BORROWER / 250000 (use golden selector for workflow ids)
compose = req("GET", "/admin/live-readiness/product-configuration/golden")
lms = (compose.get("compose") or {}).get("lms") or {}
open_res = lms.get("openLoanAccountResolution") or {}
print("GOLDEN_A_PC", json.dumps({
    "lmsProductCode": lms.get("lmsProductCode"),
    "status": lms.get("status"),
    "workflowId": lms.get("workflowId"),
    "workflowVersion": lms.get("workflowVersion"),
    "mappingSource": lms.get("mappingSource"),
    "productConfigMatchesOpenLoanAccount": lms.get("productConfigMatchesOpenLoanAccount"),
    "openLoanAccount": {
        "lmsProductCode": open_res.get("lmsProductCode"),
        "mappingSource": open_res.get("mappingSource"),
        "workflowId": open_res.get("workflowId"),
        "workflowVersion": open_res.get("workflowVersion"),
        "status": open_res.get("status"),
    },
    "note": lms.get("note"),
}, indent=2))

assert lms.get("lmsProductCode") == "IPPOPAYM01", lms
assert open_res.get("lmsProductCode") == "IPPOPAYM01", open_res
assert lms.get("productConfigMatchesOpenLoanAccount") is True
assert open_res.get("mappingSource") == "WORKFLOW_LMS_PRODUCT_CODE"
assert "no temporary Encore product-code bypass" in str(lms.get("note") or "")

# Golden B — missing mapping: blank workflow code via synthetic probe using options workflows
options = req("GET", "/admin/live-readiness/product-configuration/options")
workflows = options.get("workflows") or []
blank = next((w for w in workflows if not (w.get("lmsProductCode") or "").strip()), None)
if blank:
    compose_b = req("POST", "/admin/live-readiness/product-configuration/compose", {
        "borrowerType": blank.get("borrowerType") or "INDIVIDUAL",
        "loanProduct": blank.get("loanProduct") or "PERSONAL_LOAN",
        "intakeSegment": blank.get("intakeSegment") or "BORROWER",
        "workflowId": blank.get("id"),
        "amount": 100000,
    })
    lms_b = (compose_b.get("compose") or {}).get("lms") or {}
    open_b = lms_b.get("openLoanAccountResolution") or {}
    print("GOLDEN_B_PC", json.dumps({
        "workflowId": blank.get("id"),
        "status": lms_b.get("status"),
        "openStatus": open_b.get("status"),
        "reason": open_b.get("reason"),
        "match": lms_b.get("productConfigMatchesOpenLoanAccount"),
    }, indent=2))
    assert lms_b.get("status") == "Missing"
    assert open_b.get("reason") == "LMS_PRODUCT_MAPPING_MISSING"
else:
    print("GOLDEN_B_SKIP no blank-lms workflow in catalogue — unit tests cover fail-closed")

print("STAGING_LMS_PRODUCT_MAPPING_P0_OK")
print("ENCORE_NOT_INVOKED_BY_THIS_SCRIPT=YES")
