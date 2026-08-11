#!/bin/bash
set -eu
curl -sS -m 30 -X POST 'http://127.0.0.1:8083/api/v1/auth/login' \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@credinnov.com","password":"Bltest@123"}' -o /tmp/login.json
python3 - <<'PY'
import json
d=json.load(open("/tmp/login.json"))
token=d.get("accessToken") or d.get("token") or (d.get("data") or {}).get("accessToken")
open("/tmp/staff.token","w").write(token or "")
print("HAS_TOKEN", bool(token))
PY
STAFF=$(cat /tmp/staff.token)
curl -sS -m 30 'http://127.0.0.1:8083/api/v1/underwriting/scorecards/mapping-inventory' \
  -H "Authorization: Bearer $STAFF" -o /tmp/inv.json
curl -sS -m 30 'http://127.0.0.1:8083/api/v1/underwriting/scorecards/gacat-factors?q=bureau' \
  -H "Authorization: Bearer $STAFF" -o /tmp/cat.json
curl -sS -m 30 -X POST 'http://127.0.0.1:8083/api/v1/underwriting/scorecards/suggest-from-policy' \
  -H "Authorization: Bearer $STAFF" -H 'Content-Type: application/json' \
  -d '{"parameterIds":["bureau.score","obligation.ratio","application.business_vintage_months"]}' \
  -o /tmp/sug.json
# Find company term loan id and preview golden
python3 - <<'PY'
import json, urllib.request
token=open("/tmp/staff.token").read().strip()
req=urllib.request.Request('http://127.0.0.1:8083/api/v1/underwriting/scorecards',
    headers={"Authorization": f"Bearer {token}"})
cards=json.load(urllib.request.urlopen(req, timeout=30))
company=[c for c in cards if c.get("borrowerType")=="COMPANY" and c.get("loanProduct")=="TERM_LOAN" and c.get("active")]
print("COMPANY_TERM_LOAN_ACTIVE", len(company))
if company:
    c=company[0]
    print("CARD", c.get("name"), "v", c.get("version"), "id", c.get("id"))
    rows=(c.get("scorecardJson") or {}).get("rows") or []
    for r in rows:
        if r.get("parameter") in ("BUREAU_SCORE","MONTHLY_INCOME","OBLIGATION_RATIO","AVERAGE_BANK_BALANCE","KYC_QUALITY"):
            print("ROW", r.get("parameter"), "canonical", r.get("canonicalParameterId"), "status", r.get("mappingStatus"), "defVer", r.get("canonicalDefinitionVersion"))
    body=json.dumps({
        "scorecardId": c["id"],
        "inputs": {
            "BUREAU_SCORE": 760,
            "MONTHLY_INCOME": 60000,
            "OBLIGATION_RATIO": 30,
            "AVERAGE_BANK_BALANCE": 25000,
            "KYC_QUALITY": 1
        }
    }).encode()
    preq=urllib.request.Request('http://127.0.0.1:8083/api/v1/underwriting/scorecards/preview',
        data=body, headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"}, method="POST")
    prev=json.load(urllib.request.urlopen(preq, timeout=60))
    print("PREVIEW earned", prev.get("earnedPoints"), "max", prev.get("maxPoints"),
          "norm", prev.get("normalizedPercent"), "decision", prev.get("policyDecision"),
          "mutated", prev.get("applicationMutated"), "allowCanon", prev.get("allowCanonicalAuthority"))
    for pr in (prev.get("parameterResults") or [])[:8]:
        print("FACTOR", pr.get("parameter") or pr.get("legacyParameterKey"),
              "canon", pr.get("canonicalParameterId"),
              "pts", pr.get("points") or pr.get("earnedPoints"),
              "max", pr.get("maxPoints") or pr.get("factorMax"),
              "band", pr.get("matchedCondition") or pr.get("band") or pr.get("condition"))
inv=json.load(open("/tmp/inv.json"))
cat=json.load(open("/tmp/cat.json"))
sug=json.load(open("/tmp/sug.json"))
print("INV allowCanonicalAuthority", inv.get("allowCanonicalAuthority"))
print("INV byStatus", inv.get("byMappingStatus"))
print("INV distinct", inv.get("activeScorecardDistinctFactors"))
print("CAT allowCanonicalAuthority", cat.get("allowCanonicalAuthority"), "count", cat.get("count"))
print("SUG n", len(sug.get("suggestions") or []), "auto", [x.get("autoCreate") for x in (sug.get("suggestions") or [])])
PY
docker exec billiontech-postgres psql -U los_app -d los_core_staging -tAc \
  "SELECT COUNT(*) FROM ci_gacat_parameter_definitions;"
