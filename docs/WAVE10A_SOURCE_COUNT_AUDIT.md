# WAVE 10A — Data & Parameters Source Count Audit

## Observed (pre-fix) Equifax / Bureau Retail

| Display | Meaning before Wave 10A |
| --- | --- |
| `54 parameters available` | `parameterSupportCounts.parametersAvailable` = supportedRaw + supportedDerived + notApplicable from **legacy support status** (`DataParametersCapabilitySemantics.sourceFamilySummary`) — catalogue membership / support bucket, **not** CPES execution capability |
| `7 calculations not yet implemented` | Count of parameters with support status `CALCULATION_NOT_IMPLEMENTED` — catalogue derivationDefined && !implemented style, **not** canonical truth |
| `Directly provided (33/33)` | UI: `filterParams(sourceView.raw).length / sourceView.raw.length` — **filtered list length over list length** (pagination/filter completeness), not capability |
| `Calculated (28/28)` | Same list-cardinality pattern for `sourceView.derived` |
| `Application / manual (0/0)` | Same for `sourceView.manual` |

## Field → authority map

| Field | Backend authority | Catalogue membership | Semantic class | CPES capability | Value availability | Certification | Subscription | List cardinality |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `parametersAvailable` | legacy support buckets | yes | partial | **no** | no | no | no | no |
| `calculationNotImplemented` | legacy support | yes | partial | **no** | no | no | no | no |
| `supportedRaw` / `supportedDerived` | legacy support | yes | yes (approx) | **no** | no | no | no | no |
| `33/33` UI | React list lengths | n/a | n/a | **no** | no | no | no | **yes** |
| Wave 10A `canonicalCounts.*` | `CanonicalParameterTruthProjection` aggregate | no | yes | yes (ready/setup) | optional | yes (live approved) | unchanged org line | **no** |

## After Wave 10A (lender-facing)

Prefer honest wording from canonical aggregates, e.g.:

- Directly provided: N
- Calculated: N
- Manual: N
- Ready to test: N
- Setup required: N
- Live approved: N

Do **not** show `N/N` list completeness as capability.
Do **not** call catalogue presence “parameters available” without saying “in catalogue”.

Legacy support buckets remain under Advanced / Technical diagnostics only.
