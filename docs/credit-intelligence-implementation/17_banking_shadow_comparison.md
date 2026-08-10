# 17 — Banking Shadow Comparison

## Adapter overlays (`use-for-shadow-rules: true`)

| Scorecard key | Canonical metric | Condition |
|---------------|------------------|-----------|
| `AVERAGE_BANK_BALANCE` | `banking.avg_daily_balance_3m` | outcome PASS |
| `EMI_OBLIGATION` (+ `MONTHLY_OBLIGATION`) | `banking.monthly_obligation` | outcome PASS |
| `ANNUAL_BANKING_TURNOVER` | `banking.adjusted_business_credits_12m` | outcome PASS |

When DATA_INSUFFICIENT: keep legacy CreditControl value and set `*_diFallback` / `fallbackUsed` in `adapterFlags.banking`.

## Shadow rules (`CanonicalBankingRuleEvaluator`)

- `DQ_BANK_STATEMENT_AVAILABLE`
- `DQ_BANK_STATEMENT_COMPLETENESS`
- `DQ_BANK_ACCOUNT_OWNERSHIP`
- `BANK_ABB_MINIMUM` (threshold from `abb-minimum`; null → REFER)
- `BANK_MONTHLY_CREDIT_MINIMUM`
- `BANK_CHEQUE_RETURN_MAX` / `BANK_NACH_RETURN_MAX` (valid zero = PASS)
- `BANK_CASH_DEPOSIT_RATIO`
- `BANK_NEGATIVE_BALANCE_DAYS`
- `BANK_OD_UTILISATION`
- `BANK_EMI_OBLIGATION_AVAILABLE`

Wired into `ShadowCreditEvaluationService` → `metadata.bankingComparison`.

## Mismatch classifications

`LEGACY_DEFAULT_USED`, `CANONICAL_DATA_INSUFFICIENT`, `BALANCE_CALCULATION_DIFFERENCE`, `ACCOUNT_OWNERSHIP_MISMATCH`, `LOAN_DISBURSEMENT_EXCLUDED`, `SELF_TRANSFER_EXCLUDED`, `OD_LIMIT_MISSING`, etc.

## Production unchanged

CreditControl gap defaults and production scorecard evaluation paths remain authoritative.
