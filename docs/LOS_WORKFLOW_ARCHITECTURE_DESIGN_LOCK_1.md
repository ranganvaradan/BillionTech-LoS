# LOS Workflow Architecture — Design Lock 1

**Document:** `LOS-WORKFLOW-ARCHITECTURE-DESIGN-LOCK-1`  
**Mode:** DESIGN LOCK ONLY — **no implementation**  
**SOURCE SHA:** `060191c05bd204f67b4e3436d48137652ccaa31a`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway at lock:** V133  
**Date:** 2026-08-15  

**Upstream:**

- `docs/LOS_CREDIT_ARCHITECTURE_DESIGN_LOCK_1.md`
- `docs/LOS_EXISTING_CAPABILITY_REUSE_AUDIT_1.md`
- `docs/LOS_WORKFLOW_AUTHORITY_REQUIREMENT_RESOLUTION_AUDIT_1.md`
- `docs/DATA_PARAMETERS_DP1_ENRICHMENT.md`
- `docs/DATA_PARAMETERS_DP2_READINESS_INVENTORY_1.md`
- `docs/DATA_PARAMETERS_DP2B_CONVERGENCE_1.md`
- `docs/LOS_POLICY_SCORECARD_WEIGHT_CONTRACT_1.md`
- `docs/DATA_PARAMETERS_DP3_CONVERGENCE_1.md`

This document is the **Workflow architecture contract**. It does not change code, DB, runtime, routing, or Category selection.

---

## AUTHORITATIVE PRODUCT MODEL (LOCKED)

```
CUSTOMER CATEGORY  = lending proposition
  "What proposition is the customer applying for?"

POLICY             = credit decision logic
  "How should this application be underwritten?"

GACAT              = canonical data contract
  "What is this data item, where can it come from, is it usable?"

WORKFLOW           = umbrella journey / orchestration authority
  "What must happen, in what order, under what conditions?"

SCORECARD          = scoring subordinate to Policy
KYC                = requirements + execution via existing WF/KYC orchestration
PRODUCT CONFIG     = product/platform capability & readiness composition
                     (NOT permanent proposition-selection / runtime router)
```

### Owns / does not own

| Authority | Owns | Must NOT own |
|---|---|---|
| **Customer Category** | Name/code, Customer Role, Entity Type, Loan Product, amount range, **Policy Version relationship**, **Workflow Version relationship**, lifecycle/effectivity | Policy rules, GACAT defs, score formulas, provider normalization, live orchestration runtime |
| **Policy** | Rules, GACAT parameter requirements, calculations, hard/refer/MR logic, optional Scorecard, version/governance | Workflow journey, provider invocation, KYC execution engine, GACAT catalogue ownership |
| **GACAT** | Canonical IDs, definitions, readiness/source metadata (as evolved) | Journey sequencing, credit decision |
| **Workflow** | Orchestration of collection, docs, KYC, acquisition, wait/retry, completeness gates, Policy/Scorecard **invocation**, credit review, CAM, KFS, agreements/eSign, CP, LMS, disbursement, handoff | Policy business rules, GACAT definitions, KYC canonical definitions, Scorecard formulas/parameters, provider normalization |
| **Scorecard** | Bands/points/weights/transforms under Policy | Expanding decision-factor universe beyond Policy |
| **KYC** | Requirement packs (future light) + execution through existing orchestration | Second KYC engine; underwriting Policy content |
| **Product Config** | Capability/readiness composition; eligible option validation | Silent runtime Workflow override; Category selection engine |

---

# 1. CATEGORY → WORKFLOW CONTRACT (LOCKED)

```
Customer Category Version  →  Workflow Version     (independent)
Customer Category Version  →  Policy Version       (independent)
```

**POLICY → WORKFLOW RELATIONSHIP: NONE.** Policy must **not** own, select, or embed Workflow.

Must support:

| Pattern | Allowed |
|---|---|
| Same Policy + different Workflow | **YES** |
| Same Workflow + different Policy | **YES** |

Examples (LOCKED intent):

| Category | Policy | Workflow |
|---|---|---|
| Starter Loan | Income Policy | Standard Business Journey |
| Bank Starter | Bank Policy | Standard Business Journey *(preferred if planner covers banking acquisition)* |
| Bank Starter (if journey material) | Bank Policy | Bank-heavy Journey |

**Do not force** separate Workflow Versions when **DataRequirementPlanner** + shared journey can insert Policy-driven acquisition dynamically.

---

# 2. WORKFLOW VERSIONING (LOCKED)

## Identity vs Version

| Concept | Meaning |
|---|---|
| **Workflow** (identity) | Stable governed journey family (name/code) |
| **Workflow Version** | Immutable published snapshot of that journey’s orchestration config |

## Safest locked model (aligns with Policy Version reproducibility)

1. **Configuration (admin):** Category Version binds **Workflow identity** *or* an exact **Workflow Version** pointer advanced only by controlled publish/activation of the Category Version.  
2. **Application runtime snapshot:** Application stores **exact Workflow Version** used for the journey (and later DecisionSnapshot / equivalent retains it).  
3. **Immutability:** Once a Workflow Version is used by a live (or decided) application, mutating that version’s definition is **forbidden**. Edits create a **new** Workflow Version.  
4. Transitional mapping: today’s `workflow_configs` row (+ version column) is the practical Workflow Version until a cleaner identity/version split is introduced.

**Historical journeys must not be rewritten** by later admin edits to an “active” head.

---

# 3. WORKFLOW INSTANCE MODEL (LOCKED — design only)

Audit: no full instance engine today. **Minimal target** (do not build now; avoid BPMN-heavy engine):

### WorkflowInstance (per application)

| Field | Purpose |
|---|---|
| ApplicationId | Binding |
| WorkflowVersionId | Exact version |
| Status | NOT_STARTED / RUNNING / WAITING / BLOCKED / COMPLETED / FAILED / CANCELLED |
| CurrentState / phase | Coarse orchestration phase |
| StartedAt / CompletedAt | Audit |
| RequirementPlanId (optional) | Link to plan |

### WorkflowStepInstance (per planned step / acquisition / gate)

| Field | Purpose |
|---|---|
| StepId / StepKey | Stable within Workflow Version |
| Kind | KYC / SOURCE / GATE / POLICY_INVOKE / HUMAN / POST_DECISION / … |
| Status | PENDING / RUNNING / WAITING / SUCCEEDED / FAILED / SKIPPED / TIMED_OUT |
| Attempts | Retry count |
| FailureReason | Diagnostics |
| Output / EvidenceRef | Pointer to results (not embed business rules) |
| StartedAt / CompletedAt | Audit |

**Reuse note:** Evolve from `step_execution_record` + `kyc_step_results` + application status — do not discard them. Instance model is the **orchestration ledger**, not a second decision engine.

**WORKFLOW INSTANCE MODEL REQUIRED:** **YES** (minimal; not BPMN).

---

# 4. DATA REQUIREMENT PLANNER (LOCKED — design only)

### Component: `DataRequirementPlanner`

**Inputs:**

- Application  
- Selected Customer Category (when present)  
- Policy Version  
- Policy parameter inventory (DP-3 graph inventory)  
- Existing canonical facts  
- GACAT readiness / source metadata  
- Workflow configuration (base journey)  
- KYC context / outcomes  

**Output:** `RequirementPlan` — ordered/classified requirements.

### Classification per required item (LOCKED vocabulary)

| Class | Meaning |
|---|---|
| `ALREADY_AVAILABLE` | Valid canonical value already present |
| `DERIVABLE` | Can be computed from existing canonical inputs |
| `AUTO_SOURCE` | Automatic provider/internal source |
| `CUSTOMER_PROVIDED` | Needs customer/RM fulfilment (see §5) |
| `MANUAL_REVIEW` | Needs human extraction/review |
| `UNAVAILABLE_BLOCKER` | Cannot proceed without explicit waiver/block path |

**DATA REQUIREMENT PLANNER REQUIRED:** **YES** (small reusable component).  
**Do not implement in this lock.**

---

# 5. CUSTOMER-PROVIDED REQUIREMENTS (LOCKED)

### Term: `CUSTOMER_PROVIDED_REQUIREMENT`

Broader than “manual input.”

### Fulfilment modes (LOCKED)

| Mode | Meaning |
|---|---|
| `DIRECT_INPUT` | Form/field capture — **only if lender permits** |
| `DOCUMENT_UPLOAD` | Evidence upload |

Both may be allowed for one requirement; **do not assume** direct input is always allowed.

Example: `financial.ebitda` with no auto source → document upload required; direct value only if Policy/GACAT/source metadata permits.

**CUSTOMER_PROVIDED_REQUIREMENT LOCKED:** **YES**  
**DIRECT_INPUT MODE:** **YES** (optional per requirement)  
**DOCUMENT_UPLOAD MODE:** **YES** (optional per requirement)

---

# 6. CUSTOMER FULFILMENT ≠ DATA READINESS (LOCKED)

Two **independent** state models.

### A. CUSTOMER REQUIREMENT STATUS

`REQUIRED` → `REQUESTED` → `PROVIDED` | `WAIVED` | `NOT_APPLICABLE`

### B. DATA READINESS STATUS

`NOT_AVAILABLE` → `PROCESSING` → `EXTRACTED` → `VERIFIED` → `READY_FOR_POLICY`  
also: `FAILED` | `DATA_INSUFFICIENT`

### Critical rule (LOCKED)

If the customer uploads a required document:

- **CUSTOMER REQUIREMENT → `PROVIDED` immediately**  
- **DATA READINESS** may remain `PROCESSING`

**Do not** re-ask the customer for the same evidence merely because extraction is pending.

**CUSTOMER PROVIDED != DATA READY:** **YES**  
**DOCUMENT UPLOAD COUNTS AS CUSTOMER PROVIDED IMMEDIATELY:** **YES**

---

# 7. DOCUMENT REQUIREMENT MODEL (LOCKED)

A document may satisfy:

- one customer requirement  
- multiple canonical parameters (after extraction)  
- KYC requirement  
- Policy data requirement  
- a fulfilment / CP condition  

Example: Financial Statements upload → customer requirement `PROVIDED`; later may yield `financial.revenue`, `financial.ebitda`, `financial.networth`, `financial.debt` each progressing through DATA READINESS independently.

Workflow treats the **document fulfilment** as complete on upload; derived parameters remain in their own readiness states until verified.

---

# 8. SOURCE RESOLUTION CONTRACT (LOCKED)

For a Policy-required canonical parameter, Workflow resolves **HOW** using GACAT/source metadata.

### Conceptual precedence (not a rigid global hardcode)

1. Existing valid canonical value  
2. Derive from existing canonical inputs  
3. Automatic source/provider  
4. Customer document  
5. Document extraction / manual extraction  
6. Direct form input **if allowed**  
7. Unavailable / block  

GACAT/source metadata **may** define (design only — **no new GACAT fields in this lock**): preferred/alternate source; customer provision allowed; direct input allowed; document upload allowed; verification required.

Workflow consumes that metadata when present; does not invent silent substitutes for unresolved tokens (DP-2B/DP-3 guardrail).

---

# 9. DATA REQUIREMENT PLAN GOLDEN (LOCKED)

**Mandatory golden:**

Policy requires 12 parameters → 9 auto/available → 3 customer-facing:

| Item | Modes |
|---|---|
| A | DIRECT_INPUT allowed |
| B | DOCUMENT_UPLOAD required |
| C | DOCUMENT_UPLOAD **or** DIRECT_INPUT |

Workflow presents **only** A/B/C.  
On B upload: B customer = `PROVIDED`; B data = `PROCESSING`; **no re-ask** while processing.

---

# 10. POLICY-DRIVEN DATA ACQUISITION (LOCKED)

| Actor | Responsibility |
|---|---|
| **Policy** | Declares **WHAT** canonical data is required |
| **Workflow** | Determines **HOW** to obtain it via planner + GACAT/source binding + orchestration |

Policy must **NOT** invoke Equifax, AA, GST, KYC providers, or document parsers directly.

**POLICY REQUIRED DATA DRIVES WORKFLOW:** **YES** (requirements → plan → orchestration)  
**WORKFLOW OWNS POLICY BUSINESS RULES:** **NO**

---

# 11. PARALLEL EXECUTION (LOCKED)

Dependency-based parallelism is a **target**:

```
KYC prerequisite complete
        ↓
┌── Bureau
├── Bank / AA
├── GST
└── MCA
        ↓
Data Completeness Gate
        ↓
Policy invocation
```

### Minimal orchestration semantics (not full BPMN)

| Primitive | Meaning |
|---|---|
| Parallel group | Steps may run concurrently after shared prerequisites |
| Dependency | Step B waits on Step A (or set) |
| Wait / join | Proceed when required members succeed (or configured failure policy) |
| Retry | Bounded attempts with reason |
| Timeout | Step → TIMED_OUT → configured failure/block path |
| Partial failure | Gate decides block vs continue-with-waiver vs refer |

Today’s `parallel_groups` is advisory only — runtime honour is a future W6 deliverable.

**PARALLEL ACQUISITION TARGET:** **YES**  
**DATA COMPLETENESS GATE TARGET:** **YES**

---

# 12. KYC BOUNDARY (LOCKED)

**Reuse:**

- `KycOrchestrationServiceImpl`  
- `VkycWorkflowService`  
- existing `workflow_configs` KYC steps  

**Future composition (not new engine):**

```
Entity Type / context → light KYC Requirement Set → existing workflow steps
```

Workflow **executes** KYC. KYC outcomes become canonical readiness/evidence usable by Policy.  
**Do not** create a second KYC execution engine.

**KYC EXECUTION REUSED:** **YES**

---

# 13. POLICY EXECUTION GATE (LOCKED)

Before Workflow invokes Policy (or transitional UW package):

- All **REQUIRED** Policy parameters must meet the Policy’s required data state  
- Typically `READY_FOR_POLICY` unless Policy explicitly permits another quality state  
- **Do not invent** missing values  

If required value unavailable → Workflow enters **`DATA_REQUIREMENT_BLOCKED`** (or equivalent Application/WorkflowInstance status) — **not** silent/partial false Policy execution.

---

# 14. POLICY / SCORECARD ORDER (LOCKED)

```
Data completeness gate
  → Policy hard / decision rules
  → if continuation permitted
  → optional Scorecard
  → Policy + Scorecard result aggregation
  → credit decision recommendation
```

Reuse current runtime semantics during transition (`UnderwritingRuleEngine` + `ScorecardPolicyEngine`).  
**Do not** move scoring logic into Workflow. Scorecard remains subordinate to Policy (DP-3 / weight contract).

---

# 15. DECISION OUTCOMES (LOCKED — conceptual map)

Workflow **reacts**; Workflow **does not decide credit**.

| Conceptual outcome | Prefer existing equivalents |
|---|---|
| `AUTO_APPROVE` | APPROVED / CAM_READY path without human credit exception |
| `AUTO_REJECT` | REJECTED |
| `REFER_TO_CREDIT` | MANUAL_REVIEW / CAM / sanction-pending human paths |
| `CONDITIONAL_APPROVAL` | Approval with conditions (CAM CP / sanction conditions) |
| `DATA_INSUFFICIENT` | Block / fail-closed / DATA_REQUIREMENT_BLOCKED — not invented scores |

Do **not** create duplicate status enums if existing domain statuses already cover behaviour; map vocabulary in adapters.

---

# 16. MANUAL / CREDIT REVIEW (LOCKED)

When Policy (or transitional UW) requires human review, Workflow orchestrates the review stage and must preserve:

actor, reason, deviation, override, approval authority, timestamp, previous decision, final decision.

Reuse existing credit approval / maker-checker / CAM send-back patterns where available.

---

# 17. CAM (LOCKED)

**CAM = credit appraisal / explainability artefact.**

Populated from: Application, KYC, canonical data, Policy evidence, Scorecard evidence, manual/deviation evidence.

**CAM IS DECISION AUTHORITY: NO.**  
CAM must not become a competing decision engine.

---

# 18. POST-DECISION WORKFLOW (LOCKED)

Workflow orchestrates downstream steps based on decision + Workflow Version config:

approval → sanction → pricing → KFS → customer acceptance → agreement → eSign → mandate/security → conditions precedent → LMS creation → disbursement → servicing handoff

Not every product uses identical steps. **Workflow Version** determines the journey.

**WORKFLOW ORCHESTRATES POST-DECISION:** **YES**

---

# 19. CONDITIONS PRECEDENT / SUBSEQUENT (LOCKED — design)

| Type | Meaning |
|---|---|
| `CONDITION_PRECEDENT` | Must be satisfied **before** disbursement |
| `CONDITION_SUBSEQUENT` | Tracked **after** disbursement per lender policy |

**Today:** CP appears on CAM JSON; status gate `READY_FOR_DISBURSEMENT` exists; no full CP/CS engine.  
**Later:** extend Workflow task/requirement model — do **not** implement in this lock.

---

# 20. WORKFLOW RESOLUTION HYGIENE (LOCKED)

**Principle:** **ONE resolved Workflow Version per application.**

All consumers must use that same Version:

- KYC  
- document requirements  
- source acquisition  
- underwriting orchestration  
- post-decision journey  

**No service** independently discovers a different “active workflow” by product/segment head.

### Transitional compatibility approach

1. Prefer `loan_applications.workflow_id` (exact Version) once set.  
2. If null at create: resolve once → **persist** Workflow Version on the application → all services read binding.  
3. Fix KYC path that currently may call `getActiveWorkflow(type, product, segment)` ignoring binding (**W1**).  
4. Category selection (future) sets Category + Policy + Workflow Version together; still one binding.

**ONE WORKFLOW VERSION PER APPLICATION:** **YES**

---

# 21. PRODUCT CONFIG BOUNDARY (LOCKED)

Product Config may:

- validate/configure product capabilities  
- surface eligible Workflow options for admin composition/readiness  

Once a Customer Category Version selects its Workflow Version:

- Product Config **must not silently override** that Workflow at runtime  
- Incompatibility → **Category activation / readiness fail** — not runtime substitution  

**PRODUCT CONFIG CAN SILENTLY OVERRIDE CATEGORY WORKFLOW:** **NO**

---

# 22. WORKFLOW ADMIN TARGET (LOCKED — UX direction only)

Long-term governed lender UI concepts (do not implement now):

Workflow Name, Version, applicable Role/Entity/Product context, steps, order/dependencies, parallel groups, conditions, required documents, KYC pack, data-acquisition behaviour, post-decision steps, effective dates, lifecycle.

Avoid exposing low-level executor/code detail as the primary lender mental model.

---

# 23. STARTER / BANK STARTER GOLDEN (LOCKED)

| | Starter Loan | Bank Starter |
|---|---|---|
| Eligibility dims | BORROWER / INDIVIDUAL / BUSINESS_TERM_LOAN / 20k–500k | Same |
| Policy | Income Policy | Bank Statement Policy |
| Workflow | **Business Loan Journey** (shared preferred) | **Same** if planner inserts bank/AA |

Income Policy → planner requests appropriate manual/document income requirements.  
Bank Policy → planner adds bank/AA acquisition — **without** cloning the entire Workflow.

Separate Bank-heavy Journey only if material journey differences remain after planner/parallelism.

**STARTER/BANK STARTER SHARED WORKFLOW POSSIBLE:** **YES**

---

# 24. AUTHORITY MATRIX — LOCKED

| Capability | Authority |
|---|---|
| Customer proposition | **Customer Category** |
| Application orchestration | **Workflow** |
| Credit rules / data requirements | **Policy** |
| Canonical data / source metadata | **GACAT** |
| Score calculation | **Scorecard** (subordinate to Policy) |
| KYC execution | **Workflow + existing KYC orchestration** |
| Provider normalization | **Source adapters / metric services** |
| Product capability / readiness | **Product Config** |
| CAM | **Appraisal artefact** (not decision authority) |
| Approval / deviation | **Credit approval authority** |
| KFS | Fulfilment/compliance component **orchestrated by Workflow** |
| Agreement / eSign | Document/eSign components **orchestrated by Workflow** |
| LMS / disbursement | Fulfilment adapters **orchestrated by Workflow** |
| Audit / provenance | Application + WorkflowInstance + DecisionSnapshot (as evolved) |

---

# 25. IMPLEMENTATION SEQUENCE (LOCKED)

Refined from audit; code truth keeps W1 first (resolution hygiene before Category bind):

| Step | Scope | Live routing? |
|---|---|---|
| **W1** | Workflow resolution hygiene — one application → one resolved Workflow Version | NO |
| **W2** | Category → Workflow Version **configuration bind** (non-authoritative) | NO |
| **W3** | Requirement Plan domain + fulfilment ≠ readiness state split | NO |
| **W4** | Policy Parameter Inventory → DataRequirementPlanner | NO |
| **W5** | Dynamic customer-provided requirements (form + docs) | NO |
| **W6** | Parallel acquisition + Data Completeness Gate | NO |
| **W7** | Application Customer Category selection | NO live UW/routing yet until later |
| **W8** | Policy publication / runtime bridge | Shadow-capable |
| **W9** | Shadow execution | Shadow only |
| **W10** | Live cutover | YES (separate programme gate) |

**Not in this lock:** implement W1–W10, Category selection, shadow/live routing.

### Exact next implementation step (after this lock)

**W1 — Workflow resolution hygiene:** all KYC / acquisition / flow services consume the same persisted application Workflow Version; eliminate independent “latest active by dimensions” divergence.

---

## P0 ARCHITECTURE GUARDRAILS (LOCKED)

1. Policy must **not** own Workflow.  
2. Product Config must **not** silently override Category Workflow at runtime.  
3. One Workflow Version per application for all consumers.  
4. Customer fulfilment ≠ data readiness; upload ⇒ PROVIDED immediately.  
5. Policy declares WHAT; Workflow orchestrates HOW; no silent invented values.  
6. Prefer shared Workflow + planner over Workflow clones for Policy-only differences.  
7. Reuse KYC orchestration; no second KYC engine.  
8. CAM is not decision authority; Workflow is not credit decision authority.  
9. No live Category routing / `selectedCustomerCategoryId` until W7+ programme gates.  
10. Unresolved GACAT/Policy tokens remain unresolved (no fuzzy bind).

---

## Explicit non-goals of this lock

No code, DB, migration, Workflow runtime, Product Config, Policy Studio, GACAT, Customer Category, application Category selection, live/shadow routing, KYC execution, scorecard runtime, deploy, or push.

---

**WORKFLOW DESIGN LOCK COMPLETE:** YES  
**READY FOR W1 IMPLEMENTATION:** YES  
**READY FOR APPLICATION CATEGORY SELECTION:** NO (W7; after W1–W2 at minimum)  
**READY FOR LIVE ROUTING:** NO  

**END OF DESIGN LOCK**
