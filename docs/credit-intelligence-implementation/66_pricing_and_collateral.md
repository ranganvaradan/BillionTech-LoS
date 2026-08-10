# 66 — Pricing and Collateral (P2)

Explicit, versioned pricing components and collateral LTV structuring.

```mermaid
flowchart TB
  Base[baseRate] --> Sum
  Risk[RISK_GRADE_PREMIUM] --> Sum
  Unsec[UNSECURED_PREMIUM] --> Sum
  Ten[TENURE_PREMIUM] --> Sum
  Disc[discounts] --> Sum
  Sum[recommendedFinalRate] --> FloorCap[floor / cap clamp]
  Coll[CollateralEngine] --> LTV[effective value / LTV / shortfall]
  FloorCap --> Rec[CiCreditRecommendation]
  LTV --> Rec
```

## Pricing

```text
baseRate + Σ(component bps/10000) → clamp(floor, cap)
```

Evidence weakness prefers **REFER / conditions** when `evidenceWeaknessCausesRefer=true`, not silent price-up unless strategy says so.

## Collateral

- `required`, `maxLtv`, `haircut`
- Outputs: required value, available, effective, recommended LTV, shortfall
- Shortfall → condition (when strategy auto-generates)
