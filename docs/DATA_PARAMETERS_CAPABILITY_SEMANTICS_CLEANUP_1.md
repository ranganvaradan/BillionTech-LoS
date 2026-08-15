# DATA-PARAMETERS-CAPABILITY-SEMANTICS-CLEANUP-1

## Model

Data & Parameters is a **SETUP / CAPABILITY** catalogue. It answers:

1. Has BillionTech integrated this provider/source?
2. Can that source produce this canonical parameter?
3. Has this lender subscribed / enabled that source?

It does **not** represent application-specific value availability.

## Status vocabularies

### Source platform integration
- `PRODUCTION_READY`
- `NOT_INTEGRATED`
- `NOT_APPLICABLE` (application / manual / internal)

### Parameter support
- `SUPPORTED_RAW`
- `SUPPORTED_DERIVED`
- `PROVIDER_DOES_NOT_SUPPORT`
- `CALCULATION_NOT_IMPLEMENTED`
- `SOURCE_NOT_INTEGRATED`
- `NOT_APPLICABLE`

### Lender organisation
- `SUBSCRIBED` (Equifax when `los.integration.equifax` configured or simulation)
- `NOT_YET_SUBSCRIBED`
- `SUBSCRIPTION_SETUP_PENDING`
- `NOT_APPLICABLE`

## Implementation

- `DataParametersCapabilitySemantics` — read-only projection; **does not mutate** GACAT `production_ready`
- Wired through `DataParametersAdminService.enrich` / overview `sourceCapabilitySummary`
- Lender UI: Platform Integration / Parameter Support / Your Organisation / Production Policy Use
- Gate3 / Policy Test / Runtime / Provider Bound moved under **Advanced / Technical Details**

## Bureau Retail golden (seed registry)

| Support | Count |
|---------|------:|
| SUPPORTED_RAW | 33 |
| SUPPORTED_DERIVED | 21 |
| CALCULATION_NOT_IMPLEMENTED | 8 |
| PROVIDER_DOES_NOT_SUPPORT | 0 |
| SOURCE_NOT_INTEGRATED | 0 |
| **TOTAL** | **62** |

### `bureau.accounts.cc_writeoff`

| Field | Value |
|-------|-------|
| Source | Equifax Bureau Retail |
| Platform | PRODUCTION_READY |
| Parameter support | SUPPORTED_DERIVED |
| Evidence | `BureauMetricService.computeWriteoffCounts`; workflow provides; runtime ready |
| Catalogue `production_ready` | **false** (unchanged — Gate3 studio/runtime helper certification) |
| Available for production policy use | No — catalogue not production-certified (and/or not subscribed) |

## Flags

- **FLAGS CORRECTED:** 0 (explicit non-goal)
- Inconsistent old UI labels explained by projecting capability semantics over unchanged catalogue flags

## Authority unchanged

GACAT IDs, Policy / W3–W6, Bureau parse/calc behaviour — not modified.
