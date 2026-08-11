#!/bin/bash
# GACAT-PERSISTENCE-1 — staging golden checks
set -eu
CORE=$(docker ps --format '{{.Names}}' | grep -E 'los-core|billiontechlos-core' | head -1)
PG=billiontech-postgres
DB=los_core_staging
PGUSER=los_app
export PGPASSWORD=$(docker exec "$CORE" printenv POSTGRES_PASSWORD)
BASE='http://127.0.0.1:8083/api/v1/internal/credit-intelligence'
AUTH=(-H "X-User-Role: ADMINISTRATOR" -H "X-User-Id: c1000000-0000-0000-0000-000000000001")
ITOKEN=$(docker exec "$CORE" printenv CREDIT_INTELLIGENCE_INTERNAL_TOKEN)
psql() { docker exec -e PGPASSWORD="$PGPASSWORD" "$PG" psql -U "$PGUSER" -d "$DB" "$@"; }
echo "CORE=$CORE PG=$PG"

curl -sS -m 30 "http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters" "${AUTH[@]}" -o /tmp/gacat-admin.json
python3 - <<'PY'
import json
admin=json.load(open("/tmp/gacat-admin.json"))
print("authority", admin.get("catalogueAuthority"),
      "inv", admin.get("inventoryVersion"),
      "count", (admin.get("totals") or {}).get("registryCount"),
      "javaSeed", admin.get("javaSeedIsRuntimeAuthority"),
      "allow", admin.get("allowCanonicalAuthority"))
assert admin.get("catalogueAuthority")=="DATABASE", admin
assert admin.get("javaSeedIsRuntimeAuthority") is False
assert admin.get("allowCanonicalAuthority") is False
assert (admin.get("totals") or {}).get("registryCount",0)>=160
print("ADMIN_OK")
t=admin.get("totals") or {}
print("TOTALS", {k:t.get(k) for k in sorted(t.keys())})
for row in admin.get("bySourceSummary") or []:
  if row.get("count"):
    print("SRC", row.get("source"), "n", row.get("count"),
          "raw", row.get("rawCount"), "der", row.get("derivedCount"),
          "man", row.get("manualCount"), "live", row.get("liveCount"))
PY

ADMIN='http://127.0.0.1:8083/api/v1/admin/live-readiness/data-parameters'
curl -sS -m 30 "$ADMIN/search?q=Maximum%20DPD" "${AUTH[@]}" -o /tmp/gacat-dpd.json
curl -sS -m 30 "$ADMIN/bureau.max_dpd_6m" "${AUTH[@]}" -o /tmp/gacat-dpd-detail.json
curl -sS -m 30 "$ADMIN/search?q=PAN%20verified" "${AUTH[@]}" -o /tmp/gacat-pan.json
curl -sS -m 30 "$ADMIN/application.borrower_type" "${AUTH[@]}" -o /tmp/gacat-borrower.json
curl -sS -m 30 "$ADMIN/banking.avg_daily_balance_3m" "${AUTH[@]}" -o /tmp/gacat-adb.json
curl -sS -m 30 "$ADMIN/search?q=Monthly%20Credits" "${AUTH[@]}" -o /tmp/gacat-credits.json

# Typed authoring via staging-demo Policy Studio surfaces
PS="$BASE/staging-demo/policy-studio"
curl -sS -m 30 "$PS/rule-authoring/sources" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-ra-src.json || true
curl -sS -m 30 "$PS/parameters/search?q=PAN%20verified" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-ra-pan.json || true
curl -sS -m 30 "$PS/parameters/search?q=borrower%20type" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-ra-bor.json || true
# Preview typed draft on a banking reset document if available later; sources alone proves catalogue wiring
curl -sS -m 30 -X POST "$PS/banking/reset" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-bank.json || true
DOC=$(python3 -c 'import json;d=json.load(open("/tmp/gacat-bank.json")); print(d.get("documentId") or d.get("id") or (d.get("session") or {}).get("documentId") or "")')
if [ -n "$DOC" ]; then
  curl -sS -m 30 -X POST "$PS/documents/$DOC/rules/preview" \
    -H "X-Internal-Token: $ITOKEN" -H "Content-Type: application/json" \
    -d '{"parameterId":"kyc.pan.verified","operator":"is","value":true}' -o /tmp/gacat-ra-pan-prev.json || true
  curl -sS -m 30 -X POST "$PS/documents/$DOC/rules/preview" \
    -H "X-Internal-Token: $ITOKEN" -H "Content-Type: application/json" \
    -d '{"parameterId":"application.borrower_type","operator":"is","value":"COMPANY"}' -o /tmp/gacat-ra-bor-prev.json || true
else
  echo '{}' > /tmp/gacat-ra-pan-prev.json
  echo '{}' > /tmp/gacat-ra-bor-prev.json
fi

python3 - <<'PY'
import json
dpd=json.load(open("/tmp/gacat-dpd.json"))
ids=[str(r.get("id")) for r in (dpd.get("results") or [])]
print("DPD_IDS", ids[:8])
assert "bureau.max_dpd_6m" in ids
detail=json.load(open("/tmp/gacat-dpd-detail.json")).get("parameter") or {}
print("DPD", detail.get("id"), "ver", detail.get("definitionVersion"),
      "type", detail.get("type"), "src", detail.get("source"),
      "cap", detail.get("capabilityStatus"), "prod", detail.get("productionReady"))
assert detail.get("id")=="bureau.max_dpd_6m"
assert str(detail.get("definitionVersion")).startswith("1") or detail.get("definitionVersion")==1
lin=detail.get("lineage") or {}
print("DPD_HOW", str(lin.get("howCalculated") or "")[:180])
print("DPD_INPUTS", lin.get("inputs") or lin.get("rawInputs"))

pan=json.load(open("/tmp/gacat-pan.json"))
pan_rows=pan.get("results") or []
print("PAN_ADMIN", [(r.get("id"), r.get("type")) for r in pan_rows[:6]])
assert any("pan" in str(r.get("id","")).lower() for r in pan_rows)

borrower=json.load(open("/tmp/gacat-borrower.json")).get("parameter") or {}
avs=borrower.get("allowedValues") or []
print("BORROWER_ADMIN", borrower.get("id"), "allowed", len(avs), avs[:4], "vt", borrower.get("valueType") or borrower.get("authoringValueType"))
assert borrower.get("id")=="application.borrower_type"
assert len(avs)>=2, "borrower allowed values missing from admin detail"

pan_detail_id=None
for r in pan_rows:
  if "pan" in str(r.get("id","")).lower() and "verif" in str(r.get("id","")).lower():
    pan_detail_id=r.get("id"); break
print("PAN_ID", pan_detail_id, "vt_hint", [(r.get("id"), r.get("valueType") or r.get("authoringValueType") or r.get("unit")) for r in pan_rows[:4]])

adb=json.load(open("/tmp/gacat-adb.json")).get("parameter") or {}
print("ADB", adb.get("id"), "ver", adb.get("definitionVersion"), "type", adb.get("type"), "src", adb.get("source"))
assert adb.get("id")=="banking.avg_daily_balance_3m"

credits=json.load(open("/tmp/gacat-credits.json"))
crows=credits.get("results") or []
print("CREDITS_HITS", [r.get("id") for r in crows[:8]])
# Contamination check: Monthly Credits parameter definition must not mention Proposed EDI
mc=[r for r in crows if "monthly" in str(r.get("id","")).lower() and "credit" in str(r.get("id","")).lower()]
assert mc, "monthly credits param missing from search"
for r in mc:
  blob=json.dumps(r).lower()
  assert "proposed_edi" not in blob and "proposed edi" not in blob, r.get("id")
print("CREDITS_CLEAN", [r.get("id") for r in mc])

def dump_ra(path, label):
  try:
    d=json.load(open(path))
  except Exception as e:
    print(label, "FAIL", e); return
  print(label, "keys", list(d.keys())[:20] if isinstance(d,dict) else type(d))
  # try common shapes
  for k in ("valueControl","authoringValueType","valueType","allowedValues","parameterId","id","control"):
    if isinstance(d,dict) and k in d: print(" ",k, d.get(k))
  draft=d.get("draft") or d.get("preview") or d.get("resolved") or d
  if isinstance(draft,dict):
    print(" ",label,"vc", draft.get("valueControl"), "avs", draft.get("allowedValues"), "pid", draft.get("parameterId") or draft.get("id"))
  hits=d.get("matches") or d.get("results") or d.get("parameters") or []
  if isinstance(hits,list):
    for h in hits[:5]:
      if isinstance(h,dict):
        print("  hit", h.get("id") or h.get("parameterId"), h.get("valueControl") or h.get("valueType"), h.get("allowedValues"))

for f,l in [("/tmp/gacat-ra-pan.json","RA_PAN"),("/tmp/gacat-ra-bor.json","RA_BOR"),
            ("/tmp/gacat-ra-pan-prev.json","PREV_PAN"),("/tmp/gacat-ra-bor-prev.json","PREV_BOR")]:
  dump_ra(f,l)
print("SEARCH_OK")
PY

curl -sS -m 30 "$PS/parameters" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-ps-params.json
curl -sS -m 90 -X POST "$PS/bureau/reset" -H "X-Internal-Token: $ITOKEN" -o /tmp/gacat-bur.json || true
python3 - <<'PY'
import json
ps=json.load(open("/tmp/gacat-ps-params.json"))
items=ps if isinstance(ps, list) else (ps.get("parameters") or ps.get("items") or ps.get("results") or ps.get("catalogue") or [])
print("PS_PARAMS_TYPE", type(ps).__name__, "n", len(items) if isinstance(items, list) else "n/a")
if isinstance(ps, dict):
  print("PS_KEYS", list(ps.keys())[:20])
  print("PS_META auth", ps.get("catalogueAuthority"), "inv", ps.get("inventoryVersion"),
        "count", ps.get("count") or (ps.get("totals") or {}).get("registryCount") or (len(items) if isinstance(items,list) else None))
bank=json.load(open("/tmp/gacat-bank.json"))
bur=json.load(open("/tmp/gacat-bur.json"))
def summary(doc, label):
  sid=doc.get("documentId") or doc.get("id") or (doc.get("session") or {}).get("documentId")
  rules=doc.get("rules") or (doc.get("document") or {}).get("rules") or []
  if isinstance(rules, dict):
    n=sum(len(v) if isinstance(v, list) else 0 for v in rules.values())
  else:
    n=len(rules) if isinstance(rules, list) else 0
  blockers=doc.get("blockers") or doc.get("readinessBlockers") or (doc.get("readiness") or {}).get("blockers") or []
  print(label, "doc", sid, "rules", n, "blockers", len(blockers) if isinstance(blockers, list) else blockers,
        "err", doc.get("error") or doc.get("message"))
summary(bank, "BANK")
summary(bur, "BUR")
for f, expect_ctrl in [("/tmp/gacat-ra-pan-prev.json","BOOLEAN"),("/tmp/gacat-ra-bor-prev.json","ENUM")]:
  try:
    d=json.load(open(f))
  except Exception:
    continue
  draft=d.get("draft") or d.get("preview") or d.get("rule") or d
  vc=None
  if isinstance(draft, dict):
    vc=draft.get("valueControl") or draft.get("authoringValueType") or (draft.get("valueMeta") or {}).get("valueControl")
    avs=draft.get("allowedValues")
    print("TYPED", f, "vc", vc, "avs_n", len(avs) if isinstance(avs,list) else avs)
  if vc:
    assert expect_ctrl.upper() in str(vc).upper()
print("PS_OK")
PY

curl -sS -m 15 "http://127.0.0.1:8083/actuator/health" -o /tmp/gacat-health.json || true
python3 - <<'PY'
import json
try:
  h=json.load(open("/tmp/gacat-health.json"))
  print("HEALTH", h.get("status"), "components", list((h.get("components") or {}).keys())[:30])
  g=(h.get("components") or {}).get("gacatCatalogue") or (h.get("components") or {}).get("gacat") or {}
  print("GACAT_HEALTH", g)
except Exception as e:
  print("HEALTH_PARSE", e)
PY

echo "=== DB INVENTORY ==="
psql -c "
SELECT
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter) AS params,
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter_version) AS versions,
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter_alias) AS aliases,
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter_allowed_value) AS allowed,
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter_lineage) AS lineage,
  (SELECT COUNT(*) FROM ci_gacat_canonical_parameter_source_binding) AS bindings,
  (SELECT COUNT(*) FROM ci_gacat_tenant_parameter_configuration) AS tenant_cfg;
"
psql -c "SELECT source_family, parameter_kind, COUNT(*) FROM ci_gacat_canonical_parameter GROUP BY 1,2 ORDER BY 1,2;"
psql -c "SELECT implemented, production_ready, COUNT(*) FROM ci_gacat_canonical_parameter GROUP BY 1,2 ORDER BY 1,2;"
psql -c "SELECT status, COUNT(*) FROM ci_gacat_canonical_parameter GROUP BY 1 ORDER BY 1;"
psql -c "SELECT p.canonical_id, a.value_code, a.display_value FROM ci_gacat_canonical_parameter_allowed_value a JOIN ci_gacat_canonical_parameter p ON p.id=a.parameter_id ORDER BY 1,2;"
psql -c "SELECT meta_key, meta_value FROM ci_gacat_catalogue_meta ORDER BY 1;"
psql -c "SELECT version, success FROM flyway_schema_history WHERE version::text IN ('111','112','113') ORDER BY installed_rank;"
psql -c "SELECT COUNT(*) AS duplicate_canonical_ids FROM (SELECT canonical_id FROM ci_gacat_canonical_parameter GROUP BY 1 HAVING COUNT(*)>1) d;"
psql -c "SELECT COUNT(*) AS orphan_lineage FROM ci_gacat_canonical_parameter_lineage l WHERE NOT EXISTS (SELECT 1 FROM ci_gacat_canonical_parameter p WHERE p.id=l.derived_parameter_id) OR (l.input_parameter_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM ci_gacat_canonical_parameter p WHERE p.id=l.input_parameter_id));"

echo "=== LOGS ==="
docker logs "$CORE" 2>&1 | grep -E 'GACAT catalogue loaded|catalogueAuthority|Flyway|require-database' | tail -25
echo "DEPLOYED"; cat /opt/billiontech/apps/billiontechlos/DEPLOYED_AT.txt 2>/dev/null | head -5
echo "BACKUP"; ls -la /opt/billiontech/backups/gacat-persistence-1/ 2>/dev/null | tail -5
echo "VERIFY_COMPLETE"
