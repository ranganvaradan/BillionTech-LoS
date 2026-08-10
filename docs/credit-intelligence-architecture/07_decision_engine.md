# 07 — Decision Engine

## Purpose

Turn policy/metric/reconciliation outputs into a **controlled recommendation** for humans — never a silent final act.

## Recommendation outcomes

```text
APPROVE
APPROVE_WITH_CONDITIONS
REFER
DECLINE
DATA_INSUFFICIENT
COUNTER_OFFER
```

Structuring fields: facility type, limit, tenure, pricing, repayment, collateral, guarantors, conditions precedent/subsequent, covenants, monitoring plan; optional reduced amount / changed tenure / additional collateral.

## Authority matrix inputs

amount · product · risk grade · deviation count/severity · collateral · geography · lender · policy exceptions  

Maps to roles (officer / manager / head / board) — consolidates today’s scattered CAM/sanction roles and unused `CreditEnhancementService` matrix into one governed pack inside the policy package.

## Process

```text
Policy Engine stage results
    → Decision Engine assemble recommendation
    → persist Recommendation (immutable for this evaluation)
    → Human approval / maker-checker
    → Decision record
    → Sanction terms (if approved)
```

## Overrides (see also Part 13 themes)

Types: rule override · policy deviation · data correction · fact override · metric adjustment · authority escalation · post-approval amendment  

Each: original, replacement, reason, evidence, requester, approver, authority, timestamps, expiry, impacted rules/metrics, re-evaluation ref. Immutable history.

## Observed mapping

| Current | Target |
|---|---|
| aggregateCreditDecision APPROVED/REJECTED/MANUAL_REVIEW | Recommendation + Decision |
| completeManualUnderwritingDecision | Human Decision Engine API |
| CAM + sanction flow | Downstream of Decision; keep workflow |
| LimitSizing sanction cap | Structuring metric consumed by Decision |

Decision Engine **does not** replace human approval or disbursement controls.
