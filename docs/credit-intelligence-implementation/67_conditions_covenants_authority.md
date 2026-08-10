# 67 — Conditions, Covenants, Authority (P2)

Evidence-linked conditions/covenants and matrix-driven approval routing.

```mermaid
flowchart TB
  DI[DATA_INSUFFICIENT] --> Cond[ConditionEngine]
  Recon[Recon CONFLICT] --> Cond
  Short[Collateral shortfall] --> Cond
  Strat[covenantStrategy.defaults] --> Cov[CovenantEngine]
  Amt[Recommended amount] --> Auth[AuthorityMatrixEngine]
  Grade[Risk grade] --> Auth
  Dev[Deviation count / material exception] --> Auth
  Cond --> Rec[Recommendation]
  Cov --> Rec
  Auth --> Rec
  Rec --> Human[Human review — CiHumanCreditDecision future]
```

## Conditions

Types: `CONDITION_PRECEDENT`, `CONDITION_SUBSEQUENT`, `COVENANT`, `DOCUMENT_REQUIREMENT`, `MONITORING_REQUIREMENT`

Every condition carries reason, source, causing rule/recon, `mandatoryBefore`.

## Deviations

`CiPolicyDeviation` identified with status **REQUESTED**. P2 never auto-**APPROVED**.

## Authority bands (fixture example)

| Band | Level |
|------|-------|
| ≤ ₹5L, grade A/B, 0 deviations | CREDIT_MANAGER_L1 |
| ≤ ₹25L | CREDIT_MANAGER_L2 |
| else / material exception | CREDIT_COMMITTEE |
