# 14 — Retain / Refactor / Replace

## Retain (evolve in place)

| Component | Why |
|---|---|
| `UnderwritingRuleEngine` hardRules / dependsOn / PARAM_REF | Working hard-gate semantics |
| `ScorecardPolicyEngine` + parameterResults | Best explainability today |
| `FormulaEvaluator` | Deterministic core for Metric Engine |
| `underwriting_evaluations` | Append-only evaluation store |
| `LimitSizingService` pattern | Product metric/limit config |
| Workflow + CAM + sanction + VKYC gates | Decision/downstream |
| Provider clients (Equifax, Karza, GST, ITR, AA) | Become Source adapters |
| Flyway + PostgreSQL jsonb | Migration discipline |
| Assignment rules | Routing after decision |

## Wrap / transitional

| Component | How |
|---|---|
| `LoanApplicationFlowService.underwriteApplication` | Façade: snapshot + prod engines + shadow |
| `EffectiveUnderwritingContext` | Populate from Fact Snapshot over time |
| `rules_json` / scorecard JSON | Compile into policy_version |
| `AiLosIntegrationService` | Replace with analysis request envelope |
| Manual credit inputs UI | Write MANUAL facts + provenance |

## Refactor

| Component | Direction |
|---|---|
| `CreditControlService.resolveEffective` | Split: source merge vs metric compute vs defaults (remove silent gaps) |
| Equifax extract | Persist tradelines; derive bureau metrics |
| Authority / deviation (enhancement service) | Fold into Decision policy pack |
| Audit events | Correlate to evaluation_id / snapshot_id |

## Replace / retire

| Component | Reason |
|---|---|
| Silent gap defaults as policy inputs | Fabricates compliance |
| Demo AI-LOS URL fallback as success path | Misleading |
| `CreditRulesEngine` parallel authority | Consolidate |
| AI-LOS ML decision as SoR | Violates principle |
| Unversioned live policy mutation affecting history | Breaks reproducibility |
| Provider-specific keys in rules without facts | Coupling |

## Parallel engines to consolidate

Production underwrite · legacy `CreditDecisionServiceImpl` · `CreditRulesEngine` · `CreditEnhancementService` deviations → **one Policy + Decision path** with packages.
