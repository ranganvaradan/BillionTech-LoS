# DATA-PARAMETERS-DP1-ENRICHMENT-1

## Purpose

Enrich the **existing** Administration → Data & Parameters capability so a lender/admin can see **operational readiness** and **source lineage** for each GACAT parameter.

This is **not** a new catalogue. GACAT (`CanonicalParameterRegistry` / `ci_gacat_*`) remains the sole canonical parameter authority.

DP-1 is **visibility + a single derived readiness projection** only. It does **not** change Policy activation, production flags, or provider bindings.

## Single readiness projection

**Class:** `com.los.core.service.readiness.GacatParameterReadinessProjection`

**Wired by:** `DataParametersAdminService.enrich` (overview browse, search, gaps, parameter detail)

### Authoritative inputs (reuse only)

| Input | Authority |
|---|---|
| Capability flags (`implemented`, `sourceAvailable`, `productionReady`, path, schema) | GACAT `CanonicalParameterDefinition.Capability` |
| `policyTestReady` / `runtimeReady` / Gate3 `executionState` | `ParameterExecutabilitySupport` |
| Workflow / integration provides | `WorkflowParameterProvidesCatalog` (+ new inverse `lookupForParameter`) |
| Calculator / binding | `existingImplementationBinding` |
| Provenance | calculation summary, primitives, Gate3 `provenanceModel` |
| Consumers (proven only) | GACAT `liveScorecardParameter` / `liveRuleParameter`, Gate3 runtime aliases, workflow lookup |

### Factual dimensions (exposed separately; not independently editable)

- `implemented`
- `sourceAvailable`
- `policyTestReady`
- `runtimeReady`
- `productionReady` (catalogue certification fact; MANUAL uses Gate3 MANUAL_AUTHORISED)
- `workflowAvailable`
- `providerBound` (explicit `provider_code` — currently unused / always false)
- `mappingAvailable`
- `calculatorAvailable`
- `provenanceAvailable`

### Derived overall readiness

| State | Meaning |
|---|---|
| `PRODUCTION_READY` | Catalogue production certification + implemented (or MANUAL authorised) |
| `RUNTIME_READY_NONPROD` | Runtime path without production certification |
| `POLICY_TEST_ONLY` | Policy Test / studio path only |
| `CATALOGUE_ONLY` | Catalogue presence without executable path |
| `READINESS_UNKNOWN` | Contradictory or insufficient facts — **never silently promoted** |

Honesty rules:

- Catalogue presence ≠ readiness
- `sourceAvailable` ≠ runtime ready
- `policyTestReady` ≠ `productionReady`
- Calculator existence ≠ production certification
- Empty `provider_code` ≠ “unintegrated” for APPLICATION / WORKFLOW / MANUAL / INTERNAL

## Source types (not readiness)

`PROVIDER` · `APPLICATION_INPUT` · `WORKFLOW` · `INTERNAL_SYSTEM` · `MANUAL` · `DERIVED` · `UNKNOWN`

Provider display statuses:

- `REGISTERED` — explicit provider_code (not mass-populated in DP-1)
- `INFERRED` — proven from structured binding/integration/schema/path metadata
- `UNKNOWN` — provider identity not proven
- `NOT_APPLICABLE` — source type does not require an external provider

## UI fields

### List (badges)

Canonical ID · Name · Source Family · Source Type · Overall Readiness · Production Ready

### Filters

Source Family · Source Type · Overall Readiness · Production Ready (+ text filter)

### Detail sections (existing detail API)

A Definition · B Source · C Mapping/Calculation · D Readiness facts · E Missing Data · F Consumers · G Provenance

**Advanced / Technical Details** — expandable raw JSON (preserved).

## APIs

| Endpoint | DP-1 change |
|---|---|
| `GET .../data-parameters` | Adds `dp1`, source type / readiness enums, `knownCatalogueDrift` |
| `GET .../data-parameters/by-source` | Enriched rows include readiness projection |
| `GET .../data-parameters/search` | Results enriched (same projection) |
| `GET .../data-parameters/{id}` | Structured `sections` + `readiness` |

## Known limitations

- Policy document consumer graph is **not** indexed in DP-1 (`policyStudio` consumers empty with note).
- `provider_code` remains unset on bindings (audit: 97 paths / 0 codes) — **not** mass-populated.
- `providerBound` factual dim is therefore false until an explicit registration model exists.
- No enforcement on Policy activation (see DP-4).

## Known catalogue drift (visibility only — no GACAT mutation)

| Code | Example |
|---|---|
| `SEED_NOT_IN_DB` | `bureau.inquiries.last_3m` in Java seed, absent from DB |
| `DB_NOT_IN_SEED` | `collateral.ltv` in DB (V123), absent from seed |
| `META_SEED_COUNT_STALE` | Internal meta `seed_count` may lag row count |

Exposed on overview as `knownCatalogueDrift` and in the UI as an expandable notice.

## DP-4 follow-up (pending)

**DP-4:** Policy activation / publish must **fail closed** when a required decision parameter is not **Production Ready**.

Today Policy activation can accept Policy-Test-ready parameters. DP-1 intentionally does **not** change that. Implement DP-4 only after DP-1 UAT validates the readiness projection.

## Non-goals (unchanged)

- Policy activation semantics
- Policy Studio authority
- Customer Category / Category→Policy / Policy Scope
- Scorecard runtime / Live UW / Workflow-KYC execution
- Provider integrations / Bureau calculators
- Canonical IDs / `productionReady` flags / source mappings
- Flyway / GACAT data mutations
