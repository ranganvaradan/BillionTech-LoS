# LOS Stabilization — Phase 1 Analysis Note

## Scope
This note documents current frontend/backed wiring for the core LOS workflow screens and identifies mock/demo behavior, endpoint mismatches, and lifecycle transition mechanisms.

## Frontend screens → data sources (current)

### Dashboard (`/dashboard`)
- **File**: `frontend/src/app/dashboard/page.tsx`
- **API**:
  - `GET /api/v1/applications/dashboard/summary`
  - `GET /api/v1/applications?page=0&size=5`
- **Issue**:
  - Frontend expected a different payload shape than backend returns (contract mismatch).
  - Previously had silent mock fallback masking real failures.

### Applications list (`/applications`)
- **File**: `frontend/src/app/applications/ApplicationsContent.tsx`
- **API**:
  - `GET /api/v1/applications` (uses `Page.content`)
- **Issue**:
  - Previously had silent mock fallback masking real failures.

### Application detail (`/applications/[id]`)
- **File**: `frontend/src/app/applications/[id]/page.tsx`
- **API**:
  - `GET /api/v1/applications/{id}`
  - `POST /api/v1/applications/{id}/transition?newStatus=...&remarks=...`
  - Orchestration:
    - `POST /api/v1/flow/{id}/submit`
    - `POST /api/v1/flow/{id}/kyc`
    - `POST /api/v1/flow/{id}/bureau`
    - `POST /api/v1/flow/{id}/underwrite`
  - Tabs:
    - `GET /api/v1/audit/{applicationId}`
    - `GET /api/v1/kyc/{applicationId}/results`
- **Issues**:
  - Previously had silent mock application fallback and UI-driven status transitions.
  - Now: no mock fallback; explicit error state; core lifecycle stage changes are only exposed via orchestration actions.

### Pipeline Board (`/kanban`)
- **Nav**: Sidebar label **Pipeline** points to `/kanban`.
- **File**: `frontend/src/app/kanban/page.tsx`
- **Current state (confirmed)**:
  - Derived pipeline board from live applications list; no mock cards.

### KYC Management (`/kyc`)
- **File**: `frontend/src/app/kyc/page.tsx`
- **Current state**:
  - Demo/legacy screen that used only mock rows.

### Workflows (`/workflows`)
- **File**: `frontend/src/app/workflows/page.tsx`
- **Backend APIs exist**:
  - `GET /api/v1/workflows`, `GET /api/v1/workflows/active`, etc.
- **Current state**:
  - Frontend page is mock-only and not wired.

## Backend lifecycle transition mechanisms (current)

### Generic status transition endpoint
- **Controller**: `services/los-core-service/.../LoanApplicationController.java`
- **Endpoint**: `POST /api/v1/applications/{id}/transition`
- **Validation**:
  - Uses `ApplicationStateMachine.isValidTransition(old, new)`.
  - Now additionally blocks direct transitions into core lifecycle statuses; flow/orchestration endpoints must be used.
- **Audit**:
  - Logs event type `STATUS_CHANGE` with `old -> new`.

### Orchestration endpoints
- **Controller**: `LoanApplicationFlowController`
- **Endpoints**: `/api/v1/flow/{id}/submit|kyc|bureau|underwrite|sanction|esign|disburse|full`
- **Note**:
  - Orchestration exists and should be the canonical business flow.
  - Prerequisites enforced for Bureau/Underwriting:
    - Bureau requires KYC pass (no KYC failures)
    - Underwriting requires KYC pass and bureau score present
    - Blocked attempts log audit events.

## Live vs mock/demo summary
- **Live (intended production)**:
  - `/applications`
  - `/applications/[id]` (but previously had mock fallback)
  - `/dashboard` (but had contract mismatch and mock fallback)
- **Mock/demo (must not be in production nav as-is)**:
  - `/kyc` (KYC Management)
  - `/workflows`

## Nav items pointing to mock/demo screens
- Sidebar `Pipeline` → `/kanban`
- Sidebar `KYC Management` → `/kyc` (removed from nav)
- Sidebar `Workflows` → `/workflows` (removed from nav)

## Immediate remediation direction
- If no backend pipeline/queue endpoints exist, derive Pipeline and KYC queues from `GET /api/v1/applications` grouped by canonical `ApplicationStatus`.
- Remove all silent mock/demo fallbacks on live workflow screens; show explicit error states instead.
- Ensure post-login landing goes to a stable live page (Applications) until Dashboard is fully trustworthy.
