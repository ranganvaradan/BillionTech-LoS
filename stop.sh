#!/bin/bash
# ─────────────────────────────────────────────────────────
# LOS Platform — Stop All Services
# ─────────────────────────────────────────────────────────
# Usage:
#   ./stop.sh              — Stop all services and infrastructure
#   ./stop.sh --keep-infra — Stop services only (keep DB, Redis, etc running)
#   ./stop.sh --docker     — Stop Docker Compose stack
# ─────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

LOG_DIR="$SCRIPT_DIR/logs"
KEEP_INFRA=false
DOCKER_MODE=false

for arg in "$@"; do
  case $arg in
    --keep-infra) KEEP_INFRA=true ;;
    --docker)     DOCKER_MODE=true ;;
  esac
done

# Docker Compose mode
if [ "$DOCKER_MODE" = true ]; then
  echo -e "${BLUE}[LOS] Stopping Docker Compose stack...${NC}"
  docker compose down
  echo -e "${GREEN}[LOS] All containers stopped.${NC}"
  exit 0
fi

# ─── Stop backend services ──────────────────────────────
echo -e "${BLUE}[LOS] Stopping backend services...${NC}"

SERVICES=("discovery-service" "api-gateway" "iam-service" "enrollment-service" "los-core-service" "notification-service" "lms-adapter-service" "bank-statement-service" "frontend")

for svc in "${SERVICES[@]}"; do
  PID_FILE="$LOG_DIR/$svc.pid"
  if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      kill "$PID" 2>/dev/null
      echo -e "${YELLOW}  Stopped $svc (PID $PID)${NC}"
    else
      echo -e "${YELLOW}  $svc was not running (stale PID)${NC}"
    fi
    rm -f "$PID_FILE"
  fi
done

# Also kill any remaining Java processes for LOS (safety net)
pkill -f "los-.*\.jar" 2>/dev/null || true
# Kill any Next.js dev server
pkill -f "next dev" 2>/dev/null || true

echo -e "${GREEN}[OK] All application services stopped${NC}"

# ─── Stop infrastructure ────────────────────────────────
if [ "$KEEP_INFRA" = false ]; then
  echo -e "${BLUE}[LOS] Stopping infrastructure containers...${NC}"
  docker compose -f docker-compose.infra.yml down 2>/dev/null || \
    docker-compose -f docker-compose.infra.yml down 2>/dev/null || true
  echo -e "${GREEN}[OK] Infrastructure stopped${NC}"
else
  echo -e "${YELLOW}[LOS] Keeping infrastructure running (--keep-infra)${NC}"
fi

echo ""
echo -e "${GREEN}[LOS] All services stopped.${NC}"
echo -e "${YELLOW}[LOS] Note: Database data is persisted in Docker volumes.${NC}"
echo -e "${YELLOW}      To remove volumes: docker compose -f docker-compose.infra.yml down -v${NC}"
