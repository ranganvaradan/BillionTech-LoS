# 16 — Banking Metrics and Obligations

## Methods

| Constant | Metric codes | Behaviour |
|----------|--------------|-----------|
| `BANK_AVERAGE_DAILY_BALANCE_V1` | `banking.avg_daily_balance_{3,6,12}m` | EOD carry-forward; insufficient continuity → DI; valid zero included |
| `ADJUSTED_BANKING_TURNOVER_V1` | `banking.adjusted_business_credits_*` | Exclude loan disbursement, self transfer, capital infusion, interest credits, refunds; include CUSTOMER_RECEIPT / OTHER_OPERATING / business_receipt_flag |
| `BANK_EMI_DETECTION_V1` | `banking.monthly_obligation` | Recurring EMI/NACH lender debits; min occurrences (default 3); regularity threshold; no % estimates |
| `BANK_SOURCE_PRECEDENCE_V1` | (metadata) | AA_STRUCTURED &gt; BANK_API &gt; DIGITAL_STATEMENT &gt; OCR &gt; MANUAL |
| `BANK_TXN_CLASSIFIER_V1` | (txn classification) | Deterministic patterns |

## No-transaction AA summary path

| Metric | Behaviour |
|--------|-----------|
| ADB | **DATA_INSUFFICIENT** (prefer over SUMMARY_PROXY); `avgMonthlyBalance` in evidence only |
| Adjusted credits | DATA_INSUFFICIENT |
| Bounce 6m | REFER/PARTIAL from `bounceCount6Months` if present |
| Monthly obligation | REFER/PARTIAL from `regularEmiOutflows` — not high-confidence DERIVED |
| OD utilisation | DATA_INSUFFICIENT |

## OD / CC utilisation

Only with valid limit (`sanctionedLimit` / `drawingPower` / `overdraftLimit`). Otherwise DI with `OD_LIMIT_MISSING`.

## Evidence

When included/excluded txn ID lists exceed 20 members, persist `ci_evidence_group` and store group id + sample ids + count on metric evidence.

## Fact paths (V93)

Canonical facts emitted from metrics include `banking.balance.average_*`, `banking.credit.adjusted_business_12m`, `banking.obligation.emi_total_monthly`, bounce/cash/OD paths. Production scorecard keys are **not** overwritten.
