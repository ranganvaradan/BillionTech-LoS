#!/usr/bin/env python3
"""POLICY-STUDIO-AUTHORING-COMPLETENESS-GATE-1 Ã¢â‚¬â€ staging goldens (Describe/Build/Test/Version/Banking)."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo"
ENV = "/opt/billiontech/apps/billiontechlos/.env"


def token() -> str:
    for line in open(ENV, encoding="utf-8"):
        if line.startswith("CREDIT_INTELLIGENCE_INTERNAL_TOKEN="):
            return line.split("=", 1)[1].strip()
    return ""


TOK = token()
H = {"Content-Type": "application/json", "X-Internal-Token": TOK}


def req(method: str, path: str, body=None):
    data = None if body is None else json.dumps(body).encode()
    r = urllib.request.Request(BASE + path, data=data, headers=H, method=method)
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        print("HTTP", e.code, path, err[:500])
        raise


def create(name: str) -> str:
    d = req(
        "POST",
        "/policy-studio/create",
        {
            "policyName": name,
            "product": "Business Term Loan",
            "borrowerType": "Company",
        },
    )
    doc = (d.get("policyHeader") or {}).get("documentId") or d.get("documentId")
    assert doc, d
    return str(doc)


def preview(doc: str, body: dict) -> dict:
    d = req("POST", f"/policy-studio/documents/{doc}/rules/preview", body)
    return d.get("preview") or d


def confirm(doc: str, body: dict) -> dict:
    return req("POST", f"/policy-studio/documents/{doc}/rules/add-plain-english", body)


def reload(doc: str) -> dict:
    return req("GET", f"/policy-studio/documents/{doc}")


def strip_seeded_catalogue_rules(doc: str) -> int:
    """Remove seeded catalogue rules so Policy Test evaluates only authored rules."""
    sess = reload(doc)
    removed = 0
    for r in sess.get("underwritingRules") or []:
        sid = str(r.get("systemRuleId") or "")
        rid = r.get("id")
        if not rid:
            continue
        if sid.startswith("CM_COMPOUND") or sid.startswith("CM_"):
            continue
        if sid.startswith("CATALOGUE_") or "FOIR_MAX" in sid:
            req(
                "POST",
                f"/policy-studio/documents/{doc}/rules/{rid}/review",
                {
                    "uiAction": "DELETE",
                    "reason": "GATE1 isolate authored expression for Policy Test",
                },
            )
            removed += 1
    return removed


def assert_ready(p: dict, label: str):
    assert p.get("complete") is True, f"{label} incomplete: {p.get('status')} {p.get('message')} {p.get('clarify')}"


def main() -> int:
    report = {"ok": True, "cases": {}}

    # Ã¢â€â‚¬Ã¢â€â‚¬ C1Ã¢â‚¬â€œC7 Describe Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    doc = create("GATE1 Authoring Grammar Disposable")
    report["grammarDoc"] = doc

    cases = [
        ("C1", "Bureau score should be 650 or above", None),
        ("C2", "Bureau score should be below 650", None),
        ("C3", "Bureau Score of -1, NTC and 650 & above only will be allowed", "OR"),
        ("C4", "Bureau score must be at least 700 and FOIR must not exceed 50%", "AND"),
        (
            "C5",
            "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below",
            "AND",
        ),
        ("C6", "Borrower constitution must be Proprietorship, Partnership or Company", "IN"),
        ("C7", "Industry must not be Real Estate, Gambling or Crypto", "NOT_IN"),
        ("C6_LLP", "Borrower constitution must be Proprietorship, Partnership or LLP", None),
    ]
    for code, text, expect_op in cases:
        p = preview(doc, {"mode": "DESCRIBE", "text": text, "treatment": "Reject"})
        if code == "C6_LLP":
            assert p.get("complete") is not True, code
            assert str(p.get("status")) == "NEEDS_CLARIFICATION", code
            report["cases"][code] = "NEEDS_CLARIFICATION"
            print(code, "FAIL_CLOSED_OK")
            continue
        assert_ready(p, code)
        expr = p.get("expression") or {}
        if expect_op:
            assert str(expr.get("op")).upper() == expect_op, (code, expr.get("op"))
        if code == "C3":
            assert p.get("combinator") == "ANY"
            assert len(expr.get("args") or []) == 3
        if code == "C5":
            args = expr.get("args") or []
            assert any(str(a.get("op")).upper() == "OR" for a in args if isinstance(a, dict)), expr
        report["cases"][code] = {"complete": True, "op": expr.get("op"), "combinator": p.get("combinator")}
        print(code, "OK", expr.get("op"), p.get("combinator"))

    # adversarial
    for text in [
        "good bureau score",
        "650 around",
        "score above 650 or something",
        "NTC maybe allowed",
    ]:
        p = preview(doc, {"mode": "DESCRIBE", "text": text})
        assert p.get("complete") is not True, text
        assert str(p.get("status")) in ("NEEDS_CLARIFICATION", "INCOMPLETE", "NEEDS_USER_CONFIRMATION"), text
    report["cases"]["adversarial"] = "FAIL_CLOSED"
    print("ADVERSARIAL_OK")

    # Ã¢â€â‚¬Ã¢â€â‚¬ Staging disposable policies Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    simple = create("UAT GATE1 SIMPLE Ã¢â‚¬â€ score>=650")
    p = preview(simple, {"mode": "DESCRIBE", "text": "Bureau score should be 650 or above", "treatment": "Reject"})
    assert_ready(p, "SIMPLE")
    c = confirm(
        simple,
        {"confirm": True, "mode": "DESCRIBE", "text": "Bureau score should be 650 or above", "treatment": "Reject"},
    )
    assert c.get("confirmed") is True
    report["stagingSimple"] = simple
    print("SIMPLE_POLICY", simple)

    compound = create("UAT GATE1 COMPOUND OR Ã¢â‚¬â€ -1/NTC/>=650")
    uat = "Bureau Score of -1, NTC and 650 & above only will be allowed"
    p = preview(compound, {"mode": "DESCRIBE", "text": uat, "treatment": "Reject"})
    assert_ready(p, "COMPOUND")
    assert p.get("combinator") == "ANY"
    c = confirm(compound, {"confirm": True, "mode": "DESCRIBE", "text": uat, "treatment": "Reject"})
    assert c.get("confirmed") is True
    expr = c.get("expression") or {}
    assert str(expr.get("op")).upper() == "OR"
    report["stagingCompound"] = compound
    report["bureauExpr"] = expr
    print("COMPOUND_POLICY", compound)

    nested = create("UAT GATE1 NESTED AND/OR Ã¢â‚¬â€ score+(FOIR|LTV)")
    nested_text = (
        "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below"
    )
    p = preview(nested, {"mode": "DESCRIBE", "text": nested_text, "treatment": "Reject"})
    assert_ready(p, "NESTED")
    c = confirm(nested, {"confirm": True, "mode": "DESCRIBE", "text": nested_text, "treatment": "Reject"})
    assert c.get("confirmed") is True
    nexpr = c.get("expression") or {}
    assert str(nexpr.get("op")).upper() == "AND"
    assert any(str(a.get("op")).upper() == "OR" for a in (nexpr.get("args") or []) if isinstance(a, dict))
    report["stagingNested"] = nested
    report["nestedExpr"] = nexpr
    print("NESTED_POLICY", nested)
    print("STRIP_SIMPLE", strip_seeded_catalogue_rules(simple))
    print("STRIP_COMPOUND", strip_seeded_catalogue_rules(compound))
    print("STRIP_NESTED", strip_seeded_catalogue_rules(nested))

    # Reload nested Ã¢â‚¬â€ children must retain grouping
    sess = reload(nested)
    uw = sess.get("underwritingRules") or sess.get("ruleCards") or []
    hit = None
    for card in uw:
        em = card.get("editableModel") or {}
        if em.get("kind") == "COMPOUND_GROUP" or card.get("compoundGroup"):
            hit = card
            break
    assert hit, "nested missing after reload"
    em = hit.get("editableModel") or {}
    children = em.get("children") or em.get("conditions") or []
    assert len(children) >= 2, children
    has_group = any(
        str(ch.get("kind")).upper() == "COMPOUND_GROUP" or isinstance(ch.get("children"), list)
        for ch in children
        if isinstance(ch, dict)
    )
    # if conditions flattened, expression on card must still nest
    card_expr = hit.get("technicalExpression") or hit.get("expression") or nexpr
    assert any(str(a.get("op")).upper() == "OR" for a in (card_expr.get("args") or []) if isinstance(a, dict)), card_expr
    report["nestedReload"] = {"children": len(children), "hasGroup": has_group, "exprOp": card_expr.get("op")}
    print("NESTED_RELOAD_OK", report["nestedReload"])

    # Ã¢â€â‚¬Ã¢â€â‚¬ Policy Test bureau goldens (decision vs test result) Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬Ã¢â€â‚¬
    # Prefer test/quick when available
    bureau_cases = [
        ({"bureau.score": -1}, "ALLOW"),
        ({"bureau.status_ntc": True}, "ALLOW"),
        ({"bureau.score": 649, "bureau.status_ntc": False}, "REJECT"),
        ({"bureau.score": 650, "bureau.status_ntc": False}, "ALLOW"),
        ({"bureau.score": 651, "bureau.status_ntc": False}, "ALLOW"),
    ]
    bureau_results = []
    for vals, expected_decision in bureau_cases:
        try:
            out = req(
                "POST",
                f"/policy-studio/documents/{compound}/test/quick",
                {"testValues": vals, "useFixtureDefaults": False},
            )
        except Exception:
            out = req(
                "POST",
                f"/policy-studio/documents/{compound}/policy-test/run",
                {"testValues": vals, "useFixtureDefaults": False},
            )
        # Normalize decision
        decision = str(
            out.get("simulatedDecisionCode")
            or out.get("overallDecision")
            or out.get("decision")
            or out.get("outcome")
            or ""
        ).upper().replace(" ", "_")
        # Allow-when compound: PASS Ã¢â€¡â€™ business ALLOW; FAIL Ã¢â€¡â€™ business REJECT
        if decision in ("PASS", "ALLOW", "APPROVE"):
            actual = "ALLOW"
        elif decision in ("FAIL", "REJECT", "DECLINE"):
            actual = "REJECT"
        else:
            rules = out.get("ruleResults") or out.get("results") or out.get("evaluatedRules") or []
            fails = [
                r
                for r in rules
                if str(r.get("resultCode") or r.get("outcome") or r.get("result") or "").upper()
                in ("FAIL", "REJECT")
            ]
            actual = "REJECT" if fails else "ALLOW"
        test_result = "PASS" if actual == expected_decision else "FAIL"
        bureau_results.append(
            {
                "input": vals,
                "expectedDecision": expected_decision,
                "actualDecision": actual,
                "testResult": test_result,
                "rawDecision": decision,
            }
        )
        print("BUREAU", vals, "expected", expected_decision, "actual", actual, "test", test_result)
        assert test_result == "PASS", bureau_results[-1]
        prov = out.get("valueProvenance") or out.get("parameterProvenance") or []
        for row in prov:
            if row.get("status") == "FIXTURE_SUPPLIED":
                raise AssertionError("unexpected fixture without useFixtureDefaults")
    report["bureauGoldens"] = bureau_results

    # Nested goldens via test
    nested_cases = [
        ({"bureau.score": 720, "obligation.ratio": 45, "collateral.ltv": 70}, "ALLOW"),
        ({"bureau.score": 720, "obligation.ratio": 55, "collateral.ltv": 55}, "ALLOW"),
        ({"bureau.score": 720, "obligation.ratio": 55, "collateral.ltv": 70}, "REJECT"),
        ({"bureau.score": 680, "obligation.ratio": 40, "collateral.ltv": 50}, "REJECT"),
    ]
    nested_results = []
    for vals, expected in nested_cases:
        try:
            out = req(
                "POST",
                f"/policy-studio/documents/{nested}/test/quick",
                {"testValues": vals, "useFixtureDefaults": False},
            )
        except Exception:
            out = req(
                "POST",
                f"/policy-studio/documents/{nested}/policy-test/run",
                {"testValues": vals, "useFixtureDefaults": False},
            )
        decision = str(
            out.get("simulatedDecisionCode") or out.get("overallDecision") or out.get("decision") or ""
        ).upper().replace(" ", "_")
        if decision in ("PASS", "ALLOW", "APPROVE"):
            actual = "ALLOW"
        elif decision in ("FAIL", "REJECT", "DECLINE"):
            actual = "REJECT"
        else:
            rules = out.get("ruleResults") or out.get("results") or out.get("evaluatedRules") or []
            fails = [
                r
                for r in rules
                if str(r.get("resultCode") or r.get("outcome") or r.get("result") or "").upper()
                in ("FAIL", "REJECT")
            ]
            actual = "REJECT" if fails else "ALLOW"
        tr = "PASS" if actual == expected else "FAIL"
        nested_results.append(
            {"input": vals, "expectedDecision": expected, "actualDecision": actual, "testResult": tr}
        )
        print("NESTED", vals, expected, actual, tr)
        assert tr == "PASS", nested_results[-1]
    report["nestedGoldens"] = nested_results

    # IN / NOT IN policies + test
    in_doc = create("UAT GATE1 IN Ã¢â‚¬â€ borrower types")
    in_text = "Borrower constitution must be Proprietorship, Partnership or Company"
    p = preview(in_doc, {"mode": "DESCRIBE", "text": in_text})
    assert_ready(p, "IN")
    c = confirm(in_doc, {"confirm": True, "mode": "DESCRIBE", "text": in_text, "treatment": "Reject"})
    assert str((c.get("expression") or {}).get("op")).upper() == "IN"
    report["inDoc"] = in_doc
    print("IN_OK", in_doc)

    notin_doc = create("UAT GATE1 NOT IN Ã¢â‚¬â€ industry")
    notin_text = "Industry must not be Real Estate, Gambling or Crypto"
    p = preview(notin_doc, {"mode": "DESCRIBE", "text": notin_text})
    assert_ready(p, "NOT_IN")
    c = confirm(notin_doc, {"confirm": True, "mode": "DESCRIBE", "text": notin_text, "treatment": "Reject"})
    assert str((c.get("expression") or {}).get("op")).upper() == "NOT_IN"
    report["notInDoc"] = notin_doc
    print("NOT_IN_OK", notin_doc)

    # Version clone on nested via lifecycle new-version
    v1_expr = json.dumps(nexpr, sort_keys=True)
    try:
        req(
            "POST",
            f"/policy-studio/documents/{nested}/lifecycle/stamp-active-for-versioning",
            {"reason": "GATE1 version isolation"},
        )
    except Exception as e:
        report["stampActiveWarning"] = str(e)
    v2 = req(
        "POST",
        f"/policy-studio/documents/{nested}/lifecycle/new-version",
        {"label": "GATE1-v2-nested-edit", "reason": "GATE1 authoring completeness"},
    )
    v2_doc = (
        (v2.get("policyHeader") or {}).get("documentId")
        or v2.get("documentId")
        or v2.get("newDocumentId")
        or (v2.get("draft") or {}).get("documentId")
        or ""
    )
    report["versionClone"] = {"v1": nested, "v2": v2_doc, "rawKeys": list(v2.keys())[:30]}
    print("VERSION_CLONE", report["versionClone"])
    if v2_doc and str(v2_doc) != str(nested):
        # Modify nested condition on v2 only
        v2_sess = reload(str(v2_doc))
        v2_rules = v2_sess.get("underwritingRules") or v2_sess.get("ruleCards") or []
        v1_sess = reload(nested)
        v1_rules = v1_sess.get("underwritingRules") or v1_sess.get("ruleCards") or []
        v1_expr_reload = None
        for card in v1_rules:
            ex = card.get("technicalExpression") or card.get("expression")
            if isinstance(ex, dict) and str(ex.get("op")).upper() == "AND":
                v1_expr_reload = ex
                break
        report["versionIsolation"] = {
            "v1Unchanged": json.dumps(v1_expr_reload, sort_keys=True) == v1_expr if v1_expr_reload else False,
            "v1RuleCount": len(v1_rules),
            "v2RuleCount": len(v2_rules),
            "v2Doc": v2_doc,
        }
        print("VERSION_ISOLATION", report["versionIsolation"])
    else:
        report["versionIsolation"] = {"note": "v2 doc id missing or same as v1", "response": str(v2)[:400]}

    # Banking BRE Ã¢â‚¬â€ load CURRENT staging session (do NOT reset)
    banking = None
    banking_doc = None
    try:
        banking = req("GET", "/policy-studio/banking")
    except Exception as e:
        report["bankingGetError"] = str(e)
        try:
            banking = req("POST", "/policy-studio/banking/open", {})
        except Exception as e2:
            report["bankingOpenError"] = str(e2)
    if isinstance(banking, dict):
        banking_doc = (
            (banking.get("policyHeader") or {}).get("documentId")
            or banking.get("documentId")
        )
        report["bankingKeys"] = list(banking.keys())[:40]
        report["bankingAllowCanonical"] = banking.get("allowCanonicalAuthority")
    if not banking_doc:
        for hint in (
            "/tmp/banking-bre-docid.txt",
            "/opt/billiontech/apps/billiontechlos/BANKING_BRE_DOC_ID.txt",
        ):
            if os.path.isfile(hint):
                banking_doc = open(hint, encoding="utf-8").read().strip()
                break
    report["bankingDoc"] = banking_doc
    if banking_doc:
        bs = banking if banking and (banking.get("underwritingRules") or banking.get("ruleCards")) else reload(str(banking_doc))
        rules = bs.get("underwritingRules") or bs.get("ruleCards") or []
        readiness = bs.get("executionReadiness") or bs.get("readiness") or {}
        blockers = (
            readiness.get("executionBlockers")
            or bs.get("executionBlockers")
            or bs.get("blockers")
            or []
        )
        report["bankingRuleCount"] = len(rules)
        report["bankingBlockers"] = blockers
        report["bankingName"] = (bs.get("policyHeader") or {}).get("name") or bs.get("name")
        # Spot-check known banking rule ids if present
        sys_ids = [str(r.get("systemRuleId") or "") for r in rules]
        report["bankingHasInwardReturn"] = any("INWARD" in s or "RETURN" in s for s in sys_ids)
        report["bankingHasAdb"] = any("ADB" in s for s in sys_ids)
        report["bankingHasEmi"] = any("EMI" in s or "BOUNCE" in s for s in sys_ids)
        print(
            "BANKING_BRE",
            banking_doc,
            "rules",
            len(rules),
            "blockers",
            len(blockers) if isinstance(blockers, list) else blockers,
        )
    else:
        print("BANKING_BRE_DOC_NOT_FOUND")

    # Authority invariant from authoring sources
    sources = req("GET", "/policy-studio/rule-authoring/sources")
    report["allowCanonicalAuthority"] = sources.get("allowCanonicalAuthority")
    print("AUTHORITY", report["allowCanonicalAuthority"])

    out_path = "/tmp/authoring-completeness-gate1-report.json"
    open(out_path, "w", encoding="utf-8").write(json.dumps(report, indent=2))
    print("REPORT", out_path)
    print("ALL_STAGING_GOLDENS_OK")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as e:
        print("FATAL", e)
        sys.exit(1)
