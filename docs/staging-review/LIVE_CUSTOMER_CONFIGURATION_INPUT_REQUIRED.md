# CUSTOMER_CONFIGURATION_INPUT_REQUIRED

LOS-LIVE-P0-CLOSURE-1 — actual first live customer values are **not** present in code/config/context.
Do **not** invent them. Fill before go-live.

## Required matrix (fill)

| Field | Value |
|-------|--------|
| Customer / tenant | **REQUIRED** |
| Borrower type | **REQUIRED** |
| Loan product | **REQUIRED** |
| Intake segment | **REQUIRED** |
| Workflow ID + version | **REQUIRED** |
| Live Rule Set ID + version | **REQUIRED** |
| Scorecard ID + version | **REQUIRED** |
| LMS product mapping | **REQUIRED if LMS used** |
| PLP program/config | **OPTIONAL if applicable** |
| Required providers/integrations | **REQUIRED list** (Bureau / AA / BSA / GST_ANALYSIS / ITR / none) |

## Staging representative only (NOT live proof)

COMPANY + TERM_LOAN fixtures on staging (unique after readiness hardening):

- Workflow: `b0000000-0000-0000-0000-000000000041` v2
- Live Rule Set: `c3320000-0000-4000-a000-000000000029`
- Scorecard: `d3320000-0000-4000-a000-000000000029`
- LMS: `IPPOPAYM01`

These are **not** accepted as actual customer configuration for SAFE TO DEPLOY.

## Production env settings (names only)

```
SPRING_PROFILES_ACTIVE=prod
LOS_DEMO_ENABLED=false
LOS_PROVIDER_GAP_DEFAULTS_ENABLED=false
LOS_ADMIN_API_REQUIRE_ROLE=true
CREDIT_INTELLIGENCE_INTERNAL_TOKEN=<set-in-secret-store>
CREDIT_INTELLIGENCE_INTERNAL_TOKEN_REQUIRED=true
```

Confirm in effective config: `credit-intelligence.staging-demo.enabled=false`, `validation.enabled=false`, `allow-canonical-authority=false`.
