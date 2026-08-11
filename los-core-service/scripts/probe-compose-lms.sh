#!/bin/bash
set -eu
curl -sS -m 60 -X POST http://127.0.0.1:8083/api/v1/admin/live-readiness/product-configuration/compose \
  -H 'Content-Type: application/json' \
  -H 'X-User-Role: ADMINISTRATOR' \
  -H 'X-User-Id: a1000000-0000-0000-0000-000000000001' \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN"}' > /tmp/ph1_compose.json
python3 -c 'import json;d=json.load(open("/tmp/ph1_compose.json")); print("status",d.get("status")); print("ready",d.get("ready")); print("blockers",d.get("goLiveBlockers")); c=d.get("compose") or {}; print("lms", json.dumps(c.get("lms"), indent=2)[:1200])'
