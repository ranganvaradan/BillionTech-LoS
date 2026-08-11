# Provider readiness — LOS-PRODUCTION-P0-CLOSURE-1

Distinguish **PLATFORM PROVIDER READINESS** vs **CUSTOMER PRODUCT PROVIDER READINESS**.
Customer product readiness cannot be GREEN until human supplies product requirements.

Do **not** call production endpoints. Do **not** print secrets.

| PROVIDER | PURPOSE | PRODUCT REQUIRED? | PROD ENDPOINT CONFIGURED? | CREDENTIAL PRESENT? | FORMAT VALID? | NETWORK/TLS? | CONTRACT/CERT? | SANDBOX GOLDEN? | PROD CALL BEFORE CUTOVER? | FAILURE BEHAVIOUR | STATUS |
|----------|---------|-------------------|---------------------------|---------------------|---------------|--------------|----------------|-----------------|---------------------------|-------------------|--------|
| Bureau | Credit score | Customer-dependent | Env-driven | Staging/sandbox only | Not proven for prod | Not proven prod | Often required | Manual/staging paths | Controlled cert later | Typed missing / unavailable — no synthetic approve | AMBER (platform) |
| KYC (Karza/SurePass) | Identity | Customer-dependent | Env-driven | Staging/sandbox | Not proven prod | Not proven | Often required | Partial | Controlled cert later | Fail closed / manual review | AMBER |
| BSA / Bank stmt | Income | Customer-dependent | Env-driven | Unknown prod | — | — | — | Partial | Later | Fail closed | AMBER |
| Account Aggregator | AA | Customer-dependent | Env-driven | Unknown | — | — | — | Partial | Later | Fail closed | AMBER |
| GST | GST facts | Customer-dependent | Env-driven | Unknown | — | — | — | Partial | Later | Fail closed | AMBER |
| ITR | Tax | Customer-dependent | Env-driven | Unknown | — | — | — | Partial | Later | Fail closed | AMBER |
| PLP | Invoice discounting | NOT for COMPANY/TERM_LOAN | Staging gateway | Staging | — | — | — | N/A first product | No | Fail closed | NOT_REQUIRED |
| LMS / Encore | Open account | Yes for Term Loan open | Sandbox URL present | Sandbox admin | Sandbox | Sandbox DNS | Mapping required | Mapping golden passed | Controlled open later | LMS_PRODUCT_MAPPING_MISSING fail closed | AMBER |
| SMTP | Notify | Optional | Env | Unknown | — | — | — | — | Soft | Soft fail notify | AMBER |
| SMS | OTP/notify | Optional | Env | Unknown | — | — | — | — | Soft | Soft fail notify | AMBER |
| PayU | Payments/disburse | No for launch without disbursement | Test URL default | Test keys in defaults | Test | — | — | — | No | Out of scope | NOT_REQUIRED / RED if disbursement in scope |
| MinIO / local docs | Documents | Yes | Local/staging path | Local | — | — | — | Upload paths | No | Fail closed upload | AMBER→GREEN when prod storage configured |
| Internal CI token | Internal APIs | Yes in prod | N/A | Must be env | Length check | N/A | N/A | Startup fail if blank | No | 401/403 | GREEN (mechanism) |

## GREEN definition (this gate)

GREEN = all prerequisites that can be proven **without unsafe production transactions**.

Platform mechanism GREEN: internal token fail-closed, gap defaults off, MANUAL_AUTHORISED explicit provenance.
Customer-enabled bureau/KYC/GST/etc. remain **AMBER** until customer config + controlled connectivity certification.

## Fail-closed proof (unit / safety suite)

ScorecardSafetyFoundation GOLDEN E: GAP_DEFAULT cannot earn production points.
`block-non-authoritative-defaults=true` + `allow-non-production-demo-scoring=false` in prod.
MANUAL_AUTHORISED is an explicit staff path with provenance stamp (not automatic provider-failure fallback).
