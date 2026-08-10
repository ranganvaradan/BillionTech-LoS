# 13 — Phase C2 Validation Report

## Status

Phase C2 GST canonicalization implemented as **flag-gated shadow** path. Production underwriting and `CreditControlService` SCF GST gap defaults are **unchanged**.

## Live validation outstanding

| Area | Status |
|------|--------|
| Phase F foundation live validation | Still outstanding |
| Phase C1 bureau live validation | Still outstanding |
| Phase C2 GST live / Karza E2E | Unit-tested; live Karza report ingest pending environment enablement |

## Unit test coverage (C2)

Target command:

```bash
mvn test -Dtest=com.los.core.creditintelligence.**,GstAnalysisMapperTest,CreditControlGstAnalysisPreferenceTest
```

Suites added/updated under `com.los.core.creditintelligence.gst`:

- `GstPeriodUtilsTest` (4) — FY Apr/Mar boundaries, MMYYYY parse, lag-aware expected period
- `GstFilingStatusNormalizerTest` (2)
- `GstFreshnessEvaluatorTest` (3)
- `GstMetricServiceTest` (6) — complete 12m, partial DI, valid zero, annualization gate, multi-GSTIN sum, variance match/material/conflict/DI
- `KarzaGstCanonicalExtractorTest` (2) — sample JSON aligned with `GstAnalysisMapperTest` (+ gstr3b)
- `CanonicalGstRuleEvaluatorTest` (3)
- `LegacyUnderwritingContextAdapterGstTest` (2) — shadow overlay + DI fallback

**GST package: 22 tests.** Full filtered run (`creditintelligence.**` + GstAnalysisMapper + CreditControlGstAnalysisPreference): **66 tests, 0 failures**.

## Known limitations

1. GSTR3B amounts only available when Karza `monthWiseSummary[].gstr3b.ttlVal` is present
2. Multi-GSTIN rare in Karza PDF-upload payload; extractor supports `gstins[]` when present
3. Due dates / filed dates not always present in Karza filingStatus — delay days may be null
4. Snapshot emits GST facts when registrations exist; scorecard production path unchanged
5. `ci_metric_result.bureau_report_id` unused for GST (domain link via metadata / application_id)

## Recommended next phase

**C3 = Bank statements (or ITR)** — next financial source canonicalization after GST, reusing the same source-registry / metric / shadow pattern.
