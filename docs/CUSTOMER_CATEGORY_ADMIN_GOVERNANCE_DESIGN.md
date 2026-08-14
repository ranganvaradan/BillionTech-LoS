# CUSTOMER CATEGORY — ADMINISTRATION / AUTHORITY GOVERNANCE DESIGN

**Task:** CUSTOMER-CATEGORY-GOVERNANCE-STEP-1  
**Mode:** Design + codebase audit only (no routing, no activation, no migration, no deploy, no push)  
**Source tip:** `c55ef1b35c524dc9d44407bfec289d563b827949`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Flyway:** V128 (`customer_category`, `policy_set`)  
**Staging state:** 15 DRAFT categories / 12 DRAFT Policy Sets / 0 ACTIVE / live routing **not** wired

---

## Product principle (locked)

The Day-1 **15 × 12** matrix is **staging/reference configuration** for building and validating the platform.

It is **not** BillionTech’s hard-coded lending policy.

In the finished product, the **lender / authorised user** controls Category and Policy Set definition, composition, review, activation, and retirement. BillionTech provides the controlled platform, validation, and governance — and **must not silently decide** the lender’s credit policy.

---

## 1. Current-state audit

### 1.1 Schema (`V128`)

| Table | Purpose today |
|-------|----------------|
| `customer_category` | Matching definition + FK to exactly one `policy_set` |
| `policy_set` | Named bundle of **references** to live UW components |

**No** separate link table. Cardinality = **one Policy Set per Category**.

### 1.2 `PolicySetEntity` — what is stored today

| Field | Persistent? | Notes |
|-------|-------------|-------|
| `id`, `code`, `version_no`, `name`, `description` | YES | Business identity |
| `status` | YES | `DRAFT` \| `ACTIVE` \| `RETIRED` only |
| `primary_rule_set_id` | YES | Required FK → `underwriting_rule_sets.id` |
| `additional_rule_set_ids` | YES | JSONB UUID list (default `[]`) |
| `scorecard_id` | YES | Nullable FK → `underwriting_scorecards.id` |
| `seed_source_rule_set_id` | YES | Seed lineage only |
| Audit (`created_*`, `updated_*`, `activated_*`, `retired_*`) | YES | |
| Policy Studio policy / version | **NO** | Not present |
| Workflow / Product Config / LMS / CAM | **NO** | Out of scope today |
| Effective from / until | **NO** | Not present |
| Maker/checker evidence | **NO** | Not present |

**Verdict:** A Policy Set **does** already hold explicit persistent references to underwriting rule set(s) and (optionally) a scorecard. It does **not** reference Policy Studio artifacts. Composition is UUID-centric and incomplete as an administration product.

### 1.3 `CustomerCategoryEntity` — what is stored today

| Field | Persistent? | Notes |
|-------|-------------|-------|
| Matching: `borrower_type`, `loan_product`, `intake_segment`, `min_amount`, `max_amount` | YES | |
| `policy_set_id` | YES | Exactly one |
| `status` | YES | `DRAFT` \| `ACTIVE` \| `RETIRED` |
| `version_no` | YES | Present; clone/new-version APIs incomplete |
| `review_status`, `inference_notes` | YES | Seed/review metadata — **not** maker-checker |
| Effective from / until | **NO** | |
| Maker/checker evidence | **NO** | |

**Semantics already coded:**

- `NULL` amount = unbounded; bounds inclusive when set (`CustomerCategoryOverlapDetector` / amount checks).
- `ANY` is an explicit literal via `MatchWildcard` (not NULL).
- ACTIVE matching criteria cannot be silently edited in place (`ACTIVE_CATEGORY_IMMUTABLE`).

### 1.4 APIs / services today

| Capability | Category | Policy Set |
|------------|----------|------------|
| List / get | YES | YES |
| Create DRAFT | YES | YES |
| Update DRAFT | YES | **NO dedicated update API** |
| Clone / new version | **NO** | **NO** |
| Submit / approve (maker-checker) | **NO** | **NO** |
| Activate DRAFT → ACTIVE | YES (DRAFT only; PS must be ACTIVE for category) | YES |
| Retire | YES | YES |
| Eligible-component picker | **NO** (raw UUIDs) | **NO** |
| Admin UI | **NO** (`ui-service` has zero references) | **NO** |
| Seed preview/apply (raw 36 + Day-1 15×12) | YES | via seed services |

Activation of a Category today checks: Policy Set exists and is ACTIVE; match dimensions valid. It does **not** re-validate that referenced rule set / scorecard remain executable, and it does **not** require maker ≠ checker.

### 1.5 Existing live underwriting multi-rule behaviour

`UnderwritingRuleEngine.evaluateAll`:

1. Loads **all** `active` rule sets for application `borrowerType` + `loanProduct`, ordered by **priority DESC**.
2. Filters by amount / tenure / geography.
3. Evaluates **each** matching set.
4. Aggregates: any REJECT → reject; else any MANUAL_REVIEW → manual; else all APPROVE → approve; else mixed → manual.

`ScorecardPolicyEngine` similarly selects among **active** scorecards by borrower/product + priority (not via Policy Set today).

**Critical:** Policy Set `additional_rule_set_ids` is **not consumed** by any live engine yet. Live UW does **not** read Customer Category or Policy Set.

### 1.6 Governance patterns already in the product (to reuse)

| Domain | Lifecycle | Maker-checker | Production authority |
|--------|-----------|---------------|----------------------|
| Live Scorecards | DRAFT → IN_REVIEW → APPROVED → ACTIVE → RETIRED | YES (`ScorecardGovernanceService`) | ACTIVE only (`isExecutionActive`) |
| Policy Studio | business statuses including APPROVED / ACTIVE | YES | **Never** auto-live (`allowCanonicalAuthority=false`; APPROVED ≠ production UW) |
| Customer Category / Policy Set | DRAFT → ACTIVE → RETIRED | **NO** | N/A until routing |

---

## 2. Authority model (intended)

```
CUSTOMER CATEGORY          ← lender defines WHO/WHEN matches (routing criteria)
        ↓
POLICY SET                 ← lender selects WHICH approved executables apply
        ↓
approved executable components
   ├── Underwriting Rule Set(s)   ← existing Rule Engine owns HOW
   └── Scorecard                  ← existing Scorecard Engine owns HOW
        [future components may be added later via explicit Policy Set slots]
```

**Rules:**

- Administration controls **selection** of approved components.
- Execution engines retain **decision authority** (how rules/scorecards execute and aggregate).
- Do **not** copy rule expressions, factors, or thresholds into Category or Policy Set.
- Do **not** create another underwriting/rule engine.
- Day-1 seed rows are **data**, not product policy.

---

## 3. Customer Category object (administration target)

### 3.1 Minimum fields

| Field | Required | Notes |
|-------|----------|-------|
| `code` | YES | Stable business key; immutable after first activation |
| `name`, `description` | YES / optional | Display |
| `borrower_type` | YES | Enum or literal `ANY` |
| `loan_product` | YES | Product code or `ANY` |
| `intake_segment` | YES | Enum or `ANY` |
| `min_amount`, `max_amount` | optional | NULL = unbounded; inclusive |
| `policy_set_id` | YES | Exactly one Policy Set |
| `effective_from`, `effective_until` | recommended | NULL until = open-ended |
| `status`, `version_no` | YES | See §5 |
| Audit / governance evidence | YES | Maker, checker, timestamps, remarks |

### 3.2 Minimum user operations

1. Create Category (DRAFT)  
2. Edit DRAFT Category  
3. Clone Category  
4. Submit for approval  
5. Approve / return  
6. Activate  
7. Retire  
8. Create replacement version (new `version_no` row; prior ACTIVE retired on activate)

### 3.3 Confirmations

| Rule | Status |
|------|--------|
| NULL amount bound = unbounded | Already true |
| Boundaries inclusive | Already true |
| `ANY` explicit literal, not NULL | Already true |
| ACTIVE matching config not silently edited in place | Already true for Category |

---

## 4. Policy Set composition (administration target)

### 4.1 Role

Policy Set is a **genuine user-controlled composition object**: a named, versioned, governed selection of eligible executable components — not a dump of rule JSON.

### 4.2 Minimum fields

| Field | Required | Notes |
|-------|----------|-------|
| `code`, `name`, `description` | YES | |
| `primary_rule_set_id` | YES (Day-1) | Must be executable ACTIVE rule set |
| `additional_rule_set_ids` | optional | See §4.4 |
| `scorecard_id` | product-dependent | Prefer **required when product uses scorecards**; allow explicit “none” only if product policy permits |
| `status`, `version_no` | YES | |
| Effective dates | recommended | Align with Category or independently |
| Governance evidence | YES | |
| Studio linkage (future) | optional | Explicit slot — never auto-bind |

### 4.3 Selection UX (not UUID typing)

Users choose from **eligible APPROVED/ACTIVE executables** filtered by:

- borrower type / loan product compatibility with intended Categories;
- executable readiness (`active` / scorecard `ACTIVE` + `isExecutionActive`);
- amount coverage relative to Categories that will use the Policy Set (warning if Category range not covered).

### 4.4 Multiple rule sets — Day-1 decision

**Existing live semantics already define multi-rule aggregation** (all matching ACTIVE sets by BT+product+filters; reject-wins / manual / approve). Do **not** invent a second aggregation model inside Policy Set.

**Recommended Day-1 (simplest safe):**

| Decision | Choice |
|----------|--------|
| Policy Set selects | **Exactly one** primary underwriting rule set for routing authority |
| `additional_rule_set_ids` | Keep column for forward compatibility; **admin UI hides or freezes empty** until an explicit later task wires constrained multi-set evaluation |
| When routing is later wired | Engine evaluates **only** the Policy Set–selected rule set ID(s), using **existing** per-rule evaluation + **existing** aggregate function if/when multiple are enabled |
| Do not | Silently fall back to “all active rule sets in DB” once Category routing is authoritative |

Rationale: until routing is live, engines still use today’s global selection. When routing becomes live, Policy Set must **narrow** authority to lender-selected components — starting with one primary RS + one scorecard.

### 4.5 Scorecard

- Policy Set holds **one** scorecard reference (already modelled).
- Eligibility = scorecard execution-active (Scorecard governance ACTIVE).
- No copying of bands/thresholds into Policy Set.

---

## 5. Lifecycle (proposed)

Align with Scorecard governance pattern; keep Policy Studio separate.

### 5.1 Customer Category

`DRAFT → IN_REVIEW → APPROVED → ACTIVE → RETIRED`

| Transition | Actor | Notes |
|------------|-------|-------|
| Create / edit | Maker | Matching fields editable only in DRAFT (and returned-to-DRAFT) |
| Submit | Maker | → IN_REVIEW |
| Approve / return | Checker ≠ Maker | → APPROVED or back to DRAFT |
| Activate | Authorised activator | Only from APPROVED; validations §7 |
| Retire | Authorised | Soft end; replacement via new version |

### 5.2 Policy Set

Same lifecycle: `DRAFT → IN_REVIEW → APPROVED → ACTIVE → RETIRED`

**ACTIVE composition immutable in place** (same spirit as ACTIVE Category matching and ACTIVE scorecards): material change ⇒ new version.

### 5.3 APPROVED separate from ACTIVE?

**YES.**  

- APPROVED = checker accepted composition/definition; not yet live routing authority.  
- ACTIVE = eligible for runtime match / Policy Set resolution.  
- Mirrors Scorecards and prevents “approve equals go-live”.

Extend `ConfigLifecycleStatus` / DB check constraints accordingly in a future governance implementation step (not this audit).

---

## 6. Maker / checker

Reuse Scorecard patterns:

- Distinct maker vs checker identities (userId).
- Roles: Credit Manager / Administrator (exact matrix to match existing scorecard roles unless product dictates otherwise).
- Persist governance JSON or dedicated columns: submittedBy, approvedBy, remarks, history[].
- Audit via existing `AdminConfigAuditSupport`.

`review_status` / `inference_notes` on Category today are **not** sufficient and should not be overloaded as maker-checker.

---

## 7. Activation validation (design)

Before Category or Policy Set becomes ACTIVE:

**Policy Set activate:**

- Primary rule set exists and is executable (ACTIVE).
- Each additional (if ever enabled) exists and is executable.
- Scorecard exists and is executable when required.
- Code/version uniqueness; no broken FKs.
- Dimension consistency warnings vs known Categories (non-blocking or warning).

**Category activate:**

- Referenced Policy Set is **ACTIVE** (and not retired).
- Policy Set’s rule set(s)/scorecard still executable at activation time.
- Borrower / product / intake valid (`ANY` allowed).
- Amount range valid; effective dates valid (`from` ≤ `until`).
- Overlap analysis completed → **WARNING only** (unchanged unless product later hardens).
- No broken references.

Fail closed on missing/non-executable refs — do not activate.

---

## 8. Policy Studio boundary

**Principle preserved:** Policy Studio **APPROVED does not** automatically become live underwriting.

| Path | Binding |
|------|---------|
| Studio → live UW today | None (`allowCanonicalAuthority=false`) |
| Studio → Policy Set | **Not modelled today** |
| Future | Explicit controlled linkage step only (e.g. “promote Studio artifact to eligible executable” **or** map Studio output into a live rule set/scorecard first, then select that executable in Policy Set) |

No hidden Studio → Category → live chain.

For Day-1 / near-term Policy Set composition, selectable components remain:

1. Live `underwriting_rule_sets` (ACTIVE)  
2. Live `underwriting_scorecards` (ACTIVE)

Studio stays governance/shadow until a separate explicit cutover task.

---

## 9. Future runtime contract (design only — do not wire)

```
Application
   ↓
CustomerCategoryRoutingService
   ↓
match ACTIVE categories on:
  borrower type, product, intake, amount
  (+ effective dating when present)
   ↓
```

| Matches | Behaviour |
|---------|-----------|
| **1** | Resolve its ACTIVE Policy Set; continue automatically; audit `AUTO_SINGLE_MATCH` |
| **>1** | **Do not** silently choose; CM selection + **mandatory reason**; `MANUAL_MULTI_MATCH` |
| **0** | **Do not** fall back to a default policy; CM assignment + **mandatory reason**; `MANUAL_NO_MATCH` |

Then:

```
resolved Policy Set
   ↓
existing UnderwritingRuleEngine (selected RS only — Day-1: primary)
   +
existing ScorecardPolicyEngine (selected scorecard)
```

Customer Category **selects authority**; it does **not** make the credit decision.

DRAFT / IN_REVIEW / APPROVED / RETIRED categories have **zero** runtime authority.

---

## 10. Day-1 seed safety

### 10.1 Classification of hard-coded Day-1 identifiers

| Location | What | Classification |
|----------|------|----------------|
| `Day1ApprovedSeedCatalog.java` | 15 CC codes, 12 PS codes, RS/SC UUIDs | **Bootstrap/seed catalog only** |
| `CustomerCategoryDay1SeedService.java` | Applies catalog | **Seed/bootstrap only** |
| `CustomerCategoryController` `/seed/day1/*` | Invokes Day-1 seed | **Seed API only** |
| `CustomerCategoryDay1SeedServiceTest` | Asserts matrix | **Test only** |
| `UnderwritingRuleEngine` / `ScorecardPolicyEngine` / flow services | — | **No Day-1 codes** |
| `ui-service` | — | **No references** |

Raw 36-row seed (`CustomerCategorySeedService`) is also bootstrap-only; separate from Day-1 catalog.

### 10.2 Conclusion

**15 Day-1 categories are NOT hard-coded into runtime.**  
**12 Day-1 Policy Sets are NOT hard-coded into runtime.**

The platform continues to function if a lender creates an entirely different Category / Policy Set configuration via admin APIs (once governance/UI land). Seed endpoints are optional bootstrap tools, not lending policy.

---

## 11. Proposed administration UX (do not implement in this step)

```
Credit Intelligence
  → Customer Categories
  → Policy Sets
  → Policy Studio          (unchanged; no auto-live)
  → Scorecards             (existing live scorecard admin)
  → Underwriting Rules     (existing)
```

### Customer Categories page

- List: code, name, status, version, borrower / product / intake, amount range, Policy Set, effective period  
- Actions: create, edit (DRAFT), clone, submit, approve/return, activate, retire, view overlaps, version history  

### Policy Sets page

- List: code, name, status/version, primary rule set (name), scorecard (name), “used by N categories”  
- Actions: create, edit (DRAFT), picker for eligible RS/SC, submit, approve, activate, retire, clone/version  
- Detail: show referenced component readiness badges  

No UUID paste as primary UX.

---

## 12. Schema / API changes required before routing

### 12.1 Schema (recommended before live routing)

| Change | Why |
|--------|-----|
| Extend status enums to include `IN_REVIEW`, `APPROVED` | Maker-checker |
| Governance evidence (JSON or columns) on Category + Policy Set | Audit / checker |
| `effective_from` / `effective_until` on Category (and optionally Policy Set) | Time-bound authority |
| Optional: require `scorecard_id` NOT NULL for products that always score | Fail-closed composition |
| Keep `additional_rule_set_ids` but document unused until multi-RS routing task | Avoid false capability |

### 12.2 API (required before routing)

| Change | Why |
|--------|-----|
| Policy Set update DRAFT; clone/version | Admin completeness |
| Category clone/version | Replacement without in-place ACTIVE edit |
| Submit / approve / return endpoints | Governance |
| Activate only from APPROVED | Separate from approve |
| Eligible rule-set / scorecard catalogue endpoints | Lender selection without raw UUIDs |
| Overlap report retained | Warning on activate |
| **Do not** add routing resolve endpoint until governance + ACTIVE inventory are ready | Fail-closed product |

### 12.3 Admin UI

**Required** before lender self-service and before treating routing as production-ready. APIs alone are insufficient for lender control principle.

---

## 13. Recommended implementation sequence

1. **Governance implementation (next)** — statuses, maker-checker, immutability, effective dates, activation validation hardening, eligible pickers, clone/version. **No live routing.**  
2. **Admin UI** — Categories + Policy Sets under Credit Intelligence.  
3. **Certification on staging** — lender can create non–Day-1 configs; Day-1 remains reference DRAFT or separately activated under governance.  
4. **Routing Step 2** — `CustomerCategoryRoutingService` with 1 / many / none contract; Policy Set → single primary RS + scorecard into existing engines.  
5. **Later (optional)** — multi rule-set selection using **existing** aggregate semantics; explicit Studio eligibility bridge if product requires it.

---

## 14. Sufficiency verdict (pre-routing)

| Question | Answer |
|----------|--------|
| Current Category model sufficient for lender governance? | **NO** — missing IN_REVIEW/APPROVED, maker-checker, effective dates, clone/version, UI |
| Current Policy Set model sufficient? | **NO** — refs exist but incomplete admin (no update/governance/pickers); multi-RS unused; no Studio slot |
| Policy Set references rule sets today? | **YES** (`primary` + `additional`) |
| Policy Set references scorecard today? | **YES** (nullable) |
| Lender controls Category definition today? | **PARTIAL** (DRAFT CRUD/activate APIs; no governance/UI) |
| Lender controls Policy Set composition today? | **PARTIAL** (create/activate with UUIDs; no edit/governance/pickers) |
| Day-1 hard-coded into runtime? | **NO** |
| Ready for live routing implementation? | **NO** — complete governance (+ preferably admin UI) first |

---

## 15. Explicit non-goals of this document’s follow-on (until authorised)

- Do not implement routing.  
- Do not activate Day-1 Categories / Policy Sets.  
- Do not change live UW engines or Policy Studio authority flags.  
- Do not treat Day-1 matrix as immutable product policy.
