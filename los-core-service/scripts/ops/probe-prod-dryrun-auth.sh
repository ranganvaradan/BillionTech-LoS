#!/bin/bash
set -eu
BASE=http://127.0.0.1:18083

ANON=$(curl -sS -o /dev/null -w "%{http_code}" -m 20 "$BASE/api/v1/applications")
echo "anonymous=$ANON"
test "$ANON" = "401"

SPOOF_H=$(curl -sS -o /dev/null -w "%{http_code}" -m 20 "$BASE/api/v1/applications" \
  -H 'X-User-Id: spoof' -H 'X-User-Role: SUPER_ADMIN')
echo "spoof_headers=$SPOOF_H"
test "$SPOOF_H" = "401"

curl -sS -m 20 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"mohit@billionloans.com","password":"Bltest@123"}' > /tmp/prod_login2.json
python3 -c 'import json;d=json.load(open("/tmp/prod_login2.json")); t=(d.get("accessToken") or "").strip(); print("role",d.get("role")); open("/tmp/prod_tok2.txt","w").write(t); open("/tmp/prod_tok.txt","w").write(t)'
TOK=$(tr -d '\r\n' </tmp/prod_tok2.txt)
echo "tokenLen=${#TOK}"
test -n "$TOK"

APPS=$(curl -sS -o /tmp/prod_apps_body.json -w "%{http_code}" -m 20 \
  "$BASE/api/v1/applications" -H "Authorization: Bearer ${TOK}")
echo "jwt_apps=$APPS"
head -c 300 /tmp/prod_apps_body.json; echo
# JWT must authenticate; 200/403 acceptable (RBAC), never JWT_INVALID 401
test "$APPS" != "401"

# JWT wins over conflicting spoofed role header (must not become JWT_INVALID)
SPOOF=$(curl -sS -o /tmp/jwt_spoof.json -w "%{http_code}" -m 20 \
  "$BASE/api/v1/applications" -H "Authorization: Bearer ${TOK}" \
  -H 'X-User-Role: SUPER_ADMIN' -H 'X-User-Id: 00000000-0000-0000-0000-000000000099')
echo "jwt_plus_spoof=$SPOOF"
test "$SPOOF" != "401"

curl -sS -o /tmp/ph.json -w "health=%{http_code}\n" "$BASE/actuator/health"
curl -sS -o /tmp/golive.json -w "golive=%{http_code}\n" -X POST "$BASE/api/v1/admin/live-readiness/customer-go-live" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"borrowerType":"COMPANY","loanProduct":"TERM_LOAN","customerConfigSupplied":false}'
python3 -c 'import json;d=json.load(open("/tmp/golive.json")); print("safe",d.get("safeToGoLiveAnswer"),"failed",d.get("failedCheckCount"),"ready",d.get("ready"))'
echo "PROD_AUTH_SMOKE_OK"
