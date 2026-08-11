#!/usr/bin/env python3
import subprocess

def psql(sql):
    r = subprocess.run(
        ["docker", "exec", "billiontech-postgres", "psql", "-U", "los_app", "-d", "los_core_staging", "-t", "-A", "-c", sql],
        capture_output=True, text=True)
    return (r.stdout or "") + (("\nERR:" + r.stderr) if r.stderr else "")

print("GACAT_COUNT", psql("SELECT COUNT(*) FROM ci_gacat_canonical_parameter;").strip())
print("RULES_JSON")
print(psql("SELECT left(rules_json::text, 2000) FROM underwriting_rule_sets WHERE id='c3320000-0000-4000-a000-000000000029';")[:2000])
print("SCORECARD_JSON")
print(psql("SELECT left(scorecard_json::text, 1500) FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e';")[:1500])
print("THRESHOLDS")
print(psql("SELECT thresholds_json::text FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e';")[:800])
print("HARD_RULES")
print(psql("SELECT hard_rules_json::text FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e';")[:1200])
print("SAFETY")
print(psql("SELECT left(safety_json::text, 800) FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e';")[:800])
