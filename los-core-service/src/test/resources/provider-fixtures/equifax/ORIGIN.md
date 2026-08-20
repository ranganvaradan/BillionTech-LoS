# Equifax fixtures

| Field | Value |
|-------|--------|
| data_origin | **SYNTHETIC** |
| source | Sanitized XML shape from `EquifaxBureauAccountExtractorTest` sample |
| live_production | **No** |

`equifax_accounts_minimal.xml` is a minimal InquiryResponse envelope used only for SPI adapter contract tests. Account numbers are dummy values.

`equifax_golden_pipeline_fixture.xml` is the former `simulated/equifax-sample-inquiry-response.xml` content (score 758, 13 tradelines, report date 2026-03-12), moved here 2026-08-20 when that runtime simulation fixture was replaced with a different sample response. `EquifaxRetailRawGoldenPathTest`/`EquifaxBureauAccountExtractorTest` assert dozens of exact values against this specific fixture (report order no, narratives, scoring codes, etc.) — keeping their golden fixture stable and decoupled from whatever `EquifaxBureauProvider.simulatedFallback` returns at runtime.
