# WAVE 5 — Policy Engine Forensic Inventory (pre-implementation)

Captured at SOURCE_START_SHA `d6c662a78b7c2e937ea3348e0294b0c02a9ca336`.

## Live decision authority (unchanged in Wave 5)

`LoanApplicationFlowService` → `UnderwritingRuleEngine` + `ScorecardPolicyEngine`

Shadow / Studio / Frozen replay paths are **not** live cutover targets in this wave.

## Inventory summary

| Classification | Count (significant) |
|----------------|--------------------:|
| POLICY_SEMANTICS_AUTHORITY (DSL / CPES / SafeDerived) | ~4 |
| LEGACY_POLICY_RUNTIME | ~5 |
| SCORECARD_SCORING | ~8 |
| CREDIT_CONTROL | ~3 |
| DATA_COMPLETENESS | ~3 |
| TEST_ONLY / shadow | ~15+ |
| COMPATIBILITY | ~10 |
| UNKNOWN / intake-adjacent | ~2 |

## Key paths

| Component | Classification | CPES | Wall-clock | Live decision? |
|-----------|----------------|------|------------|----------------|
| PolicyDslInterpreterV1 | POLICY_SEMANTICS_AUTHORITY (target engine) | No (metrics map) | No (EvaluationClock) | No today |
| CanonicalParameterExecutionService | PARAMETER VALUE SPINE | Is CPES | No | No |
| FrozenUnderwritingRuleEngine | LEGACY_POLICY_RUNTIME | No | No | Via adapter/replay |
| UnderwritingRuleEngine | LEGACY_POLICY_RUNTIME | No | No | **YES** |
| ScorecardPolicyEngine | SCORECARD_SCORING (+ hardRules) | Partial mapped | No | **YES** |
| CreditControlService | CREDIT_CONTROL (prep) | No | No | Indirect |
| PolicyStudioTestExperienceService | TEST_ONLY | Yes (pre-fill) | Fixed clock | No |
| PolicyGraphPolicyTestService | TEST_ONLY DSL_WITHOUT_SPINE | No | Fixed | No |
| ShadowPolicyEngine | TEST_ONLY | No | Instant for stamps | No |
| FormulaEvaluator | SCORECARD_SCORING | No | No | Score only |
| PolicyBureauMetricService | COMPATIBILITY studio | No | No | No |
| DataCompletenessGate | DATA_COMPLETENESS | No | No | No |

## Operator inventory (Policy DSL V1)

AND OR NOT IF EQ NE GT GTE LT LTE BETWEEN IN NOT_IN EXISTS IS_MISSING
ADD SUB MUL DIVIDE COUNT SUM AVG MIN MAX

Frozen hard-rule conditions: `OP:value` (GTE/GT/LTE/LT/EQ/NE/BETWEEN) + PARAM_REF.

## Frozen rule shape

`rulesJson.hardRules[]`: `{ parameter, source, condition, decision, message, dependsOn }`  
plus constraints `minBureauScore`, `maxLoanAmount`, `requireKycSuccess`, `scorecardRules`.

Hard rule semantics: **condition match → trigger decision (REJECT/MANUAL)**  
(opposite polarity from DSL PASS-when-true eligibility expressions).

## Scorecard / CreditControl overlaps (Wave 7 ownership)

| Area | Classification |
|------|----------------|
| Scorecard hardRules[] | DUPLICATE_POLICY_LOGIC / POLICY_ELIGIBILITY embedded in scorecard |
| Scorecard bands/weights | SCORECARD_SCORING (preserve) |
| CreditControl FOIR/gap defaults | POST_POLICY_CONTROL / prep |
| CreditControl SCF min GST | LIMIT_CONTROL / hardcoded |
| ApplicationScorecardParameterResolver age | WORKFLOW_CONTROL / wall-clock |

Full class-level inventory retained in agent transcript Wave5 explore; Wave 5 implements target runtime + shadow without retiring Frozen.
