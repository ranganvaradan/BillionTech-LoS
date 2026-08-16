# WAVE 2 EXIT REPORT — Generic Calculation Engine

Extension of existing `SafeDerivedExpressionEvaluator` only. No second engine. No Wave 3.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `61e2f5de2f1feef914c25ccb2496ab44db7cc1ce` |
| SOURCE_FINAL_SHA | *(set on commit)* |
| DB_MIGRATION | **NONE** |
| WORKING_TREE_TRACKED_CLEAN_BEFORE | YES |
| KNOWN_UNTRACKED_ARTIFACTS_PRESERVED | YES (docs/, tmp-*, policy-studio-resolutions/, scripts/) |

## Engine

| Field | Value |
|-------|-------|
| SAFE_DERIVED_ENGINE_REUSED | **YES** |
| OLD_OPERATOR_SET | REF, CONST, ADD, SUB, MUL, DIV, GT, GTE, LT, LTE, EQ, IF, EVAL_AS_OF, MONTHS_SINCE_LAST_MATCH, COUNT_PERIODS_MATCHING |
| NEW_OPERATOR_SET | + AND, OR, NOT, FILTER, FIELD, PROJECT, COUNT, SUM, AVG, MIN, MAX, DISTINCT, ANY, ALL, FLATTEN, TRAILING_WINDOW |

## Models

**GENERIC_COLLECTION_MODEL** — `COLLECTION<RECORD>` / history as `List<Map>` in EvaluationContext inputs; FILTER/PROJECT/TRAILING_WINDOW compose over records; missing REF ≠ empty list.

**TYPE_SYSTEM** — `SafeDerivedTypeModel` (NUMBER/INTEGER/DECIMAL/MONEY/BOOLEAN/STRING/ENUM/DATE/YEAR_MONTH/COLLECTION/RECORD); used by `validate` / runtime rejects.

**MISSING_VS_EMPTY_MODEL** — missing/null REF → `DATA_INSUFFICIENT`; empty collection / empty filter → COUNT/SUM 0 (valid).

**TEMPORAL_MODEL** — `EVAL_AS_OF` + `TRAILING_WINDOW(windowMonths, dateField, asOf)`; no `LocalDate.now()`.

## Goldens

| Test | Result |
|------|--------|
| FILTER_SUM_TEST | PASS (7500) |
| FILTER_COUNT_TEST | PASS (2) |
| DISTINCT_COUNT_TEST | PASS (3, no COUNT_PERIODS_MATCHING) |
| MAX_TEST | PASS (60) |
| AVG_TEST | PASS (200) |
| ANY_ALL_TEST | PASS |
| EMPTY_COLLECTION_TEST | PASS (0) |
| MISSING_COLLECTION_TEST | PASS (DATA_INSUFFICIENT, not 0) |
| DERIVED_ON_DERIVED_TEST | PASS (CPES) |
| TYPE_SAFETY_TEST | PASS |
| TEMPORAL_TEST | PASS |
| BACKWARD_COMPATIBILITY_TEST | PASS (clean_history + COUNT_PERIODS dpd) |

**PARAMETER_SPECIFIC_EXECUTION_BRANCHES_ADDED** = **NONE** (evaluator source-guarded)

## Representability (no production defs created)

| Calc | REPRESENTABLE | GENERIC_EXPRESSION (sketch) | MISSING_CAPABILITY |
|------|---------------|----------------------------|-------------------|
| A. SUM CC overdue | YES | FILTER account_type==CREDIT_CARD → PROJECT overdue_amount → SUM | Production tradeline collection materialization (Wave 3) |
| B. SUM total overdue | YES | PROJECT overdue_amount → SUM | Wave 3 materialization |
| C. Active unsecured count | YES | FILTER status==ACTIVE AND secured==false → COUNT | Wave 3 materialization |
| D. Max DPD over history | YES | PROJECT dpd → MAX | — |
| E. DPD≥N month count | YES | TRAILING_WINDOW → FILTER dpd≥N → PROJECT month → DISTINCT → COUNT | — (COUNT_PERIODS_MATCHING remains compatibility op) |
| F. CC utilisation | PARTIAL | DIV(SUM filtered utilised, SUM filtered limit) | Needs utilisation/limit fields on tradeline records |
| G. Obligation/income ratio | YES | DIV(REF obligation, REF income) | — |

## Assistant honesty

**ASSISTANT_FALSE_EXECUTABLE_CLAIMS_REMAINING** — `cc_overdue` CONFIRM_EXISTING no longer returns `CAN_CALCULATE` / `knownExistingCalculation=true`. Outcome `MISSING_DATA` with explicit “not executable without authored FILTER+SUM definition”. Other KNOWN_IMPLEMENTED CONFIRM_EXISTING paths unchanged (Wave 2 scope: cc_overdue false-positive).

## Regression

| Suite | Result |
|-------|--------|
| WAVE0_REGRESSION | PASS |
| WAVE1_REGRESSION | PASS |
| VIKASAM_REGRESSION | PASS |
| SafeDerived / CPES / Surface / W6 / Scorecard spine | PASS |

## Flags

| Flag | Value |
|------|-------|
| GACAT_MUTATED | NO |
| VIKASAM_MUTATED | NO |
| PRODUCTION_DEFINITIONS_CREATED | NONE |
| POLICY_RUNTIME_CHANGED | NO |
| SCORECARD_LOGIC_CHANGED | NO |
| DB_MIGRATION | NONE |
| WAVE_3 | **NOT STARTED** |

## WAVE_2_EXIT_CRITERIA

**PASS**

STOP. Do not start Wave 3.
