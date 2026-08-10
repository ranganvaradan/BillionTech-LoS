# 57 — Policy Engine Architecture (P1)

Shadow-only modernization of credit policy execution. Production underwriting authority is unchanged.

```mermaid
flowchart TB
  Studio[Policy Studio DRAFT_ONLY] -->|READY_FOR_POLICY_BUILD| Publish[PolicyPackagePublisher]
  Publish -->|SHADOW only| Pkg[CiExecutablePolicyPackage]
  Ctx[Frozen EvaluationContext / PolicyEvaluationInput] --> Eng[ShadowPolicyEngine]
  Pkg --> Eng
  Eng --> DSL[PolicyDslInterpreterV1]
  Eng --> Orch[DeclarativeOrchestrator]
  Eng --> Score[ScorecardDefinitionExecutor]
  Eng --> Eval[CiPolicyEvaluation]
  Eval --> Explain[PolicyDecisionExplanation]
  Eval --> Evidence[CreditEvidenceView.PolicyEngineShadow]
```

## Bounded context

`com.los.core.creditintelligence.policy`

| Concern | Owner |
|---------|-------|
| Authoring | Policy Studio |
| Publishing | PolicyPackagePublisher → SHADOW |
| Execution | ShadowPolicyEngine |
| Simulation / replay | PolicySimulationService / PolicyReplayService |
| Recommendation | PolicyEvaluationOutcome (not sanction) |

## Constraints

- Status used in P1: **SHADOW** (never ACTIVE authority)
- Single interpreter: `PolicyDslInterpreterV1`
- Feature flags `credit-intelligence.policy-engine.*` default **false**
- No CAM / sanction / VKYC / workflow / CreditControl gap-default changes
