# 11 — GST Metrics and Filing Semantics

## Filing status normalization (`GST_FILING_STATUS_NORMALIZATION_V1`)

| Provider / raw | Canonical |
|----------------|-----------|
| Filed (delay ≤ 0) | `FILED` |
| Filed with delay &gt; 0, or "late" | `LATE_FILED` (+ delay days) |
| Pending / in progress | `PENDING` |
| Not filed / unfiled | `NOT_FILED` |
| N/A | `NOT_APPLICABLE` |
| Anything else / blank | `UNKNOWN` (**≠ filed**) |

## Freshness (`GST_FRESHNESS_POLICY_V1`)

Not a simplistic 365-day rule. Expected latest completed return period =

1. Previous calendar month relative to `asOf`
2. If `asOf` is still within `filingLagDays` (default **20**) after that month-end, roll back one more month

Compare actual latest filed period ≥ expected → fresh.

## Turnover metrics

| Metric code | Semantics |
|-------------|-----------|
| `gst.turnover.trailing_12m` | Prefer GSTR1 period financials; sum available months in trailing 12. If completeness &lt; `minCompletenessForTrailing12m` (0.75) **or** available months &lt; ~9 → `DATA_INSUFFICIENT`, value null |
| `gst.turnover.trailing_3m` / `trailing_6m` | Same preference; shorter window |
| `gst.turnover.current_fy_ytd` | Apr–current completed month (Indian FY) |
| `gst.turnover.annualized_current_run_rate` | Only if monthsUsed ≥ `minMonthsForAnnualization` (6) and missing not material; else DI. **Never** silently replaces trailing_12m |
| `gst.filing.timeliness_score` | Deterministic 0–100: `100 - 10*late - 20*missing` over 6m GSTR1 window |
| `gst.gstr1_gstr3b_turnover_variance` | Overlapping periods; outcomes `MATCH` / `ACCEPTABLE_VARIANCE` / `MATERIAL_VARIANCE` / `CONFLICT` / `DATA_INSUFFICIENT` using warningPct (5) and materialPct (15) |
| `gst.return.missing_count_12m` / `late_count_12m` / `max_delay_days_12m` | Filing quality counts |

### Valid zero vs missing

- Period exists with `taxable_turnover = 0` → include as **0**
- Expected period absent / no financials → **missing**, not 0

### Multi-GSTIN

Store per GSTIN. Borrower trailing turnover = **sum** of active-GSTIN qualifying period turnovers (same calendar period once per GSTIN). Documented in metric evidence as `aggregation=SUM_ACTIVE_GSTIN_PERIODS`.

## Versions

- Parser: `KARZA_GST_PARSER_V1`
- Normalizer: `GST_NORMALIZER_V1`
- Metric version: `V1`
