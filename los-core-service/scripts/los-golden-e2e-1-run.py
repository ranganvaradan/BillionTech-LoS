#!/usr/bin/env python3
"""
LOS-GOLDEN-E2E-1 — controlled live-runtime goldens (COMPANY / TERM_LOAN).
Uses MANUAL_AUTHORISED credit inputs. Does not call production providers.
Sanction LMS open hits staging Encore SANDBOX only if encore active — we stop before disburse.
"""
import json, os, time, urllib.error, urllib.request
from copy import deepcopy

ENV = open("/opt/billiontech/apps/billiontechlos/.env").read()
TOKEN = ENV.split("CREDIT_INTELLIGENCE_INTERNAL_TOKEN=")[1].splitlines()[0].strip()
BASE = "http://127.0.0.1:8083/api/v1"
H = {
    "Content-Type": "application/json",
    "X-Internal-Token": TOKEN,
    # ADMIN satisfies create + manual credit; CREDIT_MANAGER listed for multi-role headers
    "X-User-Role": "ADMIN",
    "X-User-Roles": "ADMIN,CREDIT_MANAGER,ADMINISTRATOR,RELATIONSHIP_MANAGER",
    "X-User-Id": "c1000000-0000-0000-0000-000000000001",
    "X-User-Name": "e2e_admin",
}
OUT = "/tmp/los-golden-e2e-1"
os.makedirs(OUT, exist_ok=True)
report = {
    "gate": "LOS-GOLDEN-E2E-1",
    "allowCanonicalAuthority": False,
    "goldens": {},
    "defects": [],
    "pass": {},
    "providerSafety": {},
    "encoreCalls": [],
}

def req(method, path, body=None, timeout=90):
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

def save(name, obj):
    with open(f"{OUT}/{name}.json", "w") as f:
        json.dump(obj, f, indent=2, default=str)

# ── Product Config golden (runtime authority) ──
code, pc = req("GET", "/admin/live-readiness/product-configuration/golden")
save("product_config_golden", pc)
compose = pc.get("compose") or {}
wf = compose.get("workflow") or {}
rs = compose.get("liveRuleSet") or {}
sc = compose.get("scorecard") or {}
lms = compose.get("lms") or {}
assert code == 200, pc
assert pc.get("allowCanonicalAuthority") is False
assert lms.get("productConfigMatchesOpenLoanAccount") is True
assert lms.get("lmsProductCode") == "IPPOPAYM01"
config_snap = {
    "borrowerType": "COMPANY",
    "loanProduct": "TERM_LOAN",
    "intakeSegment": "BORROWER",
    "amount": 250000,
    "workflowId": wf.get("id"),
    "workflowName": wf.get("name"),
    "workflowVersion": wf.get("version"),
    "ruleSetId": rs.get("id"),
    "ruleSetName": rs.get("name"),
    "ruleSetPriority": rs.get("priority"),
    "scorecardId": sc.get("id"),
    "scorecardName": sc.get("name"),
    "scorecardVersion": sc.get("version"),
    "scorecardStatus": sc.get("status"),
    "lmsProductCode": lms.get("lmsProductCode"),
    "lmsMappingSource": (lms.get("openLoanAccountResolution") or {}).get("mappingSource"),
    "plp": compose.get("plp"),
    "productConfigMatchesRuntime": pc.get("productConfigMatchesRuntime"),
}
report["resolvedConfiguration"] = config_snap
save("config_snapshot", config_snap)

WF_ID = wf.get("id")
uniq = str(int(time.time()))[-7:]

def create_app(tag, amount=250000, bureau=None, income=None, obligation=None,
               bank=None, kyc="PASS", include_manual=True, lms_code=None, workflow_id=None):
    pan = f"AAAAA{uniq[:4]}{tag[:1].upper()}Z"[:10]
    # Ensure PAN format AAAAA9999A
    pan = f"E2ETL{uniq[:4]}{ord(tag[0])%10}Z"
    if len(pan) != 10:
        pan = f"E2ETL{uniq}Z"[:10]
        if len(pan) < 10:
            pan = (pan + "XXXXXX")[:9] + "Z"
    # Valid-ish PAN: 5 letters, 4 digits, 1 letter
    pan = f"AAAAA{uniq[:4]}Z"
    body = {
        "borrowerType": "COMPANY",
        "loanProduct": "TERM_LOAN",
        "intakeSegment": "BORROWER",
        "requestedAmount": amount,
        "tenureMonths": 24,
        "workflowId": workflow_id or WF_ID,
        "personalInfo": {
            "fullName": f"E2E Golden {tag}",
            "email": f"e2e.{tag}.{uniq}@example.test",
            "mobile": f"90000{uniq[:5]}",
            "pan": pan,
            "state": "KA",
            "city": "Bengaluru",
        },
        "businessInfo": {
            "businessName": f"E2E {tag} Traders Pvt Ltd",
            "gstin": f"29AAAAA{uniq[:4]}Z1Z5"[:15],
            "state": "KA",
            "businessVintageMonths": 36,
        },
        "financialInfo": {},
    }
    if lms_code is not None:
        body["lmsProductCode"] = lms_code
    code, app = req("POST", "/applications", body)
    if code >= 400:
        return code, app, None
    app_id = app.get("id") or app.get("applicationId")
    if include_manual:
        manual = {
            "manualBureauScore": bureau,
            "manualBureauRemarks": f"LOS-GOLDEN-E2E-1 {tag} MANUAL_AUTHORISED",
            "monthlyIncome": income,
            "monthlyObligation": obligation,
            "averageBankBalance": bank,
            "avgDailyBalance3m": bank,
            "businessVintageMonths": 36,
            "manualKycOutcome": kyc,
            "state": "KA",
            "city": "Bengaluru",
            "decisionSources": {
                "bureauScoreSource": "MANUAL",
                "incomeSource": "MANUAL",
                "kycSource": "MANUAL",
            },
        }
        # drop Nones
        manual = {k: v for k, v in manual.items() if v is not None}
        if "decisionSources" in manual:
            pass
        c2, app2 = req("POST", f"/applications/{app_id}/manual-credit-inputs", manual)
        if c2 >= 400:
            return c2, app2, app_id
        app = app2
    return code, app, app_id

def underwrite(app_id):
    return req("POST", f"/flow/{app_id}/underwrite")

def extract_uw(resp):
    return {
        "httpHints": None,
        "creditDecision": resp.get("creditDecision") or resp.get("decision") or resp.get("aggregateCreditDecision"),
        "status": resp.get("status") or (resp.get("application") or {}).get("status"),
        "underwritingSource": resp.get("underwritingSource"),
        "policyRecommendation": resp.get("policyRecommendation"),
        "reasons": resp.get("reasons") or resp.get("aggregateReasons") or resp.get("reason"),
        "scorecardId": resp.get("scorecardId"),
        "matchedRuleId": resp.get("matchedRuleId"),
        "matchedRuleName": resp.get("matchedRuleName"),
        "riskScore": resp.get("creditRiskScore") or resp.get("riskScore"),
        "parameterResults": resp.get("parameterResults") or resp.get("scorecardParameterResults"),
        "perRule": resp.get("perRule") or resp.get("ruleResults"),
        "evidence": resp.get("scorecardEvidence") or resp.get("evidence"),
        "rawKeys": sorted(list(resp.keys()))[:40],
    }

# ═══════════════════════════════════════════════
# GOLDEN A — APPROVE
# ═══════════════════════════════════════════════
print("=== GOLDEN A APPROVE ===")
# Scorecard math target: bureau 760→40, income 80000→25, foir 20/80=25%→20, bank 25000→10, kyc→10 = 105/105 = 100%
code, app, aid = create_app("A", bureau=760, income=80000, obligation=20000, bank=25000, kyc="PASS")
save("golden_a_create", {"http": code, "app": app})
assert code < 400 and aid, app
code_u, uw = underwrite(aid)
save("golden_a_underwrite", uw)
ua = extract_uw(uw)
print("A decision", ua.get("creditDecision"), "status", ua.get("status"), "source", ua.get("underwritingSource"))
report["goldens"]["A"] = {"appId": aid, "createHttp": code, "underwriteHttp": code_u, "extract": ua, "error": uw if code_u >= 400 else None}

# If APPROVED / CAM path
if code_u < 400 and str(ua.get("creditDecision") or "").upper() in ("APPROVED", "APPROVE"):
    # ensure CAM
    c_cam, cam = req("POST", f"/flow/{aid}/cam/reviewed")
    save("golden_a_cam", cam)
    report["goldens"]["A"]["cam"] = {"http": c_cam, "status": cam.get("status"), "creditDecision": cam.get("creditDecision")}
    # Sanction — may call Encore SANDBOX; do not disburse
    c_sanc, sanc = req("POST", f"/flow/{aid}/sanction", {"interestRate": 18, "tenureMonths": 24})
    save("golden_a_sanction", sanc)
    report["goldens"]["A"]["sanction"] = {
        "http": c_sanc,
        "status": sanc.get("status") or (sanc.get("application") or {}).get("status"),
        "lmsReferenceId": sanc.get("lmsReferenceId") or sanc.get("lmsAccountId"),
        "skipLms": sanc.get("skipLms"),
        "reason": sanc.get("reason"),
        "message": str(sanc.get("message") or sanc.get("error") or "")[:300],
        "keys": sorted(list(sanc.keys()))[:30] if isinstance(sanc, dict) else [],
    }
    print("A CAM", c_cam, "SANCTION", c_sanc, report["goldens"]["A"]["sanction"].get("status"))
elif code_u < 400 and str(ua.get("creditDecision") or "").upper() == "MANUAL_REVIEW":
    # Unexpected for A — try approve path note
    report["defects"].append({"sev": "P1", "area": "A", "msg": "Expected APPROVE got MANUAL_REVIEW", "extract": ua})
    c_ap, ap = req("POST", f"/flow/{aid}/underwriting/approve")
    report["goldens"]["A"]["manualApproveFallback"] = {"http": c_ap, "status": ap.get("status")}
else:
    report["defects"].append({"sev": "P0", "area": "A", "msg": f"underwrite failed http={code_u}", "body": str(uw)[:500]})

# ═══════════════════════════════════════════════
# GOLDEN B — MANUAL REVIEW (mid scorecard band)
# ═══════════════════════════════════════════════
print("=== GOLDEN B MANUAL_REVIEW ===")
# bureau 680→25, income 30000→15, obl 15000/30000=50%→10, bank 25000→10, kyc→10 = 70/105≈67% → MANUAL
code, app, bid = create_app("B", bureau=680, income=30000, obligation=15000, bank=25000, kyc="PASS")
code_u, uw = underwrite(bid)
save("golden_b_underwrite", uw)
ub = extract_uw(uw)
print("B decision", ub.get("creditDecision"), ub.get("status"))
report["goldens"]["B"] = {"appId": bid, "underwriteHttp": code_u, "extract": ub}
if str(ub.get("creditDecision") or "").upper() == "MANUAL_REVIEW":
    # prove no sanction yet
    c_sanc, sanc = req("POST", f"/flow/{bid}/sanction", {})
    report["goldens"]["B"]["sanctionBlocked"] = {"http": c_sanc, "reason": sanc.get("reason"), "msg": str(sanc.get("message") or "")[:200]}
    # approve manually
    c_ap, ap = req("POST", f"/flow/{bid}/underwriting/approve")
    report["goldens"]["B"]["manualApprove"] = {"http": c_ap, "status": ap.get("status"), "creditDecision": ap.get("creditDecision")}
    print("B sanctionBlocked", c_sanc, "manualApprove", c_ap, ap.get("status"))
else:
    report["defects"].append({"sev": "P1", "area": "B", "msg": f"expected MANUAL_REVIEW got {ub.get('creditDecision')}", "extract": ub})

# ═══════════════════════════════════════════════
# GOLDEN C — HARD REJECT (bureau < 600)
# ═══════════════════════════════════════════════
print("=== GOLDEN C HARD REJECT ===")
code, app, cid = create_app("C", bureau=550, income=80000, obligation=20000, bank=25000, kyc="PASS")
code_u, uw = underwrite(cid)
save("golden_c_underwrite", uw)
uc = extract_uw(uw)
print("C decision", uc.get("creditDecision"), uc.get("status"))
report["goldens"]["C"] = {"appId": cid, "underwriteHttp": code_u, "extract": uc}
# CAM/sanction should fail
c_cam, cam = req("POST", f"/flow/{cid}/cam/reviewed")
c_sanc, sanc = req("POST", f"/flow/{cid}/sanction", {})
report["goldens"]["C"]["camAttempt"] = {"http": c_cam, "reason": cam.get("reason")}
report["goldens"]["C"]["sanctionAttempt"] = {"http": c_sanc, "reason": sanc.get("reason")}

# ═══════════════════════════════════════════════
# GOLDEN D — MISSING REQUIRED BUREAU
# ═══════════════════════════════════════════════
print("=== GOLDEN D MISSING DATA ===")
code, app, did = create_app("D", bureau=None, income=80000, obligation=20000, bank=25000, kyc="PASS", include_manual=True)
# Explicitly clear bureau by not setting — also try underwrite
# If gap/demo fills bureau, capture provenance
code_u, uw = underwrite(did)
save("golden_d_underwrite", uw)
ud = extract_uw(uw)
print("D http", code_u, "decision", ud.get("creditDecision"), "reason", ud.get("reasons") or uw.get("reason"))
report["goldens"]["D"] = {
    "appId": did,
    "underwriteHttp": code_u,
    "reason": uw.get("reason"),
    "message": str(uw.get("message") or "")[:300],
    "extract": ud,
}

# ═══════════════════════════════════════════════
# GOLDEN E — LMS missing mapping (fail closed)
# ═══════════════════════════════════════════════
print("=== GOLDEN E LMS MISSING ===")
# Create workflow without LMS code via API if possible
c_wf, wf_body = req("POST", "/admin/workflows", {
    "name": f"E2E NoLMS {uniq}",
    "borrowerType": "COMPANY",
    "loanProduct": "TERM_LOAN",
    "intakeSegment": "BORROWER",
    "lmsProductCode": "",
    "lmsTenureUnit": "Month",
    "steps": [{"type": "MOBILE_OTP"}],
})
# alternate path
if c_wf >= 400:
    c_wf, wf_body = req("POST", "/workflows", {
        "name": f"E2E NoLMS {uniq}",
        "borrowerType": "COMPANY",
        "loanProduct": "TERM_LOAN",
        "intakeSegment": "BORROWER",
        "lmsProductCode": "",
        "steps": [{"type": "MOBILE_OTP"}],
    })
save("golden_e_workflow", {"http": c_wf, "body": wf_body})
nolms_wf = (wf_body.get("id") if isinstance(wf_body, dict) else None)
report["goldens"]["E"] = {"workflowCreateHttp": c_wf, "workflowId": nolms_wf}

# Unit-level already covers resolver; for runtime, create app with blank lms + blank-binding if we have workflow
if nolms_wf:
    # activate? may need activate endpoint — try bind app to it even if inactive via workflowId
    code, app, eid = create_app("E", bureau=760, income=80000, obligation=20000, bank=25000,
                                lms_code="", workflow_id=nolms_wf)
    # Force blank app lms
    report["goldens"]["E"]["appId"] = eid
    # Simulate LMS resolve by underwrite→approve→cam→sanction and expect LMS_PRODUCT_MAPPING_MISSING
    if eid:
        underwrite(eid)
        # if MR or approve
        req("POST", f"/flow/{eid}/underwriting/approve")
        req("POST", f"/flow/{eid}/cam/reviewed")
        c_sanc, sanc = req("POST", f"/flow/{eid}/sanction", {"interestRate": 18, "tenureMonths": 24})
        report["goldens"]["E"]["sanction"] = {
            "http": c_sanc,
            "reason": sanc.get("reason"),
            "message": str(sanc.get("message") or "")[:300],
            "lmsReferenceId": sanc.get("lmsReferenceId"),
        }
        print("E sanction", c_sanc, sanc.get("reason"))
else:
    report["goldens"]["E"]["note"] = "workflow create failed — LMS missing covered by LmsApplicationConfigResolverTest + prior P0 gate"

# ═══════════════════════════════════════════════
# DEMO/GAP safety probe (staging allows demo scoring but marks non-authoritative)
# ═══════════════════════════════════════════════
print("=== GOLDEN GAP/DEMO SAFETY ===")
code, app, gid = create_app("G", bureau=760, income=None, obligation=None, bank=None, kyc="PASS")
code_u, uw = underwrite(gid)
save("golden_gap_underwrite", uw)
ug = extract_uw(uw)
ev = ug.get("evidence") or {}
params = ug.get("parameterResults") or []
non_auth = [p for p in params if isinstance(p, dict) and (
    str(p.get("valueProvenance") or "").upper() in ("GAP_DEFAULT", "DEMO_DEFAULT")
    or p.get("authoritativeForDecision") is False)]
report["goldens"]["GAP"] = {
    "appId": gid,
    "underwriteHttp": code_u,
    "decision": ug.get("creditDecision"),
    "evidenceAuthority": ev.get("evaluationAuthority") if isinstance(ev, dict) else None,
    "authoritativeForDecision": ev.get("authoritativeForDecision") if isinstance(ev, dict) else None,
    "nonAuthFactorCount": len(non_auth),
    "sampleNonAuth": non_auth[:5],
    "extract": ug,
}
print("GAP decision", ug.get("creditDecision"), "auth", report["goldens"]["GAP"].get("authoritativeForDecision"),
      "nonAuthFactors", len(non_auth))

# Provider safety classification
report["providerSafety"] = {
    "KYC": "BYPASSED_VIA_MANUAL_AUTHORISED (no provider call in these goldens)",
    "BUREAU": "BYPASSED_VIA_MANUAL_AUTHORISED (no Equifax call)",
    "ENCORE": "SANDBOX (credinnov-sandbox.senseitech.com) — sanction may call openAccount; disburse not invoked",
    "PLP": "NOT_REQUIRED / disabled for TERM_LOAN golden",
    "PRODUCTION_CALLED": False,
}

# Pass flags
A = report["goldens"].get("A", {})
B = report["goldens"].get("B", {})
C = report["goldens"].get("C", {})
D = report["goldens"].get("D", {})
GAP = report["goldens"].get("GAP", {})
Ae = (A.get("extract") or {})
Be = (B.get("extract") or {})
Ce = (C.get("extract") or {})

report["pass"]["A_APPROVE"] = str(Ae.get("creditDecision") or "").upper() in ("APPROVED", "APPROVE") or (
    (A.get("cam") or {}).get("status") in ("CAM_READY", "CAM_REVIEWED", "KFS_GENERATED", "SANCTIONED")
) or str((A.get("sanction") or {}).get("status") or "").upper() in ("KFS_GENERATED", "SANCTIONED", "CAM_REVIEWED")
# refine A: underwrite APPROVED OR CAM path reached after approve
if not report["pass"]["A_APPROVE"]:
    report["pass"]["A_APPROVE"] = str(Ae.get("creditDecision") or "").upper() == "APPROVED"

report["pass"]["B_MANUAL_REVIEW"] = str(Be.get("creditDecision") or "").upper() == "MANUAL_REVIEW"
report["pass"]["C_HARD_REJECT"] = str(Ce.get("creditDecision") or "").upper() in ("REJECTED", "REJECT") or str(Ce.get("status") or "").upper() == "REJECTED"
report["pass"]["D_MISSING"] = (
    D.get("underwriteHttp", 500) >= 400
    and str(D.get("reason") or "").upper() in (
        "BUREAU_SCORE_MISSING_OR_NON_POSITIVE", "KYC_OUTCOME_NOT_PASS", "DATA_INSUFFICIENT")
) or "DATA_INSUFFICIENT" in str(D.get("extract") or {}).upper() or "MISSING" in str(D.get("reason") or "").upper()
report["pass"]["GAP_MARKED_NON_AUTH"] = (
    GAP.get("authoritativeForDecision") is False
    or GAP.get("evidenceAuthority") == "DEMO_NON_PRODUCTION"
    or GAP.get("nonAuthFactorCount", 0) > 0
    or str(GAP.get("decision") or "").upper() == "MANUAL_REVIEW"
)
report["pass"]["PC_MATCHES"] = config_snap.get("productConfigMatchesRuntime") is not False and lms.get("productConfigMatchesOpenLoanAccount") is True
report["pass"]["LMS_CONFIG"] = lms.get("lmsProductCode") == "IPPOPAYM01" and lms.get("mappingSource") == "WORKFLOW_LMS_PRODUCT_CODE"
E = report["goldens"].get("E", {})
report["pass"]["E_LMS_MISSING"] = (E.get("sanction") or {}).get("reason") == "LMS_PRODUCT_MAPPING_MISSING"

save("los_golden_e2e_1_report", report)
print(json.dumps({"pass": report["pass"], "defects": report["defects"],
                  "A": Ae.get("creditDecision"), "B": Be.get("creditDecision"),
                  "C": Ce.get("creditDecision"), "D": D.get("reason"),
                  "GAP": GAP.get("decision"), "E": (E.get("sanction") or {}).get("reason"),
                  "config": config_snap}, indent=2, default=str))
print("REPORT", f"{OUT}/los_golden_e2e_1_report.json")
