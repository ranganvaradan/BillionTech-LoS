# WAVE 7 — Decision Authority Inventory

Captured at SOURCE_START_SHA `4783e14175bb88fe5cced3cea3695e5b7061c0e7`.

Live authority unchanged: `LoanApplicationFlowService` → CreditControl prep → UnderwritingRuleEngine + ScorecardPolicyEngine.

Target: `CanonicalUnderwritingOrchestration` (Policy CPR + Scorecard bands + limit/pricing + override) — **not cut over**.

Full machine-readable inventory: `DecisionAuthorityInventory`, `DuplicateBusinessConditionInventory`, `FoirAuthorityBoundary`, `CreditControlDecisionBoundary`.

## Ownership summary

| Layer | Owns |
|-------|------|
| POLICY | FAIL / REFER / knockout eligibility via CanonicalPolicyRuntime |
| SCORECARD | points / weights / bands (hardRules = POLICY_ELIGIBILITY → shadow flag) |
| CREDIT CONTROL | fact preparation; demo/gap not decision truth |
| WORKFLOW / W6 | readiness only (COMPLETE≠APPROVE) |
| LIMIT / PRICING | amount / rate controls |
| UNDERWRITING | orchestration + audited manual override |
| FROZEN / legacy UW engines | live/replay until retirement gate |

## FOIR

BEFORE: multi-authority (CC defaults + scorecard hardRules + legacy engines)  
AFTER: CC=prep; Policy/CPR=threshold; demo/gap refused as decision truth
