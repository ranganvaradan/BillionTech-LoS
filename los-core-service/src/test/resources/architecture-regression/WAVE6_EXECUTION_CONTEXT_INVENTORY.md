# WAVE 6 — Execution Context Factory Inventory

Captured at SOURCE_START_SHA `3293e22aba543047a5ce8e793a1b2ef8d56cc22f`.

## Live decision authority (unchanged)

`LoanApplicationFlowService` → `UnderwritingRuleEngine` + `ScorecardPolicyEngine`

## Context builders

| Path | Input / facts | Canonical IDs | asOf | Mode | CPES | Divergences addressed in Wave 6 |
|------|---------------|---------------|------|------|------|----------------------------------|
| PolicyStudioTestExperienceService.resolveViaExecutionSpine | testValues + seed | exact + was dual-stamp incl. dangerous | fixed 2026-08-01 | POLICY_TEST | YES | Remove dangerous stamp; unify asOf |
| PolicyStudioTestExperienceService.evaluateRule | metrics/facts | exact via CPR | was 2024-06-15 | POLICY_TEST | YES | Same CANONICAL_POLICY_TEST_AS_OF |
| CanonicalPolicyRuntime | spine EC | exact operands | explicit required | any | YES | Honor onMissing; shared shape |
| W6EvaluationContextFactory | acquisition snapshot | exact via materializer | explicit→reportDate→PH→null | W6 | downstream | Shared bridge to CPR |
| UnderwritingEvaluationContextFactory | UW + app | projectExact | often null | UW | consumer | Prefer ApplicationPolicyQueryFactory business date |
| ScorecardFactorValueAdapter | UW factory | exact | same as UW | UW | YES | Shared asOf with policy target path |
| PolicyGraphPolicyTestService | caller maps | opaque | fixed 2024-06-15 | N/A | **NO** | Rewire → CPR + CPES |
| ShadowPolicyEngine | frozen maps | DSL maps | default 2024-06-15 | shadow | NO | Canonical shadow via CPR; legacy marked WAVE_10 |
| PolicyRuntimeShadowParity | outcomes | N/A | observational | N/A | compares CPR | Expand corpus |
| ApplicationScorecardParameterResolver.age | personal DOB | N/A | **LocalDate.now()** | scorecard | bypass | Accept explicit asOf |
| evaluation.EvaluationContextFactory (CI durable) | freeze | separate | now() fallback | CI | N/A | Not spine; inventory only |

## Alias classes (CanonicalCompatibilityRegistry)

| Class | Use on CPR path |
|-------|-----------------|
| TRUE_COMPAT_ALIAS | Allowed putIfAbsent / stamp |
| SOURCE_FIELD_MAPPING | Allowed |
| LEGACY_REMAP_PENDING_WAVE6 → promoted where identical | Allowed remap |
| DANGEROUS_ALIAS_REJECTED | **Never** satisfy operands (90d ≠ 3m) |

## Simulation honesty

CPES `POLICY_TEST_INPUT`: value may be available; `capability=false`; `simulatedValueDoesNotImplyExecutable=true`.
