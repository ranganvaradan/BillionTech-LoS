# W5 — Dynamic Customer Requirements UI

**Mode:** Customer/RM UI from W4 RequirementPlan only  
**Flyway:** V136 (no new migration; `REUPLOAD_REQUIRED` fits existing VARCHAR)  
**Start SHA:** `17dc256a847b5a8aaf6a404ac12a4e3ec934cfb2`  
**Branch:** `reconcile/laptop-layout-2026-08`

## What shipped

- `CustomerRequirementsViewService` — projects Plan → customer actions (DIRECT_INPUT / DOCUMENT_UPLOAD only)
- Document grouping: one Financial Statements card for many parameters
- Dual-mode choice: Enter details vs Upload document
- PROVIDED ≠ READY: customer sees ✓ Uploaded + Processing…
- `DOCUMENT_REJECTED` → `REUPLOAD_REQUIRED`; `EXTRACTION_FAILED` keeps PROVIDED (no auto re-ask)
- APIs:
  - `GET/POST /api/v1/applications/{id}/customer-requirements/**` (staff/RM)
  - `GET/POST /api/v1/borrower/applications/{id}/customer-requirements/**`
  - Admin: `GET /api/v1/internal/requirement-plans/{id}/customer-view`
- UI: `CustomerRequirementsPanel` on RM Overview + Borrower Overview
- Save draft / resume via `draftValue` on items; actor role CUSTOMER|RM audited

## Explicitly not in W5

Source acquisition (W6), Category selection, live routing, Policy/Scorecard/KYC execution.
