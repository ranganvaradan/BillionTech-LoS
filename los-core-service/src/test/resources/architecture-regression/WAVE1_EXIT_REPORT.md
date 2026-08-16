# WAVE 1 EXIT REPORT — Execution Result Contract

Contract convergence only. No Wave 2 work. Wave 0 MUST_PRESERVE goldens remain green.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `26c44f304dbcd6035601c1792d0151ae1f7877bf` |
| SOURCE_FINAL_SHA | `bc35f12f24948e5aa989e8a851ff4f33db365f29` |
| GITHUB_REMOTE | `https://github.com/ranganvaradan/BillionTech-LoS.git` |
| GITHUB_REMOTE_SHA | `bc35f12f24948e5aa989e8a851ff4f33db365f29` (after freeze push) |
| INTERNAL_SHA (deployed) | `3da1ad4316eeda581975da41e6a23b2a258d4029` |
| CLIENT_SHA (deployed) | `3da1ad4316eeda581975da41e6a23b2a258d4029` |
| SAME_ARTIFACT | YES (staging Internal/Client unchanged this wave) |
| DB_MIGRATION | **NONE** |

## Contract

| Field | Value |
|-------|-------|
| EXECUTION_RESULT_CLASS_BEFORE | `ExecutionResult` (status/value/producer/capability/provenance) |
| EXECUTION_RESULT_CLASS_AFTER | `ExecutionResult` extended: `valueType`, `unit`, `producerVersion`, `evaluationAsOf`, `sourceSnapshotVersion`, `missingReason`, `mode` + `toCanonicalContractMap()` |
| Parallel result class added | **NO** |

### EXECUTION_STATUS_VOCABULARY

`VALUE_AVAILABLE` · `DATA_NOT_AVAILABLE` · `DEPENDENCY_NOT_AVAILABLE` · `INPUT_REQUIRED` · `NOT_EXECUTABLE` · `CALCULATION_NOT_DEFINED` · `ERROR`

Layer taxonomy: `ExecutionStatusVocabularyGuide`  
(`EXECUTION_STATUS` / `ACQUISITION_STATUS` / `AUTHORING_STATUS` / `POLICY_STATUS` / `CERTIFICATION_STATUS` / `LEGACY_DISPLAY_STATUS`)

### CAPABILITY_SEMANTICS

Producer exists and supports mode for this exact canonical ID — independent of whether this application currently has a value.

### VALUE_AVAILABILITY_SEMANTICS

`valueAvailable() == (status == VALUE_AVAILABLE)`.  
`0` / `false` / `""` / empty valid collection are values, not missing.

### MISSING_DATA_SEMANTICS

| Status | Meaning |
|--------|---------|
| DATA_NOT_AVAILABLE | Capable producer; required source/fact absent |
| DEPENDENCY_NOT_AVAILABLE | Capable authored path; dependency unresolved |
| INPUT_REQUIRED | Manual/config input required |
| NOT_EXECUTABLE | No valid producer for exact ID/mode |
| CALCULATION_NOT_DEFINED | Authored definition present but not executable (semantic/op mismatch) |
| ERROR | Unexpected failure / cycle / null context |

Absent authored definition (dynamic producer does not claim) → `NOT_EXECUTABLE` (capability false). Invalid stored definition → `CALCULATION_NOT_DEFINED`.

## Producers / consumers

**PRODUCERS_CONVERGED:** RawFactProducer · BuiltInBureauMetricProducer · BuiltInBankingMetricProducer · AuthoredDerivedProducer · ManualInputProducer · CPES Policy-Test simulation fallback  

**CONSUMERS_CONVERGED:** W6CanonicalParameterExecutor · PolicyStudioTestExperienceService · CanonicalScorecardValueResolver · ParameterExecutabilitySupport (D&P Gate3) · CanonicalParameterCapabilityProjection (Studio/Inventory) · CanonicalExecutionContractProjection  

**LEGACY_STATUS_VOCABULARIES_REMAINING:** W6 acquisition (`SOURCE_ACQUIRED`…) · Policy DSL dispositions · authoring TESTED/ACCEPTED · D&P display labels (`SUPPORT_SUPPORTED_*`) — projected, not execution authority  

**CATALOGUE_FLAGS_USED_AS_EXECUTION_AUTHORITY = NO**

## 169-parameter capability parity

| Metric | BEFORE (Wave 0) | AFTER (Wave 1) |
|--------|----------------:|---------------:|
| POLICY_TEST_CAPABLE | 38 | **40** |
| W6_CAPABLE | 28 | **30** |
| UNDERWRITING_CAPABLE | 38 | **40** |

### PARAMETERS_WITH_STATUS_CHANGED / WHY_CHANGED

| Canonical ID | Change | Class |
|--------------|--------|-------|
| `banking.avg_daily_balance_3m` | capability false→true (empty ctx); status remains `DATA_NOT_AVAILABLE` | EXPECTED_WAVE_1_CONTRACT_CHANGE |
| `banking.emi_bounce_count_3m` | same | EXPECTED_WAVE_1_CONTRACT_CHANGE |

**WHY:** `BuiltInBankingMetricProducer.hasCapability` previously required facts/config and equated “no value now” with “not capable”. Wave 1 separates capability from value availability. **No new producer and no new calculation.**

Manual still unsupported in W6 mode → W6 remains 10 below PT/UW (KNOWN_GAP unchanged in kind).

## Vikasam / Wave 0

| Check | Result |
|-------|--------|
| VIKASAM_13_REGRESSION | PASS (local harness; no producer/calc/rule mutation) |
| ENVIRONMENT_DRIFT_FOUND | **NOT VERIFIED against Client live DB this wave.** Wave-0 unit baseline still records invalid MONTHS_SINCE authored def for `bureau.dpd_30_plus_count_6m` (capability false). Client golden policy DB may hold a corrected definition — do not overwrite either environment in Wave 1. |
| WAVE0_REGRESSION_RESULTS | PASS — MUST_PRESERVE green; capability-count delta classified EXPECTED_WAVE_1_CONTRACT_CHANGE only |
| Wave0 known gaps preserved | overdue NOT_EXECUTABLE; catalogue flags ≠ cert; DSL not production-active |

## Flags

| Flag | Value |
|------|-------|
| GACAT_MUTATED | NO |
| VIKASAM_MUTATED | NO |
| NEW_CALCULATION_CAPABILITY_ADDED | NO |
| POLICY_RUNTIME_CHANGED | NO |
| BUSINESS_BEHAVIOUR_INTENTIONALLY_CHANGED | NO |
| DB_MIGRATION | NONE |
| WAVE_2 | **NOT STARTED** |

## Tests

```bash
mvn -q "-Dtest=Wave1ExecutionResultContractTest,Wave0ArchitectureRegressionTest,Wave0CapabilitySnapshotTest,Wave0VikasamBaselineTest,Vikasam13ExecutionAcceptanceTest" test
```

Result: **PASS** (including regenerated `gacat-169-capability-snapshot.json` at 40/30/40).

## WAVE_1_EXIT_CRITERIA

**PASS**

STOP. Do not start Wave 2.
