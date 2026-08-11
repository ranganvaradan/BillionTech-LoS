# LOS-LIVE-CUSTOMER-HARDENING-1 — Staging freeze notes

## Architecture freeze

Runtime credit authority remains:

`LoanApplicationFlowService` → `CreditControlService` → `UnderwritingRuleEngine` → `ScorecardPolicyEngine`

- `allowCanonicalAuthority=false` (mandatory)
- Policy Studio = governance/shadow only
- No new engines

## Production config defaults (application.yml / application-prod.yml)

| Property | Live value |
|----------|------------|
| `los.security.admin-api-require-role` | `true` |
| `los.underwriting.provider-gap-defaults-enabled` | `false` |
| `los.demo.enabled` | `false` |
| `los.security.local-dev-permit-all` | `false` |

Staging may keep `provider-gap-defaults-enabled=true` for demos; **must be false before live customer**.

## Customer config template (fill before go-live)

- Customer / tenant:
- Borrower type:
- Loan product:
- Intake segment:
- Workflow ID (exactly one active for scope):
- Live Rule Set ID (exactly one active for scope):
- Live Scorecard ID (exactly one active for scope):
- LMS product mapping:
- PLP program (if applicable):

## Rollback

1. Restore prior `ui-dist.prev.*` directory on host
2. Redeploy previous `billiontechlos-core:staging` image tag / rebuilt jar from prior commit
3. Restore DB from `pg_dump` under `/opt/billiontech/backups/los_core_staging/`
4. Do not run Flyway repair unless ops-approved (this hardening task adds **no** migration)
