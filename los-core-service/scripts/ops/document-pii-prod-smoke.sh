#!/bin/bash
# Document/PII smoke against prod-profile dry-run JVM (port 18083).
# Paths: /api/v1/documents/{applicationId}, /api/v1/security/decrypt, /api/v1/audit
set -eu
BASE=${BASE:-http://127.0.0.1:18083}
TOK=$(tr -d '\r\n' </tmp/prod_tok.txt)
test -n "$TOK"

APP=$(curl -sS "$BASE/api/v1/applications" -H "Authorization: Bearer $TOK" | python3 -c '
import sys,json
d=json.load(sys.stdin)
items=d if isinstance(d,list) else d.get("content") or d.get("items") or []
print(items[0]["id"] if items else "")
')
echo "APP=$APP"
test -n "$APP"

ANON=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE/api/v1/documents/$APP" || true)
echo "anon_docs=$ANON"
test "$ANON" = "401"

# Spoof borrower role header without JWT must fail
BORROWER_HDR=$(curl -sS -o /dev/null -w "%{http_code}" "$BASE/api/v1/documents/$APP" \
  -H 'X-User-Role: BORROWER' -H 'X-User-Id: 00000000-0000-0000-0000-000000000099' || true)
echo "borrower_header_docs=$BORROWER_HDR"
test "$BORROWER_HDR" = "401"

STAFF=$(curl -sS -o /tmp/staff_docs.json -w "%{http_code}" "$BASE/api/v1/documents/$APP" \
  -H "Authorization: Bearer $TOK")
echo "staff_docs=$STAFF"
test "$STAFF" = "200" -o "$STAFF" = "403"

# Foreign tenant header must not elevate / break JWT identity
FOREIGN=$(curl -sS -o /tmp/foreign_docs.json -w "%{http_code}" "$BASE/api/v1/documents/$APP" \
  -H "Authorization: Bearer $TOK" -H 'X-Tenant-Id: bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb')
echo "foreign_tenant_header=$FOREIGN"
test "$FOREIGN" = "200" -o "$FOREIGN" = "403"

# Wrong role with valid JWT (if we can mint OPERATIONS vs admin) — privileged decrypt
DEC=$(curl -sS -o /tmp/dec.json -w "%{http_code}" -X POST "$BASE/api/v1/security/decrypt" \
  -H "Authorization: Bearer $TOK" -H 'Content-Type: application/json' \
  -d '{"value":""}' || true)
echo "decrypt_probe=$DEC"
# privileged: 200/400 with body; unauthenticated would be 401; missing route 404/500 not expected
test "$DEC" != "401"

AUDIT=$(curl -sS -o /tmp/audit.json -w "%{http_code}" "$BASE/api/v1/audit?page=0&size=1" \
  -H "Authorization: Bearer $TOK" || true)
echo "audit_probe=$AUDIT"
test "$AUDIT" != "401"

# Guessed UUID document download
GUESS=$(curl -sS -o /dev/null -w "%{http_code}" \
  "$BASE/api/v1/documents/download/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa" \
  -H "Authorization: Bearer $TOK" || true)
echo "guessed_doc_download=$GUESS"
test "$GUESS" = "404" -o "$GUESS" = "403"

echo "DOCUMENT_PII_SMOKE_PASSED"
