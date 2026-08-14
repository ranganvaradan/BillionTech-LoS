# Bureau → GACAT → Policy / Underwriting Mapping

Project: `LOS-APP-1` (`BillionTech-LoS-reconcile-laptop-layout-2026-08`)

## Architecture (direction note compliant)

```
Equifax Provider → BureauIngestionService → BureauNormalizationService
  → CiBureauReport + Tradelines + PaymentHistory
  → BureauMetricService → CiMetricResult (GACAT metrics)
  → CanonicalBureauContextBridge (legacy scorecard aliases only)
  → CreditControlService → ScorecardPolicyEngine / UnderwritingRuleEngine
  → Policy Studio (ParameterExecutabilitySupport = authoritative executability)
```

**GACAT is SSOT.** Legacy keys (`BUREAU_SCORE`, `MAX_DPD_6M`, etc.) are compatibility aliases via `CanonicalBureauContextBridge` only.

## Canonical metric → legacy scorecard alias

| GACAT metric | Legacy scorecard key | Calculator |
|---|---|---|
| `bureau.score` | `BUREAU_SCORE` | Report score from normalization |
| `bureau.live_unsecured_loan_count` | `LIVE_UNSECURED_LOAN_COUNT` | `BureauMetricService.computeLiveUnsecured` |
| `bureau.max_dpd_6m` | `MAX_DPD_6M` | `BureauMetricService.computeMaxDpd(6m)` |
| `bureau.max_dpd_12m` | `MAX_DPD_12M` | `BureauMetricService.computeMaxDpd(12m)` |
| `bureau.recent_inquiries_90d` | `BUREAU_ENQUIRIES_3M` | `BureauMetricService.computeInquiries90d` |
| `bureau.total_monthly_obligation` | `MONTHLY_OBLIGATION`, `EMI_OBLIGATION` | `BureauMetricService.computeMonthlyObligation` |

Bridge overlays only metrics with outcome `PASS`. Never applies gap defaults or business rules.

## Executability (Gate 3)

| Component | Role |
|---|---|
| `ParameterExecutabilitySupport` | **Authoritative** platform executability (POLICY_TEST / RUNTIME / PRODUCTION) |
| `BureauDataReadinessProbe` | Per-application ingest evidence only |
| `BureauApplicationExecutabilityService` | Merges Gate-3 + bureau evidence without overriding `executionState` |

Example: `bureau.max_dpd_6m` may show `RUNTIME_READY_NONPROD` in Gate-3 while application evidence is `READY` after a successful bureau pull.

## Configuration

```yaml
credit-intelligence:
  canonicalization:
    bureau:
      enabled: true
      use-for-production-underwriting: true   # local/dev/staging
      use-for-shadow-rules: false
  shadow-evaluation:
    enabled: false
  cutover:
    allow-canonical-authority: false
```

## Local simulation

When Equifax credentials are absent, `EquifaxBureauProvider` parses `simulated/equifax-sample-inquiry-response.xml` so bureau pull → GACAT ingestion follows the same path as production XML.

### Current fixture (PCS InquiryResponse)

| Field | Value |
|---|---|
| Score (ERS4.0) | **758** |
| Hit / Success | HitCode 10 / SuccessCode 1 |
| Tradelines | **13** (5 open) |
| Masked PAN in fixture | `ABCDE1234F` |
| Parser note | SuccessCode read from `InquiryResponseHeader` (PCS) or `ResponseHeader` (legacy) |

Expected bridged legacy keys after pull (approx): `BUREAU_SCORE=758`, live unsecured ≥1 (open Personal Loan), `MAX_DPD_6M` can include history DPD (e.g. 71 on closed property loan), enquiries from `EnquirySummary` / recent dates.

## Quick E2E test application

Submit **Individual + Personal Loan** (`INDIVIDUAL` / `PERSONAL_LOAN`):

1. Start core with `spring.profiles.active=local` (or `dev`) so bureau overlay is on and Equifax credentials stay blank → simulated XML.
2. Create application: borrower **Individual**, product **Personal Loan**.
3. Complete KYC to **PASS** — workflow auto-pulls bureau (`auto_pull_bureau_after_kyc_success`).
4. Confirm bureau step / report shows score **758** and 13 accounts.
5. Run **Underwrite** — scorecard uses `BUREAU_SCORE` (GTE 750 band) plus GACAT-bridged `LIVE_UNSECURED_LOAN_COUNT` / `MAX_DPD_6M` (seeded by `V122`).

Any valid PAN format works locally; fixture PAN is masked and not matched against live Equifax.

## Source → Policy → GACAT → Underwriting

| Layer | What |
|---|---|
| Source (Equifax PCS XML) | Score, AccountDetails, History48Months, EnquirySummary |
| Policy / GACAT | `bureau.score`, `bureau.live_unsecured_loan_count`, `bureau.max_dpd_6m`, `bureau.max_dpd_12m`, `bureau.recent_inquiries_90d`, `bureau.total_monthly_obligation` |
| Underwriting (legacy) | `BUREAU_SCORE`, `LIVE_UNSECURED_LOAN_COUNT`, `MAX_DPD_6M`, `MAX_DPD_12M`, `BUREAU_ENQUIRIES_3M`, `MONTHLY_OBLIGATION` / `EMI_OBLIGATION` via `CanonicalBureauContextBridge` |

Gate-3 (`ParameterExecutabilitySupport`) remains SSOT for executability; production UW still uses scorecard/rule-set legacy keys until cutover.

## Key classes

- `CanonicalBureauContextBridge` — compatibility adapter
- `BureauDataReadinessProbe` — ingest evidence
- `BureauApplicationExecutabilityService` — Gate-3 + evidence merge
- `BureauMetricService` — derived metric calculators
- `CreditControlService.applyCanonicalBureauOverlay` — production overlay before gap defaults
