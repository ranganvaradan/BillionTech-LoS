#!/usr/bin/env python3
"""Prove UAT clause-OR fix + residual fail-closed + C3 golden + AST round-trip."""
from __future__ import annotations

import json
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo"
ENV = "/opt/billiontech/apps/billiontechlos/.env"
UAT = "bureau score > 700 OR bureau score IS EQUAL TO -1"
C3 = "Bureau Score of -1, NTC and 650 & above only will be allowed"


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
        with urllib.request.urlopen(r, timeout=90) as resp:
            raw = resp.read().decode()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        err = e.read().decode()
        print("HTTP", e.code, path, err[:800])
        raise


def create(name: str) -> str:
    d = req(
        "POST",
        "/policy-studio/create",
        {"policyName": name, "product": "Business Term Loan", "borrowerType": "Company"},
    )
    doc = (d.get("policyHeader") or {}).get("documentId") or d.get("documentId")
    assert doc, d
    return str(doc)


def preview(doc: str, text: str) -> dict:
    d = req(
        "POST",
        f"/policy-studio/documents/{doc}/rules/preview",
        {"mode": "DESCRIBE", "text": text, "treatment": "Reject"},
    )
    return d.get("preview") or d


def confirm(doc: str, body: dict) -> dict:
    return req("POST", f"/policy-studio/documents/{doc}/rules/add-plain-english", body)


def reload(doc: str) -> dict:
    return req("GET", f"/policy-studio/documents/{doc}")


def strip_seeded(doc: str) -> int:
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
                {"uiAction": "DELETE", "reason": "clause-or proof isolate"},
            )
            removed += 1
    return removed


def metric(leaf: dict) -> str:
    left = leaf.get("left") or leaf.get("arg") or {}
    return str(left.get("metric") or "")


def const_val(leaf: dict):
    right = leaf.get("right")
    if isinstance(right, dict) and "const" in right:
        c = right["const"]
        return int(c) if isinstance(c, (int, float)) else c
    return right


def same_tree(a, b) -> bool:
    return json.dumps(a, sort_keys=True, default=str) == json.dumps(b, sort_keys=True, default=str)


def policy_test(doc: str, values: dict) -> dict:
    try:
        return req(
            "POST",
            f"/policy-studio/documents/{doc}/test/quick",
            {"testValues": values, "useFixtureDefaults": False},
        )
    except Exception:
        return req(
            "POST",
            f"/policy-studio/documents/{doc}/policy-test/run",
            {"testValues": values, "useFixtureDefaults": False},
        )


def main() -> int:
    report = {"ok": True}

    doc = create("UAT CLAUSE-OR flat residual P0")
    report["doc"] = doc

    # --- UAT OR ---
    p = preview(doc, UAT)
    assert p.get("complete") is True, p
    assert str(p.get("status")) == "READY", p
    assert "Ready to confirm" in str(p.get("message") or ""), p
    expr = p.get("expression") or {}
    assert str(expr.get("op")).upper() == "OR", expr
    args = expr.get("args") or []
    assert len(args) == 2, expr
    assert str(args[0].get("op")).upper() == "GT", args[0]
    assert metric(args[0]) == "bureau.score" and const_val(args[0]) == 700, args[0]
    assert str(args[1].get("op")).upper() == "EQ", args[1]
    assert metric(args[1]) == "bureau.score" and const_val(args[1]) == -1, args[1]
    summary = str(p.get("ruleDisplay") or "")
    lines = " ".join(p.get("previewLines") or [])
    blob = (summary + " " + lines).lower()
    assert "bureau score > 700" in blob, (summary, lines)
    assert "bureau score = -1" in blob or "bureau score = -1" in summary.lower(), (summary, lines)
    assert "any" in blob or str(p.get("combinator")) == "ANY", p
    report["uatPreview"] = {
        "combinator": p.get("combinator"),
        "ruleDisplay": summary,
        "expression": expr,
    }

    confirmed = confirm(
        doc,
        {
            "confirm": True,
            "mode": "DESCRIBE",
            "text": UAT,
            "treatment": "Reject",
            "expression": expr,
            "children": p.get("children"),
            "combinator": p.get("combinator"),
        },
    )
    assert confirmed.get("confirmed") is True or confirmed.get("ok") is True or confirmed.get("rule"), confirmed
    saved = confirmed.get("expression") or (confirmed.get("rule") or {}).get("expression")
    if not saved:
        sess = reload(doc)
        rules = sess.get("underwritingRules") or []
        authored = [r for r in rules if str(r.get("systemRuleId") or "").startswith("CM_")]
        assert authored, sess
        saved = authored[-1].get("expression")
    assert same_tree(expr, saved), {"preview": expr, "saved": saved}
    report["savedAstMatchesPreview"] = True

    strip_seeded(doc)
    # Policy Test / simulate — score cases
    exec_cases = [
        ({"bureau.score": -1}, "PASS"),
        ({"bureau.score": 699}, "FAIL"),
        ({"bureau.score": 700}, "FAIL"),
        ({"bureau.score": 701}, "PASS"),
    ]
    exec_results = []
    for vals, expect in exec_cases:
        sim = policy_test(doc, vals)
        decision = str(
            sim.get("simulatedDecisionCode")
            or sim.get("overallDecision")
            or sim.get("decision")
            or sim.get("outcome")
            or sim.get("testResult")
            or ""
        ).upper().replace(" ", "_")
        # Allow-when compound: PASS/ALLOW ↔ expect PASS; FAIL/REJECT ↔ expect FAIL
        if decision in ("PASS", "ALLOW", "APPROVE"):
            got = "PASS"
        elif decision in ("FAIL", "REJECT", "DECLINE"):
            got = "FAIL"
        else:
            # fall back: rule-level result
            tr = str(sim.get("result") or sim.get("ruleResult") or "").upper()
            if "PASS" in tr and "FAIL" not in tr:
                got = "PASS"
            elif "FAIL" in tr or "REJECT" in tr:
                got = "FAIL"
            else:
                got = decision or "UNKNOWN"
        ok = got == expect
        exec_results.append({"vals": vals, "expect": expect, "got": got, "decision": decision, "ok": ok})
        assert ok, exec_results[-1]
    report["uatExecution"] = exec_results

    # --- C3 golden still passes ---
    doc3 = create("UAT C3 regression clause-or P0")
    p3 = preview(doc3, C3)
    assert p3.get("complete") is True, p3
    e3 = p3.get("expression") or {}
    assert str(e3.get("op")).upper() == "OR", e3
    assert len(e3.get("args") or []) == 3, e3
    report["c3"] = {"complete": True, "args": len(e3.get("args") or [])}

    # --- explicit A AND B ---
    p_and = preview(doc, "Bureau score must be at least 700 AND FOIR must not exceed 50%")
    assert p_and.get("complete") is True, p_and
    assert p_and.get("compoundGroup") is True or str((p_and.get("expression") or {}).get("op")).upper() == "AND", p_and
    assert str((p_and.get("expression") or {}).get("op")).upper() == "AND", p_and
    report["explicitAnd"] = True

    # --- residual OR/AND never Ready ---
    for residual in (
        "bureau score > 700 OR unknown widget must be blue",
        "bureau score > 700 AND unknown widget must be blue",
    ):
        pr = preview(doc, residual)
        assert pr.get("complete") is not True, residual
        assert str(pr.get("status")) == "NEEDS_CLARIFICATION", residual
        assert "Ready to confirm" not in str(pr.get("message") or ""), residual
    report["residualFailClosed"] = True

    out = "/tmp/clause-or-flat-residual-p0-proof.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, default=str)
    print("OK", json.dumps(report, indent=2, default=str)[:4000])
    print("REPORT", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
