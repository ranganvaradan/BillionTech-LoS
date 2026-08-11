#!/usr/bin/env python3
"""SCORECARD-SAFETY-CLOSURE-1 — inventory ACTIVE scorecard factors on staging."""
import json
import subprocess
from collections import defaultdict

sql = (
    "SELECT id::text, name, version::text, active::text, status, "
    "borrower_type, loan_product, priority::text, "
    "scorecard_json::text, hard_rules_json::text, "
    "COALESCE(safety_json::text, '{}') "
    "FROM underwriting_scorecards WHERE active = true"
)
raw = subprocess.check_output(
    [
        "docker", "exec", "-i", "billiontech-postgres",
        "psql", "-U", "los_app", "-d", "los_core_staging",
        "-t", "-A", "-F", "\x1f", "-c", sql,
    ],
    text=True,
)

active_cards = 0
factors = 0
explicit_req = explicit_dep = explicit_skip = implicit = 0
cards_with_implicit = 0
cards_fully_explicit = 0
groups = defaultdict(list)

for ln in [x for x in raw.splitlines() if x.strip()]:
    parts = ln.split("\x1f")
    if len(parts) < 11:
        continue
    sid, name, ver, act, status, bt, lp, prio, sj, hj, safety = parts[:11]
    active_cards += 1
    try:
        data = json.loads(sj)
        hard = json.loads(hj)
        saf = json.loads(safety) if safety else {}
    except Exception:
        continue
    hard_params = set()
    for r in (hard.get("rules") or []):
        if isinstance(r, dict) and r.get("parameter"):
            hard_params.add(str(r["parameter"]))
    fps = saf.get("factorPolicies") or {}
    rows = data.get("rows") or []
    seen = set()
    card_implicit = False
    card_explicit = True
    for r in rows:
        if not isinstance(r, dict):
            continue
        p = r.get("parameter")
        if not p or p in seen:
            continue
        seen.add(p)
        factors += 1
        src = r.get("source")
        pol = fps.get(p) if isinstance(fps, dict) else None
        configured = None
        if isinstance(pol, dict) and pol.get("missingData"):
            configured = str(pol["missingData"]).upper()
        hard = p in hard_params
        if configured in ("REQUIRED", "OPTIONAL_DEPRESS", "OPTIONAL_SKIP"):
            if configured == "REQUIRED":
                explicit_req += 1
            elif configured == "OPTIONAL_DEPRESS":
                explicit_dep += 1
            else:
                explicit_skip += 1
            effective = configured
            is_implicit = False
        else:
            effective = "REQUIRED" if hard else "OPTIONAL_DEPRESS"
            is_implicit = True
            implicit += 1
            card_implicit = True
            card_explicit = False
        groups[p].append(
            {
                "scorecard": name,
                "version": ver,
                "bt": bt,
                "lp": lp,
                "source": src,
                "hard": hard,
                "effective": effective,
                "implicit": is_implicit,
            }
        )
    # hard-only params
    for hp in hard_params:
        if hp in seen:
            continue
        factors += 1
        pol = fps.get(hp) if isinstance(fps, dict) else None
        configured = str(pol.get("missingData")).upper() if isinstance(pol, dict) and pol.get("missingData") else None
        if configured == "REQUIRED":
            explicit_req += 1
        elif configured in ("OPTIONAL_DEPRESS", "OPTIONAL_SKIP"):
            card_implicit = True
            card_explicit = False
            implicit += 1
        else:
            implicit += 1
            card_implicit = True
            card_explicit = False
        groups[hp].append(
            {
                "scorecard": name,
                "version": ver,
                "bt": bt,
                "lp": lp,
                "source": "HARD_RULE",
                "hard": True,
                "effective": configured or "REQUIRED",
                "implicit": configured != "REQUIRED",
            }
        )
    if card_implicit:
        cards_with_implicit += 1
    if card_explicit:
        cards_fully_explicit += 1

print("Active scorecards =", active_cards)
print("Factors =", factors)
print("Factors explicit REQUIRED =", explicit_req)
print("Factors explicit OPTIONAL_DEPRESS =", explicit_dep)
print("Factors explicit OPTIONAL_SKIP =", explicit_skip)
print("Factors implicit/legacy =", implicit)
print("Scorecards with implicit factors =", cards_with_implicit)
print("Scorecards fully explicit =", cards_fully_explicit)
print("Scorecards currently safe for historical execution =", active_cards)
print("Scorecards safe to create a new ACTIVE version =", cards_fully_explicit)
print("--- factor groups ---")
for p, items in sorted(groups.items()):
    hard_n = sum(1 for i in items if i["hard"])
    impl_n = sum(1 for i in items if i["implicit"])
    print(f"{p}: count={len(items)} hardRuleOn={hard_n} implicit={impl_n} "
          f"recommended={'REQUIRED' if hard_n else 'OPTIONAL_DEPRESS (legacy)'} "
          f"humanDecisionRequired={impl_n > 0 and hard_n == 0}")
