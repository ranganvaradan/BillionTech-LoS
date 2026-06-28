# LMS / Encore integration mapping (legacy bl-core → LOS v2)

Runtime layout:

- **`services/los-core-service`** — LMS integration: `/api/v1/lms/**`, LMS tables on **`los_core`** PostgreSQL; HTTP to **remote** Encore using `EncoreHttpTransport` / `DefaultEncoreLmsApi` (`com.los.encore.client.*` in los-core).

There is **no** standalone `lms-adapter-service` and **no** local vendor Encore Docker stack. Configure with `ENCORE_BASE_URL` / `los.lms.encore.base-url`.

## Transport split

| Legacy | Transport | LOS v2 |
|--------|-------------|--------|
| `EncoreHTTPClientServiceFacadeImpl` | Apache HttpClient, Basic Auth | `com.los.encore.client.http.EncoreHttpTransport` (in **los-core**) → remote Encore |
| `EncoreServiceFacadeImpl` (HTTP) | REST JSON (`encore.api.*`) | `com.los.encore.client.api.DefaultEncoreLmsApi` |
| `EncoreServiceFacadeImpl` / `TransactionServiceFacadeImpl` using `EncoreWebServiceFacade` | proprietary JAR | **Gap** — see `EncoreLegacyJarBridge` |
| `EncoreJobsServiceFacadeImpl.updateBLLMSDueAmts` | SSO + invoices | **Partial**: `EncoreSummarySyncJob` in **los-core** |

## Class → package mapping

| Legacy class | New location |
|--------------|----------------|
| `EncoreHTTPClientServiceFacadeImpl` | `com.los.encore.client.http.EncoreHttpTransport` |
| `EncoreServiceFacade` (HTTP-backed) | `EncoreLmsApi` + `DefaultEncoreLmsApi` |
| Orchestration + DB | `com.los.lms.service.LmsService` (**los-core-service**) |
| `EncoreTransaction` | `LmsLoanHandover`, `LmsRepaymentCallback`, Flyway (`V41`–`V44` on `los_core`) |
| `EncoreJobsServiceFacadeImpl` (subset) | `EncoreSummarySyncJob` |

## API endpoint keys (legacy `encore.api.*` → `los.lms.encore.api.*`)

Implemented in `DefaultEncoreLmsApi` against paths in `EncoreClientProperties.EncoreApiEndpoints`.

## Logging events

Structured codes in `com.los.encore.client.logging.LmsLogEvent` (HTTP) plus LOS-specific usage in jobs/callbacks.

## Callbacks

`POST /api/v1/lms/callback/repayment` on **los-core-service**. Optional HMAC + idempotency as before.

## Parity gaps

See `docs/encore-bl-core-parity-spec.md` and `EncoreLegacyJarBridge`.
