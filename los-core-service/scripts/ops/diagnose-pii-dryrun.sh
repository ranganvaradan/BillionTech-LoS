#!/bin/bash
set -eu
BASE=http://127.0.0.1:18083
TOK=$(tr -d '\r\n' </tmp/prod_tok.txt)
APP=25b3574c-3e88-443f-bd76-337bb3fc1752
echo "tokenLen=${#TOK}"
curl -sS -o /tmp/docs_body.json -w "docs=%{http_code}\n" \
  "$BASE/api/v1/applications/$APP/documents" -H "Authorization: Bearer ${TOK}"
head -c 1000 /tmp/docs_body.json; echo
curl -sS -o /tmp/dec_body.json -w "dec=%{http_code}\n" -X POST \
  "$BASE/api/v1/admin/security/decrypt" \
  -H "Authorization: Bearer ${TOK}" -H 'Content-Type: application/json' -d '{}'
head -c 500 /tmp/dec_body.json; echo
# wrong role: borrower token if login works
curl -sS -m 20 -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"borrower@example.com","password":"Bltest@123"}' > /tmp/borrower_login.json || true
python3 -c 'import json;d=json.load(open("/tmp/borrower_login.json")); print("borrower",d.get("role"), bool(d.get("accessToken")))' || true
docker logs los-core-prod-dryrun 2>&1 | grep -iE 'Exception|ERROR|documents|decrypt' | tail -30
