# W6 — Acquisition Orchestration + Data Completeness Gate

**Mode:** IMPLEMENT W6 ONLY  
**Flyway:** V137 (`requirement_acquisition_attempt`)  
**Start SHA:** `7b51cab121639e4e3bb7a27612a5e9384186ac9b`  
**Branch:** `reconcile/laptop-layout-2026-08`

## What shipped

- **Single coordinator:** `WorkflowAcquisitionCoordinator` — RequirementPlan is WHAT; W6 is WHEN/HOW
- **Reuse adapters** (no parallel engines): Bureau, AA, KYC, GST, ITR, OCR/document extraction, derivation
- **Preferred source only** — alternatives never run simultaneously
- **Idempotent attempts** via `requirement_acquisition_attempt.execution_key`
- **Dependency-aware waves** (`AcquisitionDependencyGraph`) — parallel capable, fail-closed on cycles
- **Customer wait does not block** unrelated automatic acquisitions
- **Source success ≠ data ready** — `CanonicalFactReadinessReconciler`
- **Explicit fallback** (e.g. AA → BANK_STATEMENT_UPLOAD) with persisted previous/fallback/reason
- **EXTRACTION_FAILED ≠ REUPLOAD_REQUIRED**
- **DataCompletenessGate:** READY_FOR_POLICY / WAITING_FOR_CUSTOMER / ACQUISITION_IN_PROGRESS / MANUAL_REVIEW_REQUIRED / BLOCKED
- **No Policy / Scorecard / Category / live routing / DP-4 enforcement**

## Admin APIs

- `POST /api/v1/internal/requirement-plans/{id}/acquire`
- `GET  /api/v1/internal/requirement-plans/{id}/completeness-gate`
- `GET  /api/v1/internal/requirement-plans/{id}/acquisition-attempts`

## Explicitly not in W6

Policy auto-execution, Scorecard, Category selection, live routing, DP-4 global certification, BPMN.
