#!/usr/bin/env python3
import json, subprocess

def psql(sql):
    r = subprocess.run(
        ["docker", "exec", "billiontech-postgres", "psql", "-U", "los_app", "-d", "los_core_staging", "-t", "-A", "-c", sql],
        capture_output=True, text=True)
    return r.stdout.strip()

raw = psql("SELECT scorecard_json::text FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e'")
sc = json.loads(raw)
rows = sc.get("rows") or sc.get("parameters") or []
print("ROW_COUNT", len(rows))
for r in rows:
    print(json.dumps({
        "id": r.get("id"),
        "parameter": r.get("parameter"),
        "canonical": r.get("canonicalParameterId"),
        "condition": r.get("condition"),
        "score": r.get("score"),
        "source": r.get("source"),
        "mappingStatus": r.get("mappingStatus"),
    }))
print("THRESH", psql("SELECT thresholds_json::text FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e'"))
print("HARD", psql("SELECT hard_rules_json::text FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e'"))
