# 18 — Phase C3 Validation Report

## Scope validated

| Area | Status | How |
|------|--------|-----|
| Domain entities / repos (V92) | Done | Match Flyway V92 |
| Fact/metric definitions (V93) | Already present | No recreate |
| AaFiDataParser simulated transactions | Done | Unit tests |
| Classifier / duplicate / ADB / turnover / EMI / OD | Done | Unit tests |
| Normalization + metrics E2E (mocked repos) | Done | `BankingNormalizationE2ETest` |
| Adapter overlay | Done | `LegacyUnderwritingContextAdapterBankingTest` |
| Shadow rules | Done | `CanonicalBankingRuleEvaluatorTest` |
| Admin API | Done | `/api/v1/internal/credit-intelligence/banking/...` |
| Feature flags default off | Done | YAML + properties |
| Production CreditControl gap defaults | Untouched | Explicit constraint |

## Outstanding (prior phases / next)

| Item | Notes |
|------|-------|
| Phase F live E2E | Still outstanding (foundation shadow against real underwrite) |
| Phase C1 live E2E | Bureau canonical against live Equifax pull still outstanding |
| Phase C2 live E2E | GST canonical against live Karza still outstanding |
| Phase C3 live AA E2E | Validated via **simulated** AA payload + unit tests; live Setu FI with real Transactions TBD |
| Phase C4 recommendation | **ITR / AIS / 26AS** canonicalization |

## Test command

```bash
mvn test -Dtest=com.los.core.creditintelligence.**
```

## Limitations

1. OCR bank-statement ingest hook is available (`ingestFromOcrExtract`) but not wired into `OcrExtractionService` by default (optional).
2. ADB without txn/balance series is always DI (summary AMB not used as ADB value).
3. OD utilisation requires limit fields on account — AA simulation currently has no OD account with limits.
4. Ownership match depends on application personalInfo/businessInfo names when present.
5. Bulk EvidenceGroup stores full member id list in DB JSON for audit; metric evidence keeps sample + count only.
