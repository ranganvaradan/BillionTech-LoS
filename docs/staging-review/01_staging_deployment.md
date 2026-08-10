# 01 — Staging deployment

**Audience:** Platform / Credit ops preparing CEO review  
**Environment:** Isolated staging only — never production DB

---

## Prerequisites

- PostgreSQL reachable locally or via Docker
- JDK 17+ and Maven for `los-core-service`
- Node 20+ for `ui-service`
- Optional Redis / Rabbit if your local stack requires them (same defaults as base `application.yml`)

## Create database

Recommended isolated Docker stack (avoids clashing with other local Postgres on 5432):

```bash
docker run -d --name los-core-staging-pg \
  -e POSTGRES_USER=los_admin -e POSTGRES_PASSWORD=los_secret_2026 \
  -e POSTGRES_DB=los_core_staging -p 55432:5432 postgres:16-alpine

docker run -d --name los-core-staging-redis -p 6379:6379 \
  redis:7-alpine redis-server --requirepass los_redis_2026

docker run -d --name los-core-staging-rabbit -p 5672:5672 -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=los_rabbit -e RABBITMQ_DEFAULT_PASS=los_rabbit_2026 \
  rabbitmq:3-management-alpine
```

Or with existing Postgres:

```bash
createdb los_core_staging
```

```sql
CREATE DATABASE los_core_staging;
```

Copy env template:

```bash
cp infra/los/.env.staging.example infra/los/.env.staging
# edit POSTGRES_* if needed — POSTGRES_DB must stay los_core_staging
# default example uses POSTGRES_PORT=55432
```

## Start backend (staging profile)

Requires Postgres DB `los_core_staging` (see `.env.staging.example`; recommended host port **55432**). Redis/Rabbit required by base `application.yml` defaults.

```bash
cd los-core-service
# PowerShell
$env:POSTGRES_PORT='55432'
$env:POSTGRES_DB='los_core_staging'
$env:POSTGRES_USER='los_admin'
$env:POSTGRES_PASSWORD='los_secret_2026'
$env:SPRING_PROFILES_ACTIVE='staging'
mvn -DskipTests spring-boot:run "-Dspring-boot.run.profiles=staging"
```

Confirm:

- Profile active: `staging`
- Port `8083` (default)
- Flyway migrates against `los_core_staging` (V86–latest)
- `credit-intelligence.staging-demo.enabled=true`
- `credit-intelligence.cutover.allow-canonical-authority=false`

Health check:

```bash
curl -s http://localhost:8083/api/v1/internal/credit-intelligence/staging-demo/health
```

Expected JSON includes `allowCanonicalAuthority: false` and fixture safety stamps.

**Verified in CI/dev machine:** `mvn -DskipTests compile` SUCCESS; `StagingDemoWorkspaceTest` 3/3 PASS (CASE_A–E workspaces). Full Spring Boot start verified against dedicated Postgres `los_core_staging` on port **55432** with Redis/Rabbit staging containers; Flyway **V86→V107** applied; `GET .../staging-demo/health` returns `allowCanonicalAuthority: false`.

### Migration fix applied for clean installs

`V98` previously indexed `ci_bank_transaction(application_id)` — that column does not exist (it is on `ci_bank_account`). Indexes were corrected so fresh staging DBs migrate cleanly.

### JPA scan fix

`LosCoreServiceApplication` EntityScan / EnableJpaRepositories now include `policy`, `decision`, `aiunderwriter`, and `cutover` packages (required for Decision Engine admin beans at startup).

## Start UI

```bash
# UI
cd ui-service
npm run dev
```

Open staff UI (typically `http://localhost:5173/los/`), log in as staff, use sidebar **Credit Intelligence**.

Vite proxies `/los/api` → backend `/api` (default target `http://localhost:8083`).

## What staging enables

| Flag | Value | Purpose |
|------|-------|---------|
| `los.security.local-dev-permit-all` | true | Isolated staging review only |
| `credit-intelligence.staging-demo.enabled` | true | CEO demo aggregate APIs |
| `validation` / shadow engines / stub AI | true | Fixture workspace |
| `allow-canonical-authority` | **false** | Never production-authoritative |

See [05_review_checklist.md](./05_review_checklist.md). Security: staging uses `local-dev-permit-all` for isolated review only; never production DB; `allow-canonical-authority` remains false; mask PII in UI.
