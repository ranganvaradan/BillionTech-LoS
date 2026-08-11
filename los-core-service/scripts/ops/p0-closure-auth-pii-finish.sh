#!/bin/bash
set -eu
sed -i 's/\r$//' /tmp/document-pii-prod-smoke.sh
bash /tmp/document-pii-prod-smoke.sh
echo '---WRONG-ROLE---'
curl -sS -o /tmp/ops_login.json -w 'login=%{http_code}\n' -X POST http://127.0.0.1:18083/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"operations@billionloans.com","password":"Bltest@123"}' || true
python3 - <<'PY'
import json
try:
    d=json.load(open('/tmp/ops_login.json'))
    print('role', d.get('role'), 'token', bool(d.get('accessToken')))
except Exception as e:
    print('parse_fail', e)
PY
TOK=$(python3 -c 'import json;print((json.load(open("/tmp/ops_login.json")).get("accessToken") or "").strip())' 2>/dev/null || true)
if [ -n "$TOK" ]; then
  curl -sS -o /tmp/ops_admin.json -w 'ops_golive=%{http_code}\n' -X POST \
    http://127.0.0.1:18083/api/v1/admin/live-readiness/customer-go-live \
    -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
    -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}'
  head -c 200 /tmp/ops_admin.json; echo
fi
docker exec billiontech-postgres psql -U los_app -d los_core_prod_dryrun -c '\dt' | grep -iE 'user|staff|account' | head -20 || true
