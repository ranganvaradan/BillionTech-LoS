# W2 — Category → Workflow Version Bind

**Mode:** configuration / governance only  
**Flyway:** V134 (no new migration in this note)  
**Non-goals:** Category runtime selection, progressive disambiguation UI, live Workflow routing, Requirement Planner, Policy/Scorecard/KYC rule changes

## What shipped

- Customer Category Version binds an **exact Workflow Version** (`workflow_configs.id` + version + content hash), independently of Policy Version.
- At most **one** Workflow Version per Category Version; many Categories may share one Workflow.
- Activation readiness includes `WORKFLOW_SELECTED` / identity / compatibility checks (`WORKFLOW_LINKAGE_REQUIRED`, `WORKFLOW_VERSION_MUTATED`, etc.).
- Admin UI: separate Policy Version and Workflow Version pickers; composition = Category → Policy + Workflow.
- Unit coverage: `CategoryWorkflowVersionBindTest` (W2 mandatory cases). Existing Category service tests pass `CategoryWorkflowBindService` into the constructor.

## Guardrail

W2 does **not** write `loan_applications.workflow_id`. Runtime path remains: Category selected later → Category’s Workflow → `ApplicationWorkflowResolver` (future).

## 9. Future progressive disambiguation contract (design only — not implemented)

Future sequence (comment / design lock only):

1. Application basic facts → find eligible Customer Categories.
2. If exactly one: may auto-select (product-configurable).
3. If multiple: identify distinguishing facts/questions (e.g. customer intent, programme/channel, bank-data preference, financial statements availability, invoice-finance route, anchor programme).
4. Ask the minimum additional questions; persist answers; recompute eligible Categories.
5. Repeat until one Category is selected, user/RM selects explicitly, or none remain eligible.
6. Once selected: **Category supplies the exact Workflow Version** — the question resolver must **not** select Workflow directly.

This contract is documented here only; W2 does not implement disambiguation questions or Category runtime routing.
