# LOS Workflow Authority / Requirement Resolution Audit 1

**Document:** `LOS-WORKFLOW-AUTHORITY-REQUIREMENT-RESOLUTION-AUDIT-1`  
**Mode:** AUDIT / ARCHITECTURE REVIEW ONLY — **no implementation**  
**SOURCE SHA:** `060191c05bd204f67b4e3436d48137652ccaa31a`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway at audit:** V133  
**Date:** 2026-08-15  

**Upstream locks / audits consulted:**

- `docs/LOS_CREDIT_ARCHITECTURE_DESIGN_LOCK_1.md`
- `docs/LOS_EXISTING_CAPABILITY_REUSE_AUDIT_1.md`
- `docs/DATA_PARAMETERS_DP1_ENRICHMENT.md`
- `docs/DATA_PARAMETERS_DP2_READINESS_INVENTORY_1.md`
- `docs/DATA_PARAMETERS_DP2B_CONVERGENCE_1.md`
- `docs/LOS_POLICY_SCORECARD_WEIGHT_CONTRACT_1.md`
- `docs/DATA_PARAMETERS_DP3_CONVERGENCE_1.md`

**Non-goals (honoured):** no code/DB/migration/Workflow/Product Config/KYC/Policy/GACAT/Scorecard/Category changes; no `selectedCustomerCategoryId`; no routing/shadow routing; no deploy; no push.

---

## 1. Executive summary

Workflow today is **not** a durable instance engine and **not** yet the umbrella orchestration authority envisioned for Category → Policy → journey. It is a **mixture (E)**:

| Slice | Reality |
|---|---|
| Config | `workflow_configs` JSON (steps, intake, docs, parallel_groups advisory) |
| Execution bridge | `WorkflowExecutionCoordinator` + narrow `FlowStepType` executors |
| KYC/provider loop | `KycOrchestrationServiceImpl` (sequential) |
| End-to-end lifecycle | **`LoanApplicationFlowService`** (imperative status machine) |
| Product Config | Compose/readiness **preview** over existing tables — **not** routing authority |
| Policy / Scorecard / GACAT | Separate authorities; live UW still legacy rule-set + scorecard path |
| Customer Category | Config bind to Policy Version; **no persisted workflow_id** on Category |

**Strategic fit to target:** The preferred direction — **shared base Workflow that dynamically inserts data-acquisition from Policy parameter inventory** — is **architecturally coherent** and **partially supported by reuse** (intakeConfig, document slots, KYC steps, DP-3 inventory, banking precedence), but **missing** a Data Requirement Planner, customer-provided requirement model (fulfilment ≠ readiness), parallel acquisition runtime, and Policy-as-published runtime invocation from Workflow.

**POLICY SHOULD OWN WORKFLOW:** **NO** (confirmed by design lock and code separation).

---

## 2. Existing Workflow architecture

### WORKFLOW_PRIMARY_SERVICE

Composite:

1. **`WorkflowEngineServiceImpl`** — CRUD / activate / parallel-plan advisory for `workflow_configs`
2. **`ActiveWorkflowConfigService`** — resolve config for an application (`app.workflowId` preferred)
3. **`WorkflowExecutionCoordinator`** — bridge config → `IStepExecutor` (explicitly *not* an instance engine)
4. **`KycOrchestrationServiceImpl`** — sequential KYC step loop + provider routing
5. **`LoanApplicationFlowService`** — **actual loan lifecycle owner** (submit → KYC → bureau → underwrite → CAM → sanction → KFS → eSign → disburse)

REST: `WorkflowController` → `/api/v1/workflows`  
UI: `ui-service` `/workflows` (`WorkflowsPage`, `WorkflowStepEditorPanel`)

### WORKFLOW_CONFIG_TABLES

| Table / column | Role |
|---|---|
| `workflow_configs` | Definition: `steps` JSONB, `intake_config`, `parallel_groups`, `intake_segment`, LMS/bureau flags, VKYC conditions |
| `workflow_lms_product_mapping` | Segment → LMS product |
| `program_masters.workflow_config_id` | PLP program → workflow FK |
| Notification `workflow_*` mappings | Event templates (notification-service) |

**No** `workflow_definition` graph table beyond JSON.

### WORKFLOW_INSTANCE_TABLES

**None.** Surrogates:

| Surrogate | Role |
|---|---|
| `loan_applications.workflow_id` | Optional binding (V79/V84) |
| `kyc_step_results` | Per-step KYC outcomes |
| `step_execution_record` | Executor audit (V13: “not a workflow engine”) |
| `application_status_history` | Status transitions |

### WORKFLOW_STEP_MODEL

1. **Config steps** — `workflow_configs.steps[]`: `{step, order, mandatory, provider, …}`
2. **`KycStepType`** — identity/advanced KYC identities (PAN, Aadhaar, CIN_MCA21, GST_ANALYSIS, ITR, …)
3. **`FlowStepType`** (executor registry) — narrow spine: `KYC_WORKFLOW`, `VKYC`, `BUREAU_PULL`, `ESIGN`, `DISBURSE`

Many KYC JSON rows collapse to one `KYC_WORKFLOW` executor via `WorkflowFlowStepOrderResolver`.

### WORKFLOW_STATE_MODEL

| Layer | Model |
|---|---|
| Application | `ApplicationStatus` (DRAFT → KYC_IN_PROGRESS → UNDERWRITING → CAM_* → SANCTION_* → ESIGN → READY_FOR_DISBURSEMENT → DISBURSED, …) |
| KYC step | `StepOutcome` (PENDING/SUCCESS/FAILURE/MANUAL_REVIEW/SKIPPED/ERROR) |
| Executor audit | `StepExecutionStatus` (STARTED/SUCCESS/FAILED) |

Transitions are **imperative** in `LoanApplicationFlowService` / KYC — not a declarative WF state machine.

### WORKFLOW_EXECUTOR_MODEL

```
LoanApplicationFlowService / caller
  → WorkflowExecutionCoordinator.executeFlowStepForApplication
    → StepExecutionRecordingService
      → StepExecutorRegistry
        → KycWorkflowStepExecutor | BureauPullStepExecutor
          | EsignInitiateStepExecutor | DisburseLmsStepExecutor
            → IntegrationRouter / LmsService / providers
```

### Classification: **E — mixture**

| Evidence | Class |
|---|---|
| Workflows admin UI + step editor | A UI/journey sequencing |
| `intakeConfig` + `WorkflowIntakeValidator` | B partial business orchestration |
| KYC sequential provider loop | C provider/data acquisition |
| `LoanApplicationFlowService` status path through disbursement | D end-to-end lifecycle (outside instance engine) |

---

## 3. Product Config relationship

**Code truth:** `ProductConfigurationComposeService` is a **thin compose over existing tables** — readiness/preview for Product Config UI (`/product-configuration`). It may **display** a selected `workflowId` nest; it does **not** persist workflow definitions or execute them.

| Question | Answer |
|---|---|
| Who chooses `workflowId`? | Client on create/update (`CreateApplicationRequest.workflowId`) or fallback to highest active by borrowerType×product×intakeSegment |
| Product Config stores workflowId? | Compose input only — not authoritative app routing store |
| Controls step availability? | No — `workflow_configs.steps` does |
| Chosen by borrower/entity/product? | Yes for **resolution key** of workflow_configs |
| Differs by intake/customer role? | Yes (`intakeSegment`) |
| Programme/subProgram? | PLP `program_masters.workflow_config_id`; subProgram does not own workflow |
| Loan amount? | Not primary workflow selector; VKYC amount conditions exist |
| Provider readiness? | Product Config readiness surface; not runtime router |
| Routing authority? | **No** — Product Config is config/readiness compose |

### Authority boundary (today)

**PRODUCT CONFIG OWNS:**

- Compose/preview of existing capabilities (workflow coverage, LMS mapping, rule-set/scorecard readiness signals)
- Admin UX for “is this product package coherent?”

**WORKFLOW OWNS:**

- `workflow_configs` definition (steps, intake fields/docs, providers, VKYC triggers)
- KYC step projection + provider preference on steps
- Binding key dimensions (borrowerType × loanProduct × intakeSegment)
- (Partial) LMS product code / bureau flags on config

**LoanApplicationFlowService OWNS:** lifecycle status progression and underwrite/CAM/sanction/KFS/eSign/disburse orchestration calls.

---

## 4. Application entry

| Concern | Today |
|---|---|
| Registration | `DemoAuthService` / auth → `los_users` |
| Customer | Soft resolve (`ApplicationCustomerIdResolver`) — not full CRM create |
| Application create | `LoanApplicationServiceImpl.createApplication` |
| Product / entity / amount / tenure | On `CreateApplicationRequest` |
| Customer Role | `IntakeSegment` (BORROWER/ANCHOR) + intake modes in UI |
| Workflow assignment | Optional `workflowId` at create; validated by dimensions; may be null |
| Change later? | **Yes** via `updateApplication` while non-terminal |
| Explicit vs derived | Explicit if provided; else **derived** at resolve time (latest active) |
| `subProgramId` | Not on create; PLP link path for invoice-discounting |
| Channel | Not first-class create field |

**Gap vs target Loan Intent → Eligible Categories → Selected Category → Policy → Workflow:** Category selection and Policy Version binding are **not** on the live create path yet (`selectedCustomerCategoryId` forbidden/not present). Application already carries Role/Entity/Product/Amount — enough to **eligibility-match** Categories later, and enough to bind a workflow by dimensions today.

**Known inconsistency:** `KycOrchestrationServiceImpl.executeWorkflow` still resolves via `getActiveWorkflow(type, product, segment)` and may **ignore** `app.workflowId`, while `ActiveWorkflowConfigService` prefers binding. P0 hygiene for any future Category→Workflow projection.

---

## 5. Manual form requirements

| Capability | Verdict | Evidence |
|---|---|---|
| Dynamic form engine from parameter list | **NO** | No `DynamicForm*` |
| Config-driven intake fields | **PARTIAL** | `intakeConfig.personalFields`, `customFields`, `mandatoryFieldGroups`, age/tenure rules |
| Validation | **PARTIAL** | `WorkflowIntakeValidator` when `policy=WORKFLOW_DRIVEN` |
| Customer vs RM | **PARTIAL** | Separate wizards / co-applicant flags |
| Save/resume | **YES** | DRAFT + repeated updates |

**Target:** Policy requires GACAT X → if no auto source and direct input permitted → customer-provided requirement.  
**Missing:** planner + requirement object + form renderer keyed by GACAT IDs (Product readiness extracts `requiredParameterIds` but does **not** generate intake UI).

---

## 6. Document requirements

| Capability | Verdict | Evidence |
|---|---|---|
| Config document lists | **PARTIAL** | `standaloneDocuments`, step-linked docs in intake validation |
| Hard-coded checklist | **YES** | `DocumentServiceImpl` (PAN/AADHAAR/BANK_STATEMENT/PHOTOGRAPH class) |
| Upload / borrower visibility | **YES/PARTIAL** | Document APIs + visibility helpers |
| OCR | **PARTIAL** | Simulated / limited (`OcrExtractionService`) |
| Rich document status enum | **Misaligned** | Rich `DocumentStatus` used heavily in **Policy Studio** docs, not loan fulfilment |

### Two-state distinction (target)

| Dimension | Target | Today |
|---|---|---|
| CUSTOMER FULFILMENT | NOT_PROVIDED / PROVIDED | Upload existence / checklist — **not** first-class |
| DATA READINESS | NOT_READY → READY_FOR_POLICY | Exists in **Policy Studio / GACAT / CI readiness**, **not** paired to customer fulfilment on loan docs |

**Business rule audit:** Uploading a document does **not** automatically mark a customer obligation PROVIDED while extraction continues — because that dual model does not exist yet. Risk of re-asking customer is **config/checklist driven**, not planner-driven.

---

## 7. Customer-provided requirement concept

**Target:** `CUSTOMER_PROVIDED_REQUIREMENT` with fulfilment modes `DIRECT_INPUT` | `DOCUMENT_UPLOAD` (or both), independent of extraction readiness.

**Today:** **NO** such domain type/enums.

**Reuse candidates:**

- Direct: `intakeConfig` fields + personalInfo JSON
- Document: document upload + workflow document slots
- KYC taxonomy (`KycRequirementType`) is **different** (verification/info) — do not overload as GACAT fulfilment

**Feasibility:** EXTEND intake + documents behind a thin NEW_SMALL_COMPONENT planner/requirement store — do not invent a second KYC engine.

---

## 8. Source-resolution hierarchy

**Target order:** known canonical → derive → automatic source → document+extract → manual extract → direct input (if allowed) → unavailable/block.

| Existing piece | Scope |
|---|---|
| `BankSourcePrecedence` | Banking only: AA_STRUCTURED > BANK_API > DIGITAL_STATEMENT > OCR > MANUAL |
| `CreditControlService.resolveEffective` | UW effective sources: PROVIDER \| MANUAL (+ demo fallback) for bureau/income/KYC |
| Step `provider` preference | KYC router preferred provider then aggregator |
| CI metric fallbacks | Legacy underwriting adapters |
| General preferred→alternate→document→unavailable matrix | **NO** |

**Verdict:** SOURCE RESOLUTION/FALLBACK = **PARTIAL** (domain-specific, not GACAT-general).

---

## 9. Policy → data requirement planning

DP-3 delivers:

```
Policy Version → persisted rule graph → parameter inventory (+ readiness projection)
```

APIs: `.../dp3/policies/{id}/parameter-inventory`, factor picker, Policy Test on graph.

**Workflow cannot consume this list today** for journey planning.

| REUSABLE | REQUIRED NEW |
|---|---|
| DP-3 inventory + GACAT readiness | **Data Requirement Planner** (NEW_SMALL → grows) |
| `intakeConfig` / document slots | Requirement instance store on application |
| KYC/Bureau/GST/ITR executors | Source resolver (generalize banking precedence) |
| Product readiness `requiredParameterIds` signal | UI dynamic requirement renderer |
| Policy→legacy compile plan (design-only) | Published Policy runtime package invocation (later DP / cutover) |

**POLICY PARAMETER INVENTORY CAN DRIVE WORKFLOW:** **PARTIAL** (data exists; consumer does not).

---

## 10. Source orchestration (who triggers)

| Source | Workflow role | Primary services | Sync/async | GACAT/canonical |
|---|---|---|---|---|
| KYC identity | **Yes** (`KYC_WORKFLOW`) | `KycOrchestrationServiceImpl`, providers | Sync loop; some provider async | `kyc.*` via CI/GACAT bridges (partial) |
| VKYC | Adjacent (`VkycWorkflowService`) | HyperVerge | Async webhook | Gate, not UW param |
| Bureau | **Yes** (`BUREAU_PULL` + auto after KYC) | `BureauPullStepExecutor`, Equifax path | Mostly sync pull | `bureau.*` |
| Bank / AA | **Mostly outside** FlowStepType | `AccountAggregatorService`, banking CI | Mixed | Banking GACAT / normalization |
| GST | **Hybrid** (step + admin report) | `GstAnalysisService` | Async report | Partial |
| ITR | **Hybrid** | ITR workflow helpers | Mixed | Partial |
| Financial statements | Manual / OCR / extract merge | Intake + CreditControl | Manual-heavy | Partial |
| MCA | As KYC `CIN_MCA21` | Karza/Authbridge | Sync step | Partial |
| Internal LOS | Always | Application JSON | — | Application fields |

Failure semantics today: mandatory KYC fail → `KYC_FAILED` / underwrite block; GST incomplete → underwrite block if on workflow; bureau score ≤0 → underwrite block.

---

## 11. Parallel execution

| Feature | Status |
|---|---|
| `parallel_groups` JSONB | Stored |
| `getParallelExecutionPlan` | **Advisory only** (BR-6.2) |
| Runtime fork/join | **Not implemented** — KYC `for` loop sequential |
| Wait/join nodes | Not in `FlowStepType` |
| De facto async | VKYC/eSign webhooks + status waits |

**Minimal extension for target (KYC → parallel Bureau/Bank/GST/MCA → completeness gate → Policy):**

1. Honour `parallel_groups` (or dependency DAG) in a **flow runner** (extend coordinator — not Category-owned).
2. Async completion events → join wait state.
3. Data Completeness Gate service consuming requirement plan statuses.
4. Keep Policy/Scorecard engines as **callees**, not step owners of business rules.

**PARALLEL DATA ACQUISITION SUPPORTED:** **PARTIAL** (config shape only).

---

## 12. KYC

Reuse prior audit (`LOS_EXISTING_CAPABILITY_REUSE_AUDIT_1`):

- Step list from `workflow_configs.steps`
- Influenced by Entity×Product×Role workflow key; VKYC has richer conditions
- `conditionalRules` stored but **not** used for identity skip
- Customer Category **does not** select KYC today
- Prefer light **KYC Requirement Set → existing steps** — **not** a new KYC execution engine

**KYC ORCHESTRATION REUSABLE:** **YES**

---

## 13. Policy execution (current call chain)

**Live path (not Policy Studio):**

```
LoanApplicationFlowService.underwriteApplication
  → KycOrchestrationService.computeKycOutcome
  → CreditControlService.resolveEffective
  → gates (KYC pass, GST if configured, bureau score > 0)
  → CreditIntelligenceFoundationService.prepare (best-effort)
  → UnderwritingRuleEngine.evaluateAll          ← legacy underwriting_rule_sets
  → ScorecardPolicyEngine.evaluate              ← legacy scorecards / LEGACY_POINTS_V1
  → persist decision + assignment rules
  → CreditAppraisalService.ensureCamForApplication
```

**Policy Test / DP-3 graph:** Studio/admin only (`PolicyGraphPolicyTestService`) — **not** on loan path.  
**Policy production authority:** unchanged (still blocked per DP-3/DP-4).

---

## 14. Scorecard

| Question | Today |
|---|---|
| When | Inside `underwriteApplication` after rule engine |
| Always? | Invoked on that path (anchor flows excluded) |
| Hard-rule bypass | Hard rules can drive reject/refer semantics before/around scorecard aggregation in rule multi-result |
| Influences decision | Yes — combined with rule outcomes into credit decision |
| Manual review | Scorecard / rule outcomes can land MANUAL_REVIEW-class paths; CAM review separate |
| Aggregator | `LoanApplicationFlowService` + CreditControl context |

Target Policy→Scorecard subordination exists in DP-3 model; **live** still picks scorecard by borrower/product/amount/geo via `ScorecardPolicyEngine`, not Policy Version link.

---

## 15. Credit / manual review

| Mechanism | Evidence |
|---|---|
| KYC manual review | `KycManualReviewServiceImpl` |
| Scorecard / rule MANUAL_REVIEW | UW decision fields |
| CAM send-back | `CAM_SENT_BACK` |
| Reject at CAM/sanction | Flow reject APIs |
| Maker/checker | Governance patterns on CAM/config; not full separate credit authority engine |
| Anchor | Separate due-diligence / rating path |

Closest existing decision vocabulary: APPROVED / REJECTED / MANUAL_REVIEW / CAM_* / SANCTION_* — map target AUTO_REJECT / REFER_TO_CREDIT / CONDITIONAL_APPROVAL / AUTO_APPROVE onto these rather than inventing duplicates.

---

## 16. CAM

| Aspect | Today |
|---|---|
| Service | `CreditAppraisalService` |
| When | Ensured after underwriting decision path |
| Auto-generated | Yes (`ensureCamForApplication`) with editable fields |
| Inputs | Application, UW/scorecard context, conditions precedent JSON |
| Prerequisite to sanction | Flow requires CAM reviewed (or legacy APPROVED) before sanction |
| Authority | **Explainable artefact** — not independent decision engine (aligned with target principle) |

**CAM INTEGRATED:** **YES**

---

## 17. Sanction / offer / KFS

| Piece | Integration |
|---|---|
| Sanction | `LoanApplicationFlowService.sanctionApplication` + `SanctionRecord` |
| Pricing/tenure/amount | Sanction params / app fields |
| KFS | `KfsService` / PDF generation post-sanction |
| PLP gates | Program approval checks for anchor |
| Customer acceptance | Downstream eSign |

**KFS INTEGRATED:** **YES**

---

## 18. Agreements / eSign

| Piece | Integration |
|---|---|
| eSign | `EsignInitiateStepExecutor` + flow status `ESIGN_PENDING` |
| Templates / stamp / guarantees | Present in varying depth; not fully generalized template engine for all security docs |
| VKYC gate | Blocks CAM/sanction/eSign/disburse when required |

**AGREEMENT/ESIGN INTEGRATED:** **PARTIAL** (eSign strong; full agreement suite uneven)

---

## 19. Pre-disbursement conditions

| Piece | Today |
|---|---|
| Conditions Precedent | On CAM JSON (`conditionsPrecedent`) — credit artefact |
| `READY_FOR_DISBURSEMENT` | Explicit flow status after eSign (+ VKYC gate) |
| Systematic CP tracker blocking LMS | **PARTIAL** — status gates more than a first-class CP satisfaction engine |
| Bank verify | Often via KYC penny-drop earlier |

---

## 20. LMS / disbursement / handoff

| Piece | Today |
|---|---|
| LMS | `LmsService` + workflow LMS product mapping; account may be created around sanction in some paths |
| Disburse | `DisburseLmsStepExecutor` + flow |
| Sequence | Configurable mapping exists; **actual** path is flow-driven: sanction → KFS → eSign → READY_FOR_DISBURSEMENT → DISBURSE/LMS — invoice-discounting may skip term disbursement |
| Servicing handoff | LMS integration surface; not a separate Workflow-owned servicing engine |

**LMS CREATION INTEGRATED:** **PARTIAL**  
**DISBURSEMENT INTEGRATED:** **YES/PARTIAL** (executor + flow; product-specific exceptions)

---

## 21. State models — current equivalents

| Target family | Closest today | Gap |
|---|---|---|
| CUSTOMER REQUIREMENT STATUS | Checklist / missing intake validation errors | No REQUIRED/REQUESTED/PROVIDED/WAIVED/NA object |
| DATA STATUS | GACAT/Policy readiness; CI prep; OCR simulated | Not linked to customer fulfilment |
| SOURCE STATUS | KYC `StepOutcome`; provider errors; CreditControl source tags | No unified source attempt ledger |
| DOCUMENT STATUS (loan) | Limited document fields | Policy Studio statuses not reused for fulfilment |

---

## 22. Authority matrix (code + target)

| CAPABILITY | AUTHORITY TODAY | TARGET AUTHORITY |
|---|---|---|
| Customer proposition | Customer Category (config) | Customer Category |
| Credit decision policy | Legacy rule sets (+ Studio Policy not live) | Policy Version |
| Canonical parameter | GACAT | GACAT |
| Scoring | Scorecard engine (legacy pick) | Scorecard subordinate to Policy |
| KYC requirements | Fused in `workflow_configs.steps` | Light KYC Requirement Set → WF projection |
| KYC execution | Workflow/KYC orchestration | Workflow/KYC orchestration |
| Data acquisition | Mixed WF + adjacent services | Workflow + source adapters |
| Journey sequencing | LoanApplicationFlowService + WF config | Workflow (umbrella) |
| Manual information | intakeConfig | Workflow (from planner) |
| Document requirements | WF + hardcoded docs | Workflow (+ fulfilment≠readiness) |
| Product capabilities | Product Config compose | Product Config |
| CAM | CreditAppraisalService | CAM service (artefact) |
| Credit approval | Flow + CAM review + roles | Credit authority / flow |
| KFS | KfsService | KFS service under WF stage |
| Agreement/eSign | eSign executor + flow | WF stage + eSign service |
| LMS creation | LmsService | LMS adapter under WF |
| Disbursement | Disburse executor + flow | WF stage |
| Audit | audit events / step_execution_record / history | same + requirement provenance |

---

## 23. Customer Category ↔ Workflow relationship

**Code truth:**

- Category persists **Policy** linkage (`policy_document_id` / policy set transitional) — **not** `workflow_id`
- Day-1 seed **validates** a catalog `workflowId` exists/active for dimension coherence but does **not** persist Category→Workflow
- Application binds workflow by dimensions / optional `workflowId`
- Design lock: Category → Policy; Policy must **not** own Workflow
- Two Categories may share Policy / differ Workflow (and vice versa)

### Recommendation: **A (refined) — Customer Category references Workflow Version**

Rationale vs alternatives:

| Option | Verdict |
|---|---|
| **A Category → Workflow Version** | **Preferred.** Matches Day-1 seed intent, preserves Policy≠Workflow, enables Starter vs Bank Starter with shared Policy dims but different journeys |
| **B Product Config resolves Workflow** | Reject as **routing** authority — Product Config is compose/readiness only |
| **C Journey/Profile indirection** | Acceptable later rename of “Workflow Version as Journey Profile”; unnecessary new noun now |
| **D Derive from Policy + Product Config** | **Reject** — would make Policy own journey; conflicts with lock |

Selection flow (future, not implemented):

```
Eligible Categories → Selected Category
  → Policy Version (credit)
  → Workflow Version (journey)
  → Application.workflowId binding
```

---

## 24. Starter / Bank Starter golden

| Dimension | Starter | Bank Starter |
|---|---|---|
| Role / Entity / Product / Amount | Shared pattern | Shared pattern |
| Policy | Income Policy | Bank Policy |
| Journey | Lighter manual income | Bank/AA-heavy |

**Preferred approach if architecture supports:** shared **base Workflow** + **dynamic data-acquisition inserts** from Policy parameter inventory (not full workflow clones).

**Support assessment:**

| Need | Support |
|---|---|
| Separate Category → Policy binds | YES (config) |
| Separate Category → Workflow binds | NOT persisted yet — recommend A |
| Dynamic inserts from Policy inventory | PARTIAL — inventory YES; planner/renderer NO |
| Bank/AA as first-class parallel acquisition | PARTIAL — services exist; not FlowStepType spine |
| Avoid cloning entire workflows | YES as design goal; needs planner + parallel runner |

**STARTER/BANK STARTER GOLDEN SUPPORTED BY TARGET:** **YES** (as design); **PARTIAL** on current runtime.

---

## 25. Reuse matrix

| Capability | Class |
|---|---|
| Workflow engine (config + coordinator) | **EXTEND** (instance/plan runner; honour parallel_groups) |
| Workflow admin UI | **EXTEND** |
| Dynamic forms | **EXTEND** intakeConfig → **NEW_SMALL** GACAT-aware renderer |
| Document collection | **EXTEND** (+ fulfilment≠readiness) |
| KYC orchestration | **REUSE_AS_IS** (+ light requirement packs later) |
| Data requirement planner | **NEW_SMALL_COMPONENT** |
| Source resolver (general) | **NEW_SMALL** wrapping banking precedence + CreditControl patterns |
| Parallel acquisition | **EXTEND** coordinator |
| Policy invocation (live) | **WRAP** then cutover — keep `UnderwritingRuleEngine` transitional |
| Scorecard invocation | **WRAP** / EXTEND toward Policy-linked V2 (DP-3 already prepared) |
| Manual review | **REUSE_AS_IS** / EXTEND statuses mapping |
| CAM | **REUSE_AS_IS** |
| KFS | **REUSE_AS_IS** |
| Agreements | **EXTEND** |
| eSign | **REUSE_AS_IS** |
| LMS | **REUSE_AS_IS** / EXTEND mapping |
| Disbursement | **REUSE_AS_IS** |
| Product Config | **REUSE_AS_IS** as readiness — **do not** promote to router |
| Policy Set lender UX | **DEPRECATE** (transitional runtime only) |
| Full second KYC Profile engine | **DEPRECATE**/avoid |
| Category-owned orchestration | **DEPRECATE**/avoid |

---

## 26. P0 / P1 / P2 gaps

### P0

1. Unify workflow resolution (`app.workflowId` vs KYC `getActiveWorkflow`) — correctness before Category routing
2. Define Category → Workflow Version bind (config only) **without** live routing
3. Data Requirement Planner design (consume DP-3 inventory) — design lock companion
4. Customer fulfilment ≠ data readiness model (document upload PROVIDED immediately)
5. Do **not** make Product Config or Policy the Workflow owner

### P1

1. Parallel acquisition runtime (honour `parallel_groups` / dependency DAG)
2. Dynamic requirement form/document renderer from planner
3. General source resolver (canonical → derive → provider → document → manual → block)
4. Completeness gate before Policy/UW invocation
5. Align AA/Bank/GST/ITR as first-class acquisition nodes under WF umbrella (wrap existing services)

### P2

1. Published Policy as live UW authority (DP-4+)
2. Full CP satisfaction engine for pre-disbursement
3. Richer agreement/security document suite
4. KYC Requirement Set packs by Entity Type
5. Application Category selection + `selectedCustomerCategoryId` (separate programme)

---

## 27. Recommended implementation sequence

1. **Workflow Architecture Design Lock** (this audit → lock doc) — Category↔Workflow = A; Policy≠Workflow; fulfilment≠readiness; planner concept  
2. Fix workflow resolution consistency (P0 hygiene)  
3. Category → Workflow Version **configuration bind** (no live routing)  
4. Data Requirement Planner + requirement state model (config/shadow)  
5. Parallel acquisition runner + completeness gate  
6. Dynamic customer requirements UI (form + docs)  
7. Application Category selection design/implement (non-authoritative → then live)  
8. Policy publish / runtime package cutover (after DP-4 readiness)  
9. Retire lender-facing Policy Set / direct rule-set UX  

**Exact next implementation step (after this audit):**  
Produce **`LOS_WORKFLOW_ARCHITECTURE_DESIGN_LOCK_1`** locking Category→Workflow Version (A), umbrella Workflow orchestration boundaries, fulfilment≠readiness states, and planner interfaces — **still no live routing**.

---

## Appendix A — Classification return block

```
WORKFLOW_PRIMARY_SERVICE:
  WorkflowEngineServiceImpl + ActiveWorkflowConfigService
  + WorkflowExecutionCoordinator + KycOrchestrationServiceImpl
  + LoanApplicationFlowService (lifecycle)

WORKFLOW_CONFIG_TABLES:
  workflow_configs; workflow_lms_product_mapping;
  program_masters.workflow_config_id

WORKFLOW_INSTANCE_TABLES:
  NONE (surrogates: loan_applications.workflow_id,
  kyc_step_results, step_execution_record, application_status_history)

WORKFLOW_STEP_MODEL:
  steps JSONB + KycStepType + FlowStepType spine

WORKFLOW_STATE_MODEL:
  ApplicationStatus + StepOutcome + StepExecutionStatus (imperative)

WORKFLOW_EXECUTOR_MODEL:
  IStepExecutor registry → KYC / Bureau / eSign / Disburse

CLASSIFICATION: E (mixture)
```

---

**END OF AUDIT**
