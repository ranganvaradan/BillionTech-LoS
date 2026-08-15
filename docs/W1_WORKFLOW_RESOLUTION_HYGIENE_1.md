# W1 — Workflow Resolution Hygiene

**SOURCE START SHA:** `060191c05bd204f67b4e3436d48137652ccaa31a`  
**Flyway:** V134  
**Mode:** IMPLEMENT W1 ONLY

## Resolution paths (before → after)

| Path | Before | Classification | After |
|---|---|---|---|
| `loan_applications.workflow_id` | Optional bind | AUTHORITATIVE when set | AUTHORITATIVE + version/source/hash |
| `ActiveWorkflowConfigService.findActiveForApplication` | Prefer bind else highest active | FALLBACK / DUPLICATE | Delegates to `ApplicationWorkflowResolver` |
| `KycOrchestrationServiceImpl` | `workflowEngine.getActiveWorkflow(type,product,segment)` | **UNSAFE_INDEPENDENT_RESOLUTION** | `ApplicationWorkflowResolver.requireConfig` |
| `GstWorkflowRequirement` / `ItrWorkflowRequirement` | Same independent active lookup | **UNSAFE_INDEPENDENT_RESOLUTION** | Same resolver |
| `VkycWorkflowService` / Flow / Bureau / LMS / SLA / notifications | Via `ActiveWorkflowConfigService` | AUTHORITATIVE-ish | Via resolver (no silent substitute) |
| `WorkflowEngineServiceImpl.getActiveWorkflow` | Dimension head | LEGACY admin/catalog | Unchanged for admin API only |
| Product Config compose | Preview only | FALLBACK for initial options | Must not override persisted app WF |
| Customer Category | N/A | — | Untouched (W2) |

## Single resolver

`ApplicationWorkflowResolver` — resolve once, persist, reuse; fail closed on broken bind (`WORKFLOW_VERSION_NOT_FOUND`); no silent active fallback.

## Mutation safety (P1 residual)

In-place WorkflowConfig edits still allowed. W1 stores `workflow_content_hash` and sets `definitionMutatedSinceResolve` when detected. Full Workflow Version immutability governance deferred to P1.

## Explicit non-goals

No Category→Workflow bind, planner, parallel, live routing, Policy/Scorecard/KYC rule changes.
