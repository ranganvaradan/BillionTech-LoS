#!/bin/bash
# ─────────────────────────────────────────────────────────
# LOS Platform — Full Stack Startup Script
# ─────────────────────────────────────────────────────────
# Usage:
#   ./start.sh              — Start infra + build + run all services + frontend
#   ./start.sh --skip-build — Start without rebuilding (use existing JARs)
#   ./start.sh --docker     — Start everything via Docker Compose
#   ./start.sh --infra-only — Start only infrastructure (Postgres, Redis, RabbitMQ, MinIO)
# ─────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

LOG_DIR="$SCRIPT_DIR/logs"
mkdir -p "$LOG_DIR"

# ─── Parse arguments ────────────────────────────────────
SKIP_BUILD=false
DOCKER_MODE=false
INFRA_ONLY=false

for arg in "$@"; do
  case $arg in
    --skip-build)  SKIP_BUILD=true ;;
    --docker)      DOCKER_MODE=true ;;
    --infra-only)  INFRA_ONLY=true ;;
  esac
done

# ─── Docker Compose mode ────────────────────────────────
if [ "$DOCKER_MODE" = true ]; then
  echo -e "${BLUE}[LOS] Starting full stack via Docker Compose...${NC}"
  echo -e "${YELLOW}[LOS] Building all services (this may take 5-10 minutes on first run)...${NC}"
  docker compose up --build -d
  echo ""
  echo -e "${GREEN}[LOS] All services starting via Docker Compose!${NC}"
  echo -e "  Eureka Dashboard:  http://localhost:8761"
  echo -e "  API Gateway:       http://localhost:8080"
  echo -e "  Frontend:          http://localhost:3000"
  echo -e "  RabbitMQ Console:  http://localhost:15672 (los_rabbit/los_rabbit_2026)"
  echo -e "  MinIO Console:     http://localhost:9001  (los_minio_admin/los_minio_secret_2026)"
  echo ""
  echo -e "${YELLOW}[LOS] Run 'docker compose logs -f' to watch logs${NC}"
  exit 0
fi

# ─── Check prerequisites ────────────────────────────────
echo -e "${BLUE}[LOS] Checking prerequisites...${NC}"

check_cmd() {
  if ! command -v "$1" &>/dev/null; then
    echo -e "${RED}[ERROR] $1 is not installed. $2${NC}"
    exit 1
  fi
}

check_cmd "java" "Install Java 21: https://adoptium.net/"
check_cmd "mvn" "Install Maven 3.9+: https://maven.apache.org/download.cgi"
check_cmd "node" "Install Node.js 18+: https://nodejs.org/"
check_cmd "docker" "Install Docker: https://docs.docker.com/get-docker/"

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VER" -lt 21 ] 2>/dev/null; then
  echo -e "${RED}[ERROR] Java 21+ required. Found Java $JAVA_VER${NC}"
  exit 1
fi
echo -e "${GREEN}[OK] Java $JAVA_VER, Maven, Node.js, Docker found${NC}"

# ─── Start Infrastructure ───────────────────────────────
echo -e "${BLUE}[LOS] Starting infrastructure (PostgreSQL, Redis, RabbitMQ, MinIO)...${NC}"
docker compose -f docker-compose.infra.yml up -d 2>/dev/null || \
  docker-compose -f docker-compose.infra.yml up -d

echo -e "${YELLOW}[LOS] Waiting for infrastructure to be healthy...${NC}"
sleep 5

# Wait for PostgreSQL
for i in $(seq 1 30); do
  if docker exec los-postgres pg_isready -U los_admin &>/dev/null; then
    echo -e "${GREEN}[OK] PostgreSQL is ready${NC}"
    break
  fi
  if [ $i -eq 30 ]; then
    echo -e "${RED}[ERROR] PostgreSQL failed to start${NC}"
    exit 1
  fi
  sleep 2
done

# Wait for Redis
for i in $(seq 1 15); do
  if docker exec los-redis redis-cli -a los_redis_2026 ping 2>/dev/null | grep -q PONG; then
    echo -e "${GREEN}[OK] Redis is ready${NC}"
    break
  fi
  sleep 2
done

# Wait for RabbitMQ
for i in $(seq 1 30); do
  if docker exec los-rabbitmq rabbitmq-diagnostics check_running &>/dev/null; then
    echo -e "${GREEN}[OK] RabbitMQ is ready${NC}"
    break
  fi
  sleep 3
done

echo -e "${GREEN}[OK] All infrastructure is ready${NC}"

if [ "$INFRA_ONLY" = true ]; then
  echo ""
  echo -e "${GREEN}[LOS] Infrastructure is running. Ports:${NC}"
  echo -e "  PostgreSQL:        localhost:5432  (los_admin/los_secret_2026)"
  echo -e "  Redis:             localhost:6379  (password: los_redis_2026)"
  echo -e "  RabbitMQ:          localhost:5672  (los_rabbit/los_rabbit_2026)"
  echo -e "  RabbitMQ Console:  localhost:15672"
  echo -e "  MinIO:             localhost:9000  (los_minio_admin/los_minio_secret_2026)"
  echo -e "  MinIO Console:     localhost:9001"
  exit 0
fi

# ─── Build Backend ──────────────────────────────────────
if [ "$SKIP_BUILD" = false ]; then
  echo ""
  echo -e "${BLUE}[LOS] Building all backend services (Maven)...${NC}"
  mvn clean package -DskipTests -q 2>&1 | tail -5
  echo -e "${GREEN}[OK] All services built successfully${NC}"
fi

# ─── Start Backend Services ─────────────────────────────
echo ""
echo -e "${BLUE}[LOS] Starting backend services...${NC}"

start_service() {
  local name=$1
  local dir=$2
  local port=$3
  local jar

  jar=$(find "$dir/target" -name "*.jar" -not -name "*-sources.jar" | head -1)
  if [ -z "$jar" ]; then
    echo -e "${RED}[ERROR] No JAR found for $name. Run without --skip-build first.${NC}"
    return 1
  fi

  echo -e "${YELLOW}  Starting $name on port $port...${NC}"
  nohup java -jar "$jar" \
    --server.port=$port \
    > "$LOG_DIR/$name.log" 2>&1 &
  echo $! > "$LOG_DIR/$name.pid"
}

# 1. Discovery Service (must start first)
start_service "discovery-service" "services/discovery-service" 8761
echo -e "${YELLOW}  Waiting for Eureka to initialize (20s)...${NC}"
sleep 20

# Verify Eureka is up
for i in $(seq 1 15); do
  if curl -s http://localhost:8761/actuator/health 2>/dev/null | grep -q '"UP"'; then
    echo -e "${GREEN}[OK] Discovery Service is UP${NC}"
    break
  fi
  if [ $i -eq 15 ]; then
    echo -e "${RED}[ERROR] Discovery Service failed to start. Check logs/discovery-service.log${NC}"
    exit 1
  fi
  sleep 3
done

# 2. API Gateway
start_service "api-gateway" "services/api-gateway" 8080
sleep 5

# 3. Remaining services (can start in parallel)
start_service "iam-service" "services/iam-service" 8081
start_service "enrollment-service" "services/enrollment-service" 8082
start_service "los-core-service" "services/los-core-service" 8083
start_service "notification-service" "services/notification-service" 8084
start_service "lms-adapter-service" "services/lms-adapter-service" 8085
start_service "bank-statement-service" "services/bank-statement-service" 8086

echo -e "${YELLOW}  Waiting for services to register with Eureka (30s)...${NC}"
sleep 30

# ─── Verify Backend Health ──────────────────────────────
echo ""
echo -e "${BLUE}[LOS] Verifying service health...${NC}"

check_health() {
  local name=$1
  local port=$2
  if curl -s "http://localhost:$port/actuator/health" 2>/dev/null | grep -q '"UP"'; then
    echo -e "${GREEN}  [UP] $name (:$port)${NC}"
  else
    echo -e "${RED}  [DOWN] $name (:$port) — check logs/$name.log${NC}"
  fi
}

check_health "discovery-service" 8761
check_health "api-gateway" 8080
check_health "iam-service" 8081
check_health "enrollment-service" 8082
check_health "los-core-service" 8083
check_health "notification-service" 8084
check_health "lms-adapter-service" 8085
check_health "bank-statement-service" 8086

# ─── Start Frontend ─────────────────────────────────────
echo ""
echo -e "${BLUE}[LOS] Starting frontend...${NC}"
cd frontend
if [ ! -d "node_modules" ]; then
  echo -e "${YELLOW}  Installing npm dependencies...${NC}"
  npm install --silent
fi
echo -e "${YELLOW}  Starting Next.js dev server on port 3000...${NC}"
nohup npm run dev > "$LOG_DIR/frontend.log" 2>&1 &
echo $! > "$LOG_DIR/frontend.pid"
cd ..

sleep 5

# ─── Done! ──────────────────────────────────────────────
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          LOS Platform v2.0 — All Services Running!         ║${NC}"
echo -e "${GREEN}╠══════════════════════════════════════════════════════════════╣${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║  Frontend:          http://localhost:3000                   ║${NC}"
echo -e "${GREEN}║  API Gateway:       http://localhost:8080                   ║${NC}"
echo -e "${GREEN}║  Eureka Dashboard:  http://localhost:8761                   ║${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║  Login:  admin / Admin@LOS2026                             ║${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║  Services:                                                 ║${NC}"
echo -e "${GREEN}║    IAM:           http://localhost:8081/swagger-ui.html     ║${NC}"
echo -e "${GREEN}║    Enrollment:    http://localhost:8082/swagger-ui.html     ║${NC}"
echo -e "${GREEN}║    LOS Core:      http://localhost:8083/swagger-ui.html     ║${NC}"
echo -e "${GREEN}║    Notification:  http://localhost:8084/swagger-ui.html     ║${NC}"
echo -e "${GREEN}║    LMS Adapter:   http://localhost:8085/swagger-ui.html     ║${NC}"
echo -e "${GREEN}║    Bank Statement: http://localhost:8086/swagger-ui.html    ║${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║  Infrastructure:                                           ║${NC}"
echo -e "${GREEN}║    PostgreSQL:     localhost:5432                           ║${NC}"
echo -e "${GREEN}║    Redis:          localhost:6379                           ║${NC}"
echo -e "${GREEN}║    RabbitMQ:       http://localhost:15672                   ║${NC}"
echo -e "${GREEN}║    MinIO Console:  http://localhost:9001                    ║${NC}"
echo -e "${GREEN}║                                                            ║${NC}"
echo -e "${GREEN}║  Logs:  ./logs/<service-name>.log                          ║${NC}"
echo -e "${GREEN}║  Stop:  ./stop.sh                                          ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
