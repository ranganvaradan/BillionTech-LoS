# CUSTOMER CATEGORY PHASE-1 DESIGN

**Document:** CUSTOMER-CATEGORY-PHASE1-DESIGN-1  
**Mode:** Design / one-pager only (no implementation)  
**Baseline SHA:** `c20429d31fc19b41f015761da54bc2e0d6b8b28e`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway tip (current):** V127  
**Bureau / GACAT:** FROZEN (CERT-4 CERTIFIED, P0=0)

---

## 1. Objective

Introduce **Customer Category → Policy Set** as the **live underwriting routing** layer:

> Which Policy Set should govern this application?

Then continue with **existing** live authorities:

```
APPLICATION
  → APPLICATION ATTRIBUTES
  → CUSTOMER CATEGORY MATCH
  → POLICY SET
  → EXISTING LIVE UNDERWRITING
       → RULE SET(S)
       → SCORECARD
       → DECISION
```

Customer Category is a **routing construct only**. It is **not** a new rule engine, scorecard engine, GACAT, Policy Studio, or Bureau layer.

**One / Many / None (locked product behaviour):**

| Match count | Behaviour |
|-------------|-----------|
| **1** | Auto-select category + Policy Set; audit as `AUTO_SINGLE_MATCH` |
| **>1** | Block UW; CM chooses; mandatory reason; `MANUAL_MULTI_MATCH` |
| **0** | Block UW; CM assigns eligible category; mandatory reason; `MANUAL_NO_MATCH` |

No silent default category unless business later defines one explicitly.

---

## 2. Domain model (minimum)

Prefer **4 tables**. Do **not** introduce a separate many-to-many link table in Phase 1 (cardinality = 1 Policy Set per Category).

### 2.1 `customer_category`

| Aspect | Design |
|--------|--------|
| **PURPOSE** | Named routing definition: match criteria + pointer to one Policy Set |
| **PRIMARY KEY** | `id` UUID |
| **BUSINESS KEY** | `code` (stable, unique, immutable after first activate) — **not** display name |
| **CORE FIELDS** | `code`, `name`, `description`, criteria columns (see §3), `policy_set_id`, `status`, `version_no`, `seed_source_rule_set_id` (nullable) |
| **STATUS** | `DRAFT` → `ACTIVE` → `RETIRED` |
| **VERSIONING** | Integer `version_no`; new row or new version record on material edit of ACTIVE (see §8) |
| **AUDIT** | `created_at/by`, `updated_at/by`, `activated_at/by`, `retired_at/by` |
| **RELATIONSHIPS** | FK → `policy_set.id` (required when ACTIVE) |

### 2.2 `policy_set`

| Aspect | Design |
|--------|--------|
| **PURPOSE** | Named bundle of **references** to existing live UW authorities (no duplicated rules) |
| **PRIMARY KEY** | `id` UUID |
| **BUSINESS KEY** | `code` (stable unique) |
| **CORE FIELDS** | `code`, `name`, `status`, `version_no`, `primary_rule_set_id`, `additional_rule_set_ids` JSONB (optional empty), `scorecard_id` (nullable), `notes` |
| **STATUS** | `DRAFT` → `ACTIVE` → `RETIRED` |
| **VERSIONING** | Same pattern as category |
| **AUDIT** | created/updated/activated/retired |
| **RELATIONSHIPS** | FK → `underwriting_rule_sets.id`; optional FK → `underwriting_scorecards.id` |

**Phase 1 content of Policy Set:**

- ≥1 **existing ACTIVE** `underwriting_rule_sets` row (primary required)
- Optional **one** ACTIVE scorecard reference where product uses scorecards
- **Out of scope Phase 1:** required-data profile, workflow config, LMS mapping, PLP flags, CAM templates (may be added later as optional FKs)

### 2.3 `application_policy_routing` (current binding)

| Aspect | Design |
|--------|--------|
| **PURPOSE** | Current routing resolution for an application (single current row per app) |
| **PRIMARY KEY** | `id` UUID |
| **BUSINESS KEY** | `application_id` UNIQUE |
| **CORE FIELDS** | outcome status, selected `customer_category_id` + `version_no`, `policy_set_id` + `version_no`, `selection_source`, match evidence JSON, candidates JSON, reason, actors/timestamps, last evaluation ref |
| **STATUS** | See §10 failure / outcome enum |
| **VERSIONING** | Binding points at **immutable version numbers** of category/policy set used |
| **AUDIT** | Embedded + event table |
| **RELATIONSHIPS** | FK → `loan_applications`, category, policy_set |

### 2.4 `application_policy_routing_event` (history)

| Aspect | Design |
|--------|--------|
| **PURPOSE** | Append-only audit of resolve / select / change / re-evaluate |
| **PRIMARY KEY** | `id` UUID |
| **CORE FIELDS** | `application_id`, `event_type`, before/after snapshots, reason, actor, `at`, `evaluation_id` nullable |
| **RELATIONSHIPS** | FK → application; no update/delete in app layer |

**Rejected for Phase 1:** separate `customer_category_policy_set` link table; dimension-weight registries; AI/fuzzy routers.

---

## 3. Matching dimensions (Phase 1)

Grounded in **actual** `LoanApplication` / live rule-set fields today:

Live UW already selects by `borrowerType` + `loanProduct` (+ amount/tenure/geo filters). Workflow also uses `intakeSegment`. Soft/metrics-only fields are **not** Phase-1 match keys.

| Dimension | Phase 1 | Source field | NULL on application | Wildcard | Match type |
|-----------|---------|--------------|---------------------|----------|------------|
| Borrower type | **YES** | `LoanApplication.borrowerType` (`INDIVIDUAL` \| `PROPRIETOR` \| `PARTNERSHIP` \| `COMPANY`) | Criterion fails unless category uses `ANY` | **ANY** | EXACT / ANY |
| Loan product | **YES** | `LoanApplication.loanProduct` (string, e.g. term/invoice codes as stored) | Criterion fails unless `ANY` | **ANY** | EXACT / ANY |
| Intake segment | **YES** | `LoanApplication.intakeSegment` (`BORROWER` \| `ANCHOR`) | Criterion fails unless `ANY` | **ANY** | EXACT / ANY |
| Requested amount | **YES** | `LoanApplication.requestedAmount` | If amount null → category with any amount bound **does not match**; unbounded category (both null) may match | Amount: null bound = unbounded | Inclusive RANGE |
| Tenure | **NO** | `tenureMonths` | — | — | Live rule sets may still filter tenure **inside** Policy Set evaluation |
| Geography | **NO** | personalInfo / rule-set geo JSON | — | — | Deferred; remains inside rule/scorecard filters |
| Secured/unsecured | **NO** | no first-class field | — | — | Open decision if product later adds field |
| Industry | **NO** | soft / scorecard metric | — | — | Not routing |
| Business vintage | **NO** | soft | — | — | Not routing |
| Existing/new customer | **NO** | soft / NTC metric | — | — | Not routing |
| Programme / scheme | **NO** | `subProgramId` | — | — | Deferred (PLP/programme separate) |
| Anchor/PLP identity | **NO** | `plp*` | — | — | ANCHOR apps already special-cased in flow; segment covers BORROWER/ANCHOR |

**Smallest useful Phase-1 set:** `borrowerType` + `loanProduct` + `intakeSegment` + amount range.

---

## 4. Amount range semantics (locked)

Currency: **INR** (same as `requestedAmount` / rule-set amounts today). No FX in Phase 1.

| Bound | Meaning |
|-------|---------|
| `min_amount = null` | No lower bound |
| `max_amount = null` | No upper bound |
| Both null | Amount criterion always true **if** application has any amount **or** amount is null (see below) |
| Both present | Inclusive: `min_amount <= requestedAmount <= max_amount` |

**Inclusive / exclusive:**  
- Lower: **inclusive**  
- Upper: **inclusive**  
Examples: min=50000 max=50000000 → ₹50,000 and ₹5,00,00,000 both match.

**Invalid ranges (activation BLOCKING):**  
- `min_amount` and `max_amount` both non-null and `min_amount > max_amount`  
- Negative amounts  

**Application amount null:**  
- If category has **any** non-null amount bound → **no match** for that category  
- If category has **both** amount bounds null → amount criterion passes  

**Do not** treat null application amount as zero.

---

## 5. ANY / wildcard semantics (locked)

**For enum/string dimensions (`borrower_type`, `loan_product`, `intake_segment`):**

| Storage | Meaning |
|---------|---------|
| Literal **`ANY`** | Criterion always matches when application value is **non-null** |
| Concrete enum/string | Exact equality (case-normalized to existing enum/name conventions) |

**Why not NULL for ANY:**  
Amount already uses **NULL = unbounded**. Reusing NULL for “ANY borrower type” conflates “unspecified criterion” with “unbounded amount” and with DB null. Explicit `ANY` is unambiguous in admin UX and seeds.

**Application value null vs ANY:**  
- Category = `ANY` and application attribute **null** → criterion **fails** (unknown app value ≠ wildcard).  
- Category = concrete value and application null → **fails**.

**Open decision (document only):** whether `loan_product` uses a closed enum table or free string matching existing `loanProduct` values — recommend **string exact match** to current live storage, validated against known product codes at activation (WARNING if unknown).

---

## 6. Match algorithm (deterministic)

For each **ACTIVE** Customer Category (current definition version):

```
matches(app, category) iff
  every configured criterion matches
```

- No fuzzy match, no LLM, no weights, no hidden priority.
- Overlaps are allowed; they produce **MULTIPLE MATCH**.
- Return candidates with evidence: `categoryId`, `code`, `name`, `versionNo`, matched criteria vs application values, `policySetId`, `policySetVersionNo`, `status`.

**Ordering of candidates for CM UI:** stable sort by `code` ascending (display only; not priority).

---

## 7. One / Many / None runtime state machine

```
resolve(application)
  → candidates = match(ACTIVE categories)
  → |c|=1 → CATEGORY_RESOLVED (AUTO_SINGLE_MATCH) → bind → continue UW
  → |c|>1 → CATEGORY_SELECTION_REQUIRED → wait CM select
  → |c|=0 → CATEGORY_ASSIGNMENT_REQUIRED → wait CM assign
```

| Case | UW engines | Persistence |
|------|------------|-------------|
| Auto single | Proceed with Policy Set refs | Bind + evidence + `selection_source=AUTO_SINGLE_MATCH` |
| Multi | **Do not** choose; do not run rule/scorecard selection for UW | Outcome + full candidate list |
| None | **Do not** invent default | Outcome; CM picks from **ACTIVE** categories (eligible list = all ACTIVE, or filtered by product if admin configures later — **Phase 1: all ACTIVE**) |

Typed outcomes (API): see §10.

---

## 8. Manual choice / override

| Topic | Phase-1 rule |
|-------|----------------|
| **Who selects (multi / none)** | `CREDIT_MANAGER` (also `ADMIN` / `ADMINISTRATOR` / `PLATFORM_ADMIN` for ops) |
| **Who configures categories** | `ADMIN` / `ADMINISTRATOR` / `PLATFORM_ADMIN`; optional `RISK_MANAGER` read/approve if existing matrix allows |
| **When** | **Before** underwriting evaluation that depends on Policy Set (gate inside `underwriteApplication` before `evaluateAll` / scorecard) |
| **After UW already evaluated** | Changing category/Policy Set **must not** mutate historical decision. Create new routing event + **new underwriting evaluation / decision record**; prior evaluation remains immutable |
| **CAM / post-decision** | Phase 1: **no** silent mid-CAM rewrite; re-route only via explicit “re-underwrite” path with new evaluation id |

Mandatory `reason` (non-blank, min length e.g. 10) for `MANUAL_MULTI_MATCH` and `MANUAL_NO_MATCH`.

---

## 9. Policy Set model

- References **existing** live `underwriting_rule_sets` / `underwriting_scorecards`.
- **Does not** copy `rules_json` / scorecard JSON.
- **Category → Policy Set cardinality (Phase 1): exactly one** (`customer_category.policy_set_id` required when ACTIVE).
- Multiple rule sets under one Policy Set: primary + optional additional IDs; live evaluation uses **those IDs only** (replaces today’s “all matching borrower+product rule sets”).
- Scorecard: at most one; if null, scorecard engine skipped or falls back only if product explicitly requires — **recommend: null means no scorecard for this Policy Set** (no silent global fallback).

**Open decision:** when Policy Set lists multiple rule sets, retain today’s aggregate severity (REJECT > MANUAL_REVIEW > APPROVE) — recommend **YES** (no new merge math).

---

## 10. Failure / outcome semantics

| Code | HTTP-ish | Meaning |
|------|----------|---------|
| `CATEGORY_RESOLVED` | 200 | Bound; UW may proceed |
| `CATEGORY_SELECTION_REQUIRED` | 409 / business | Multi match; waiting CM |
| `CATEGORY_ASSIGNMENT_REQUIRED` | 409 / business | No match; waiting CM |
| `CATEGORY_CONFIGURATION_INVALID` | 422 | Active category/policy set fails validator |
| `POLICY_SET_NOT_READY` | 422 | Linked rule set/scorecard not ACTIVE / missing |

Do **not** use generic 500 for these expected states.

---

## 11. Lifecycle & versioning

### Customer Category / Policy Set

`DRAFT` → `ACTIVE` → `RETIRED`

| Rule | Recommendation |
|------|----------------|
| Create | Admin; starts `DRAFT` |
| Activate | Admin; pass activation validator; optional maker-checker **WARNING-level** for Phase 1 (not blocking unless org requires — **open decision**) |
| Edit ACTIVE criteria or Policy Set link | **Do not mutate in place**. Create new `version_no`, retire old from matching (old row `RETIRED` or superseded flag), activate new — **or** require retire + new category. Prefer **version bump** with same `code`. |
| Retirement | Stop matching; existing `application_policy_routing` rows keep prior version ids |

### Policy Set activation

Must reference only **ACTIVE** live rule sets / scorecards.  
**Policy Studio APPROVED ≠ live Policy Set.** Explicit linkage/governance only.

---

## 12. Audit / reproducibility

Every routing decision stores **immutable** references:

- `applicationId`
- `customerCategoryId` + `customerCategoryVersionNo` (+ optional snapshot JSON of criteria)
- `policySetId` + `policySetVersionNo` (+ snapshot of linked rule_set_id(s), scorecard_id)
- `selectionSource` (`AUTO_SINGLE_MATCH` \| `MANUAL_MULTI_MATCH` \| `MANUAL_NO_MATCH`)
- candidate ids + match evidence
- `selectedBy` / `selectedAt` / reason
- previous selection (on change)
- `underwriting_evaluation_id` / decision snapshot id when UW runs

**Historical explanation never re-resolves against current ACTIVE categories.**  
Decision snapshots should stamp category/policy set codes + versions (extend existing `DecisionConfigurationSnapshotBuilder` pattern; `allowCanonicalAuthority` remains false).

---

## 13. Seed strategy (from `underwriting_rule_sets`)

For each **active** live rule set, create **DRAFT** Policy Set + **DRAFT** Category:

| Inferred | From |
|----------|------|
| borrower type | `underwriting_rule_sets.borrower_type` |
| product | `loan_product` |
| amount | `min_amount` / `max_amount` (null preserved) |
| intake segment | **Do not invent** if absent → store `ANY` |

**Do not invent:** industry, secured, programme, channel beyond `ANY`.

| Seed field | Value |
|------------|-------|
| Status | `DRAFT` / review flag `REVIEW_REQUIRED` |
| Auto-ACTIVE | **NO** |
| Naming (display) | e.g. `{BORROWER_TYPE} · {PRODUCT} · {SEGMENT} · {amount band}` |
| Identity | generated `code` e.g. `CC_SEED_{ruleSetId short}` — **stable UUID/code**, not name |
| Link | Policy Set primary_rule_set_id = that rule set; scorecard: attach only if unique active scorecard shares same borrower+product (**else leave null + WARNING**) |

Seed output is reviewable in admin UI before activation.

---

## 14. Overlap handling

Overlaps are **first-class** (multi-match).

| Level | When |
|-------|------|
| **INFO** | Partial overlap on amount with different products |
| **WARNING** (default on activate) | Two ACTIVE categories can both match same concrete app vector (borrower×product×segment×amount sample or symbolic overlap) |
| **BLOCKING** | Identical criteria + same Policy Set (duplicate); or invalid range; or broken FKs |

**Phase 1: do not BLOCK mere overlap.** Surface WARNING in admin. No priority scoring.

---

## 15. Runtime insertion point

**Current chain:**

`LoanApplicationFlowService.underwriteApplication`  
→ `CreditControlService.resolveEffective`  
→ `UnderwritingRuleEngine.evaluateAll` (select by borrower+product+filters)  
→ `ScorecardPolicyEngine.evaluate` (first match)

**Insert:**

```
resolveEffective(app, …)          // facts unchanged; Bureau/GACAT frozen
CustomerCategoryRoutingService.resolve(app)
  → if not CATEGORY_RESOLVED: stop with typed outcome (CM UI)
  → else PolicySetRefs
UnderwritingRuleEngine.evaluateAll(app, ctx, outcome, policySetRefs)
ScorecardPolicyEngine.evaluate(app, ctx, outcome, policySetRefs)
```

**Narrow service boundary:** `CustomerCategoryRoutingService` only.  
**Do not** put category logic in `BureauMetricService`, `CanonicalBureauContextBridge`, PolicyDsl, or scorecard scoring math.

**Rollback / dual authority:** prefer feature flag  
`los.underwriting.customer-category-routing-enabled` (default **false** until activated)  
When false → retain today’s borrower+product selection. When true → Policy Set refs are the **only** selection authority (no permanent dual silent merge).

---

## 16. Policy Studio relationship

| Studio | Customer Category |
|--------|-------------------|
| Authoring / governance | Live applicability routing |
| APPROVED ≠ live | Explicit Policy Set link to **live** rule sets/scorecards |
| `allowCanonicalAuthority=false` unchanged | Does not enable studio as UW authority |

Phase 1: **YES** — existing live rule sets remain the linked runtime authority.

---

## 17. UI concept (design only)

### Admin — Customer Categories

Columns: Name, Status, Borrower Type, Product, Amount Range, Channel/Segment, Policy Set, Overlap warning, Updated  

Actions: Open, Copy, Activate, Retire  

Detail tabs: Definition, Policy Set, Match Preview, Audit  

### Credit Manager — Multi / None

Candidate cards: category, policy set, why matched (or “manual assign”), rule set/scorecard summary, reason textbox, Confirm  

No implementation in this task.

---

## 18. API proposal (minimal)

Align with existing `/api/...` conventions; JWT RBAC.

**Configuration**

- `GET /api/customer-categories`
- `POST /api/customer-categories`
- `GET /api/customer-categories/{id}`
- `PUT /api/customer-categories/{id}` (DRAFT / new version rules)
- `POST /api/customer-categories/{id}/activate`
- `POST /api/customer-categories/{id}/retire`
- `GET|POST /api/policy-sets` (+ `{id}`, activate, retire) as needed

**Application routing**

- `POST /api/applications/{id}/customer-category/resolve`
- `GET /api/applications/{id}/customer-category/candidates`
- `POST /api/applications/{id}/customer-category/select` (body: categoryId, reason)
- `GET /api/applications/{id}/customer-category/history`

No client-supplied identity; use verified JWT.

---

## 19. DB schema proposal (no migration yet)

### `customer_category`

| Column | Type | Null | Notes |
|--------|------|------|-------|
| id | UUID | N | PK |
| code | VARCHAR(64) | N | UNIQUE |
| name | VARCHAR(200) | N | |
| description | TEXT | Y | |
| status | VARCHAR(20) | N | DRAFT/ACTIVE/RETIRED |
| version_no | INT | N | |
| borrower_type | VARCHAR(32) | N | enum or `ANY` |
| loan_product | VARCHAR(64) | N | or `ANY` |
| intake_segment | VARCHAR(32) | N | or `ANY` |
| min_amount | NUMERIC(18,2) | Y | null = no lower bound |
| max_amount | NUMERIC(18,2) | Y | null = no upper bound |
| policy_set_id | UUID | Y | required if ACTIVE |
| seed_source_rule_set_id | UUID | Y | |
| criteria_snapshot | JSONB | Y | optional freeze aid |
| created_at/by, updated_at/by, activated_at/by, retired_at/by | | | |

Indexes: `(status)`, `(borrower_type, loan_product, intake_segment)`, UNIQUE `(code, version_no)` or unique code with version rows — **recommend unique `code` for current head + history table OR unique (code, version_no)**. Simplest: **one row per version**; unique `(code, version_no)`; partial unique index on `code` WHERE status=ACTIVE.

### `policy_set`

| Column | Type | Null |
|--------|------|------|
| id | UUID | N |
| code | VARCHAR(64) | N UNIQUE with version |
| name | VARCHAR(200) | N |
| status | VARCHAR(20) | N |
| version_no | INT | N |
| primary_rule_set_id | UUID | N FK |
| additional_rule_set_ids | JSONB | N default `[]` |
| scorecard_id | UUID | Y FK |
| created/updated/activated/retired | | |

### `application_policy_routing`

| Column | Type | Null |
|--------|------|------|
| id | UUID | N |
| application_id | UUID | N UNIQUE FK |
| outcome | VARCHAR(40) | N |
| selection_source | VARCHAR(40) | Y |
| customer_category_id | UUID | Y |
| customer_category_version_no | INT | Y |
| policy_set_id | UUID | Y |
| policy_set_version_no | INT | Y |
| match_evidence | JSONB | Y |
| candidates | JSONB | Y |
| reason | TEXT | Y |
| selected_by | VARCHAR(100) | Y |
| selected_at | TIMESTAMPTZ | Y |
| last_evaluation_id | UUID | Y |
| updated_at | TIMESTAMPTZ | N |

### `application_policy_routing_event`

Append-only: id, application_id, event_type, payload JSONB, actor, at.

**Tenancy:** single-tenant deployment; **no** new tenant_id column required for this feature.

---

## 20. Activation validator

**BLOCKING**

- Valid status transition
- Amount range valid
- `policy_set_id` present; Policy Set ACTIVE
- Primary rule set exists and `active=true`
- Additional rule set ids exist and active
- Scorecard exists and ACTIVE/eligible if referenced
- No DRAFT runtime authorities
- Enum/`ANY` values legal
- Unique ACTIVE `(code)` constraint

**WARNING**

- Overlap with other ACTIVE categories
- Seeded scorecard missing
- Unknown `loan_product` string vs product catalogue

**INFO**

- Tenure/geo still applied inside linked rule sets

---

## 21. Example scenarios

| # | Application | Expected |
|---|-------------|----------|
| 1 | COMPANY / TERM_LOAN / BORROWER / ₹2,50,000 with one matching category | Auto bind; `AUTO_SINGLE_MATCH` |
| 2 | COMPANY / TERM / ₹25,00,000 matches broad ANY-amount and ₹50K–₹5CR | `CATEGORY_SELECTION_REQUIRED` |
| 3 | LLP / INVOICE_DISCOUNTING / no ACTIVE category | `CATEGORY_ASSIGNMENT_REQUIRED` (**note:** `LLP` not in current `BorrowerType` enum — treat as product data gap; Phase 1 enums follow existing `BorrowerType`) |
| 4 | Amount = min bound | Match (inclusive) |
| 5 | Amount = max bound | Match (inclusive) |
| 6 | Category borrower_type=`ANY`, app COMPANY | Match on borrower criterion |
| 7 | Category requires COMPANY, app borrowerType null | No match for that category |

---

## 22. RBAC

| Action | Roles |
|--------|-------|
| Configure / activate / retire | `ADMIN`, `ADMINISTRATOR`, `PLATFORM_ADMIN` |
| Multi/none select | `CREDIT_MANAGER` (+ admin roles) |
| Read / audit | above + `RISK_MANAGER` if present in JWT matrix |

Trust JWT only; never client-supplied user id.

---

## 23. Migration / rollback

- Next Flyway version = **repository tip + 1** at implementation time (do **not** reserve V128 in this design).
- Feature flag disables Category routing without deleting live rule sets/scorecards.
- When flag off: legacy borrower+product selection remains sole authority.

---

## 24. Phase-1 scope

### SHOULD INCLUDE

- Domain tables above  
- Match on borrower type, product, intake segment, amount  
- One/many/none + CM select APIs  
- Policy Set → live rule set(s) + optional scorecard  
- Versioning + routing audit/events  
- Seed DRAFT from `underwriting_rule_sets`  
- Insertion before rule/scorecard engines  
- Feature flag  
- Activation validator + overlap WARNING  

### SHOULD NOT INCLUDE

- New rule/scorecard engines  
- Bureau/GACAT/Policy Studio changes  
- Weighted/fuzzy/AI routing  
- Multi Policy Sets per category  
- Industry/geo/secured/programme as match dims  
- Soft-reset / DB reset  
- Automatic ACTIVE from seed  
- Studio APPROVED auto-live  
- Soft-reset UI / full admin polish beyond minimal  

---

## 25. Open decisions (explicit)

1. **Maker-checker on category activate** — recommended optional for Phase 1; confirm with Risk.  
2. **Eligible list on NO MATCH** — all ACTIVE vs product-filtered; Phase 1 default **all ACTIVE**.  
3. **Multiple rule sets in one Policy Set** — confirm keep existing severity aggregation.  
4. **Scorecard null** — confirm “no scorecard” vs legacy first-match fallback (recommend **no silent fallback**).  
5. **`BorrowerType` vs business labels** (e.g. LLP) — product must map LLP into existing enum or extend enum in a later change (out of Phase 1 design invent).  
6. **Partial unique ACTIVE code** storage shape (versioned rows vs head+history).  

---

## 26. Implementation steps after approval

1. Confirm open decisions §25  
2. Flyway schema (next free version after tip)  
3. Domain entities + `CustomerCategoryRoutingService`  
4. Wire flag + gate in `LoanApplicationFlowService.underwriteApplication`  
5. Adapt rule/scorecard engines to accept explicit Policy Set refs  
6. Config + application routing APIs + RBAC  
7. Seed job DRAFT from rule sets  
8. Minimal admin + CM selection UI  
9. Decision snapshot fields for category/policy set versions  
10. Tests: match matrix, one/many/none, audit immutability, flag off = legacy  
11. Staging parity deploy (both cores, isolated DBs) — **after** approval only  

---

## Explicit recommendations (summary)

| Topic | Recommendation |
|-------|----------------|
| **NULL AMOUNT BOUND** | Unbounded that side; both null = no amount constraint |
| **ANY TOKEN** | Store literal `ANY` for enum dims; never NULL-as-ANY |
| **CATEGORY→POLICY SET CARDINALITY** | Exactly one |
| **ACTIVE CATEGORY EDIT** | No in-place mutate; new version / retire+replace |
| **OVERLAP** | Allowed; WARNING on activate; not BLOCKING |
| **NO MATCH** | CM assign; mandatory reason; no default |
| **MULTIPLE MATCH** | CM choose; mandatory reason; no silent pick |
| **SEED ACTIVATION** | DRAFT / REVIEW_REQUIRED only; never auto-ACTIVE |
| **POLICY STUDIO AUTO-LIVE** | NO |
| **LIVE ROUTING AUTHORITY** | Policy Set → existing `underwriting_rule_sets` (+ optional scorecard); Category router selects Policy Set only |

---

**DESIGN FILE CREATED:** `docs/CUSTOMER_CATEGORY_PHASE1_DESIGN.md`  
**CODE CHANGED:** NO  
**DB CHANGED:** NO  
**DEPLOYED:** NO  
**GIT PUSHED:** NO  

**CUSTOMER CATEGORY DESIGN READY FOR APPROVAL:** YES

STOP.
