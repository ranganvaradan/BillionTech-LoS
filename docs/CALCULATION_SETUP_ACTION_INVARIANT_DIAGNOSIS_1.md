# CALCULATION-SETUP-ACTION-INVARIANT-1 — Diagnosis

## Sentinels

| | Average account age (months) | Credit-card overdue amount |
|--|--|--|
| canonicalId | `bureau.average_account_age_months` | `bureau.cc_overdue_amount` |
| businessReadiness | NOT_READY | NOT_READY |
| reason | CALCULATION_NOT_DEFINED | CALCULATION_NOT_DEFINED |
| nextAction / allowedActions | Set up calculation | Set up calculation |
| Legacy parameterSupport | CALCULATION_NOT_IMPLEMENTED | SUPPORTED_DERIVED (false catalogue authority) |

## Action paths (pre-fix)

```
AVERAGE_ACCOUNT_AGE_ACTION_PATH =
  CanonicalParameterState → presentation.allowedActions=["Set up calculation"]
  → SuggestCalculationWorkflow.needsSetup=true via legacy supportStatus match
  → setup card visible

CC_OVERDUE_ACTION_PATH =
  CanonicalParameterState → presentation.allowedActions=["Set up calculation"]
  → SuggestCalculationWorkflow.needsSetup=false (no calculationRequired prop;
     supportStatus ≠ CALCULATION_NOT_IMPLEMENTED)
  → workflow suppressed → dead-end UX
```

## Root cause

```
ROOT_CAUSE_OF_ACTION_DIVERGENCE =
  Setup visibility gated on legacy parameterSupport / calculationRequired,
  not on canonical businessReadinessReason === CALCULATION_NOT_DEFINED
  / presentation.allowedActions / nextAction.
```

## Fix (this task)

- Shared gate: `requiresCalculationSetupAction` in `lenderTruthDisplay.ts`
- `SuggestCalculationWorkflow` primary CTA = **Set up calculation**; clarification / proposal review inside setup
- Data & Parameters detail + cards, Policy inventory, Policy Studio resolver/rules, parameter resolver wired to canonical authority
- Resolver: proposal case keeps outer primary **Set up calculation**; `setupFlowHint` = Review proposed calculation
- Backend audit: `CalculationSetupActionInvariantTest`
- Static UI architecture guard in vitest

Clarification and proposal review remain **authoring-workflow** states inside setup — they must not replace the primary setup entry point.
