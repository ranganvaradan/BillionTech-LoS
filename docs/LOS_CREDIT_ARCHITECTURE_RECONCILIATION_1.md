# LOS Credit Architecture Reconciliation — Audit 1

**Document:** `LOS-CREDIT-ARCHITECTURE-RECONCILIATION-1`  
**Mode:** Audit / design only — **NO implementation**  
**SOURCE SHA:** `6d61c5eeada60b025d639cf2b4cd19da172c051d`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway tip:** V129  
**Date:** 2026-08-14  

**Authority for this audit:** the TARGET business model below supersedes any Phase-1 assumption that  
`Application → Customer Category → Policy Set → Rule Set + Scorecard` is the final product architecture.

---

## AUTHORITATIVE TARGET BUSINESS MODEL (assessment baseline)

```
APPLICATION
  → CUSTOMER / PARTY
  → CUSTOMER ROLE          (BORROWER | ANCHOR)
  → ENTITY TYPE            (INDIVIDUAL | PROPRIETOR | PARTNERSHIP | LLP | PVT LTD | PUBLIC LTD | …)
  → LOAN PRODUCT
  → CUSTOMER CATEGORY      (lender-defined lending proposition)
       matching dimensions define ELIGIBILITY (not unique selection)
       → POLICY            (underwriting / credit-decision policy)
  → KYC PROFILE            (from Entity Type + optional context; separate from Policy)
```

**Locked corrections vs old Phase-1 narrative:**

1. Two Categories **may share identical matching dimensions** and differ only by name + Policy (e.g. STARTER LOAN vs BANK STARTER).
2. Overlap is **eligibility intersection**, not necessarily a configuration defect.
3. Selection of Category is a **separate process** from eligibility matching.
4. Target attach point is **Category → Policy**, not Category → Policy Set → Rule Set.
5. Policy Set is **under review** — must not be assumed to remain user-facing.
6. Historical `underwriting_rule_sets` and Policy Studio Policy must not remain two competing lender-facing credit-policy concepts indefinitely.
7. Policy Studio should eventually be the lender-facing authoring/governance surface for Policy.

---

# PART 1 — CUSTOMER ROLE / ENTITY TYPE

## 1.1 Current representations

| CURRENT TERM | CLASS / ENUM | DB FIELD | API FIELD | MEANING TODAY | TARGET TERM |
|---|---|---|---|---|---|
| Intake Segment | `IntakeSegment` (`BORROWER`, `ANCHOR`) | `loan_applications.intake_segment`, `workflow_configs.intake_segment`, `customer_category.intake_segment` | `intakeSegment` | Onboarding / application path: borrower origination vs anchor (invoice discounting) onboarding | **Customer Role** |
| Borrower Type | `BorrowerType` (`INDIVIDUAL`, `PROPRIETOR`, `PARTNERSHIP`, `COMPANY`) | `loan_applications.borrower_type`, `workflow_configs.borrower_type`, `underwriting_rule_sets.borrower_type`, `underwriting_scorecards.borrower_type`, `customer_category.borrower_type` | `borrowerType` | Legal / constitution category of the party | **Entity Type** (partial) |
| — | *(none)* | — | — | LLP as distinct entity | **Entity Type: LLP** — **MISSING** |
| — | *(none)* | — | — | Private Limited as distinct from Public Limited | **Entity Type: PRIVATE / PUBLIC LIMITED** — **MISSING** (collapsed into `COMPANY`) |
| Customer Role | *(no class)* | — | — | Not a first-class field | Target: Customer Role |
| Applicant Role | *(no class)* | — | — | Co-applicant appears only as intake/workflow `partyRole` metadata in places | Not Customer Role |
| Customer Type | *(no class)* | — | — | Unused | Do not introduce without definition |

**Evidence:**

- `los-core-service/.../enums/BorrowerType.java` — four values only.
- `los-core-service/.../enums/IntakeSegment.java` — `BORROWER` / `ANCHOR`; javadoc ties workflow resolution to `(borrowerType, loanProduct, intakeSegment)`.
- `LoanApplication` stores `borrowerType`, `loanProduct`, `intakeSegment` — **no** `customerCategoryId`, `customerRole`, `entityType`, `policyId`.
- Grep: no `PRIVATE_LIMITED`, `PUBLIC_LIMITED`, `applicantRole`, `customerRole`, `customer_type` as domain enums.
- `LLP` appears only in Policy Studio test scope strings / fixtures, **not** in `BorrowerType`.

## 1.2 Semantic conflation (explicit)

| Concept | Current LOS field | Conflation risk |
|---|---|---|
| **Customer Role** (Borrower vs Anchor) | `intakeSegment` | Named “intake” but used as role for workflow/category matching. Easy to confuse with “how the app was intake’d” vs “party’s commercial role”. |
| **Entity / legal type** | `borrowerType` | Named “borrower” but used for ANCHOR applications too (`COMPANY` + `ANCHOR`). Conflates “borrower” vocabulary with legal constitution. |
| **Applicant / party role within app** (primary, co-applicant, director) | Workflow/intake metadata, tax `SubjectScope` | Not unified with Customer Role or Entity Type. |
| **Customer Category Role dimension** | `customer_category.intake_segment` | Same enum as application intake — correct for BORROWER/ANCHOR, wrong name for target vocabulary. |

**Verdict:** Role and Entity Type are **already separate columns** (good). Naming and enum granularity do **not** match target. No implementation proposed here.

---

# PART 2 — LOAN PRODUCT MODEL

## 2.1 Supported product inventory (canonical UI / V32 catalog)

| CODE | DISPLAY NAME | WORKFLOW | PRODUCT CONFIG | RULE-SET | POLICY STUDIO |
|---|---|---|---|---|---|
| `PERSONAL_LOAN` | Personal Loan | Yes (per borrower type × intake) | Via active `workflow_configs` distinct triples | Yes (`loan_product` match) | Applicability `products[]` (string) |
| `BUSINESS_TERM_LOAN` | Business Term Loan | Yes | Yes | Yes | Yes |
| `BUSINESS_WC_OD` | Business Working Capital — Overdraft | Yes | Yes | Yes | Yes |
| `BUSINESS_WC_INVOICE_DISCOUNTING` | Business Working Capital — Invoice Discounting | Yes (+ ANCHOR path) | Yes | Yes | Yes |
| `TERM_LOAN` | Term Loan | Yes | Yes | Yes | Yes |
| `LOAN_AGAINST_PROPERTY` | Loan Against Property | Yes | Yes | Yes | Yes |
| `LOAN_AGAINST_SECURITIES` | Loan Against Securities | Yes | Yes | Yes | Yes |
| `LOAN_AGAINST_GOLD` | Loan Against Gold | Yes | Yes | Yes | Yes |

**Source of truth for codes:** `ui-service/src/catalog/loanProducts.ts` (`LOAN_PRODUCT_CODES`).  
**Backend:** V32 standardized catalog; Product Config products = distinct `(borrowerType, loanProduct, intakeSegment)` from `workflow_configs` (not a separate product master table).

## 2.2 `BUSINESS_LOAN` specifically

| Question | Answer |
|---|---|
| Does `BUSINESS_LOAN` exist as an independent **current** catalog code? | **NO** |
| Is “Business Loan” currently `BUSINESS_TERM_LOAN`? | **YES** — display label “Business Term Loan”; UI legacy map `BUSINESS_LOAN → BUSINESS_TERM_LOAN` |
| Is “Business Loan” only a business label? | As a **generic product family**, yes — there is no single code `BUSINESS_LOAN` for WC OD / Invoice / Term. Target language “Business Loan” must map to an explicit code (likely `BUSINESS_TERM_LOAN` or a new family code — **decision deferred**). |
| Remaining references | Pre-V32 seeds; UI legacy alias; one `CreditRulesEngine` map key; Equifax taxonomy `BUSINESS_LOAN_*` (different namespace) |

---

# PART 3 — CUSTOMER CATEGORY AUDIT

**Artifacts:** `customer_category` (V128+V129), `CustomerCategoryEntity`, governance APIs, Admin UI (`CustomerCategoriesPage`).

### Answers (mandatory)

| # | Question | Answer |
|---|---|---|
| 1 | Can two categories have identical Role + Entity + Product + Min + Max but different names? | **YES at DB/API** — unique key is `(code, version_no)` + at most one ACTIVE per `code`. Match dimensions are **indexed, not unique**. |
| 2 | Do unique constraints prevent this? | **NO** |
| 3 | Does overlap validation classify this as invalid? | **NO** — emits `OverlapWarning` (severity WARNING) |
| 4 | Does activation block overlap? | **NO** — `activate` stores `overlapAtActivate`; audit text: “overlaps are WARNING only” |
| 5 | Does UI present overlap as error or information? | **WARNING / information** — amber “Overlap” badge; section title “Overlap warnings”; activation modal: “Overlaps are warnings only” |
| 6 | Can Category be explicitly selected by ID on an application? | **NO** — `LoanApplication` has **no** `customerCategoryId` (or equivalent). Live UW does not read categories. |
| 7 | Does current Category assume Policy Set is mandatory? | **YES** — DB `policy_set_id NOT NULL`; entity `nullable=false`; create throws `POLICY_SET_REQUIRED` |
| 8 | What changes are required to support STARTER LOAN + BANK STARTER simultaneously? | See Part 11. Short form: **coexistence of rows is already allowed**; missing pieces are **selection model**, **Policy attach (not Policy Set)**, **product code clarity for BUSINESS_LOAN**, and **runtime wiring** (out of scope for this audit). |

**Schema evidence:** V128 `uq_customer_category_code_version`; match index only on `(borrower_type, loan_product, intake_segment)`.

---

# PART 4 — ELIGIBILITY VS SELECTION

## 4.1 Conceptual split (required)

| Concern | Meaning |
|---|---|
| **ELIGIBILITY** | Which Customer Categories *could* this application qualify for, given Role + Entity Type + Product + Amount (+ dates/status)? |
| **SELECTION** | Which Category is this application *actually applying under*? |

Matching dimensions alone **must not** be assumed to uniquely choose a Category.

## 4.2 Sources present / absent in LOS today

| Potential selection source | Present today? | Notes |
|---|---|---|
| Explicit `CustomerCategoryId` on application | **NO** | Field does not exist |
| Customer-selected lending proposition at intake | **NO** | No Category picker in intake |
| Channel | Partial | Not Category-bound |
| Anchor / programme | Partial | PLP/anchor paths; not Category FK |
| Pre-approved offer | **NO** Category link | |
| RM selection | **NO** Category | |
| CM selection (manual multi-match) | **Designed in Phase-1 docs** | Not implemented for Category routing |
| Unique-match automatic selection | **Designed in Phase-1 docs** | Not implemented; would be **wrong as sole model** under target (identical dimensions allowed) |

## 4.3 Recommended deterministic hierarchy (design only — not implemented)

```
1. If Application.customerCategoryId is set
     → validate eligibility against current ACTIVE Category version
     → if eligible → SELECTED
     → if ineligible → fail closed / force re-select (policy TBD)

2. Else derive ELIGIBLE set = ACTIVE Categories matching Role+Entity+Product+Amount (+effective dates)

3. If |eligible| == 0 → block selection / block create Category (ops)

4. If |eligible| == 1 AND product/config allows auto-select
     → may AUTO_SELECT (audit AUTO_SINGLE_MATCH)

5. If |eligible| > 1
     → do NOT silently prioritize
     → require controlled selection (customer proposition / RM / CM)
     → audit SELECTED_FROM_ELIGIBLE + actor + reason
```

**Current Phase-1 “one/many/none” routing design assumed unique match preference and CM override — still useful for selection UX, but overlap must be reframed as eligibility, not defect.**

---

# PART 5 — KYC ARCHITECTURE

## 5.1 Where KYC currently lives

**KYC CURRENTLY LIVES IN:**

1. **Workflow step configuration** — `workflow_configs.steps` JSON (`step`, `mandatory`, `order`, `provider`), resolved by `(borrowerType, loanProduct, intakeSegment)` via `WorkflowEngine.getActiveWorkflow`.
2. **Per-application execution tables** — `kyc_step_results`, `manual_kyc_reviews`, document `kyc_step_type`.
3. **Orchestration services** — `com.los.core.service.kyc.*` (`KycOrchestrationServiceImpl`, mandatory groups, identity workflow).
4. **VKYC** — `com.los.core.service.vkyc.*` + columns on applications/workflows.
5. **Decision Policy KYC facts / shadow** — `com.los.core.creditintelligence.decisionpolicy.kyc.*`, `ci_kyc_policy_evaluation` (V110).
6. **Product Config / readiness** — workflow steps surfaced; parameter coverage maps KYC steps → facts (`WorkflowParameterProvidesCatalog`).
7. **UI** — KYC queue / application KYC panels (workflow-driven).

## 5.2 KYC Profile object?

**NO reusable KYC Profile entity/table.** Requirements are **workflow-step lists**, not a versioned profile master.

## 5.3 What drives requirements today?

| Driver | Used? |
|---|---|
| Borrower type (Entity Type) | **Indirectly** — selects workflow |
| Product | **Indirectly** — selects workflow |
| Workflow | **YES — primary** |
| Customer Role (intake segment) | **Indirectly** — part of workflow key |
| Rule set | **No** for KYC step list |
| Policy Studio Policy | **Shadow/facts only** — not constitutional KYC roster |

## 5.4 Parties (Individual / Proprietor / Partnership / LLP / Company / Directors / Partners / UBOs / Signatories)

| Subject | Current handling |
|---|---|
| Individual / Proprietor / Partnership / Company | Via `BorrowerType` → different workflow step mixes (e.g. CIN for company) |
| LLP | **Not a first-class Entity Type** |
| Directors / Promoters | Tax `SubjectScope` only; **not** KYC party orchestration |
| Partners | Document slots / partnership deed — not partner KYC entities |
| UBOs / PEP / sanctions / authorised signatories | Explicitly **unsupported** in `KycFactCatalog.unsupportedCapabilityCodes()` |
| Co-applicants | Intake `partyRole: CO_APPLICANT` |

## 5.5 Target recommendation (design)

```
EntityType 1 → N KycProfileVersion   (base constitutional / identity / ownership KYC)
Application / Category / Programme → optional CONTEXTUAL overlays (extra docs, VKYC, etc.)
Policy → may require bank/GST/financial/bureau EVIDENCE for credit decision
         MUST NOT define company UBO/director constitutional KYC merely because it is Policy
```

---

# PART 6 — UNDERWRITING_RULE_SETS DEEP AUDIT

## 6.1 Model / fields

**Table/entity:** `underwriting_rule_sets` / `UnderwritingRuleSet`

| Field | Present? |
|---|---|
| id, name | Yes |
| borrowerType, loanProduct | Yes (match) |
| min/max amount, geography, tenure | Yes (optional filters) |
| priority, active | Yes |
| rulesJson | Yes (hardRules, constraints, optional scorecardRules, static decision) |
| version / effective dates / maker-checker / status lifecycle | **No** |
| scorecard FK | **No** |
| intake / Customer Role | **No** |

## 6.2 Runtime behaviour

**Live path:**

`LoanApplicationFlowService.underwriteApplication`
→ `CreditControlService.resolveEffective` (**facts/metrics only — does not select rule sets**)
→ `UnderwritingRuleEngine.evaluateAll` (**selects & evaluates all ACTIVE matching rule sets**)
→ `ScorecardPolicyEngine.evaluate` (**separate priority pick of one ACTIVE scorecard**)
→ merge (hard-rule precedence) → `UnderwritingEvaluationService.record`

**Multiple rule sets:** all matching ACTIVE sets execute; aggregate REJECT > MANUAL_REVIEW > APPROVE. Priority orders candidates but does **not** select a single set.

**Decision snapshot:** `routing.ruleSetIds`, `routing.scorecardId`, `productionAuthority=LIVE_UW_PATH`, `allowCanonicalAuthority=false`, `policyStudioReference=null`.

## 6.3 Classification

**IS UNDERWRITING_RULE_SET TODAY:**

### **D — legacy lender-facing rule package + C — live runtime executable package**

*(also “partial Policy” in capability terms, but not a Policy document)*

**Support:**

- Flat JSON package, no document version/lifecycle (`V17`, entity).
- Is the **live production authority** for hard rules on the UW path.
- No Policy Studio identity; Product Config / Policy Set only **reference** UUIDs.
- Lacks maker-checker, applicability Customer Role, Policy Test, GACAT-depth authoring.

---

# PART 7 — POLICY STUDIO DEEP AUDIT

## 7.1 What a Policy Studio Policy can represent today

- Policy **document** + versions (`CiPolicyDocument`)
- Extracted clauses, interpretations, **rule candidates** with PolicyDsl AST
- Draft **package** (`POLICY_DSL_V1`)
- **Applicability** (products, borrower types, amounts, effective dates, business status)
- Maker-checker reviews, schedule/retire lifecycle
- Policy Test (non-authoritative)
- GACAT / parameter bindings / readiness gates
- Shadow routing evidence

## 7.2 What it can execute today

- **Shadow / test / dual-run tooling** via `PolicyDslInterpreterV1` / `ShadowPolicyEngine` / `ShadowPolicyRoutingService`
- **Not** the live underwriting decision path

## 7.3 Capability vs rule sets / gaps

| Capability | Studio Policy |
|---|---|
| Hard reject | Yes (FAIL treatment) |
| Manual review | Yes (REFER) |
| Allow / pass | Yes (PASS) |
| Scorecard relationship | **No live scorecard entity link** |
| Applicability | Yes (richer than RS) |
| Effective dates | Yes |
| Runtime live package | **No** (`allowCanonicalAuthority=false`; productionAuthority DISABLED even when business ACTIVE) |

## 7.4 Why not live authority

1. Cutover flag `allowCanonicalAuthority` defaults **false** (“MUST stay false in G0”).
2. Live UW never invokes Studio engine; snapshots stamp `LIVE_UW_PATH` and null Studio reference.
3. `ShadowPolicyRoutingService` explicitly must not change production outcomes.
4. Business ACTIVE ≠ production authority.

## 7.5 Required to replace lender-facing Underwriting Rules (design)

1. Policy gains explicit composition: decision treatments + **scorecard binding** + data requirements.
2. Publish/compile path to a **deterministic runtime package** (may temporarily compile into RS+SC form).
3. Decision snapshots stamp Policy identity as production authority.
4. Cutover: shadow → dual-run → live; then retire Live Rules UI.
5. Customer Category attaches to **Policy version**, not Policy Set of RS UUIDs.

---

# PART 8 — POLICY VS UNDERWRITING RULE SET MATRIX

| Capability | POLICY (Studio) | UNDERWRITING_RULE_SET | Classification |
|---|---|---|---|
| Identity | Document UUID + version | UUID | DIFFERENT_SEMANTICS |
| Version | Document/lineage/clone | None | POLICY_ONLY |
| Lifecycle | DRAFT→REVIEW→APPROVED→SCHEDULED/ACTIVE→RETIRE | `active` boolean | POLICY_ONLY |
| Maker/checker | Yes | No | POLICY_ONLY |
| Applicability | products, borrowerTypes, amounts, dates | borrower+product+amount/tenure/geo | DIFFERENT_SEMANTICS |
| Customer Role | Not first-class | Not on RS | DIFFERENT_SEMANTICS (both weak) |
| Entity Type | applicability borrowerTypes | `borrower_type` | SAME-ish |
| Product | products[] | `loan_product` | SAME-ish |
| Amount | min/max on applicability | min/max amount | SAME-ish |
| Rule AST | PolicyDsl JSON | `rules_json` | DIFFERENT_SEMANTICS |
| Hard reject | FAIL | REJECT | SAME |
| Pass | PASS | APPROVE | SAME |
| Manual review | REFER | MANUAL_REVIEW | SAME |
| Data requirements | GACAT / registry depth | Implicit in rule sources | POLICY_ONLY (depth) |
| Calculations | AND/OR/IF/DIVIDE, measures | Limited; more in scorecard engine | POLICY_ONLY |
| Bureau | Catalogue metrics | hardRules + scorecard params | DIFFERENT_SEMANTICS |
| Banking | Catalogue / readiness | Scorecard/params | DIFFERENT_SEMANTICS |
| GST | Catalogue | Scorecard/params | DIFFERENT_SEMANTICS |
| Financials | Catalogue | Scorecard/params | DIFFERENT_SEMANTICS |
| Scorecard | No entity link | Separate live engine (+ optional inline) | RULESET_ONLY (runtime link) |
| Effective dates | Yes | No | POLICY_ONLY |
| Audit | Package + applicability immutability | Evaluation snapshots of matched IDs | DIFFERENT_SEMANTICS |
| Policy Test | First-class | None | POLICY_ONLY |
| Runtime execution (live) | Shadow only | **Yes — live** | RULESET_ONLY |
| Decision snapshot (live) | Shadow evidence | `LIVE_UW_PATH` | RULESET_ONLY |
| Historical reproduction | Replay/shadow | Snapshot + explanation | DIFFERENT_SEMANTICS |

---

# PART 9 — POLICY SET DEEP AUDIT

## 9.1 What it is

`policy_set` / `PolicySetEntity`: **composition + governance only**.

- Stores: `primaryRuleSetId`, `additionalRuleSetIds` (Phase-1 must be empty), `scorecardId`, code/version/status/audit.
- **Contains no lending rule AST.**

## 9.2 Mandatory answers

| # | Question | Answer |
|---|---|---|
| 1 | Does it contain lending logic? | **NO** |
| 2 | Or only references? | **ONLY REFERENCES** (+ lifecycle metadata) |
| 3 | Can Policy itself contain/reference those same things? | **Eventually yes** — Studio Policy should bind scorecard + executable treatments; today it does not bind scorecard entity |
| 4 | Needed for reuse? | **Convenience only** — reuse can be Policy version reuse or shared compiled package |
| 5 | Compensating for Studio not live? | **YES — primarily** |
| 6 | Would a normal credit user understand Policy Set ≠ Policy? | **NO — high confusion risk** |
| 7 | Could it become internal compiled/runtime package? | **YES** — plausible internal artifact during migration |

## 9.3 Classification

### **KEEP_INTERNAL_ONLY** (near-term) → **MERGE_INTO_POLICY / DEPRECATE** (target)

Do **not** alter yet. Do **not** treat as permanent user-facing product concept.

Admin UI currently exposes Policy Sets as first-class — this is **debt relative to target**, not confirmation of permanence.

---

# PART 10 — SCORECARD PLACEMENT

## 10.1 Today

```
Live UW: ScorecardPolicyEngine picks one ACTIVE scorecard by borrower+product+priority
Policy Set: stores scorecardId (governance / activation readiness only)
Product Config: optional selected scorecardId (readiness convergence; does not change runtime)
Rule set: optional inline scorecardRules (not entity FK)
Policy Studio: no scorecard entity link
Customer Category: only via Policy Set
```

## 10.2 Target recommendation

**Lender-facing:**

```
Customer Category → Policy (version)
                     └── includes / binds Scorecard (or explicit “no scorecard” mode)
```

**Runtime may still execute** a compiled scorecard package, but users should not navigate Category → Policy Set → Rule Set → Scorecard.

**Preferred relationship:** `Policy N → 0..1 Scorecard` (composition inside Policy), not a sibling user-facing Policy Set bag.

---

# PART 11 — STARTER LOAN / BANK STARTER GOLDEN

### Scenario

| Category | Role | Entity | Product | Amount | Policy intent |
|---|---|---|---|---|---|
| STARTER LOAN | BORROWER | INDIVIDUAL | BUSINESS_LOAN* | 20k–500k | Income-based |
| BANK STARTER | BORROWER | INDIVIDUAL | BUSINESS_LOAN* | 20k–500k | Bank-statement-based |

\*Canonical code today would be `BUSINESS_TERM_LOAN` unless a new code is introduced.

Application: BORROWER + INDIVIDUAL + BUSINESS_TERM_LOAN + 300k → **both eligible**; one must be **selected**.

| Check | Result |
|---|---|
| CURRENT MODEL SUPPORTS COEXISTENCE (two Category rows) | **YES** |
| CURRENT MATCHER SUPPORTS THIS | **N/A / NO** — live Category matcher **not implemented**; overlap detector would treat as WARNING eligibility intersection (correct direction) |
| CURRENT OVERLAP MODEL SUPPORTS THIS | **YES** (WARNING, not invalid) |
| CURRENT DB CONSTRAINTS SUPPORT THIS | **YES** |
| CURRENT UI SUPPORTS THIS | **PARTIAL** — allows create; shows WARNING; **no** application selection UX; **forces Policy Set**, not Policy |

### REQUIRED CHANGES (design list — not implementing)

1. Reframe overlap UX copy: “eligible for multiple propositions” not “configuration conflict”.
2. Add Application.`customerCategoryId` (or equivalent) + eligibility validation.
3. Selection UX at intake / RM / CM for multi-eligible.
4. Replace mandatory Policy Set with **Policy** reference (or transitional dual-write).
5. Clarify product code for “Business Loan”.
6. Ensure Policies differ (income vs bank) as first-class Policies, not two Policy Sets of RS UUIDs as the lender mental model.
7. Shared KYC Profile by Entity Type (Individual) — independent of which Category selected.

---

# PART 12 — KYC GOLDEN

**Case:** PRIVATE LIMITED COMPANY borrower, Working Capital product, Category = Working Capital Starter.

| Requirement | Current architecture |
|---|---|
| KYC resolves from Entity Type → KYC Profile | **NO** — resolves from **workflow** for `(COMPANY*, product, BORROWER)`. No Pvt Ltd subtype; no KYC Profile object |
| Policy asks bank/GST/financials/bureau | **Possible** via Studio metrics / live scorecard+rules — but **not** cleanly separated as “Policy evidence vs KYC Profile” |
| Policy must NOT define UBO/director constitutional KYC | **Partially met by accident** — UBO/director KYC largely **unsupported**; CIN_MCA21 is entity verification, not director roster KYC |
| Separation of concerns | **WEAK** — workflow mixes identity KYC steps with product path; Policy/Studio is parallel |

**Meets target golden?** **NO** (missing Entity Type granularity + KYC Profile object + clean Policy boundary).

---

# PART 13 — TARGET DOMAIN MODEL

```
Party (Customer)
  └── plays CustomerRole (BORROWER | ANCHOR) on Application
  └── has EntityType (INDIVIDUAL | PROPRIETOR | PARTNERSHIP | LLP | PRIVATE_LIMITED | PUBLIC_LIMITED | …)

EntityType 1 ── N KycProfileVersion
  (base constitutional / identity / ownership KYC)

LoanProduct 1 ── N Application

CustomerCategory
  ├── name (lending proposition)
  ├── customerRole
  ├── entityType
  ├── loanProduct
  ├── minAmount / maxAmount          // ELIGIBILITY dimensions (non-unique)
  ├── N ── 1 PolicyVersion           // SELECTED policy for this proposition
  └── status / version / governance

PolicyVersion
  ├── authored in Policy Studio
  ├── lifecycle + maker/checker
  ├── applicability (may mirror or constrain Category)
  ├── decision logic (reject / refer / pass)
  ├── data requirements (credit evidence)
  ├── 0..1 ScorecardVersion binding
  └── publishes → RuntimePackage (may initially compile to RS+SC form)

Application
  ├── party, customerRole, entityType, product, amount
  ├── customerCategoryId (SELECTED) — validated ∈ eligible
  ├── kycProfileVersionId (resolved primarily from entityType + overlays)
  └── DecisionSnapshot (policy id, runtime package ids, facts, outcome)

DEPRECATED / INTERNAL (migration):
  PolicySet          → internal compiled composition bag only, then remove
  underwriting_rule_sets (lender UI) → runtime package / retired UI
```

**Cardinalities (recommended):**

| Relation | Cardinality |
|---|---|
| EntityType → KycProfileVersion | 1 → N |
| CustomerCategory → PolicyVersion | N → 1 (active binding); history via Category versioning |
| PolicyVersion → ScorecardVersion | N → 0..1 |
| Application → CustomerCategory | N → 0..1 (required once Category routing live) |
| Application → PolicyVersion | N → 1 via selected Category (denormalize for snapshot) |

**Do not retain Policy Set as user-facing** merely because it exists.

---

# PART 14 — SAFE MIGRATION PLAN

| Phase | Intent | Notes |
|---|---|---|
| **PHASE 0** | Freeze new **live** Category routing; freeze Policy Set as permanent product story | Admin UI may remain for DRAFT governance only |
| **PHASE 1** | Terminology / domain correction | Rename concepts in docs/API DTOs gradually: IntakeSegment→CustomerRole display; BorrowerType→EntityType; expand entity enum later |
| **PHASE 2** | KYC Profile | Introduce versioned KYC Profile by Entity Type; workflows become projections/overlays |
| **PHASE 3** | Policy gains composition | Scorecard binding, Customer Role applicability, publish contract |
| **PHASE 4** | Customer Category → Policy | Make Policy FK first-class; Policy Set optional/internal |
| **PHASE 5** | Policy publishes/compiles to current live runtime representation if needed | Bridge: Policy → (RuleSet+Scorecard package) without dual lender UX |
| **PHASE 6** | Shadow: legacy RuleSet path vs Policy path | Same apps; compare decisions |
| **PHASE 7** | Policy becomes live authority | Snapshots stamp Policy; cutover flags |
| **PHASE 8** | Retire lender-facing Underwriting Rules UI | Keep tables as runtime packages if required |
| **PHASE 9** | Hide/remove Policy Set from user surfaces | Delete after no Category depends on it |

Refine after PHASE 1 vocabulary lock and Policy publish design.

---

# PART 15 — CURRENT DRAFT DATA

| Object | Count | Recommendation |
|---|---|---|
| 15 DRAFT Customer Categories | 15 | **KEEP** for admin UAT / learning; **TRANSFORM** later (Policy FK, vocabulary, selection). Do **not** DELETE_RECREATE until Policy attach exists. |
| 12 DRAFT Policy Sets | 12 | **KEEP** temporarily as **migration bridge**; plan **TRANSFORM → internal** then **DELETE_RECREATE or drop** once Category→Policy works. Do not invest in Policy Set as long-term UX. |

0 ACTIVE — correct; do not activate under old mental model.

---

# PART 16 — CLIENT PRIORITY −1 AUTHORITY ANOMALY

**What happened (Day-1 seed apply):** Client DB lacked some staging UUIDs referenced by approved Day-1 Policy Set matrix. Staging UUID-equivalent ACTIVE rule sets/scorecards were **inserted on client at priority −1** so FKs validate while **higher-priority live winners remain unchanged**. DBs were not copied wholesale. Categories/Policy Sets stayed DRAFT.

**Migration relevance:**

- Client has **extra ACTIVE** RS/SC rows that exist only for FK compatibility.
- When Category→Policy goes live or IDs are unified, these −1 rows must be **retired or remapped**.
- Not product design — **environment remediation debt**.
- Does **not** justify keeping Policy Set forever; does justify careful ID strategy in PHASE 4–5.

**Do not fix in this audit.**

---

# PART 17 — TERMINOLOGY

| CURRENT | TARGET | RATIONALE |
|---|---|---|
| Borrower Type | **Entity Type** | Legal constitution; used for anchors too |
| Customer Type | *(avoid / define)* | Ambiguous; do not use without definition |
| Intake Segment | **Customer Role** (BORROWER/ANCHOR) | Commercial role on the application |
| Customer Role | **Customer Role** | Keep; map from intakeSegment |
| Entity Type | **Entity Type** | Expand beyond COMPANY collapse |
| Policy (Studio) | **Policy** | Lender-facing credit decision policy |
| Underwriting Rules / rule sets | **Runtime rule package** (internal) / retire lender name “Underwriting Rules” | Stop competing with Policy |
| Policy Set | **Deprecated user-facing** / optional **Runtime composition package** | Confuses vs Policy |
| KYC Rules | Prefer **KYC Profile requirements** / workflow steps | “Rules” overloads Policy |
| KYC Profile | **KYC Profile** | Introduce as first-class |

---

# PART 18 — ARCHITECTURE GAP SEVERITY

## P0 — wrong product / competing authorities / incorrect lending decision architecture

1. Treating **Policy Set → Rule Set** as the permanent Category attach point (vs **Category → Policy**).
2. Building **live Category routing** that assumes **unique dimension match** and treats identical-dimension Categories as defects.
3. Leaving **two lender-facing credit-policy concepts** (Live Underwriting Rules vs Policy Studio Policy) without a publish/cutover plan.
4. Activating Day-1 Categories under the old Policy Set mental model as if that were the finished architecture.

## P1 — material migration / design debt

1. No Application.`customerCategoryId` / eligibility-vs-selection model.
2. No KYC Profile; KYC driven only by workflow triples.
3. Entity Type enum missing LLP / Pvt / Public split (`COMPANY` collapse).
4. Policy Studio lacks live scorecard binding + production authority path.
5. Client priority −1 UUID copies.
6. Admin UI elevates Policy Set as peer of Categories.

## P2 — terminology / cleanup

1. `borrowerType` / `intakeSegment` naming vs Entity Type / Customer Role.
2. `BUSINESS_LOAN` legacy alias vs `BUSINESS_TERM_LOAN`.
3. Overlap UX copy still sounds like “warning/config issue” rather than “multiple eligible propositions”.
4. Docs that still teach Category → Policy Set → Rule Set as the end state.

---

## DOCUMENT CONTROL

- **CODE CHANGED:** NO (this audit only adds this document when committed later — implementation tasks must not silently change product).
- **For this task instruction:** produce the document; do not deploy/push/migrate.

---

*End of LOS_CREDIT_ARCHITECTURE_RECONCILIATION_1*
