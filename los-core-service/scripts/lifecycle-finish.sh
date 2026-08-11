#!/usr/bin/env bash
set -euo pipefail
DOC=5e53b516-825a-42df-8fc7-39ff656a80e0
BASE=http://127.0.0.1:8083/api/v1/internal/credit-intelligence/staging-demo
python3 - <<'PY'
import json
d=json.load(open("/tmp/ap.json"))
life=d.get("lifecycle") or d
print("approve", d.get("message") or life.get("message"), life.get("businessStatus"))
PY
curl -sS -o /tmp/sc.json -w "sched=%{http_code}\n" -X POST \
  "$BASE/policy-studio/documents/$DOC/lifecycle/schedule" \
  -H "Content-Type: application/json" -H "X-Tenant-Id: staging" \
  -d '{"products":["DIGILEAP"],"effectiveFrom":"2026-09-01","effectiveUntil":"2027-08-31","businessDate":"2026-09-15"}'
python3 - <<'PY'
import json
d=json.load(open("/tmp/sc.json"))
life=d.get("lifecycle") or d
print("sched", d.get("message") or life.get("message"), life.get("businessStatus"),
      "allowCanonical", life.get("allowCanonicalAuthority"),
      "prod", life.get("productionAuthority"))
PY
curl -sS "$BASE/policy-studio/documents/$DOC/lifecycle" -H "X-Tenant-Id: staging" -o /tmp/fin.json
python3 - <<'PY'
import json
d=json.load(open("/tmp/fin.json"))
print("FINAL", d.get("businessStatus"))
print("primary", d.get("primaryAction"))
print("shadow", d.get("shadowBoundary"))
print("allowCanonical", d.get("allowCanonicalAuthority"))
PY
