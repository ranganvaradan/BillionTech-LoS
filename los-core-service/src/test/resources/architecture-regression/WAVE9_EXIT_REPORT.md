# WAVE 9 EXIT REPORT — Unified Truth Projection and UI Convergence

One canonical truth projection consumed by lender/admin surfaces.
Projection-convergence only — **no** capability, policy, scoring, Frozen, or Vikasam mutation.

## Freeze

| Field | Value |
|-------|-------|
| SOURCE_START_SHA | `261e622e4cea9bb00f46178a0b9939c11999fca2` |
| SOURCE_FINAL_SHA | *(set at commit)* |
| GITHUB_REMOTE_SHA | *(set at push)* |

## TRUTH_PROJECTION_CLASS

`CanonicalParameterTruthProjection`  
Package: `com.los.core.creditintelligence.policystudio.truth`

## TRUTH_PROJECTION_INPUT_AUTHORITIES

| Axis | Authority |
|------|-----------|
| Semantic | `GacatSemanticRegistry` / `GacatSemanticProjection` (Wave 4) |
| Execution | CPES `ExecutionResult` / `ExecutionCapabilityAuthority` |
| Capability spine view | `CanonicalParameterCapabilityProjection` (facade) |
| Certification | `ProductionCertificationService` (Wave 8) |
| Display copy | `LenderTruthDisplayMapper` (shared) |
| Surface overlays | `SurfaceCanonicalTruthFacade` |

Does **not** duplicate CPES, certification, or GACAT semantics.

## READINESS_PROJECTIONS_BEFORE → AFTER

See `WAVE9_READINESS_DISPOSITION.json`.

Key rewires:
- `DataParametersAdminService.enrich` → attaches `canonicalTruth` + primary status from truth
- `ScorecardConvergenceService.toPickerItem` → consumes truth; ingredients hidden by default
- `CanonicalParameterCapabilityProjection` → certification from Wave-8 ledger when installed
- Frontend `dp1Display` / `lenderUxCopy` / `GacatFactorPicker` → prefer backend primary labels

Legacy helpers classified KEEP_AS_FACADE / REWIRE / RETIRE_IN_WAVE10 — no broad delete in Wave 9.

## SURFACES_CONVERGED

DATA_PARAMETERS · POLICY_STUDIO · POLICY_INVENTORY · SCORECARD_PICKER · POLICY_TEST · WORKFLOW_W6 · UNDERWRITING

## SHARED_LENDER_DISPLAY_MAPPER

`LenderTruthDisplayMapper` (+ frontend `lenderTruthDisplay.ts`)

## PRIMARY_STATUS_VOCABULARY

APPROVED_FOR_LIVE_USE · READY_TO_TEST · CALCULATION_NEEDS_SETUP · CAN_CALCULATE_WHEN_DATA_AVAILABLE · NEEDS_MANUAL_INPUT · NOT_YET_SUPPORTED · APPROVAL_REVOKED

## CALCULATION_EXPLANATION_MODEL

RAW → “Taken directly from …”  
BUILT_IN → BuiltIn plain English / calculationSummary  
AUTHORED capable → approved definition explanation  
Missing → “Calculation is not set up yet.”  
MANUAL → “Entered by the lender or application intake.”

## ADVANCED_DETAILS_MODEL

Single Advanced block: canonical ID, semantic, execution, calculation, certification, spineCapability

## POLICY_SELECTABILITY_MODEL

`policySelectableDefault` from Wave 4; INGREDIENT default-hidden in scorecard picker (`advanced:` / `include:ingredient` to reveal)

## Cross-surface goldens

| Golden | Result |
|--------|--------|
| CC_OVERDUE | BUSINESS_PARAMETER/AUTHORED · not executable · calc needs setup · UNCERTIFIED · surfaces agree |
| CLEAN_HISTORY | BUSINESS_PARAMETER/AUTHORED · capability=spine · cert independent · surfaces agree |
| DPD_COUNT | AUTHORED · capability=spine only · cert independent · surfaces agree |
| RAW bureau.score | RAW · capable · taken-directly explanation · cert independent |
| MANUAL application.borrower_type | MANUAL_INPUT · not “unsupported” |
| CERTIFIED fixture | UNCERTIFIED→not approved; CERTIFIED→Approved for live use; capability unchanged |

## Parity (169 IDs × 7 surfaces)

EXECUTION_CAPABILITY_MISMATCH_COUNT = **0**  
EXECUTION_STATUS_MISMATCH_COUNT = **0**  
CERTIFICATION_STATUS_MISMATCH_COUNT = **0**

## Catalogue flags

CATALOGUE_IMPLEMENTED_USED_AS_READINESS = **NO**  
CATALOGUE_PRODUCTION_READY_USED_AS_LIVE_STATUS = **NO**

## Capability

| Mode | Before | After |
|------|--------|-------|
| POLICY_TEST | 40 | 40 |
| W6 | 30 | 30 |
| UNDERWRITING | 40 | 40 |

PRODUCERS_ADDED = **0**  
DEFINITIONS_CREATED = **0**  
POLICY_RUNTIME_CHANGED = **NO**  
SCORECARD_LOGIC_CHANGED = **NO** (picker presentation only)  
LIVE_DECISION_AUTHORITY_CHANGED = **NO**  
VIKASAM_MUTATED = **NO**  
DB_MIGRATION = **NONE**

## Regression

| Suite | Result |
|-------|--------|
| WAVE0–8 | PASS (134 wave tests incl. Wave9) |
| Wave9UnifiedTruthProjectionTest | PASS (18) |
| Frontend lender display tests | PASS (20) |
| UI_PRODUCTION_BUILD | PASS |

## WAVE_9_EXIT_CRITERIA = PASS

STOP. Do not start Wave 10.
