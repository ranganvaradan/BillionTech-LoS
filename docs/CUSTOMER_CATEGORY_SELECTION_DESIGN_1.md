# CUSTOMER CATEGORY SELECTION DESIGN 1

**Task:** CUSTOMER-CATEGORY-SELECTION-DESIGN-1  
**Mode:** AUDIT + DESIGN ONLY — no code, DB, deploy, live routing  
**Source SHA:** `95f20a22c1622f15c27236b99a3128e50b74e849`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway baseline observed:** V137 (W6); Category schema V128–V135  
**Downstream handoff:** Category lock → W4 RequirementPlan → W5 customer UI → W6 acquisition  

---

## 0. Executive verdict

Customer Category already exists as a **config-time lending proposition** with exact Policy Version + Workflow Version binds (W2). Application **runtime selection** (`eligibleCategories` / `selectedCustomerCategory*`) does **not** exist.

Target: Registration → Basic Intent → Eligibility → Progressive Disambiguation → Explicit/Auto Selection → lock Category + Policy + Workflow Versions → W4/W5/W6.

**Reuse preference:** extend existing intake / `intakeConfig` / Product Config catalogues — **do not** build a third questionnaire engine.

**Overlap of Categories on the same Role/Entity/Product/Amount dimensions is VALID** (WARNING only today; keep that).

---

## 1. Current-state audit (repository facts)

### 1.1 Customer Category (EXISTS — config only)

| Fact | Location |
|------|----------|
| Table/entity `customer_category` | `CustomerCategoryEntity.java`, V128+ |
| Versioning `code` + `version_no` | same |
| Lifecycle DRAFT→…→ACTIVE→RETIRED | `ConfigLifecycleStatus` |
| Criteria: role (`intake_segment`), entity (`borrower_type`), product, amount range | same |
| Exact Policy Version bind | `policy_applicability_id`, `policy_document_id`, `policy_version_label` (V130) |
| Exact Workflow Version bind | `workflow_id`, `workflow_version`, `workflow_content_hash` (V135 / W2) |
| Overlap = WARNING, does not block activation | `CustomerCategoryOverlapDetector` |
| Admin APIs + UI | `CustomerCategoryController`, `CustomerCategoriesPage.tsx` |
| Activation readiness (Policy + Workflow linkage/compat) | `activation-readiness` |
| Programme/channel as Category criteria columns | **MISSING** |
| Customer-facing proposition copy (benefits, display order, selection behaviour) | **PARTIAL** — `name`/`description` only |
| Disambiguation question config on Category | **MISSING** |
| Runtime match/select APIs for applications | **MISSING** |
| `selectedCustomerCategoryId` on `LoanApplication` | **MISSING** (docs forbid until W7+) |

### 1.2 W1 Workflow resolution (EXISTS — not Category-driven)

| Fact | Location |
|------|----------|
| Single resolver per app | `ApplicationWorkflowResolver` |
| If `app.workflowId` set → that exact version (fail closed) | W1 |
| Else dimensional DEFAULT lookup (borrowerType, loanProduct, intakeSegment) | W1 |
| Persist `workflow_version`, `workflow_resolution_source`, hash | `LoanApplication` |
| Category selection writes `app.workflow_id` | **MISSING** (W2 explicitly deferred) |

### 1.3 Application intake (EXISTS — reusable for Basic Intent)

| Fact | Location |
|------|----------|
| Create: `borrowerType`, `loanProduct`, `intakeSegment` (BORROWER/ANCHOR), `requestedAmount`, optional `workflowId` | `CreateApplicationRequest`, UI intake |
| Entity enum today: INDIVIDUAL, PROPRIETOR, PARTNERSHIP, COMPANY | `BorrowerType` / UI catalog |
| LLP / Pvt Ltd / Public Ltd as distinct types | **MISSING** (collapsed into COMPANY in docs) |
| Anchor intake wizards + invoice sub-program link | `AnchorIntakeWizard`, `selectedSubProgramId` |
| Channel as first-class application field | **MISSING** |
| Programme/subProgram on app for PLP/invoice | EXISTS (`subProgramId`) — **not** Category criteria |

### 1.4 Workflow intakeConfig / dynamic forms (EXISTS — reuse candidate)

| Fact | Location |
|------|----------|
| `intakeConfig`: personalFields, customFields, docs, age/tenure, mandatory groups | `workflowIntakeRules.ts`, Workflow admin panel |
| Driven by **selected Workflow** after Category lock | Runtime today assumes Workflow already known |
| Product Config | Readiness composition; **not** routing/selection authority |
| W5 Customer Requirements UI | Post-W4 plan projection — **not** Category selection |

### 1.5 Policy / Workflow content as eligibility

| Fact | Verdict |
|------|---------|
| Category eligibility dimensions = Role, Entity, Product, Amount (+ effectivity) | EXISTS |
| Policy/Workflow **contents** as hidden eligibility | Must **not** be used unless lender configures safe disambiguation attrs |
| Policy scope must **cover** Category at bind time | `CustomerCategoryPolicyScopeCompatibility` |
| W4 can show how two Policies differ in data requirements | EXISTS (planner) — usable as **design input** for safe questions only |

### 1.6 Observed LOS pattern vs recommendation

| Pattern | Observed in repo | Recommendation |
|---------|------------------|----------------|
| Proposition eligibility | Category criteria (config) | Keep; add runtime eligibility service |
| Proposition selection | Missing | Add selection + lock |
| Journey/workflow binding | W1 dimensional / explicit id; W2 Category→WF config | After selection: Category WF → app |
| Policy version binding | Category→Policy (config) | After selection: Category Policy → app |
| Progressive intake | intakeConfig + W5 | Reuse catalogue; new stage **before** full WF intake |
| Decisioning after data | W6 gate → future Policy bridge | Unchanged |

---

## 2. Target architecture

```
Registration / basic identity
        ↓
Basic Application Intent
  (Role, Entity Type, Product, Amount, known programme/channel context)
        ↓
Category Eligibility Engine  →  eligibleCategories[]
        ↓
Resolution mode:
  AUTO_SINGLE_MATCH
  | PROGRESSIVE_DISAMBIGUATION  (safe questions → recompute)
  | EXPLICIT_PROPOSITION_SELECTION
        ↓
selectedCustomerCategory (+ version) + selectionSource
        ↓
Lock exact:
  Category Version
  Policy Version (from Category bind)
  Workflow Version (from Category bind)
        ↓
Persist app.workflow_id = Category.workflow_id
        ↓
W4 plan-from-policy → W5 customer requirements → W6 acquisition
        ↓
(later) Policy runtime bridge — NOT this design’s implementation
```

**Authorities (locked):**

| Concern | Authority |
|---------|-----------|
| What proposition | Customer Category |
| What credit rules | Policy Version bound to Category |
| How journey/KYC/docs run | Workflow Version bound to Category |
| What data still needed | W4 RequirementPlan |
| Customer remaining actions | W5 |
| Acquisition orchestration | W6 |

---

## 3. Basic Application Intent

### 3.1 Minimum fields (before eligibility)

| Field | Required for eligibility | Source today |
|-------|--------------------------|--------------|
| Customer Role (BORROWER / ANCHOR) | YES | `intakeSegment` |
| Entity Type | YES | `borrowerType` |
| Loan Product | YES | `loanProduct` |
| Requested Loan Amount | YES (when Category has bounds) | `requestedAmount` |
| Programme / sub-program / channel | OPTIONAL pre-filter | `subProgramId` / context — **not** Category column yet |
| Full financials / bureau / KYC docs | NO | Deferred to Workflow + W4/W5/W6 |

Do **not** require Policy-specific financial data at registration.

### 3.2 Known context precedence (ask only what is unknown)

1. Trusted application/session context (RM channel, anchor programme, deep-link product)  
2. Basic Intent answers  
3. Eligible Categories  
4. Progressive disambiguation questions  
5. Explicit proposition choice  

---

## 4. Eligibility contract

```
eligibleCategories = ACTIVE Categories where:
  role matches (or Category ANY)
  entity type matches (or ANY)
  product matches (or ANY)
  amount in [min,max] (null = unbounded)
  effectivity window contains now
  Policy bind present + activation-valid
  Workflow bind present + activation-valid
  optional programme/channel filter IF configured (future criteria)
```

**Not eligibility dimensions (unless explicitly configured as safe attrs):**

- Policy rule thresholds  
- Scorecard weights  
- Bureau cutoffs  
- Internal GACAT parameter inventory  

**Multiple matches = VALID.**  
Do **not** resolve by DB order, UUID, creation date, or “most specific” heuristics.

```
eligibleCategories = 2
selectedCategory = null
```
is a valid transient application state.

---

## 5. Selection contract

```
selectedCustomerCategoryId
selectedCustomerCategoryVersion   // code + version_no or immutable Category row id
selectedPolicyDocumentId / applicability / versionLabel   // copied from Category bind
selectedWorkflowId / workflowVersion / contentHash          // copied from Category bind
selectionSource: CUSTOMER | RM | SYSTEM_AUTO
selectionReason: optional (RM governance)
selectedAt
```

**Eligibility ≠ Selection.**  
RM may only select from `eligibleCategories` unless a future **governed override** model exists (design only — do not implement now).

---

## 6. Disambiguation contract

### 6.1 Three resolution modes

| Mode | When | Behaviour |
|------|------|-----------|
| **AUTO_SINGLE_MATCH** | Exactly one eligible + lender allows auto | System selects; no question |
| **PROGRESSIVE_DISAMBIGUATION** | N>1 and safe configured differentiators exist | Ask min questions; recompute candidates |
| **EXPLICIT_PROPOSITION_SELECTION** | N>1 and no further safe partition / customer chose “both” | Present propositions; require explicit pick |

No arbitrary fallback. No silent generic Category.

### 6.2 Outcomes when stuck / empty

| Outcome | Meaning |
|---------|---------|
| `NO_ELIGIBLE_CATEGORY` | Change amount/product, RM assist, save draft — never invent Category |
| `EXPLICIT_PROPOSITION_SELECTION_REQUIRED` | Show business-friendly cards |
| `WAITING_FOR_DISAMBIGUATION_ANSWER` | Progressive question outstanding |

### 6.3 Progressive algorithm (deterministic)

Given candidate set `C` (|C| > 1):

1. Load lender-configured **DisambiguationAttributes** attached to Categories (and/or shared question catalogue).  
2. Filter to attributes marked `SAFE_FOR_CATEGORY_DISAMBIGUATION = true`.  
3. Exclude any attribute tagged `CREDIT_THRESHOLD` / Policy-decision / GACAT decision operand.  
4. Rank candidate questions by:  
   - **Partition power** (expected reduction of |C|)  
   - **Answerability** (customer can answer without acquisition)  
   - **Catalogue reuse** (existing intake field key preferred)  
   - **Non-sensitivity**  
5. Ask **one** (or minimal set) highest-ranked question.  
6. Apply answer → filter `C` → repeat until |C|≤1 or no safe question remains → explicit selection.

**Minimum question principle:** do not ask every differentiator; stop when ambiguity resolved or only explicit choice remains.

---

## 7. Safe disambiguation model

### 7.1 Concept: `SAFE_FOR_CATEGORY_DISAMBIGUATION`

A question/attribute is safe only if:

- Lender explicitly configured it for Category selection  
- It describes **proposition/journey** characteristics, not underwriting thresholds  
- Answering it does **not** require Bureau/KYC/GST acquisition  
- It does **not** expose pass/fail credit logic  

**Allowed examples (when configured):**

- Preferred financial evidence route (statements vs bank/AA vs both)  
- Programme / facility preference (when not already known)  
- Employment vs business context (high-level)  
- Documentation availability preference  

**Forbidden examples:**

- “Is bureau score > 700?”  
- “Is EBITDA > ₹10 lakh?”  
- “Do you have DPD > 30?”  

### 7.2 Using W4 / Policy differences as *design input only*

W4 can reveal that Policy A needs income statements while Policy B needs AA/banking.

That difference may **inspire** a lender-configured safe question (“How would you prefer to provide financial information?”).

It must **not** auto-publish every Policy parameter as a customer question.  
Mapping: Policy requirement family → optional `DisambiguationAttribute` with explicit allow-list.

### 7.3 One reusable question catalogue (target)

| Layer | Role |
|-------|------|
| **Field/Question Catalogue** (shared) | Canonical keys, labels, types, safety flags |
| Workflow `intakeConfig` | References catalogue keys for post-Category journey |
| Category disambiguation config | References same catalogue keys for pre-selection |
| W5 RequirementPlan projection | Customer fulfilment of Policy requirements (after selection) |

**Avoid three independent authorities.** Prefer EXTEND catalogue + references.

Until catalogue is unified: Category may store `disambiguationHints` JSON referencing intake field keys / document groups already used in W4 (`pendingDocumentGroup`, fulfilment modes).

---

## 8. Proposition presentation

When `EXPLICIT_PROPOSITION_SELECTION`:

Customer/RM sees lender-configured:

- Display name  
- Short description  
- Benefits (bullets)  
- Requirements summary (customer language)  
- Display order  

**Never show:** Policy UUID, Workflow UUID, GACAT ids, scorecard, thresholds, rule counts.

---

## 9. RM-assisted selection

| Source | Persist |
|--------|---------|
| DIRECT CUSTOMER | `selectionSource=CUSTOMER` |
| RM ASSISTED | `selectionSource=RM` + optional reason |
| SYSTEM AUTO | `selectionSource=SYSTEM_AUTO` |

Override of ineligible Category: **design reserved** (maker-checker, reason codes) — **not in first implementation**.

---

## 10. Version lock immutability

Once selection committed (and application leaves Category-selection stage):

| Binding | Source | Immutable thereafter |
|---------|--------|----------------------|
| Category Version | Selected row (`id` + `code`/`version_no`) | YES |
| Policy Version | Category bind snapshot | YES |
| Workflow Version | Category bind → write `app.workflow_id` | YES |

Later config edits to ACTIVE Category definitions create **new versions**; existing apps keep locked ids.

Aligns with W1: after lock, `ApplicationWorkflowResolver` must honour `app.workflow_id` and never re-run dimensional DEFAULT for that app.

---

## 11. W1 compatibility transition

| App class | Behaviour |
|-----------|-----------|
| **New apps (post feature)** | Intent → Category select → set `workflow_id` from Category → W1 resolves pinned WF |
| **Existing apps with `workflow_id`** | Keep W1 pin; do not force Category backfill unless migration job |
| **Existing apps without Category, dimensional WF** | Continue W1 DEFAULT until Category selection feature enabled for their channel |
| **Dual-write period** | Selection sets Category + Policy snapshot + `workflow_id`; resolution source = `CATEGORY_SELECTION` |

No second independent active-workflow lookup after Category selection.

---

## 12. Policy resolution

Runtime: **Category’s bound Policy Version only**.  
Do not re-match Policy Studio scopes at runtime because another applicability “also fits.”  
Scope compatibility remains **configuration-time** (already enforced at Category↔Policy bind).

---

## 13. Programme / channel precedence

**Today:** programme/channel are **not** Category eligibility columns; `subProgramId` is PLP/invoice linkage.

**Target:**

1. If trusted context already implies a programme/channel → pre-filter Categories that declare matching applicability (future optional criteria or tags).  
2. Else include in progressive questions only if lender configured.  
3. Never ask what the platform already knows.

---

## 14. Anchor model

- Role `ANCHOR` is first-class (`CustomerRole` / `IntakeSegment`).  
- Entity Type remains independent of Role.  
- Categories already seed ANCHOR + invoice products.  
- Anchor may initiate invoice financing / dealer / SCF / other products via **Category criteria**, not hardcoded role assumptions.  
- Do not collapse Anchor into borrowerType naming.

---

## 15. Entity Type expansion (design only — no implement)

| Today | Target |
|-------|--------|
| COMPANY (collapsed) | PRIVATE_LIMITED_COMPANY, PUBLIC_LIMITED_COMPANY, LLP (+ keep COMPANY as legacy alias) |
| PARTNERSHIP | Keep separate from LLP (KYC/docs often differ) |

**Migration approach:**

1. Introduce new enum values + compatibility map `COMPANY → PRIVATE_LIMITED_COMPANY` (configurable default).  
2. Category criteria accept new values; old ACTIVE Categories with COMPANY remain valid.  
3. Applications store historical value; eligibility uses compatibility expansion.  
4. No silent rewrite of locked historical apps.

---

## 16. KYC timing

```
Basic identity (minimal)
  → Category selection (no expensive KYC for disambiguation)
  → Category Workflow starts
  → KYC steps execute per Workflow
```

Exception only if regulated identity is legally required before proposition (lender policy) — not the default.

---

## 17. Category Admin UX changes (design)

Extend Admin (reuse `CustomerCategoriesPage`) to configure:

| Area | Fields |
|------|--------|
| Proposition | name, customer-facing description, benefits[], requirementsSummary, displayOrder |
| Eligibility | Role, Entity Type(s), Product(s), amount range, effectivity |
| Binds | Policy Version, Workflow Version (existing) |
| Programme/channel | optional applicability tags (future columns) |
| Disambiguation | attribute values + linked SAFE catalogue questions |
| Selection behaviour | allowAutoSingleMatch, allowExplicitChoice, requireRmReason |
| Internal | Policy Set remains hidden/transitional |

**Activation checks (add to readiness):**

- Policy + Workflow binds valid (existing)  
- Customer-facing metadata present if ACTIVE for customer channels  
- Disambiguation refs resolve to catalogue / SAFE flags  
- No CREDIT_THRESHOLD exposure  
- Effectivity valid  
- **Overlap still WARNING only — does not block**

---

## 18. Runtime sequence (happy path)

1. Create application with Basic Intent.  
2. `GET eligible-categories` → list.  
3. If 1 and auto allowed → select + lock.  
4. If N>1 → `GET disambiguation-plan` → ask → `POST answer` → recompute.  
5. If still N>1 → `GET propositions` → `POST select`.  
6. Persist locks; set `workflow_id`; emit `CATEGORY_SELECTED`.  
7. Invoke W4 `plan-from-policy` with locked Policy.  
8. W5/W6 proceed.

---

## 19. API proposal (design — not implement)

| API | Purpose |
|-----|---------|
| `POST /applications/{id}/category-eligibility/evaluate` | Compute `eligibleCategories` |
| `GET  /applications/{id}/category-eligibility` | Current eligible set + mode |
| `GET  /applications/{id}/category-disambiguation/next` | Next safe question or null |
| `POST /applications/{id}/category-disambiguation/answers` | Submit answer; recompute |
| `GET  /applications/{id}/category-propositions` | Customer-facing cards |
| `POST /applications/{id}/category-selection` | Explicit/auto select + lock |
| `GET  /applications/{id}/category-selection` | Locked binding audit |

Admin: extend Category CRUD with proposition + disambiguation config; keep Policy Set internal.

---

## 20. State machine (application Category stage)

```
INTENT_CAPTURED
  → ELIGIBILITY_EVALUATED
      → AUTO_SELECTED
      → DISAMBIGUATION_IN_PROGRESS ↔ ELIGIBILITY_EVALUATED
      → PROPOSITION_SELECTION_REQUIRED
      → NO_ELIGIBLE_CATEGORY
  → CATEGORY_LOCKED  (terminal for this stage)
```

From `CATEGORY_LOCKED`: Workflow resolution pinned; W4 may run.

---

## 21. Goldens (design acceptance)

### 21.1 Starter / Bank Starter — PASS (target)

Intent: BORROWER, INDIVIDUAL, BUSINESS_TERM_LOAN, ₹300,000  
Eligible: Starter Loan, Bank Starter (overlap OK)  
Safe question: financial-information route  
Answer: Bank account data → **Bank Starter** selected  
Locks: Bank Starter Category Version + Policy Version + Workflow Version  
Then W4/W5/W6.

### 21.2 Both-valid — PASS (target)

Answer “both” → both remain eligible → **EXPLICIT_PROPOSITION_SELECTION** — no arbitrary pick.

### 21.3 No-question — PASS (target)

Two eligible; no safe question → **EXPLICIT_PROPOSITION_SELECTION_REQUIRED** — not error, not auto manual-review, not arbitrary.

### 21.4 No-match — PASS (target)

`NO_ELIGIBLE_CATEGORY` — recovery paths; **no** generic Category.

### 21.5 One-match — PASS (target)

`AUTO_SINGLE_MATCH` when allowed; no unnecessary question.

---

## 22. Persistence contract (future schema sketch — DO NOT IMPLEMENT NOW)

Illustrative only:

- `loan_applications.selected_customer_category_id`  
- `loan_applications.category_selection_source`  
- `loan_applications.category_selected_at`  
- optional `application_category_eligibility_snapshot` / answer store  
- Category: `customer_facing_json`, `disambiguation_json`, `display_order`, optional programme tags  

Exact migration belongs to implementation phase (W7 programme).

---

## 23. Migration plan (phased)

| Phase | Scope |
|-------|-------|
| **P0 Design lock** | This document |
| **P1 Config** | Admin proposition + SAFE disambiguation attrs; catalogue keys; activation readiness extensions |
| **P2 Runtime eligibility + selection APIs** | No live UW; staging feature flag |
| **P3 Lock → write workflow_id + Policy snapshot** | W1 resolution source `CATEGORY_SELECTION` |
| **P4 Wire W4 after lock** | plan-from-policy on selection event |
| **P5 Entity Type expansion** | LLP / Pvt / Public + COMPANY alias |
| **P6 Programme/channel criteria** | Optional Category filters |
| **Later** | Live routing / UW cutover (separate programme gate) |

---

## 24. P0 / P1 / P2 risks

| Priority | Risk | Mitigation |
|----------|------|------------|
| P0 | Arbitrary Category pick among overlaps | Forbidden; explicit modes only |
| P0 | Leaking credit thresholds in questions | SAFE flag + deny list + review |
| P0 | Second questionnaire engine | Mandate catalogue reuse |
| P1 | W1 DEFAULT fights Category WF | Selection must set `workflow_id` before resolver DEFAULT |
| P1 | Existing apps without Category | Compatibility path; no forced rewrite |
| P1 | Auto-exposing W4 params as questions | Allow-list mapping only |
| P2 | COMPANY→LLP migration breaks seeds | Alias + dual-read period |
| P2 | RM selects ineligible Category | Block until override model |

---

## 25. Exact next implementation sequence

1. **Config model:** SAFE disambiguation attributes + customer-facing proposition metadata on Category (Admin).  
2. **Shared question catalogue** (minimal): evidence-route, programme tags — referenced by Category + intakeConfig.  
3. **Eligibility service** (read-only evaluate) behind feature flag.  
4. **Selection + lock service** writing Category + Policy snapshot + `workflow_id`.  
5. **Progressive disambiguation** using catalogue.  
6. **Explicit proposition UI** (customer + RM).  
7. **Hook W4** on `CATEGORY_LOCKED`.  
8. **Entity Type expansion** (separate change set).  
9. **Only later:** live routing / UW authority cutover.

---

## 26. Explicit non-goals (this task)

No: `selectedCustomerCategoryId` implementation, new DB migration, routing, activation for live UW, Policy/Scorecard execution, W4/W5/W6 code changes, DP-4, new KYC engine, new questionnaire engine, deploy, push.

---

## 27. Document control

| Item | Value |
|------|-------|
| Authoring mode | Audit + design |
| Code changed | NO |
| DB changed | NO |
| Deployed | NO |
| Git pushed | NO |
| Ready for implementation design handoff | YES |
| Ready for live routing | NO |
