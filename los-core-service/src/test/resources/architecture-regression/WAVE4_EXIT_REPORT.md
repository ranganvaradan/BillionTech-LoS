# WAVE 4 EXIT REPORT — GACAT Semantic Classification

Semantic registry / governance only. No Wave 5. No ID churn. No CPES capability change.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `1d32d74cb4b1ea055ed20e8b940c8e4024c1cdaa` |
| SOURCE_FINAL_SHA | *(set after content commit)* |
| GITHUB_REMOTE_SHA | *(set after push)* |
| DB_MIGRATION | **NONE** (code registry + JSON artifact; no schema change) |
| SEMANTIC_VERSION | `GACAT-SEMANTIC-4.0.0` |

## GACAT_TOTAL

**169** — all classified (`unknownSemanticCount = 0`)

## PARAMETER_CLASS_COUNTS

| Class | Count |
|-------|------:|
| INGREDIENT | 65 |
| BUSINESS_PARAMETER | 90 |
| MANUAL_INPUT | 10 |
| CONFIGURATION | 4 |
| DECISION_OUTPUT | 0 |

## CARDINALITY_COUNTS

| Cardinality | Count |
|-------------|------:|
| SCALAR | 110 |
| COLLECTION | 56 |
| HISTORY | 3 |

## CALCULATION_MODE_COUNTS

| Mode | Count |
|------|------:|
| RAW | 95 |
| BUILT_IN | 27 |
| AUTHORED | 33 |
| MANUAL | 10 |
| CONFIG | 4 |

## VALUE_TYPE_COUNTS

See artifact `baselines/gacat-169-semantic-classification.json`.

## UNKNOWN_SEMANTIC_COUNT

**0**

## FULL_CLASSIFICATION_ARTIFACT

`los-core-service/src/test/resources/architecture-regression/baselines/gacat-169-semantic-classification.json`

Columns: canonicalId, family, parameterClass, cardinality, calculationMode, valueType, unit,
policySelectableDefault, sourceDomain, dependencies, legacyType, legacyImplemented,
legacyProductionReady, semanticIssue, aliasGroup, overlapRelation.

## COLLECTION_ELEMENT_CONTRACTS

| Collection | Status |
|------------|--------|
| bureau.tradelines | Formalized (Wave-3 materialized) |
| bureau.tradeline.payment_history | Formalized HISTORY |
| bureau.inquiries | Formalized |
| bank.transactions / bank.accounts | Assessed — not catalalogued / not fabricated |
| GST / ITR periods | Assessed — period ingredients only |

## LEGACY_FLAGS_CLASSIFIED_AS_METADATA_ONLY

`implemented` / `production_ready` / catalogue `type` retained as **LEGACY_EXECUTION_METADATA**.
Test proves flipping them does **not** change CPES capability for `bureau.cc_overdue_amount`.

## POLICY_SELECTABLE_DEFAULT_COUNTS

| Selectable | Count |
|------------|------:|
| true | 100 |
| false | 69 |

Default: BUSINESS_PARAMETER + MANUAL_INPUT → true; INGREDIENT / CONFIGURATION → false.
Metadata only — Policy Studio picker filtering deferred (Wave 9); existing policy refs preserved.

## KNOWN_OVERLAPS_CLASSIFICATION

| Pair | Relation |
|------|----------|
| status_ntc ↔ thin_file_indicator | OVERLAPPING_CONCEPT |
| recent_inquiries_90d ↔ inquiries.last_3m | SEMANTICALLY_DISTINCT |
| recent_inquiries_90d ↔ BUREAU_ENQUIRIES_3M | DEPRECATED_ALIAS (dangerous) |
| cc_overdue_amount ↔ overdue.amount | SEMANTICALLY_DISTINCT |
| clean_history_months ↔ months_since_last_delinquency | SEMANTICALLY_DISTINCT |
| kyc.pan.verified ↔ kyc.pan_verified | TRUE_ALIAS |
| tradeline.* ↔ bureau.tradelines | NEEDS_REVIEW (field vs collection key) |
| itr.income.total ↔ financial.revenue | OVERLAPPING_CONCEPT |
| aa.transport.note | CONFIGURATION meta |

## Capability (empty context)

| Mode | Before | After |
|------|-------:|------:|
| POLICY_TEST | 40 | **40** |
| W6 | 30 | **30** |
| UNDERWRITING | 40 | **40** |

## VIKASAM_13_SEMANTIC_CLASSIFICATION

| ID | Class | Mode | Cardinality |
|----|-------|------|-------------|
| bureau.score | BUSINESS_PARAMETER | RAW | SCALAR |
| bureau.recent_inquiries_90d | BUSINESS_PARAMETER | BUILT_IN | SCALAR |
| bureau.settled_account_count | BUSINESS_PARAMETER | BUILT_IN | SCALAR |
| bureau.written_off_account_count | BUSINESS_PARAMETER | BUILT_IN | SCALAR |
| bureau.accounts.cc_writeoff | BUSINESS_PARAMETER | BUILT_IN | SCALAR |
| bureau.accounts.writeoff_non_cc | BUSINESS_PARAMETER | BUILT_IN | SCALAR |
| bureau.tradeline.suit_filed | BUSINESS_PARAMETER | RAW | SCALAR |
| bureau.credit_after_overdue.clean_history_months | BUSINESS_PARAMETER | AUTHORED | SCALAR |
| bureau.dpd_30_plus_count_6m | BUSINESS_PARAMETER | AUTHORED | SCALAR |
| bureau.cc_overdue_amount | BUSINESS_PARAMETER | AUTHORED | SCALAR |
| bureau.overdue.amount | BUSINESS_PARAMETER | AUTHORED | SCALAR |
| bureau.overdue.age_months | BUSINESS_PARAMETER | AUTHORED | SCALAR |
| bureau.max_dpd_6m | BUSINESS_PARAMETER | BUILT_IN | SCALAR |

Plus catalogue: `bureau.tradeline.payment_history` → INGREDIENT / RAW / HISTORY.

Vikasam policy **not mutated**.

## Regression

| Suite | Result |
|-------|--------|
| WAVE0 | PASS |
| WAVE1 | PASS |
| WAVE2 | PASS |
| WAVE3 | PASS |
| WAVE4 | PASS |
| W6 / PT / Surface / Vikasam | PASS |

## Flags

| Flag | Value |
|------|-------|
| CANONICAL_IDS_CHANGED | **0** |
| PRODUCERS_ADDED | **0** |
| CALCULATION_DEFINITIONS_CREATED | **0** |
| GACAT_RUNTIME_SEMANTICS_CHANGED | **NO** |
| VIKASAM_MUTATED | **NO** |
| POLICY_RUNTIME_CHANGED | **NO** |
| SCORECARD_LOGIC_CHANGED | **NO** |
| WAVE_5_STARTED | **NO** |

## WAVE_4_EXIT_CRITERIA

| # | Criterion | Status |
|---|-----------|--------|
| 1 | All 169 classified | PASS |
| 2 | Ingredient vs business parameter explicit | PASS |
| 3 | Scalar vs collection/history explicit | PASS |
| 4 | Calculation mode explicit | PASS |
| 5 | Collection element contracts for Wave-3 bureau | PASS |
| 6 | Legacy flags ≠ CPES capability | PASS |
| 7 | No canonical ID change | PASS |
| 8 | No execution capability added | PASS |
| 9 | No calculation definition created | PASS |
| 10 | Existing policies remain valid | PASS |
| 11 | Wave 0–3 green | PASS |

**WAVE_4_EXIT_CRITERIA = PASS**

STOP. Do not start Wave 5.
