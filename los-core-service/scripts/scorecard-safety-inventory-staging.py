#!/usr/bin/env python3
import json
import subprocess
from collections import defaultdict

sql = (
    "SELECT id::text, name, version::text, active::text, status, "
    "borrower_type, loan_product, priority::text, scorecard_json::text "
    "FROM underwriting_scorecards"
)
raw = subprocess.check_output(
    [
        "docker",
        "exec",
        "-i",
        "billiontech-postgres",
        "psql",
        "-U",
        "los_app",
        "-d",
        "los_core_staging",
        "-t",
        "-A",
        "-F",
        "\x1f",
        "-c",
        sql,
    ],
    text=True,
)
rows = [ln for ln in raw.splitlines() if ln.strip()]
print("total", len(rows))
active = 0
weight = 0
computed = 0
manual = 0
ladders = []
for ln in rows:
    parts = ln.split("\x1f")
    if len(parts) < 9:
        continue
    sid, name, ver, act, status, bt, lp, prio, sj = parts[:9]
    if act in ("t", "true", "True"):
        active += 1
    try:
        data = json.loads(sj)
    except Exception:
        continue
    rlist = data.get("rows") or []
    if any(
        isinstance(r, dict)
        and isinstance(r.get("weight"), (int, float))
        and r.get("weight") not in (0, None)
        for r in rlist
    ):
        weight += 1
    if any(isinstance(r, dict) and str(r.get("source", "")).upper() == "COMPUTED" for r in rlist):
        computed += 1
    if any(isinstance(r, dict) and "MANUAL" in str(r.get("source", "")).upper() for r in rlist):
        manual += 1
    g = defaultdict(list)
    for r in rlist:
        if isinstance(r, dict) and r.get("parameter"):
            g[str(r.get("parameter"))].append(str(r.get("condition") or ""))
    for param, conds in g.items():
        gtes = [c for c in conds if c.upper().startswith(("GTE:", "GT:"))]
        ltes = [c for c in conds if c.upper().startswith(("LTE:", "LT:"))]
        if len(gtes) >= 2 or len(ltes) >= 2:
            ladders.append((sid, name, ver, param, conds, act, status, bt, lp))

print("active", active)
print("using_weight_metadata", weight)
print("using_computed", computed)
print("using_manual_sources", manual)
print("factors_with_ordered_ladders", len(ladders))
print("--- COMPANY TERM_LOAN active ---")
for ln in rows:
    parts = ln.split("\x1f")
    if len(parts) < 9:
        continue
    sid, name, ver, act, status, bt, lp, prio, sj = parts[:9]
    if bt == "COMPANY" and lp == "TERM_LOAN" and act in ("t", "true", "True"):
        print(f"id={sid} name={name} v={ver} priority={prio} status={status}")
