# 83 — Real-Data Validation (G0.1)

```mermaid
flowchart LR
  Bundles[validation-bundles CASE_A-E] --> Classify[ORIGIN classification]
  Fixtures[provider-fixtures ORIGIN.md] --> Classify
  Classify --> Weights[CUTOVER_VALIDATION_EVIDENCE_V1]
  Classify --> Count[realStoredCaseCount]
  Count -->|fixture only| Zero[0 toward minimum]
  Weights --> Score[weightedEvidenceScore]
```

## Weights (validation-strength only)

| Origin | Weight |
|--------|--------|
| ANONYMIZED_REAL_DEV_DATA / REAL_DEV | 1.00 |
| STORED_PROVIDER_FIXTURE / STORED_PROVIDER | 0.90 |
| USER_SUPPLIED_SAMPLE | 0.60 |
| REPRESENTATIVE_PROVIDER_FIXTURE | 0.40 |
| SYNTHETIC | 0.20 |

Representative/synthetic fixtures **do not** count toward `cutover.min-real-or-stored-cases` (default 20).

## Current repo honesty

With present classpath evidence: **realStoredCaseCount = 0**. No fabricated applications.
