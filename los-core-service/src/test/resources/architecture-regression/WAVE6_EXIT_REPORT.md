# WAVE 6 EXIT REPORT — Test / Live Execution Convergence

Converge Policy Test, Graph Test, target-live canonical, and shadow canonical onto
**CanonicalPolicyRuntime + CPES + exact IDs + explicit asOf**.
**Live decision authority unchanged.** Frozen **not** retired.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `3293e22aba543047a5ce8e793a1b2ef8d56cc22f` |
| SOURCE_FINAL_SHA | `c4ca1eb89a583095a9e2894a20dad0ec8b9cdf05` |
| GITHUB_REMOTE_SHA | `9d5aab26f6ddcba8e7dd40054aa85f117b02aa26` |
| DB_MIGRATION | **NONE** |

## EXECUTION_CONTEXT_PATHS_BEFORE

Policy Test dual asOf (2026-08-01 spine / 2024-06-15 DSL); Graph = DSL-only no CPES;
UW Scorecard often null asOf; age = LocalDate.now(); dangerous aliases stamped on PT;
ShadowPolicyEngine frozen maps without spine.

## SHARED_CONTEXT_CONTRACT_AFTER

`SharedCanonicalEvaluationSupport` + spine `EvaluationContext`  
Policy Test / Graph / TargetLive / W6→policy / Scorecard factory share the same semantic shape.
Adapters only — no second fact authority.

| Runtime | Class |
|---------|-------|
| POLICY_TEST_RUNTIME | CanonicalPolicyRuntime (via PolicyStudioTestExperienceService) |
| TARGET_LIVE_RUNTIME | TargetLiveCanonicalPolicyEvaluation → CanonicalPolicyRuntime |
| GRAPH_TEST_RUNTIME | PolicyGraphPolicyTestService → CanonicalPolicyRuntime |
| SHADOW_RUNTIME | PolicyRuntimeShadowParity + CanonicalPolicyRuntime (legacy ShadowPolicyEngine = WAVE_10) |

| Flag | Value |
|------|-------|
| ALL_CANONICAL_POLICY_PATHS_USE_CPES | **YES** |
| GRAPH_TEST_SPINE_BACKED | **YES** |
| SIMULATION_CHANGES_REAL_CAPABILITY | **NO** |
| LIVE_DECISION_AUTHORITY_CHANGED | **NO** |

## EVALUATION_ASOF_MODEL

Explicit required on CanonicalPolicyRuntime.  
Policy Test / Graph: request asOf or intentional Wave-0 default `2026-08-01` (recorded).  
Target-live / Scorecard UW: ApplicationPolicyQueryFactory business date (submittedAt/createdAt) — never now().

## WALL_CLOCK_CANONICAL_PATHS_REMAINING

- `evaluation.EvaluationContextFactory` durable CI freeze (not spine policy path)
- Legacy `ShadowPolicyEngine` / prospect sim clocks (not CPR authority)

## SIMULATION_OVERRIDE_MODEL

`SimulationOverride` + CPES `POLICY_TEST_INPUT` with `capability=false`, honesty projection fields.

## MISSING_DATA_SEMANTICS

Wave-1 statuses → DATA_INSUFFICIENT (or ERROR). Explicit `onMissing` PASS/FAIL/REFER honored identically in test and target-live. Default DI.

## ALIAS_TABLE

TRUE_COMPAT only on runtime stamp path.  
DANGEROUS: `BUREAU_ENQUIRIES_3M` ↛ `bureau.recent_inquiries_90d`.  
**DANGEROUS_ALIAS_COUNT** = entries classified DANGEROUS_ALIAS_REJECTED (>0).

## Parity

| Metric | Value |
|--------|-------|
| W6_POLICY_CONTEXT_PARITY | PASS (shared normalize) |
| SCORECARD_POLICY_CONTEXT_PARITY | PASS (same UW factory asOf) |
| NON_VIKASAM_PARITY_CASE_COUNT | 6 compound/op goldens |
| NON_VIKASAM_PARITY_MATCH_COUNT | 6 |
| NON_VIKASAM_PARITY_MISMATCH_COUNT | 0 |
| VIKASAM_TEST_LIVE | Policy Test ≡ TargetLive (overdue remain DI) |
| FROZEN_RETIREMENT_READY | **NO** |
| FROZEN_NOT_TRANSLATABLE | remains for unmapped keys |

## Capability

| Mode | Before | After |
|------|-------:|------:|
| POLICY_TEST | 40 | **40** |
| W6 | 30 | **30** |
| UNDERWRITING | 40 | **40** |

## Flags

GACAT_MUTATED = **NO**  
VIKASAM_MUTATED = **NO**  
PRODUCTION_DEFINITIONS_CREATED = **NONE**  
SCORECARD_LOGIC_CHANGED = **NO** (asOf wiring only; scoring formulas unchanged)  
WAVE_7_STARTED = **NO**

## Regression

Wave0–5 PASS · Wave6 PASS

## WAVE_6_EXIT_CRITERIA = PASS

STOP. Do not start Wave 7.
