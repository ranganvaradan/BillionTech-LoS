# 02 — Fact Dictionary (Phase F)

## Seeded canonical paths (V87)

| Path | Domain | Notes |
|------|--------|-------|
| `application.requested_amount` | APPLICATION | From `LoanApplication` |
| `application.requested_tenure_months` | APPLICATION | |
| `application.loan_purpose` | APPLICATION | From personalInfo when present |
| `applicant.business_vintage_months` | APPLICANT | Scorecard `BUSINESS_VINTAGE_MONTHS` |
| `applicant.declared_annual_income` | APPLICANT | financialInfo when present |
| `applicant.verified_annual_income` | APPLICANT | `effectiveIncome * 12` |
| `kyc.pan_verified` / `kyc.identity_verified` | KYC | From `kycPassEffective` |
| `kyc.gst_available` | KYC | GST scorecard keys |
| `bureau.consumer.score` | BUREAU | Effective bureau score |
| `bureau.live_unsecured_loan_count` | BUREAU | |
| `bureau.total_live_exposure` | BUREAU | If present |
| `bureau.total_monthly_obligation` | BUREAU | |
| `bureau.max_dpd` | BUREAU | If present |
| `bureau.source_available` | BUREAU | Derived from bureauSource |
| `banking.monthly_credit` / `average_balance` / `existing_emi` | BANKING | |
| `obligations.monthly_emi_total` | OBLIGATIONS | |
| `collateral.market_value` | COLLATERAL | `PROPERTY_VALUE` |
| `metrics.foir` / `metrics.ltv` | METRICS | DERIVED (or DEFAULTED) |
| `limit.requested_amount` / `computed_eligible_amount` / `sanction_cap` | LIMIT | |
| `compat.*` | COMPAT | Full scorecard reconstruction |

## Classifications (Phase F rules)

| Situation | Classification |
|-----------|----------------|
| Provider-backed value | `VERIFIED` |
| Manual UI (`creditControl.manual`) | `MANUAL` |
| Declared application fields | `DECLARED` |
| Demo / provider-gap fallback | `DEFAULTED` (never `VERIFIED`) |
| FOIR / LTV / eligible / cap | `DERIVED` |
| AI classes | Enum only — **not produced** |

## Value encoding

Scalars stored as JSONB `{"v": <scalar>}` for consistency.

## Metadata on facts

- `originService`, `originField`
- `defaulted` (bool)
- `originalMissing` (bool) where applicable

## Source mapping

| Source type | Typical origin |
|-------------|----------------|
| `LEGACY_CONTEXT` | CreditControlService / financial_info |
| `APPLICATION` | loan_applications |
| `CONSUMER_BUREAU` / `MANUAL_DECLARATION` | bureauScoreSource |
| `KYC` | kycSource |
| Income sources | Bank statement / manual / demo |

Sensitive keys containing `pan`, `aadhaar`, `raw`, `payload`, `responseBody` are stripped from source metadata.
