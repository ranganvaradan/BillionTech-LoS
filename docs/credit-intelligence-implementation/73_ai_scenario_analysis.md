# 73 — AI Scenario Analysis (A1)

AI may describe what-if questions; **deterministic Decision Engine** computes amounts. AI only explains. Scenario rows are stored separately from canonical recommendations.

```mermaid
sequenceDiagram
  participant UW as Underwriter
  participant API as AiScenarioService
  participant DE as ShadowDecisionEngine
  participant Store as ci_ai_scenario
  UW->>API: AiScenarioRequest amount/tenure/collateral
  API->>DE: recommend(mutated DecisionRuntimeInput copy)
  DE-->>API: CiCreditRecommendation scenarioOnly
  API->>Store: deterministic_result + ai_explanation
  Note over DE,Store: Canonical recommendation untouched
```

## Flags

- `ai-underwriter.scenario-enabled` default **false**

## Rules

- Mutate a **copy** of `DecisionRuntimeInput` only
- Persist `ci_ai_scenario` + `SCENARIO` suggestion
- `overwritesCanonical=false`
- AI outage / disabled → `AI_ASSISTANCE_UNAVAILABLE` (underwriting continues)
