# LOS Platform v2.0

Modernized, API-first Loan Origination System built with Spring Boot 3.3, **Java 17 (LTS)**, Next.js 16, PostgreSQL 16, Redis 7.2, and RabbitMQ 3.13.

## Architecture

```
                 ┌─────────────────────┐
                 │   Frontend (3000)   │  Next.js 16 + TypeScript + Tailwind CSS v4
                 │     20 Routes       │  Dashboard, Applications, KYC, Workflows,
                 └─────────┬───────────┘  Co-Lending, Collateral, Reports, Portal...
                           │
                    API Gateway (8080)     JWT validation, CORS, rate limiting, OWASP headers
                    ┌──────┼──────────┐
                    │      │          │
               IAM (8081)  │    Enrollment (8082)
            Auth, RBAC,    │    Registration, OTP,
            2FA, API Keys  │    DigiLocker
                           │
                    LOS Core (8083)       Loan lifecycle, KYC, Workflow Engine,
                           │              Credit Decision, eSign, KFS, AA, NACH,
                  ┌────────┼────────┐     Co-Lending, Collateral, OCR, Reports,
                  │        │        │     LMS handover + /api/v1/lms/** (→ vendor Encore HTTP)
           Notification  │   Discovery
             (8084)      │   (8761)
           SMS/Email/    │   Eureka
           WhatsApp      │   Registry
```

## Quick Start

### Option 1: One-Command Startup (Recommended for Local Dev)

```bash
# Prerequisites: JDK 17, Maven 3.9+, Node.js 18+, Docker

# Start everything (infrastructure + build + all services + frontend)
./start.sh

# Stop everything
./stop.sh
```

**Startup options:**
```bash
./start.sh                # Full startup (build + run everything)
./start.sh --skip-build   # Skip Maven build (use existing JARs)
./start.sh --infra-only   # Start only PostgreSQL, Redis, RabbitMQ, MinIO
./start.sh --docker       # Start everything via Docker Compose

./stop.sh                 # Stop everything
./stop.sh --keep-infra    # Stop services, keep infrastructure running
./stop.sh --docker        # Stop Docker Compose stack
```

### Option 2: Docker Compose (Full Stack)

Production UI listens on **host port 8080** by default (`LOS_UI_HOST_PORT`) so it does not conflict with system nginx on port 80. See [docs/deployment-port-mapping.md](docs/deployment-port-mapping.md).

```bash
# Build and start everything in Docker containers
docker compose up --build -d
# UI: http://localhost:8080  |  prod: docker compose -f docker-compose.prod.yml up -d --build

# View logs
docker compose logs -f

# Stop
docker compose down
```

### Option 3: Manual Startup (Step-by-Step)

```bash
# 1. Start infrastructure
docker compose -f docker-compose.infra.yml up -d

# 2. Build all services
mvn clean package -DskipTests

# 3. Start services (in order)
java -jar services/discovery-service/target/*.jar &       # :8761 — wait 20s
java -jar services/api-gateway/target/*.jar &             # :8080
java -jar services/iam-service/target/*.jar &             # :8081
java -jar services/enrollment-service/target/*.jar &      # :8082
java -jar services/notification-service/target/*.jar &    # :8084
java -jar services/los-core-service/target/*.jar &         # :8083 (includes LMS → vendor Encore)

# 4. Start frontend
cd frontend && npm install && npm run dev                 # :3000
```

### Frontend-Only (Mock Data, No Backend Required)

```bash
cd frontend
npm install
npm run dev
# Open http://localhost:3000 — works with built-in mock data
```

## Default Credentials

| Service | Username | Password |
|---------|----------|----------|
| LOS Admin | `admin` | `Admin@LOS2026` |
| PostgreSQL | `los_admin` | `los_secret_2026` |
| Redis | — | `los_redis_2026` |
| RabbitMQ | `los_rabbit` | `los_rabbit_2026` |
| MinIO | `los_minio_admin` | `los_minio_secret_2026` |

## Services

| Service | Port | Description |
|---------|------|-------------|
| Discovery (Eureka) | 8761 | Service registry |
| API Gateway | 8080 | Request routing, JWT validation, rate limiting, security headers |
| IAM Service | 8081 | Authentication, RBAC (5 roles), TOTP 2FA, API keys, IP whitelist, session limits |
| Enrollment Service | 8082 | Customer registration, OTP, assisted registration, DigiLocker |
| LOS Core Service | 8083 | Full loan lifecycle — applications, KYC orchestration, workflow engine, credit decision, documents, eSign, KFS, Account Aggregator, NACH, co-lending, collateral, OCR, reports; **LMS integration** (`/api/v1/lms/**`) calls remote Encore over HTTP (`ENCORE_BASE_URL`, `ENCORE_API_USERNAME`, `ENCORE_API_PASSWORD`) |
| Notification Service | 8084 | SMS, email, WhatsApp (9 templates), in-app alerts, bulk SMS |

## Frontend Routes (20 Pages)

| Route | Description |
|-------|-------------|
| `/login` | Login with TOTP 2FA support |
| `/dashboard` | Pipeline summary, stat cards, recent applications |
| `/applications` | Searchable/filterable application list |
| `/applications/new` | 4-step new application wizard |
| `/applications/[id]` | Application detail with 6 tabs |
| `/kanban` | Pipeline board with SLA indicators |
| `/kyc` | KYC dashboard with progress tracking |
| `/workflows` | Workflow config CRUD with step visualization |
| `/transactions` | Disbursement/repayment tracking |
| `/co-lending` | Partner management, allocations, settlement |
| `/collateral` | Collateral valuation cards |
| `/notifications` | Notification history with filters |
| `/reports` | Analytics, MIS, Regulatory (3 tabs) |
| `/portal` | Customer-facing portal (OTP login, guided KYC, status tracking) |
| `/admin/users` | User management with RBAC |
| `/admin/aggregators` | Aggregator configuration |
| `/admin/api-keys` | API key management + IP whitelist |

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL 16 | 5432 | Primary database (4 schemas: losiam, losenrollment, loscore, losnotification) |
| Redis 7.2 | 6379 | Cache, OTP sessions, rate limiting |
| RabbitMQ 3.13 | 5672 / 15672 | Message queue / Management UI |
| MinIO | 9000 / 9001 | S3-compatible object storage (4 buckets) |
| Prometheus | 9090 | Metrics collection (optional: `--profile monitoring`) |
| Grafana | 3001 | Monitoring dashboards (optional: `--profile monitoring`) |

## API Documentation

Each service exposes Swagger UI when running:
- IAM: http://localhost:8081/swagger-ui.html
- Enrollment: http://localhost:8082/swagger-ui.html
- LOS Core: http://localhost:8083/swagger-ui.html (includes **LMS / Encore** endpoints under `/api/v1/lms/**`)
- Notification: http://localhost:8084/swagger-ui.html

## Tech Stack

- **Java 17 (LTS)**
- **Spring Boot 3.3** + Spring Cloud 2023.0
- **Next.js 16** + TypeScript + Tailwind CSS v4
- **PostgreSQL 16** with JSONB + Flyway migrations
- **Redis 7.2** for caching and session management
- **RabbitMQ 3.13** for event-driven notifications
- **MinIO** for document storage (S3-compatible)
- **SpringDoc OpenAPI 3.1** for API documentation
- **Docker** + Docker Compose for containerized deployment

## Project Structure

```
los-platform/
├── docker-compose.yml           # Full-stack Docker Compose
├── docker-compose.infra.yml     # Infrastructure only
├── init-databases.sql           # PostgreSQL initialization
├── start.sh                     # One-command startup script
├── stop.sh                      # Shutdown script
├── pom.xml                      # Parent Maven POM
├── monitoring/                  # Prometheus config
├── services/
│   ├── discovery-service/       # Eureka (8761)
│   ├── api-gateway/             # Gateway (8080)
│   ├── iam-service/             # IAM (8081)
│   ├── enrollment-service/      # Enrollment (8082)
│   ├── los-core-service/        # Core (8083) + LMS HTTP client → vendor Encore
│   ├── notification-service/    # Notifications (8084)
└── frontend/                    # Next.js 16 (3000)
    ├── src/app/                 # App Router pages
    ├── src/components/          # Shared components
    ├── src/lib/                 # API client, utilities
    └── src/types/               # TypeScript types
```

## Logs

When using `start.sh`, logs are written to `./logs/`:
- `logs/discovery-service.log`
- `logs/api-gateway.log`
- `logs/iam-service.log`
- `logs/enrollment-service.log`
- `logs/los-core-service.log`
- `logs/notification-service.log`
- `logs/los-core-service.log`
- `logs/frontend.log`
