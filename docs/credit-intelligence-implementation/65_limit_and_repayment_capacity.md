# 65 — Limit and Repayment Capacity (P2)

Multi-method limit sizing with strategy-driven combine. Never silent max of turnover sources.

```mermaid
flowchart LR
  Req[Requested amount] --> Comb[Combine strategy]
  T[TURNOVER_LIMIT] --> Comb
  B[BANKING_CREDIT_LIMIT] --> Comb
  F[FOIR_LIMIT] --> Comb
  D[DSCR_LIMIT] --> Comb
  C[COLLATERAL_LIMIT] --> Comb
  P[POLICY_CAP] --> Comb
  R[REQUESTED_AMOUNT] --> Comb
  Comb --> Rec[Recommended amount / COUNTER_OFFER]
```

## Methods

| Method | Formula sketch |
|--------|----------------|
| TURNOVER_LIMIT | `turnoverSource × turnoverPct` (GST/ITR/BANK/TRIANGULATED/MIN_OF_SOURCES) |
| BANKING_CREDIT_LIMIT | `bankingMultiplier × bankingMetric` |
| FOIR_LIMIT | `(income×allowedFoir − obligations) → principal via EMI_FLAT_V1 / EMI_REDUCING_V1` |
| DSCR_LIMIT | `(cashFlow / minDscr) → principal`; DI if missing |
| COLLATERAL_LIMIT | `collateral × (1−haircut) × maxLtv` |
| POLICY_CAP / REQUESTED_AMOUNT | Cap / request |

## Combine

`MIN` · `MAX` · `WEIGHTED` · `SELECT_PRIMARY` · `CONDITIONAL`

P2 validation fixture uses **MIN** of eligible methods.
