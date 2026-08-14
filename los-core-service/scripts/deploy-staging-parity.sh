#!/usr/bin/env bash
# Staging parity deploy — ONE release to INTERNAL (:80/:8083) and CLIENT (:8085/:8084).
# Keeps DBs isolated. Exposes an explicit maintenance window. Health-gates before success.
set -euo pipefail

INTERNAL_APP=/opt/billiontech/apps/billiontechlos
CLIENT_APP=/opt/billiontech/apps/billiontechlos-client
RELEASE_DIR=${RELEASE_DIR:-/opt/billiontech/releases/los-parity}
EXPECTED_SHA=${EXPECTED_SHA:?EXPECTED_SHA required}
export INTERNAL_APP CLIENT_APP
MAINTENANCE_MSG="MAINTENANCE WINDOW: staging cores will restart; UAT may see temporary unavailability until health gate passes."

log() { echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"; }
fail() { echo "ERROR: $*" >&2; exit 1; }

wait_health() {
  local port=$1 name=$2
  local i
  for i in $(seq 1 90); do
    if curl -sf -m 3 "http://127.0.0.1:${port}/actuator/health" >/dev/null; then
      log "HEALTH_OK ${name} :${port}"
      return 0
    fi
    sleep 3
  done
  fail "health gate failed for ${name} :${port}"
}

assert_actuator_sha() {
  local port=$1
  local full
  full=$(curl -sS -m 5 "http://127.0.0.1:${port}/actuator/info" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("git",{}).get("commit",{}).get("id",{}).get("full",""))')
  [[ "$full" == "$EXPECTED_SHA" ]] || fail "actuator :${port} SHA='$full' expected '$EXPECTED_SHA'"
  log "ACTUATOR_SHA_OK :${port}=$full"
}

[[ -d "$RELEASE_DIR" ]] || fail "RELEASE_DIR missing: $RELEASE_DIR"
[[ -f "$RELEASE_DIR/MANIFEST.txt" ]] || fail "MANIFEST.txt missing"
[[ -d "$RELEASE_DIR/ui-dist" ]] || fail "ui-dist missing"
grep -q "SOURCE_SHA=$EXPECTED_SHA" "$RELEASE_DIR/MANIFEST.txt" || fail "manifest SHA mismatch"

log "$MAINTENANCE_MSG"
log "INTERNAL target: frontend ${INTERNAL_APP}/ui-dist + core :8083 (los_core_staging)"
log "CLIENT target: frontend ${CLIENT_APP}/ui-dist + core :8084 (los_core_client)"

TS=$(date -u +%Y%m%dT%H%M%SZ)

# --- Frontend: same artifact to both ---
for APP in "$INTERNAL_APP" "$CLIENT_APP"; do
  [[ -d "$APP" ]] || fail "app dir missing: $APP"
  if [[ -d "$APP/ui-dist" ]]; then
    cp -a "$APP/ui-dist" "$APP/ui-dist.prev.$TS"
  fi
  rm -rf "$APP/ui-dist.new"
  mkdir -p "$APP/ui-dist.new"
  cp -a "$RELEASE_DIR/ui-dist/." "$APP/ui-dist.new/"
done

# runtime-env.js differs by surface (same hashed JS)
INTERNAL_TOKEN_INTERNAL=$(grep -E '^CREDIT_INTELLIGENCE_INTERNAL_TOKEN=' "$INTERNAL_APP/.env" 2>/dev/null | head -1 | cut -d= -f2- || true)
INTERNAL_TOKEN_CLIENT=$(grep -E '^CREDIT_INTELLIGENCE_INTERNAL_TOKEN=' "$CLIENT_APP/.env" 2>/dev/null | head -1 | cut -d= -f2- || true)

export INTERNAL_TOKEN_INTERNAL INTERNAL_TOKEN_CLIENT
python3 - <<'PY'
import json, os
from pathlib import Path

def write(path, surface, label, token):
    payload = {
        "surface": surface,
        "label": label,
        "internalToken": token or None,
    }
    # Emit as JS assigning a JSON-literal object (safe escaping)
    Path(path).write_text(
        "window.__BT_RUNTIME__ = " + json.dumps(payload, ensure_ascii=False) + ";\n",
        encoding="utf-8",
    )

write(
    os.environ["INTERNAL_APP"] + "/ui-dist.new/runtime-env.js",
    "INTERNAL",
    "STAGING — INTERNAL",
    os.environ.get("INTERNAL_TOKEN_INTERNAL") or None,
)
write(
    os.environ["CLIENT_APP"] + "/ui-dist.new/runtime-env.js",
    "CLIENT",
    "STAGING — CLIENT TEST",
    os.environ.get("INTERNAL_TOKEN_CLIENT") or None,
)
print("runtime-env written")
PY

rm -rf "$INTERNAL_APP/ui-dist" && mv "$INTERNAL_APP/ui-dist.new" "$INTERNAL_APP/ui-dist"
rm -rf "$CLIENT_APP/ui-dist" && mv "$CLIENT_APP/ui-dist.new" "$CLIENT_APP/ui-dist"
log "FRONTEND_DEPLOYED identical assets + per-surface runtime-env.js"

# --- Core: build once with SHA args, then recreate both ---
cd "$INTERNAL_APP"
ABBREV=$(printf '%s' "$EXPECTED_SHA" | cut -c1-7)
BUILD_TIME=$(date -u +%Y-%m-%dT%H:%M:%SZ)
COMMIT_TIME=${COMMIT_TIME:-$BUILD_TIME}
log "Building core image with GIT_COMMIT=$EXPECTED_SHA (maintenance window active)"
docker compose build \
  --build-arg "GIT_COMMIT=$EXPECTED_SHA" \
  --build-arg "GIT_COMMIT_ABBREV=$ABBREV" \
  --build-arg "GIT_BRANCH=reconcile/laptop-layout-2026-08" \
  --build-arg "GIT_COMMIT_TIME=$COMMIT_TIME" \
  --build-arg "GIT_BUILD_TIME=$BUILD_TIME" \
  los-core

log "Recreating INTERNAL core (los_core_staging)…"
docker compose up -d --no-deps los-core
wait_health 8083 INTERNAL
assert_actuator_sha 8083

log "Recreating CLIENT core (los_core_client) from SAME image…"
cd "$CLIENT_APP"
docker compose up -d --no-deps --force-recreate los-core-client
wait_health 8084 CLIENT
assert_actuator_sha 8084

# Image identity
IMG=$(docker inspect billiontechlos-core --format '{{.Image}}')
IMG2=$(docker inspect billiontechlos-core-client --format '{{.Image}}')
[[ "$IMG" == "$IMG2" ]] || fail "core image mismatch $IMG vs $IMG2"

echo "$EXPECTED_SHA" > "$INTERNAL_APP/BUILD_SHA.txt"
echo "$EXPECTED_SHA" > "$CLIENT_APP/BUILD_SHA.txt"
echo "$TS" > "$INTERNAL_APP/DEPLOYED_AT.txt"
echo "$TS" > "$CLIENT_APP/DEPLOYED_AT.txt"
cp "$RELEASE_DIR/MANIFEST.txt" "$INTERNAL_APP/RELEASE_MANIFEST.txt"
cp "$RELEASE_DIR/MANIFEST.txt" "$CLIENT_APP/RELEASE_MANIFEST.txt"

log "MAINTENANCE WINDOW CLOSED — both surfaces healthy"
log "DEPLOY_OK sha=$EXPECTED_SHA image=$IMG"
