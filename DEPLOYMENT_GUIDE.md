# LOS Platform v2.0 — Local Deployment Guide

> **Your situation:** You already have apps running on ports 3000–3006.
> This guide uses **port 3007** for the frontend to avoid conflicts.
> All backend services use ports 8080–8084 and 8761 — no conflict.

---

## Prerequisites

| Tool | Minimum Version | Check Command | Install |
|------|----------------|---------------|---------|
| **Java** | 21 | `java -version` | [Adoptium](https://adoptium.net/) |
| **Maven** | 3.9+ | `mvn -version` | [Maven](https://maven.apache.org/download.cgi) |
| **Node.js** | 18+ | `node -v` | [Node.js](https://nodejs.org/) |
| **Docker** | 20+ | `docker --version` | [Docker Desktop](https://docs.docker.com/get-docker/) |
| **Docker Compose** | v2 | `docker compose version` | Included with Docker Desktop |

---

## Port Map (What LOS Uses)

| Component | Port | Will it conflict? |
|-----------|------|-------------------|
| Frontend (Next.js) | **3007** (changed from default 3000) | No |
| PostgreSQL | 5432 | Check: `lsof -i :5432` |
| Redis | 6379 | Check: `lsof -i :6379` |
| RabbitMQ | 5672, 15672 | Check: `lsof -i :5672` |
| MinIO | 9000, 9001 | Check: `lsof -i :9000` |
| Eureka (Discovery) | 8761 | Unlikely |
| API Gateway | 8080 | Check: `lsof -i :8080` |
| IAM Service | 8081 | Unlikely |
| Enrollment Service | 8082 | Unlikely |
| LOS Core Service | 8083 | Unlikely — includes LMS `/api/v1/lms/**` → remote Encore (`ENCORE_BASE_URL`) |
| Notification Service | 8084 | Unlikely |

> **If any port conflicts:** See "Troubleshooting" at the bottom.

---

## Option A: Frontend Only (Fastest — No Backend Needed)

This is the fastest way to see the UI. All 20 pages work with built-in mock data.

```bash
# 1. Unzip the project
unzip los-platform-v2.0.zip -d los-platform
cd los-platform/frontend

# 2. Install dependencies
npm install

# 3. Start on port 3007 (avoiding your existing 3000-3006)
npx next dev --port 3007

# 4. Open in browser
#    http://localhost:3007/dashboard
```

**That's it!** You should see the Dashboard with 156 total applications, pipeline charts, and the full sidebar with all 20 routes.

**Login credentials (if you visit /login):** `admin / Admin@LOS2026`

---

## Option B: Full Stack — Step by Step (Backend + Frontend)

### Step 1: Unzip & Navigate

```bash
unzip los-platform-v2.0.zip -d los-platform
cd los-platform
```

### Step 2: Start Infrastructure (Docker)

```bash
# Start PostgreSQL, Redis, RabbitMQ, MinIO in Docker
docker compose -f docker-compose.infra.yml up -d
```

Wait ~30 seconds, then verify:

```bash
# Check all containers are running
docker ps

# You should see 4 containers:
#   los-postgres   (port 5432)
#   los-redis      (port 6379)
#   los-rabbitmq   (ports 5672, 15672)
#   los-minio      (ports 9000, 9001)
```

**Verify health:**
```bash
docker exec los-postgres pg_isready -U los_admin
# Should print: "accepting connections"

docker exec los-redis redis-cli -a los_redis_2026 ping
# Should print: "PONG"
```

> **If port 5432 is already in use** (you have a local PostgreSQL):
> Edit `docker-compose.infra.yml` and change `"5432:5432"` to `"5433:5432"`.
> Then set `export POSTGRES_PORT=5433` before starting services.

### Step 3: Build All Backend Services

```bash
# From the project root (los-platform/)
mvn clean package -DskipTests
```

This takes 2-4 minutes. You should see:
```
[INFO] BUILD SUCCESS
[INFO] 7 modules built successfully
```

> **If Maven build fails:**
> - Ensure `java -version` shows 21+
> - Ensure `JAVA_HOME` points to Java 21
> - Try: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)` (macOS)
> - Or: `export JAVA_HOME=/path/to/java-21` (Linux/Windows)

### Step 4: Start Backend Services (in order)

Open **separate terminal windows/tabs** for each service. Start them in this exact order:

**Terminal 1 — Discovery Service (must start first, wait until ready):**
```bash
cd los-platform
java -jar services/discovery-service/target/*.jar --server.port=8761
```
Wait until you see: `Started DiscoveryServiceApplication`
Then verify: Open http://localhost:8761 — you should see the Eureka dashboard.

**Terminal 2 — API Gateway:**
```bash
cd los-platform
java -jar services/api-gateway/target/*.jar --server.port=8080
```
Wait until: `Started ApiGatewayApplication`

**Terminal 3 — IAM Service:**
```bash
cd los-platform
java -jar services/iam-service/target/*.jar --server.port=8081
```

**Terminal 4 — Enrollment Service:**
```bash
cd los-platform
java -jar services/enrollment-service/target/*.jar --server.port=8082
```

**Terminal 5 — LOS Core Service:**
```bash
cd los-platform
java -jar services/los-core-service/target/*.jar --server.port=8083
```

**Terminal 6 — Notification Service:**
```bash
cd los-platform
java -jar services/notification-service/target/*.jar --server.port=8084
```

Encore LMS is **remote HTTP only**. Set `ENCORE_BASE_URL`, `ENCORE_API_USERNAME`, and `ENCORE_API_PASSWORD` in `.env.prod` (see `external-services/README.md` and `docs/encore-lms-integration.md`). There are no local Encore vendor containers.

### Step 5: Verify Backend Services

After ~30 seconds, check Eureka at http://localhost:8761. You should see the LOS services registered:
- API-GATEWAY
- IAM-SERVICE
- ENROLLMENT-SERVICE
- LOS-CORE-SERVICE
- NOTIFICATION-SERVICE

You can also verify individual health:
```bash
curl http://localhost:8080/actuator/health   # Gateway
curl http://localhost:8081/actuator/health   # IAM
curl http://localhost:8083/actuator/health   # Core (includes LMS integration)
curl http://localhost:8084/actuator/health   # Notification
```

Each should return: `{"status":"UP"}`

### Step 6: Start Frontend

```bash
cd los-platform/frontend
npm install
npx next dev --port 3007
```

Open http://localhost:3007/dashboard

**The frontend will now call the real backend via the API Gateway at port 8080** instead of using mock data.

---

## Option C: Docker Compose — Everything in Containers

This builds all services as Docker images and runs everything in containers.

```bash
cd los-platform

# Build and start everything (takes 5-10 minutes first time)
docker compose up --build -d

# Watch logs
docker compose logs -f

# Frontend will be at http://localhost:3000
# (In Docker mode, port 3000 is used inside the container network.
#  If 3000 conflicts, edit docker-compose.yml line 211:
#  change "3000:3000" to "3007:3000")
```

**To change frontend port in Docker mode:**
Edit `docker-compose.yml`, find the `frontend` service, and change:
```yaml
    ports:
      - "3007:3000"   # <-- change left side to 3007
```

---

## Stopping Everything

### If you used Option B (manual):
```bash
# Stop frontend: Ctrl+C in the terminal running it

# Stop backend services: Ctrl+C in each terminal
# Or kill all Java processes:
pkill -f "los-.*\.jar"

# Stop infrastructure:
cd los-platform
docker compose -f docker-compose.infra.yml down

# To also delete database data (fresh start):
docker compose -f docker-compose.infra.yml down -v
```

### If you used Option C (Docker Compose):
```bash
cd los-platform
docker compose down

# To also delete all data:
docker compose down -v
```

---

## Default Credentials

| Service | Username | Password |
|---------|----------|----------|
| **LOS Admin Login** | `admin` | `Admin@LOS2026` |
| PostgreSQL | `los_admin` | `los_secret_2026` |
| Redis | — | `los_redis_2026` |
| RabbitMQ (http://localhost:15672) | `los_rabbit` | `los_rabbit_2026` |
| MinIO Console (http://localhost:9001) | `los_minio_admin` | `los_minio_secret_2026` |

---

## What to Test (Key Pages)

| URL | What You'll See |
|-----|----------------|
| http://localhost:3007/dashboard | Pipeline summary: 156 total, 49 active, 22 approved, 8 today |
| http://localhost:3007/applications | 8 applications, searchable list |
| http://localhost:3007/applications/new | 4-step new loan wizard |
| http://localhost:3007/co-lending | Partner management (SBI, HDFC, Bajaj Finance) |
| http://localhost:3007/collateral | 6 collateral cards with valuations |
| http://localhost:3007/admin/api-keys | API key management + IP whitelist |
| http://localhost:3007/kanban | Pipeline board with SLA indicators |
| http://localhost:3007/notifications | Notification history with filters |
| http://localhost:3007/reports | Analytics, MIS, Regulatory tabs |
| http://localhost:3007/portal | Customer-facing portal |

**Dark mode:** Click the Moon icon (top-right, next to the bell) to toggle.

---

## Troubleshooting

### Port 5432 already in use (local PostgreSQL)
```bash
# Option 1: Stop your local PostgreSQL
brew services stop postgresql   # macOS
sudo systemctl stop postgresql  # Linux

# Option 2: Use a different port
# Edit docker-compose.infra.yml, change "5432:5432" to "5433:5432"
# Then when starting services, add: --spring.datasource.url=jdbc:postgresql://localhost:5433/los_iam
```

### Port 8080 already in use
```bash
# Find what's using it
lsof -i :8080
# Kill it or start the gateway on a different port:
java -jar services/api-gateway/target/*.jar --server.port=8090
# Update frontend to use the new gateway port:
NEXT_PUBLIC_API_URL=http://localhost:8090 npx next dev --port 3007
```

### Maven build fails with "Java version" error
```bash
# Verify Java 21
java -version   # Must show 21.x.x

# macOS: Set JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Linux: Set JAVA_HOME
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk

# Windows (PowerShell):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
```

### Docker containers won't start
```bash
# Check if Docker is running
docker info

# Check for port conflicts
docker ps -a    # See all containers (including stopped)
docker compose -f docker-compose.infra.yml down   # Clean up old containers
docker compose -f docker-compose.infra.yml up -d   # Restart fresh
```

### Services fail to register with Eureka
- Ensure Discovery Service started **first** and shows "Started" in its terminal
- Wait at least 20 seconds after Discovery starts before starting other services
- Check http://localhost:8761 to see which services registered
- Check individual service logs for errors

### Frontend shows blank page or errors
```bash
# Clear Next.js cache and reinstall
cd frontend
rm -rf .next node_modules
npm install
npx next dev --port 3007
```

---

## Quick Reference Commands

```bash
# Start infrastructure only
docker compose -f docker-compose.infra.yml up -d

# Build backend
mvn clean package -DskipTests

# Start frontend on port 3007
cd frontend && npx next dev --port 3007

# Check all service health
for port in 8761 8080 8081 8082 8083 8084; do
  echo -n "Port $port: "
  curl -s http://localhost:$port/actuator/health 2>/dev/null | grep -o '"status":"[A-Z]*"' || echo "DOWN"
done

# Stop all Java services
pkill -f "los-.*\.jar"

# Stop infrastructure
docker compose -f docker-compose.infra.yml down
```

---

## Production EC2: redeploy

On the server (e.g. `/vol/LOS_APP`):

```bash
git pull origin los_plp_integration

docker compose -f docker-compose.prod.yml down
docker volume rm los_app_encore_vendor_mysql_data 2>/dev/null || true

docker compose -f docker-compose.prod.yml up -d --build
```

**UI port:** `los_ui` binds **8080→80** by default (`LOS_UI_HOST_PORT=8080` in `.env.prod`). Host nginx keeps port **80**; proxy `/los/` (or your path) to `http://127.0.0.1:8080/`. Full table: [docs/deployment-port-mapping.md](docs/deployment-port-mapping.md).

Verify:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
# Should NOT list: los_encore_mysql, los_encore_server, los_encore_client

curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8080/
curl -s http://127.0.0.1:8080/api/actuator/health 2>/dev/null || true
```

Ensure `.env.prod` sets `ENCORE_BASE_URL`, `ENCORE_API_USERNAME`, and `ENCORE_API_PASSWORD`. Trigger a small LMS flow in the UI and confirm `los_core` logs show HTTP to your remote Encore host.
