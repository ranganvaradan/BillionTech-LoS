# WAVE 3 EXIT REPORT — Canonical Materialization & Provenance

DATA/FACT convergence only. No Wave 4. No GACAT semantic/classification change.
No Vikasam mutation. No production authored definitions. No Policy DSL → production.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `4d5d2289217416b91d01f672c3dc80db764a4777` |
| SOURCE_FINAL_SHA | `cece016f9cbd4f52f19b55fae6f762274b86120a` |
| GITHUB_REMOTE_SHA / FREEZE_TIP | `3ddf906ba8b59e869554768a22c1b01c527c85a5` |
| DB_MIGRATION | **NONE** |
| KNOWN_UNTRACKED_ARTIFACTS_PRESERVED | YES |

## MATERIALIZATION_PATH_BEFORE

```
Ci* → CiMetricResult → UnderwritingFactSnapshotBuilder (legacy remapped paths)
  → CiUnderwritingFact → W6EvaluationContextFactory / UnderwritingEvaluationContextFactory
  → EvaluationContext (mixed remapped IDs) → CPES
Collections (tradelines / payment_history / inquiries): NOT in EvaluationContext
BuiltInBanking stagingFixture: used in UW/W6 when calculator flags set
W6 asOf: LocalDate.now()
```

## MATERIALIZATION_PATH_AFTER

```
Ci* → metrics + CiBureauTradeline/PaymentHistory/Inquiry
  → UnderwritingFactSnapshotBuilder (legacy remaps + exact GACAT dual-write + collection facts)
  → CiUnderwritingFact
  → W6EvaluationContextFactory / UnderwritingEvaluationContextFactory
      → CanonicalCompatibilityRegistry.projectExactCanonicalFacts
      → CanonicalFactMaterializer (collections + asOf)
  → EvaluationContext (exact IDs + collections) → CPES
BuiltInBanking stagingFixture: POLICY_TEST only
W6 asOf: explicit → reportDate → latest payment_history month → null (never now())
```

## EXACT_CANONICAL_ID_BOUNDARY

`CanonicalCompatibilityRegistry` is the single compatibility registry.
At EvaluationContext / CPES boundary, `projectExactCanonicalFacts` ensures exact GACAT IDs
(e.g. `bureau.score`, `bureau.max_dpd_6m`, `bureau.recent_inquiries_90d`,
`banking.avg_daily_balance_3m`) when a TRUE_COMPAT remap/alias has a value.
Dangerous reverse materialization (`BUREAU_ENQUIRIES_3M` → `recent_inquiries_90d`) is rejected.

## SCALAR_FACTS_MATERIALIZED

Dual-write / projection for spine-capable scalars including:
`bureau.score`, `bureau.max_dpd_*`, `bureau.recent_inquiries_90d`, writeoff/settled/exposure metrics,
`banking.avg_daily_balance_3m`, `banking.emi_bounce_count_3m`, `application.declared_income`,
`kyc.pan.verified`, `gst.turnover.trailing_12m`, `application.requested_amount`, `kyc.quality`.

## COLLECTIONS_MATERIALIZED

| Key | Source | Notes |
|-----|--------|-------|
| `bureau.tradelines` | `CiBureauTradeline` | empty list when report present but no rows |
| `bureau.tradeline.payment_history` | `CiBureauPaymentHistory` | missing key vs empty list distinguished |
| `bureau.inquiries` | `CiBureauInquiry` | empty when report present |

Bank/GST/ITR period collections: **not fabricated** (normalized models assessed; not emitted without genuine support in this wave).

## PAYMENT_HISTORY_CONTRACT

Element fields from `CiBureauPaymentHistory` only: `month`/`period` (YYYY-MM), `dpd`,
`paymentStatus`/`status`, `tradelineId`/`tradeline_ref`, `observationId`, `sourceReference`, `estimated`.

## TRADELINE_CONTRACT

Fields from `CiBureauTradeline` only: account type/category, status, secured, overdue/current/limit,
opened/closed dates, suit_filed, written_off, settled, is_live, tradeline refs.

## INQUIRY_CONTRACT

`inquiry_date`/`date`, `member_name`, `purpose`, `amount`, `inquiryId`.

## BANKING_FIXTURE_PROD_PATH_REMOVED

`BuiltInBankingMetricProducer`: stagingFixture refused for UNDERWRITING / W6_ACQUISITION.
Facts absent → `DATA_NOT_AVAILABLE` with capability=true. POLICY_TEST fixture allowed with
`fixtureAuthority=POLICY_TEST_ONLY`.

## ACQUISITION_FACT_EXECUTION_SEPARATION

Preserved: entity `acquisitionSuccessDoesNotImplyValueAvailable=true`.
W6 acquisitionClaim still refuses readiness from source success alone for GACAT IDs.
Report acquired + payment_history omitted → RAW PH `DATA_NOT_AVAILABLE` → authored dep unavailable.

## EVALUATION_ASOF_MODEL

Never `LocalDate.now()` in W6/UW context factories.
Order: explicit caller asOf → bureau `reportDate` → derive from latest PH month → null
(+ `evaluationAsOfMissing` or `evaluationAsOfDerivedFromPaymentHistory`).

## PROVENANCE_MODEL_AFTER

| Producer | Provenance |
|----------|------------|
| RAW | sourceType, asOf, sourceSnapshotVersion, bureauReportId, materialization map, rowCount for collections |
| BUILT_IN banking | fixtureAuthority when POLICY_TEST; DNA reason when refused |
| AUTHORED | definitionId/version/status, operatorChain, dependencyProvenance, asOf, collectionInputs (identity + rowCount) |

## ALIAS_REMAP_TABLE

See `CanonicalCompatibilityRegistry.allEntries()`.

| Class | Examples |
|-------|----------|
| TRUE_COMPAT_ALIAS | score ↔ consumer.score / BUREAU_SCORE |
| SOURCE_FIELD_MAPPING | kyc.pan.verified ↔ kyc.pan_verified |
| LEGACY_REMAP_PENDING_WAVE6 | max_dpd_* ↔ bureau.dpd.max_*; inquiries.count_90d; banking.balance.average_3m |
| DANGEROUS_ALIAS_REJECTED | BUREAU_ENQUIRIES_3M ↔ recent_inquiries_90d (**not reversed**) |

## DANGEROUS_ALIASES_REMAINING

`compat.BUREAU_ENQUIRIES_3M` / `BUREAU_ENQUIRIES_3M` still listed in inventory/runtimeFactAliases for visibility;
never used for reverse exact-ID materialization.

## REAL_PH_GENERIC_DSL_TEST / REAL_TRADELINE_GENERIC_DSL_TEST

`Wave3CanonicalMaterializationTest`:
- PH: FILTER dpd≥30 → DISTINCT month → COUNT = 3 (via materializer → CPES, test-only def)
- Tradelines: FILTER CREDIT_CARD → PROJECT overdue_amount → SUM = 7500

## Capability counts (empty context)

| Mode | Before (Wave 2 tip) | After Wave 3 |
|------|---------------------|--------------|
| POLICY_TEST | 40 | **40** |
| W6 | 30 | **30** |
| UNDERWRITING | 40 | **40** |

**CAPABILITY_CHANGED**: none (expected — Wave 3 is materialization/value availability).
**VALUE_AVAILABILITY_CHANGED**: when normalized collections/scalars present in context (path-dependent).
**ID_MAPPING_CHANGED**: dual-write + projection only; no GACAT ID renames.

## VIKASAM_13_REGRESSION

Read-only. No new overdue defs.
Expected: suit_filed / clean_history / dpd_30_plus may gain value when PH/tradelines materialized in that environment;
cc_overdue / overdue.amount / overdue.age_months remain without production calculation.
Harness baseline (POLICY_TEST representative facts) unchanged disposition classes.

## Regression

| Suite | Result |
|-------|--------|
| WAVE0_REGRESSION | PASS |
| WAVE1_REGRESSION | PASS |
| WAVE2_REGRESSION | PASS |
| Wave3CanonicalMaterializationTest | PASS |
| W6ExecutionSpineClosureAcceptanceTest | PASS |
| WorkflowAcquisitionW6GoldensTest | PASS |
| Vikasam13 / SurfaceConvergence | PASS |
| UnderwritingFactSnapshotBuilderTest | PASS |

## Flags

| Flag | Value |
|------|-------|
| GACAT_MUTATED | **NO** |
| VIKASAM_MUTATED | **NO** |
| PRODUCTION_DEFINITIONS_CREATED | **NONE** |
| POLICY_RUNTIME_CHANGED | **NO** |
| SCORECARD_LOGIC_CHANGED | **NO** |
| PARAMETER_SPECIFIC_CALCULATORS | **NONE** |
| WAVE_4_STARTED | **NO** |

## WAVE_3_EXIT_CRITERIA

| # | Criterion | Status |
|---|-----------|--------|
| 1 | Exact canonical IDs at EvaluationContext boundary | PASS |
| 2 | Payment history reaches real execution contexts | PASS |
| 3 | Tradeline collection where normalized source exists | PASS |
| 4 | Generic SafeDerived on materialized collections | PASS |
| 5 | Missing ≠ empty collections | PASS |
| 6 | Banking fixture cannot produce live/UW/W6 value | PASS |
| 7 | Acquisition ≠ fact/value availability | PASS |
| 8 | ExecutionResult provenance improved | PASS |
| 9 | No parameter-specific calculation added | PASS |
| 10 | No GACAT semantic/classification change | PASS |
| 11 | No policy runtime change | PASS |
| 12 | Wave0/1/2 MUST_PRESERVE green | PASS |

**WAVE_3_EXIT_CRITERIA = PASS**

STOP. Do not start Wave 4.
