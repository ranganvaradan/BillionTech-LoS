#!/usr/bin/env bash
set -euo pipefail
DOC=c46ed387-581f-4569-96c7-e3251c86376a
BASE=http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo
python3 - <<'PY'
import json
d=json.load(open("/tmp/ap.json"))
life=d.get("lifecycle") or d
print("approve_status", life.get("businessStatus") or d.get("businessStatus"))
print("approve_msg", (d.get("message") or life.get("message") or "")[:200])
PY
curl -sS -o /tmp/sc.json -w "sched_http=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/schedule" \
  -H "Content-Type: application/json" -H "X-Tenant-Id: staging" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","businessDate":"2026-09-15"}'
python3 - <<'PY'
import json
d=json.load(open("/tmp/sc.json"))
life=d.get("lifecycle") or d
print("sched_status", life.get("businessStatus") or d.get("businessStatus") or d.get("status"))
print("sched_msg", (d.get("message") or life.get("message") or "")[:300])
print("allowCanonical", life.get("allowCanonicalAuthority"), "prod", life.get("productionAuthority"))
print("durableWarn", life.get("durableCatalogueWarning") or d.get("durableCatalogueWarning"))
PY
curl -sS -o /tmp/fin.json "$BASE/policy-studio/documents/$DOC/lifecycle" -H "X-Tenant-Id: staging"
python3 - <<'PY'
import json
d=json.load(open("/tmp/fin.json"))
print("FINAL", d.get("businessStatus"))
print("primary", d.get("primaryAction"))
print("shadow", d.get("shadowBoundary"))
print("allowCanonical", d.get("allowCanonicalAuthority"))
PY
