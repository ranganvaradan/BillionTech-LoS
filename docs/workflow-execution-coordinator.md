# Workflow execution — design note (LOS_Design_v2 backend alignment)

## What is config-only today

- `workflow_configs` table stores **per borrower/loan product** KYC (and some seeds include **BUREAU_PULL**) as `steps[]` with `step`, `order`, `mandatory`, optional `provider`.
- **Provider hints and KYC step order** are consumed by **KYC orchestration** and **Integration Router**; **SLA / escalation / parallel groups** in the same row are for operations/tat, not the loan HTTP flow in code.
- `IWorkflowEngineService` / `WorkflowEngineServiceImpl` are **CRUD + activation** for `WorkflowConfig` (no execution loop).

## What is still hardcoded in `LoanApplicationFlowService`

- **Lifecycle ordering** of major phases: submit → (KYC, bureau) → underwrite → sanction → (eSign, completeEsign) → disburse.
- **Status rules and audit** for submit, underwrite, sanction, completeEsign, and the credit/KFS work between phases.
- **eSign and disburse** are not modeled as rows in `workflow_configs.steps` (those rows are KYC/bureau-scoped in seeds today).

## Which `IStepExecutor` / `FlowStepType` values map from workflow JSON

- **`KYC_WORKFLOW`**: the JSON lists **KYC sub-steps** (e.g. `PAN_VERIFY`); a single `KycWorkflowStepExecutor` run already delegates to the orchestration engine, which uses that config. The coordinator **does not** run one executor per KYC sub-step.
- **`BUREAU_PULL`**: can appear in `steps[]` (e.g. V2 TERM). Post-KYC pull is still a single `BUREAU_PULL` executor.
- **`ESIGN` / `DISBURSE`**: not present in current seeds as flow-level step names; they remain **driven by application status and flow methods**, not by `steps` JSON, until a future `flowLevelSteps` (or similar) is added.

## What should not change yet

- No **workflow instance** storage, no branch/join/scheduler, no **async** engine.
- No replacement of the **KYC engine**; no per-sub-step `IStepExecutor` per JSON row.
- **Public HTTP contracts** and existing **status transitions** for each phase.

## `WorkflowExecutionCoordinator` role (incremental)

- **Reads** the active `WorkflowConfig` for the application.
- **Derives a high-level ordered list** of `FlowStepType` for **KYC and bureau** (with defaults when bureau is missing from config but the product still expects a pull — backward compatible).
- **Validates** optional strict mode: reject a `FlowStepType` that is not allowed by that resolved list (see `los.workflow.step-validation-strict`); `ESIGN` and `DISBURSE` are always allowed in strict mode (they are not in KYC `steps` JSON).
- **Executes** through **`StepExecutionRecordingService`** (same recording as before).

`executeFullFlow` keeps the same **sequence, keys, and branching**; only the **four** public methods that already used recording now go through the coordinator for validation and workflow resolution.

**Beans:** `WorkflowResolutionOptions` is a Spring `@Bean` (defaults) from `com.los.core.config.WorkflowExecutionConfig`. Property `los.workflow.step-validation-strict` (default `false`) controls optional guardrails; when `true` and the resolved list omits a pre-sanction step (e.g. bureau) because `addBureauWhenMissingFromConfig` is `false` in a custom `WorkflowResolutionOptions` bean, `BUREAU_PULL` is rejected. Production defaults keep backward-compatible behavior.
