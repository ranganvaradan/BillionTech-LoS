# W3 — Requirement Plan + Fulfilment / Readiness State Model

**Mode:** Domain / state infrastructure only  
**Flyway:** V136  
**Branch:** `reconcile/laptop-layout-2026-08`

## What shipped

- Per-application `RequirementPlan` + `RequirementItem` + state transition audit
- Independent states: customer fulfilment ≠ data readiness ≠ source acquisition
- Fulfilment modes: DIRECT_INPUT, DOCUMENT_UPLOAD, AUTOMATIC_SOURCE, DERIVATION, MANUAL_REVIEW
- Completeness evaluator (`COMPLETE_FOR_NEXT_STAGE` / `INCOMPLETE` / `BLOCKED` / `PROCESSING`)
- Admin/debug APIs under `/api/v1/internal/requirement-plans`
- `DataRequirementPlanner` interface + stub (full Policy inventory → plan is **W4**)

## Locked invariants

| Rule | |
|---|---|
| Document upload | fulfilment → `PROVIDED` immediately |
| Document PROVIDED | does **not** imply `READY_FOR_POLICY` |
| One document | may satisfy multiple canonical parameters |
| AUTOMATIC_SOURCE-only | no customer DIRECT_INPUT / CUSTOMER_FALLBACK |

## Explicitly not in W3

Category selection, live/shadow routing, Policy cutover, source execution, dynamic customer UI, parallel acquisition runner.

## Progressive next steps

- **W4:** Policy Parameter Inventory → `DataRequirementPlanner`
- **W5:** Dynamic customer requirement UI
