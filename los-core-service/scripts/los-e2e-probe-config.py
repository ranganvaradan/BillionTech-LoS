#!/usr/bin/env python3
import json, subprocess

def psql(sql):
    r = subprocess.run(
        ["docker", "exec", "billiontech-postgres", "psql", "-U", "los_app", "-d", "los_core_staging", "-t", "-A", "-F", "|", "-c", sql],
        capture_output=True, text=True)
    return r.stdout.strip(), r.stderr.strip()

out = {}
out["counts"], err = psql("""
SELECT (SELECT COUNT(*) FROM loan_applications)||'|'||
       (SELECT COUNT(*) FROM workflow_configs)||'|'||
       (SELECT COUNT(*) FROM workflow_configs WHERE active)||'|'||
       (SELECT COUNT(*) FROM underwriting_rule_sets WHERE active)||'|'||
       (SELECT COUNT(*) FROM underwriting_scorecards WHERE active)
""")
print("COUNTS", out["counts"], err)

# find gacat tables
tabs, _ = psql("SELECT tablename FROM pg_tables WHERE schemaname='public' AND (tablename ILIKE '%canonical%' OR tablename ILIKE '%gacat%' OR tablename ILIKE '%ci_param%') ORDER BY 1")
print("TABLES", tabs)

# rule set JSON keys
rs, _ = psql("SELECT jsonb_typeof(rules::jsonb), left(rules::text, 500) FROM underwriting_rule_sets WHERE id='c3320000-0000-4000-a000-000000000029'")
print("RULES_SNIP", rs[:800])

sc, _ = psql("SELECT left(coalesce(parameters::text, factor_bindings::text, '{}'), 800) FROM underwriting_scorecards WHERE id='358c83f1-420b-4bdb-be40-78259367238e'")
print("SCORECARD_SNIP", sc[:800])

# columns
cols, _ = psql("SELECT string_agg(column_name, ',') FROM information_schema.columns WHERE table_name='underwriting_rule_sets'")
print("RS_COLS", cols)
cols2, _ = psql("SELECT string_agg(column_name, ',') FROM information_schema.columns WHERE table_name='underwriting_scorecards'")
print("SC_COLS", cols2)

# gap defaults env in container
env = subprocess.run(["docker", "exec", "billiontechlos-core", "printenv"], capture_output=True, text=True).stdout
for line in env.splitlines():
    if any(k in line for k in ("GAP", "ENCORE_BASE", "SPRING_PROFILES", "CANONICAL", "LMS_")):
        if "PASSWORD" in line or "TOKEN" in line or "SECRET" in line:
            print(line.split("=")[0]+"=***")
        else:
            print(line)
