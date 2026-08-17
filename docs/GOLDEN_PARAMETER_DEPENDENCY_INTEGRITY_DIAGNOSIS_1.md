# GOLDEN-PARAMETER-DEPENDENCY-INTEGRITY-1 — DIAGNOSIS (pre-fix)

## Observed contradiction (Client cfdf903)

| ID | Class | Mode | businessReadiness | reason | capability | valueAvailable | exec.deps |
|----|-------|------|-------------------|--------|------------|----------------|-----------|
| bureau.inquiries.current_month | BUSINESS_PARAMETER | BUILT_IN | READY | READY | true | false | [] |
| bureau.inquiry | INGREDIENT | RAW | NOT_READY | RAW_FIELD_NOT_AVAILABLE | false | false | [] |
| bureau.inquiry.date | INGREDIENT | RAW | NOT_READY | RAW_FIELD_NOT_AVAILABLE | false | false | [] |

Catalogue (`GacatCatalogueSeed`) declares current_month dependencies:
`[bureau.inquiry, bureau.inquiry.date]` — same IDs shown in UI "How is this calculated?".

## How parent became READY

1. `bureau.inquiries.current_month` is registered in `ExecutionSpineProducerBootstrap.RAW_FACT_IDS`, so `RawFactProducer.hasCapability=true` even with no applicant facts (`DATA_NOT_AVAILABLE`).
2. `BusinessReadinessProjector` treats `capability=true` as structurally READY for derived/built-in.
3. Dependency recursion uses only `execution.dependencies` or authored calculation deps — both empty for this ID — so **catalogue `requiredPrimitives` are never consulted**.
4. Even if deps were present, projector **only propagates** NOT_READY when dep reason ∈ `{CALCULATION_NOT_DEFINED, CALCULATION_INVALID, DEPENDENCY_NOT_READY}` — **`RAW_FIELD_NOT_AVAILABLE` and `SOURCE_*` are ignored**.

## TEST CONDITION 2 verdict

| Option | Verdict |
|--------|---------|
| A. raw dependency status wrong | **NO** — `bureau.inquiry` / `bureau.inquiry.date` have no CPES producer (not in RAW_FACT_IDS); Equifax collection path is `bureau.inquiries`. Structural RAW_FIELD_NOT_AVAILABLE is honest for those exact IDs. |
| B. derived readiness wrong | **YES** |
| C. dependency IDs do not resolve to same canonical records | **Partial** — IDs match catalogue/UI, but readiness path never looks them up |
| D. aliases causing identity divergence | **NO** for these three exact IDs |
| E. other defect | **YES** — RawFactProducer capability for a catalogue-DERIVED ID without compositional dependency integrity; projector filters out structural RAW/SOURCE dep failures |

## ROOT_CAUSE (final)

**B + A (corrected):**

1. **Derived readiness was wrong:** `BusinessReadinessProjector` treated CPES `capability=true` as sufficient and ignored catalogue `requiredPrimitives`. It also filtered out `RAW_FIELD_NOT_AVAILABLE` / `SOURCE_*` when propagating dependency failures.
2. **RAW status was wrong for Equifax-normalized ingredients:** `bureau.inquiry` / `bureau.inquiry.date` (and tradeline fields used by BMS metrics) are extracted by Equifax normalization but were not registered as structural RAW producers — so they incorrectly showed `RAW_FIELD_NOT_AVAILABLE`.

## Fix

- Compose derived READY only when every catalogue-resolvable mandatory dependency is READY (any structural NOT_READY → `DEPENDENCY_NOT_READY`).
- `PlatformNormalizedRawFieldCatalog` + RawFactProducer registration for Equifax-normalized fields (structural mapping ≠ applicant valueAvailable).
- Stamp `calculation.dependencyCanonicalIds` / `structuralDependencies` from the same identity list.

## Audit (post-fix)

TOTAL_DERIVED_PARAMETERS = 60  
READY_DERIVED_WITH_NOT_READY_DEPENDENCY_COUNT = 0  
UNRESOLVED_DEPENDENCY_ID_COUNT = 0  
