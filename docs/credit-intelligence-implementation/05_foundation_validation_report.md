# 05 — Foundation Validation Report

## Purpose

Read-only report for Phase F acceptance (spec §16). Does not mutate applications or workflow.

## API / service

- Service: `CreditIntelligenceValidationReportService.buildReport(applicationId)`
- HTTP: `GET /api/v1/internal/credit-intelligence/applications/{applicationId}/compare`

## Fields

| Field | Source |
|-------|--------|
| `application_id` | path |
| `application_number` | LoanApplication |
| `tenant` | `credit-intelligence.default-tenant-id` |
| `product` | loanProduct |
| `production_outcome` | latest PRODUCTION_REFERENCE evaluation |
| `shadow_outcome` | latest SHADOW evaluation |
| `comparison_status` | shadow row |
| `fact_snapshot_version` / `fact_snapshot_hash` | latest snapshot |
| `policy_version` / `policy_hash` | linked policy version |
| `rule_result_count` | shadow StandardRuleResult count |
| `pass_count` / `fail_count` / `refer_count` / `data_insufficient_count` | by outcome |
| `defaulted_fact_count` | facts with classification DEFAULTED |
| `shadow_duration_ms` | shadow metadata |

## How to run locally

1. Enable flags for a test tenant/product:

```yaml
credit-intelligence:
  foundation:
    enabled: true
  shadow-evaluation:
    enabled: true
    async: false
  internal-token: local-dev-token
```

2. Underwrite a representative application.
3. Call compare / validation-report endpoint with `X-Internal-Token`:

```bash
curl -s -H "X-Internal-Token: local-dev-token" \
  "http://localhost:8083/api/v1/internal/credit-intelligence/applications/{applicationId}/validation-report"
```

4. Confirm MATCH (or investigate MISMATCH without changing production).

## Validation results (this milestone)

Unit tests for the CI package pass (`mvn test -Dtest=com.los.core.creditintelligence.**`). Live DB validation against representative underwrites was **not** executed in this workspace session (no production/local underwrite dataset exercised with flags on). After enabling flags locally, use the curl command above and record MATCH/MISMATCH rows in an ops note.

Expected first-run posture with flags on and adapters healthy: **MATCH** for applications whose shadow path can reconstruct the same effective context and engines. Mismatches should be treated as parity defects in snapshot/adapter mapping, not as decision changes.

## Known Phase F limitations

- Gap defaults still exist in production `CreditControlService` (recorded as DEFAULTED, not removed)
- Raw bureau payloads are not stored — references only
- Replay uses SHADOW type (not a separate REPLAY row yet)
- Tenant is global default until LoanApplication carries tenantId
- DB triggers enforce frozen fact / published policy immutability; service layer also rejects post-freeze fact inserts

## Recommended Phase C first task

Replace silent gap defaults for `LIVE_UNSECURED_LOAN_COUNT` with tradeline-derived facts and emit `DATA_INSUFFICIENT` when tradelines are absent instead of inventing counts.
