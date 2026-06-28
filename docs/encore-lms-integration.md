# Encore LMS integration (LOS 2.0)

## Architecture

LOS integrates with **Encore LMS over remote HTTP**. There is no local vendor Encore server, MySQL, or static UI in the Docker stack.

| Component | Role |
|-----------|------|
| `com.los.encore.client.*` in **los-core-service** | HTTP client (`EncoreHttpTransport`, `DefaultEncoreLmsApi`) |
| Remote Encore API | Configured via `ENCORE_BASE_URL` + Basic Auth |

See `external-services/README.md` for environment variables.

## Configuration

### LOS `los-core-service` → remote HTTP

| Variable | Purpose |
|----------|---------|
| `ENCORE_BASE_URL` | Encore API base URL (Credinnov sandbox: `http://credinnov-sandbox.senseitech.com/credinnov-encore-server/`) |
| `ENCORE_API_USERNAME` / `ENCORE_API_PASSWORD` | Basic Auth to Encore webservices |
| `LMS_ENCORE_SYNC_ENABLED` / `LMS_ENCORE_SYNC_CRON` | Scheduled summary sync (`EncoreSummarySyncJob` in los-core) |
| `LMS_CALLBACK_HMAC_SECRET` | Optional HMAC for repayment callback |

Spring properties: `los.lms.encore.*` and `los.lms.callback.*` in `application.yml`.

Copy from `.env.example` into `.env.prod` for deployment.

## LOS core

`los-core-service` hosts LMS controllers and calls Encore over HTTP. There is **no** separate LMS microservice and **no** vendor Docker containers.

## Gateway

Public LMS routes target **`lb://los-core-service`** (route id `encore-lms`, path `/api/v1/lms/**`).

## Legacy mapping

See [lms-integration-mapping.md](lms-integration-mapping.md).
