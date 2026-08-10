# 46 — Bureau BRE Interpretation

Authoritative source: `src/test/resources/policy-fixtures/bureau-bre/Bureau_BRE.txt`.

## Nested overdue

Parent `OVERDUE_PARENT` with children 1–4 via `parentClauseId` (not four unrelated rules).

## Score semantics

```text
OR(score = -1, bureau.status_ntc, score >= 650)
```

**Not** simply `score >= 650`. NTC flagged for canonical confirmation across providers.

## Hard fails

Write-off except CC; CC overdue **>** 5000; DPD **>** 30 in last 6m; Settled/Restructured; Legal suit; DBT/PWOS/LSS; Multiple PAN; Inquiries current month **>** 3 (EvaluationContext clock); Account Sold.

## Missing metrics (candidates)

`bureau.overdue.age_months`, `bureau.credit_after_overdue.exists`, `bureau.credit_after_overdue.clean_history_months` — not equivalent to `bureau.max_dpd_6m`.
