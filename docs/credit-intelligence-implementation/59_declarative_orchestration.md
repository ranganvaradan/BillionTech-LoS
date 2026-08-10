# 59 — Declarative Orchestration (P1)

Package-owned stages replace Java call-order as business logic.

```mermaid
flowchart TB
  S1[1 DATA_READINESS] --> S2[2 IDENTITY_KYC]
  S2 --> S3[3 FRAUD_SANCTIONS]
  S3 --> S4[4 BUREAU]
  S4 --> S5[5 BANKING]
  S5 --> S6[6 GST_TAX]
  S6 --> S7[7 FINANCIAL_ANALYSIS]
  S7 --> S8[8 OBLIGATIONS]
  S8 --> S9[9 RECONCILIATION]
  S9 --> S10[10 SCORECARD]
  S10 --> S11[11 ELIGIBILITY]
  S11 --> S12[12 LIMIT]
  S12 --> S13[13 PRICING]
  S13 --> S14[14 RECOMMENDATION]
```

## Stage fields

`stageCode`, `sequence`, `dependsOn`, `rules`, `continueOnFail`, `continueOnRefer`, `parallelizable`, `required`

## Semantics

- Knockout FAIL may stop later eligibility when `continueOnFail=false`
- Evidence / audit / explanation still collected for executed stages
- Default aggregation precedence (overridable by package): KNOCKOUT FAIL > HARD FAIL > REFER > DI > PASS
