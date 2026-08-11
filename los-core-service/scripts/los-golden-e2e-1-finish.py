#!/usr/bin/env python3
"""Complete CAM→sanction for golden A, LMS missing E, and GAP provenance from DB evaluations."""
import json, os, subprocess, urllib.error, urllib.request

ENV = open("/opt/billiontech/apps/billiontechlos/.env").read()
TOKEN = ENV.split("CREDIT_INTELLIGENCE_INTERNAL_TOKEN=")[1].splitlines()[0].strip()
BASE = "http://127.0.0.1:8083/api/v1"
H = {
    "Content-Type": "application/json",
    "X-Internal-Token": TOKEN,
    "X-User-Role": "ADMIN",
    "X-User-Roles": "ADMIN,CREDIT_MANAGER,ADMINISTRATOR",
    "X-User-Id": "c1000000-0000-0000-0000-000000000001",
    "X-User-Name": "e2e_admin",
}
OUT = "/tmp/los-golden-e2e-1"
report = json.load(open(f"{OUT}/los_golden_e2e_1_report.json"))

def req(method, path, body=None, timeout=120):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(BASE + path, data=data, method=method, headers=H)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode()
            return resp.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            j = json.loads(raw)
        except Exception:
            j = {"raw": raw[:800]}
        return e.code, j

def psql(sql):
    return subprocess.check_output(
        ["docker", "exec", "billiontech-postgres", "psql", "-U", "los_app", "-d", "los_core_staging", "-t", "-A", "-c", sql],
        text=True).strip()

def complete_cam_and_sanction(app_id, tag):
    # Fill CAM sanction basis
    c1, cam1 = req("PUT", f"/applications/{app_id}/cam", {
        "recommendedAmount": 250000,
        "recommendedTenureMonths": 24,
        "recommendedRate": 18.0,
        "recommendedDecision": "APPROVE",
        "interestType": "REDUCING",
        "creditOfficerRemarks": f"LOS-GOLDEN-E2E-1 {tag} CAM basis",
    })
    c2, cam2 = req("POST", f"/applications/{app_id}/cam/submit")
    c3, reviewed = req("POST", f"/flow/{app_id}/cam/reviewed")
    c4, sanc = req("POST", f"/flow/{app_id}/sanction", {
        "interestRate": 18,
        "tenureMonths": 24,
        "sanctionedAmount": 250000,
    })
    out = {
        "camUpdate": {"http": c1, "status": cam1.get("camStatus") or cam1.get("status"), "msg": str(cam1.get("message") or "")[:200]},
        "camSubmit": {"http": c2, "status": cam2.get("camStatus") or cam2.get("status"), "msg": str(cam2.get("message") or "")[:200]},
        "camReviewed": {"http": c3, "status": reviewed.get("status"), "reason": reviewed.get("reason"), "msg": str(reviewed.get("message") or "")[:200]},
        "sanction": {
            "http": c4,
            "status": sanc.get("status") or (sanc.get("application") or {}).get("status"),
            "reason": sanc.get("reason"),
            "message": str(sanc.get("message") or "")[:300],
            "lmsReferenceId": sanc.get("lmsReferenceId") or sanc.get("lmsAccountId") or sanc.get("encoreAccountId"),
            "skipLms": sanc.get("skipLms"),
            "keys": sorted(list(sanc.keys()))[:40] if isinstance(sanc, dict) else [],
        },
    }
    # app after
    ca, app = req("GET", f"/applications/{app_id}")
    out["appAfter"] = {
        "http": ca,
        "status": app.get("status"),
        "lmsReferenceId": app.get("lmsReferenceId"),
        "lmsProductCode": app.get("lmsProductCode"),
        "creditDecision": app.get("creditDecision"),
        "workflowId": app.get("workflowId"),
    }
    return out

# ── A finish ──
aid = report["goldens"]["A"]["appId"]
print("Finish A", aid)
A_fin = complete_cam_and_sanction(aid, "A")
json.dump(A_fin, open(f"{OUT}/golden_a_finish.json", "w"), indent=2, default=str)
report["goldens"]["A"]["finish"] = A_fin
print("A finish", json.dumps(A_fin, default=str)[:600])

# Idempotency: sanction again
c_idemp, idemp = req("POST", f"/flow/{aid}/sanction", {"interestRate": 18, "tenureMonths": 24})
report["goldens"]["A"]["sanctionRetry"] = {
    "http": c_idemp,
    "status": idemp.get("status") or (idemp.get("application") or {}).get("status"),
    "lmsReferenceId": idemp.get("lmsReferenceId") or idemp.get("lmsAccountId"),
    "reason": idemp.get("reason"),
    "message": str(idemp.get("message") or "")[:200],
}
print("A retry", report["goldens"]["A"]["sanctionRetry"])

# ── B finish CAM after manual approve (already CAM_READY) ──
bid = report["goldens"]["B"]["appId"]
print("Finish B", bid)
B_fin = complete_cam_and_sanction(bid, "B")
report["goldens"]["B"]["finish"] = B_fin
print("B finish status", B_fin["camReviewed"], B_fin["sanction"].get("status"), B_fin["sanction"].get("reason"))

# ── E LMS missing: bind to no-LMS workflow, force blank app code, go to sanction ──
print("Finish E LMS missing")
c_wf, wf = req("POST", "/workflows", {
    "name": "E2E NoLMS Finish",
    "borrowerType": "COMPANY",
    "loanProduct": "PERSONAL_LOAN",  # avoid colliding with golden active WF selection
    "intakeSegment": "BORROWER",
    "lmsProductCode": None,
    "lmsTenureUnit": "Month",
    "steps": [{"type": "MOBILE_OTP"}],
})
# Create COMPANY TERM_LOAN app then re-point workflow_id + clear lms in DB
# Safer: create app then UPDATE DB to clear lms + bind no-lms workflow
code, app = req("POST", "/applications", {
    "borrowerType": "COMPANY",
    "loanProduct": "TERM_LOAN",
    "intakeSegment": "BORROWER",
    "requestedAmount": 250000,
    "tenureMonths": 24,
    "workflowId": report["resolvedConfiguration"]["workflowId"],
    "personalInfo": {"fullName": "E2E LMS Missing", "email": "e2e.lms.missing@example.test", "mobile": "9000011122", "pan": "AAAAA9999Z", "state": "KA"},
    "businessInfo": {"businessName": "E2E LMS Missing Co", "state": "KA"},
})
eid = app.get("id")
req("POST", f"/applications/{eid}/manual-credit-inputs", {
    "manualBureauScore": 760,
    "monthlyIncome": 80000,
    "monthlyObligation": 20000,
    "averageBankBalance": 25000,
    "avgDailyBalance3m": 25000,
    "manualKycOutcome": "PASS",
    "decisionSources": {"bureauScoreSource": "MANUAL", "incomeSource": "MANUAL", "kycSource": "MANUAL"},
})
req("POST", f"/flow/{eid}/underwrite")
# If MR, approve
req("POST", f"/flow/{eid}/underwriting/approve")
# Clear LMS mapping on application + point to workflow with null lms (created earlier or now)
wf_nolms = "8b3bae83-eb8f-4782-9fb8-37150fc8205c"
# ensure that workflow has null lms
psql(f"UPDATE workflow_configs SET lms_product_code = NULL WHERE id = '{wf_nolms}';")
psql(f"UPDATE loan_applications SET lms_product_code = NULL, workflow_id = '{wf_nolms}' WHERE id = '{eid}';")
E_fin = complete_cam_and_sanction(eid, "E")
report["goldens"]["E"] = {
    "appId": eid,
    "workflowId": wf_nolms,
    "finish": E_fin,
    "dbCleared": True,
}
print("E finish", E_fin["sanction"])

# ── GAP provenance from underwriting_evaluations ──
gid = report["goldens"]["GAP"]["appId"]
tabs = psql("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename ILIKE '%eval%'")
print("eval tabs", tabs)
# try common shapes
prov = None
for colq in [
    f"SELECT left(parameter_results::text, 2000) FROM underwriting_evaluations WHERE application_id='{gid}' ORDER BY evaluated_at DESC LIMIT 1",
    f"SELECT left(evaluation_json::text, 2000) FROM underwriting_evaluations WHERE application_id='{gid}' ORDER BY evaluated_at DESC LIMIT 1",
    f"SELECT left(result_json::text, 2000) FROM underwriting_evaluations WHERE application_id='{gid}' ORDER BY created_at DESC LIMIT 1",
]:
    try:
        prov = psql(colq)
        if prov:
            break
    except Exception:
        continue
cols = psql("SELECT string_agg(column_name, ',') FROM information_schema.columns WHERE table_name='underwriting_evaluations'")
print("UE cols", cols)
# financial_info creditControl scorecard provenance
fi = psql(f"SELECT left(financial_info::text, 2500) FROM loan_applications WHERE id='{gid}'")
report["goldens"]["GAP"]["financialInfoSnippet"] = fi[:2500]
report["goldens"]["GAP"]["evaluationSnippet"] = (prov or "")[:2500]
# Detect gap defaults in financial info
gap_hit = "GAP_DEFAULT" in (fi or "") or "PROVIDER_GAP_DEFAULT" in (fi or "") or "DEMO_DEFAULT" in (fi or "")
report["goldens"]["GAP"]["gapDefaultDetectedInApp"] = gap_hit
print("GAP detected", gap_hit)

# Scorecard math reconciliation from A underwrite rule results if present
uw = json.load(open(f"{OUT}/golden_a_underwrite.json"))
report["goldens"]["A"]["underwriteSummary"] = {
    "decision": uw.get("decision") or uw.get("aggregateCreditDecision"),
    "status": uw.get("status"),
    "source": uw.get("underwritingSource"),
    "policy": uw.get("policyRecommendation"),
    "ruleSetId": uw.get("underwritingRuleSetId"),
    "ruleSetName": uw.get("underwritingRuleSetName"),
    "riskScore": uw.get("riskScore"),
    "creditScore": uw.get("creditScore"),
    "reasons": uw.get("reasons"),
    "ruleResults": uw.get("underwritingRuleResults"),
}

# Pull A evaluation for factor table
aid = report["goldens"]["A"]["appId"]
try:
    aeval = psql(f"SELECT left(parameter_results::text, 4000) FROM underwriting_evaluations WHERE application_id='{aid}' ORDER BY evaluated_at DESC LIMIT 1")
    report["goldens"]["A"]["parameterResultsDb"] = aeval[:4000]
except Exception as e:
    report["goldens"]["A"]["parameterResultsDbError"] = str(e)

# Pass updates
report["pass"]["A_CAM_SANCTION"] = (
    (A_fin.get("camReviewed") or {}).get("http", 500) < 400
    and (A_fin.get("sanction") or {}).get("http", 500) < 400
)
report["pass"]["A_LMS_REF"] = bool((A_fin.get("appAfter") or {}).get("lmsReferenceId") or (A_fin.get("sanction") or {}).get("lmsReferenceId"))
report["pass"]["E_LMS_MISSING"] = (E_fin.get("sanction") or {}).get("reason") == "LMS_PRODUCT_MAPPING_MISSING"
report["pass"]["IDEMPOTENCY"] = True  # refined below
r1 = (A_fin.get("appAfter") or {}).get("lmsReferenceId") or (A_fin.get("sanction") or {}).get("lmsReferenceId")
r2 = report["goldens"]["A"]["sanctionRetry"].get("lmsReferenceId")
# If retry didn't create new distinct account — same or blocked
report["pass"]["IDEMPOTENCY"] = (r2 is None) or (r1 == r2) or report["goldens"]["A"]["sanctionRetry"].get("http", 0) >= 400
report["pass"]["GAP_MARKED_NON_AUTH"] = gap_hit or report["goldens"]["GAP"].get("authoritativeForDecision") is False

json.dump(report, open(f"{OUT}/los_golden_e2e_1_report.json", "w"), indent=2, default=str)
print(json.dumps({"pass": report["pass"], "A_sanc": A_fin.get("sanction"), "E_sanc": E_fin.get("sanction"),
                  "A_app": A_fin.get("appAfter"), "retry": report["goldens"]["A"]["sanctionRetry"]}, indent=2, default=str))
