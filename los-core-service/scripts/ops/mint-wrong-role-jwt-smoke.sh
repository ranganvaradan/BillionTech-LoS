#!/bin/bash
# Mint OPERATIONS JWT with dry-run HMAC secret; admin API must 403.
set -eu
BASE=http://127.0.0.1:18083
SECRET='dry-run-hmac-secret-32chars-minimum!!'
python3 - <<'PY' > /tmp/ops_tok.txt
import base64, hashlib, hmac, json, time
secret = b'dry-run-hmac-secret-32chars-minimum!!'
def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b'=').decode()
header = b64(json.dumps({"alg":"HS256","typ":"JWT"},separators=(',',':')).encode())
now = int(time.time())
claims = {
  "sub":"a1000000-0000-0000-0000-000000000099",
  "uid":"a1000000-0000-0000-0000-000000000099",
  "role":"OPERATIONS",
  "roles":["OPERATIONS"],
  "tenantId":"00000000-0000-0000-0000-000000000001",
  "iat": now,
  "exp": now + 3600,
  "iss":"los-core"
}
payload = b64(json.dumps(claims,separators=(',',':')).encode())
sig = b64(hmac.new(secret, f'{header}.{payload}'.encode(), hashlib.sha256).digest())
print(f'{header}.{payload}.{sig}', end='')
PY
TOK=$(tr -d '\r\n' </tmp/ops_tok.txt)
echo "ops_tokenLen=${#TOK}"
APPS=$(curl -sS -o /tmp/ops_apps.json -w "%{http_code}" "$BASE/api/v1/applications" -H "Authorization: Bearer $TOK")
ADMIN=$(curl -sS -o /tmp/ops_admin.json -w "%{http_code}" -X POST \
  "$BASE/api/v1/admin/live-readiness/customer-go-live" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}')
DEC=$(curl -sS -o /tmp/ops_dec.json -w "%{http_code}" -X POST "$BASE/api/v1/security/decrypt" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' -d '{"value":"x"}')
echo "ops_apps=$APPS ops_admin=$ADMIN ops_decrypt=$DEC"
test "$APPS" = "200" -o "$APPS" = "403"
test "$ADMIN" = "403"
test "$DEC" = "403"
echo "WRONG_ROLE_SMOKE_PASSED"
