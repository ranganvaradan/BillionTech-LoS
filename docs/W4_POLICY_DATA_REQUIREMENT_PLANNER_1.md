# W4 — Policy → Data Requirement Planner

**Mode:** Planning only (no acquisition execution)  
**Flyway:** V136 (no new migration; plan metadata carries graph/semantic hashes)  
**Start SHA:** `f15911839be0327173ceb237a5e04f97f41ffe76`  
**Branch:** `reconcile/laptop-layout-2026-08`

## What shipped

- `PolicyDrivenDataRequirementPlanner` — DP-3 Policy Rule Graph inventory → `RequirementPlan`
- Exact GACAT IDs only; unresolved operands fail closed (`UNRESOLVED_CANONICAL_PARAMETER`)
- Fulfilment order: existing fact → derivation → automatic source → document → direct input → `NO_FULFILMENT_PATH`
- Document-first fallback + multi-parameter document grouping (`pendingDocumentGroup`)
- DIRECT_INPUT vs DOCUMENT_UPLOAD kept distinct (never collapsed to “manual”)
- Plan explainability on each item (`sourceHints.explanation`)
- Idempotent planning via `semanticHash`; controlled replan reconciles PROVIDED/readiness
- Admin: `POST /api/v1/internal/requirement-plans/plan-from-policy`  
  and `GET /{id}/planning-summary`

## Reuse (no parallel authorities)

| Authority | Use in W4 |
|---|---|
| W3 RequirementPlan domain | Persist plan/items/states |
| DP-3 `PolicyRuleGraphService.parameterInventory` | WHAT is required |
| DP-1 `GacatParameterReadinessProjection` | production readiness / source type |
| `WorkflowParameterProvidesCatalog` | proven steps/integrations |
| Existing CI facts | Prefer before any customer request |
| Workflow document requirements (hints/config) | HOW document fulfilment is planned |

## Explicitly not in W4

Source execution (Bureau/AA/GST/ITR/OCR), KYC execution, Policy/Scorecard run, CAM/KFS, Category selection, live routing, dynamic customer UI (W5), acquisition orchestration (W6).

## Progressive next

- **W5:** Dynamic customer requirements UI from planning summary  
- **W6:** Acquisition orchestration for AUTO_SOURCE / DERIVATION candidates  
