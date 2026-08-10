# 23 — Phase C4 Validation Report

## Scope validated

Phase C4 ITR / AIS / Form 26AS canonicalization in `los-core-service`:

- Domain entities + repos for V94
- Extractor / normalizer / metrics / ingestion / shadow / adapter / admin API
- Unit + fixture E2E tests (Karza-shaped JSON from `ItrReturnFormsMapperTest`)
- Docs 19–23

## Not changed

- `CreditControlService` gap defaults (`SCF_GAP_ITR_INCOME`, etc.)
- Production scorecard path / live underwriting decisions
- Flyway V94/V95 (pre-written)

## Test command

```bash
mvn test -Dtest=com.los.core.creditintelligence.**,ItrReturnFormsMapperTest,CreditControlItrPreferenceTest
```

**Result:** 111 tests, 0 failures (includes 21 new Phase C4 tax tests).

## Outstanding (prior phases)

| Phase | Status |
|-------|--------|
| F / C1 / C2 / C3 live E2E | Still outstanding (provider/environment dependent) |
| C4 | Validated via Karza-shaped fixtures + units |
| AIS / 26AS live pulls | Not wired in production providers yet |

## Recommend C5

**Cross-source reconciliation (GST ↔ ITR ↔ Bank)** — join trailing GST turnover, ITR business turnover, and banking adjusted credits with variance bands and shadow mismatch classifications, still flag-gated and non-authoritative.
