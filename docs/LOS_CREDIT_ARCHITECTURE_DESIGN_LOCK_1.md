# LOS Credit Architecture — Design Lock 1

**Document:** `LOS-CREDIT-ARCHITECTURE-DESIGN-LOCK-1`  
**Mode:** DESIGN LOCK ONLY — **no implementation**  
**SOURCE SHA:** `6d61c5eeada60b025d639cf2b4cd19da172c051d`  
**Branch:** `reconcile/laptop-layout-2026-08`  
**Upstream audit:** `docs/LOS_CREDIT_ARCHITECTURE_RECONCILIATION_1.md`  
**Date:** 2026-08-14  

This document is the **architecture contract** for subsequent implementation.  
It supersedes any Phase-1 assumption that the permanent product model is:

```
Application → Customer Category → Policy Set → Rule Set + Scorecard
```

---

## AUTHORITATIVE BUSINESS MODEL (LOCKED)

```
PARTY / CUSTOMER
  └── participates in APPLICATION with CUSTOMER ROLE (BORROWER | ANCHOR)

ENTITY TYPE
  INDIVIDUAL | PROPRIETOR | PARTNERSHIP | LLP
  | PRIVATE_LIMITED_COMPANY | PUBLIC_LIMITED_COMPANY | …

LOAN PRODUCT
  (existing platform taxonomy — unchanged in this lock)

CUSTOMER CATEGORY  = lender-defined lending proposition
  eligibility dimensions (Role, Entity Type, Product, Min/Max Amount, effective)
  → POLICY VERSION   (ONE lender-facing underwriting policy concept)

POLICY
  authored/governed in Policy Studio
  may use bureau, bank, GST, financials, calculations,
  hard / manual-review / allow rules, optional scorecard

SCORECARD
  Category → Policy → optional Scorecard
  (not Category → Policy Set → Rule Set → Scorecard)

KYC
  Entity Type → KYC Profile
  separate from underwriting Policy

APPLICATION stores (target):
  Customer Role, Entity Type, Loan Product, Amount,
  Selected Customer Category (+ version),
  Policy Version used, KYC Profile/version used,
  decision evidence
```

**Locked truths:**

1. Customer Category is a **lending proposition**, not merely a routing bucket.
2. Two Categories **may share identical eligibility dimensions** and link different Policies.
3. Dimensional “overlap” is **not inherently a configuration defect**.
4. Eligibility ≠ Selection.
5. There is **one** lender-facing underwriting Policy concept.
6. `underwriting_rule_sets` is transitional runtime/legacy — not the target lender UX.
7. Policy Set is **not** the permanent lender-facing credit object.
8. KYC document/compliance requirements do **not** live inside underwriting Policy.

---

# PART A — TERMINOLOGY LOCK

| CURRENT TERM | TARGET BUSINESS TERM | TRANSITIONAL CODE TERM | CHANGE REQUIRED NOW? | CHANGE REQUIRED LATER? |
|---|---|---|---|---|
| `intakeSegment` / Intake Segment | **Customer Role** | keep `intakeSegment` / `IntakeSegment` in code/DB | **NO** (docs + UI labels only when UX work starts) | YES — eventual API/DB rename or alias |
| Customer Type | *(avoid — undefined)* | do not introduce | **NO** | Only if product defines it distinctly |
| Customer Role | **Customer Role** | map from `intakeSegment` | **NO** code | YES — first-class naming |
| `borrowerType` / Borrower Type | **Entity Type** | keep `borrowerType` / `BorrowerType` | **NO** | YES — expand enum + rename/alias |
| Entity Type | **Entity Type** | future enum values beyond COMPANY | **NO** | YES — LLP / PRIVATE_LIMITED / PUBLIC_LIMITED |
| Loan Product | **Loan Product** | existing product codes | **NO** | taxonomy changes only by separate product decision |
| Customer Category | **Customer Category** | `customer_category` | **NO** | linkage fields evolve |
| Policy (Studio) | **Policy** | `CiPolicyDocument` / Policy Version | **NO** | publish + Category bind |
| Underwriting Rules (UI) | **Policy** (lender-facing) | retire UX name | **NO** | YES — retire UI |
| `underwriting_rule_sets` | **Runtime / legacy package** (internal) | keep table | **NO** | YES — stop direct lender edit |
| Policy Set | **Internal transitional runtime composition** | keep `policy_set` temporarily | **NO** | YES — hide UX; rename to technical package later |
| KYC Rules | Prefer **KYC Profile requirements** | workflow steps today | **NO** | YES — KYC Profile |
| KYC Profile | **KYC Profile** | *(does not exist yet)* | **NO** | YES — introduce domain |

**Recommended mappings (locked):**

- `intakeSegment` → Customer Role  
- `borrowerType` → Entity Type  
- Underwriting Rules (lender concept) → Policy  
- Policy Set → internal transitional / runtime composition only  

**Do not rename code in this design-lock task.**

---

# PART B — CUSTOMER CATEGORY ELIGIBILITY (LOCKED)

## Definition

An application is **ELIGIBLE** for a Customer Category when **all** of the following hold:

1. **Customer Role** matches Category Customer Role (today: `intakeSegment`).
2. **Entity Type** matches Category Entity Type (today: `borrowerType`), including `ANY` wildcard semantics if retained.
3. **Loan Product** matches Category Loan Product (including `ANY` if retained).
4. **Requested amount** falls within Category min/max bounds (null bound = unbounded on that side; inclusive bounds).
5. Category is **ACTIVE** and within **effective** dating (if set).
6. Any future explicit eligibility conditions (when added) are satisfied.

**Eligibility does NOT mean selected.**

## Conceptual operation (not implemented)

```
CustomerCategoryEligibilityService.findEligible(application)
  → List<CustomerCategory>   // ACTIVE eligible propositions only
```

Inputs conceptually: Customer Role, Entity Type, Loan Product, requested amount, as-of timestamp.

Identical eligibility dimensions across multiple Categories is **allowed**.

---

# PART C — CUSTOMER CATEGORY SELECTION (LOCKED)

Selection is **separate** from eligibility.

## Precedence (LOCKED)

| Order | Source | Behaviour |
|---|---|---|
| **1** | **EXPLICIT_APPLICATION_SELECTION** | Application already carries `CustomerCategoryId` (customer / sales-RM / partner-anchor / pre-approved offer). **Validate eligibility.** If eligible → SELECTED. If not → fail closed / force re-select (no silent substitute). |
| **2** | **PROGRAMME / CHANNEL SELECTION** | Programme, campaign, or anchor configuration explicitly identifies Category. **Validate eligibility.** |
| **3** | **UNIQUE ELIGIBILITY AUTO-SELECTION** | No explicit Category; **exactly one** eligible Category; product/config permits auto-select → may AUTO_SELECT. |
| **4** | **MULTIPLE ELIGIBLE CATEGORIES** | **NOT a configuration error.** Return **`CATEGORY_SELECTION_REQUIRED`**. Controlled choice among eligible propositions (customer / CM / RM per workflow). |
| **5** | **NO ELIGIBLE CATEGORY** | Return **`NO_ELIGIBLE_CATEGORY`**. **Do not** silently assign arbitrary/default credit policy. Manual controlled assignment only if an approved business workflow permits, with mandatory reason + audit. |

**Forbidden:** silently prioritizing among multiple eligible Categories by code, priority, or creation order.

---

# PART D — SELECTION AUDIT (LOCKED)

Every application that participates in Category-based credit must eventually persist:

| Field | Purpose |
|---|---|
| `customerCategoryId` | Selected Category identity |
| `customerCategoryVersion` | Exact Category version used |
| `selectionSource` | How it was chosen |
| `selectedBy` | Actor (system / user id) |
| `selectedAt` | Timestamp |
| `selectionReason` | Required for manual / CM / override paths |
| `eligibleCategoryIdsAtSelection` | Optional but recommended snapshot of eligibility set |
| selection evidence | Structured audit payload |

## `selectionSource` (conceptual enum — LOCKED)

```
CUSTOMER_SELECTED
RM_SELECTED
CM_SELECTED
ANCHOR_PROGRAMME
CHANNEL_PROGRAMME
PREAPPROVED_OFFER
AUTO_SINGLE_ELIGIBLE
MIGRATED_LEGACY
OTHER_CONTROLLED
```

## Change after underwriting has begun (LOCKED)

Changing selected Category **must NOT rewrite** prior underwriting history.

Required behaviour:

- prior DecisionSnapshot / evaluation remains immutable;
- Category change triggers a **new evaluation** and/or **controlled restart** of credit assessment;
- new snapshot records new Category + Policy versions and links prior evaluation ids for audit trail.

---

# PART E — CATEGORY → POLICY CONTRACT (LOCKED)

## Target relation

```
Customer Category (configuration identity)
  → binds Policy identity
  → resolves to one ACTIVE Policy Version at a point in time
     (subject to Category effective dating and Policy publish state)
```

## Configuration vs reproducibility

| Layer | Model |
|---|---|
| **Configuration (admin)** | Category binds **Policy identity** (or Category Version binds a Policy Version pointer that can be advanced by controlled publish). |
| **Runtime decision (application)** | DecisionSnapshot **must store exact Policy Version** used. |

## Safest locked model

1. **Category Version** records the intended **Policy Version** (or Policy identity + resolved version at activation of that Category Version).  
2. **Application DecisionSnapshot** always stores:
   - `customerCategoryId` + `customerCategoryVersion`
   - `policyId` + **`policyVersion` (exact)**
   - runtime package ids if a compiled bridge exists  

This maximises historical reproducibility and avoids “Category still points at Policy head that moved after decision.”

**Not locked as permanent UX:** Category → Policy Set → Rule Set.

---

# PART F — POLICY COMPOSITION (LOCKED)

## What Policy owns conceptually

A Policy Version includes or references:

- PolicyDsl / decision rules  
- parameter / data requirements  
- calculations / measures  
- treatments (hard reject / manual review / allow-pass)  
- **optional Scorecard** reference  
- effective period  
- governance lifecycle  
- version identity  

## Scorecard fit (LOCKED)

```
Policy Version
  → optional Scorecard Version reference
```

**Recommendation LOCKED:** Scorecard remains **separately versioned** and **referenced by Policy**, not physically embedded as opaque bytes inside Policy — unless a future technical packaging step creates an immutable published bundle that *copies* resolved references for runtime.

Lender mental model remains: **“This Category uses this Policy.”** Scorecard is a Policy component, not a Category/Policy-Set sibling the user must assemble.

---

# PART G — POLICY PUBLICATION CONTRACT (LOCKED)

Policy Studio Policy is **not** live authority today. Future controlled model:

```
DRAFT POLICY
  → author
  → test
  → submit
  → approve
  → PUBLISH FOR RUNTIME
  → ACTIVE Policy Version (published)
```

## Three authorities (LOCKED vocabulary)

| Authority | Meaning |
|---|---|
| **AUTHORING AUTHORITY** | Policy Studio — drafts, tests, reviews, schedules business ACTIVE metadata |
| **PUBLISHED POLICY AUTHORITY** | The Policy Version intentionally released for credit use (Category may bind only published versions) |
| **RUNTIME REPRESENTATION** | Executable form used by engines (during migration may be compiled legacy Rule Set + Scorecard package) |

## Migration bridge (LOCKED concept — not implemented)

```
Published Policy Version
  → compiles/publishes to internal Runtime Package
       → legacy underwriting_rule_sets representation (as needed)
       → scorecard reference
```

This allows Policy to become **business authority** without immediately rewriting `UnderwritingRuleEngine`.

**Guardrail:** Policy Studio **approve** alone does **not** auto-bind live authority. Explicit **publish for runtime** is required.

---

# PART H — UNDERWRITING_RULE_SET TRANSITION (LOCKED)

| Aspect | Lock |
|---|---|
| Future role | **LEGACY / INTERNAL RUNTIME REPRESENTATION** |
| Lender-facing | **NOT** the target configuration object |
| During migration | May remain technically required for live execution |
| Direct lender editing | Phase out once Policy publication owns changes |
| Delete table | **Do not delete** in near term |

**How users stop editing it:**  
After Policy publication is authoritative, Live Underwriting Rules UI is retired (or read-only). Changes flow: Policy authoring → publish → regenerate/update internal runtime package. Direct RS edits become platform/ops exceptional only.

---

# PART I — POLICY SET TRANSITION (LOCKED)

| Aspect | Lock |
|---|---|
| Near-term | **KEEP_INTERNAL_ONLY** (bridge) |
| Target | **MERGE into Policy publication / DEPRECATE as user-facing** |
| Contains lending logic? | **No** — references only |
| User-facing admin UI long-term | **Must not** remain a peer concept to Policy |

## Transitional technical model (allowed)

```
Published Policy Version
  → internal Runtime Package   (today’s Policy Set shape may temporarily serve)
       → primary legacy Rule Set id
       → scorecard id
```

Eventual technical names (optional later — **do not rename now**):

- `PolicyRuntimePackage`
- `PublishedPolicyPackage`

Current Day-1 DRAFT Policy Sets: keep as **non-activated bridge test data** only.

---

# PART J — STARTER / BANK STARTER GOLDEN (LOCKED PROOF)

### Categories

| | STARTER LOAN | BANK STARTER |
|---|---|---|
| Role | BORROWER | BORROWER |
| Entity | INDIVIDUAL | INDIVIDUAL |
| Product | BUSINESS_TERM_LOAN | BUSINESS_TERM_LOAN |
| Amount | 20,000–500,000 | 20,000–500,000 |
| Policy | Income-Based Policy | Bank-Statement Policy |

### Application

BORROWER + INDIVIDUAL + BUSINESS_TERM_LOAN + 300,000

### Eligibility

```
[ STARTER LOAN, BANK STARTER ]
```

### Selection cases

| Input | Result |
|---|---|
| Explicit Category = BANK STARTER | Validate eligible → **SELECTED = BANK STARTER** → **Policy = Bank-Statement Policy** |
| No Category selected | **`CATEGORY_SELECTION_REQUIRED`** (not an error of configuration) |
| Exactly one eligible (hypothetically) | May `AUTO_SINGLE_ELIGIBLE` if enabled |

### KYC

Same Entity Type → same **Base Individual KYC Profile** regardless of which Category is selected.

### Ambiguity check

| Ambiguity type | Present? |
|---|---|
| Which Categories are eligible? | **No** — deterministic eligibility |
| Which Category is selected without explicit choice when multiple? | **No** — requires selection outcome |
| Which Policy runs once Category selected? | **No** — Category → Policy Version |
| Which KYC Profile? | **No** — Entity Type → KYC Profile |

**GOLDEN: PASS** under locked model.

---

# PART K — KYC BOUNDARY LOCK

| Owns | Examples |
|---|---|
| **KYC Profile** | Identity/compliance: documents, PAN, Aadhaar/OVD, CIN, partnership deed, directors, partners, UBOs, authorised signatories, PEP/sanctions where applicable |
| **Policy** | Credit decision logic; may **require state** such as `KYC_COMPLETE = true` as an input |

**LOCKED:** Policy **must not** redefine underlying KYC document/constitutional requirements merely because it is the credit Policy.

Contextual KYC overlays (Role / Product / applicant role / regulation) may exist later — still owned by KYC domain, not Policy rule text.

**Do not implement KYC Profile in this design-lock task.**

---

# PART L — CURRENT DATA TREATMENT (LOCKED RECOMMENDATION)

| Object | Count | Treatment |
|---|---|---|
| Customer Categories | 15 DRAFT | **KEEP** as non-authoritative staging examples. Later **TRANSFORM** terminology/linkage (Policy bind). **Do not activate** under old Policy Set end-state narrative. **Do not DELETE_RECREATE** until Category→Policy path exists. |
| Policy Sets | 12 DRAFT | **KEEP temporarily** as internal bridge / test composition data. **Do not activate.** Eventually migrate behind Policy publication or retire. **Do not invest** in Policy Set as permanent UX. |

---

# PART M — OVERLAP MODEL CHANGE (LOCKED)

## UX terminology (target)

Prefer:

- **“Other eligible propositions”**
- or **“Also eligible”** / **“Eligibility overlap”**

Avoid implying automatic defect via sole label **“Overlap Warning”** without qualification.

## Distinguishing legitimate multi-eligibility vs accidental duplicate (LOCKED)

| Situation | Classification |
|---|---|
| Same eligibility dimensions **AND different Policy** | **LEGITIMATE MULTI-ELIGIBILITY** (e.g. STARTER vs BANK STARTER) — informational “also eligible” |
| Same eligibility dimensions **AND same Policy** **AND overlapping effective period** | **PROBABLE ACCIDENTAL DUPLICATE** — configuration warning |
| Same dimensions, same Policy, non-overlapping effective dating | Versioning / replacement — usually OK |

Platform must support both classes; only the duplicate class is a configuration smell.

---

# PART N — P0 ARCHITECTURE GUARDRAILS (LOCKED)

Future development **MUST NOT**:

1. Assume Customer Category dimensions must uniquely resolve to one Category.  
2. Make Policy Set the permanent lender-facing credit object.  
3. Maintain Underwriting Rules and Policy as parallel lender-facing authorities indefinitely.  
4. Put KYC document/constitutional rules inside underwriting Policy.  
5. Auto-bind Policy Studio approval directly to live authority (publish is required).  
6. Allow a live Category-routed decision without exact **selected Category + Policy Version** evidence in the decision snapshot.

---

# PART O — NEXT MIGRATION ORDER (LOCKED SEQUENCE)

| Step | Work |
|---|---|
| **STEP 1** | Terminology / domain compatibility additions (aliases, docs, DTO comments) — **no destructive renames** |
| **STEP 2** | Application `selectedCustomerCategoryId` (+ version / selection audit fields) model |
| **STEP 3** | Customer Category **eligibility + selection APIs** — **NON-AUTHORITATIVE** (no live UW change) |
| **STEP 4** | KYC Profile domain |
| **STEP 5** | Policy gains Category applicability / linkage / composition (incl. scorecard reference) |
| **STEP 6** | Policy publication → internal runtime package (may use Policy Set–shaped bridge) |
| **STEP 7** | Shadow Policy vs legacy runtime |
| **STEP 8** | Policy becomes authoritative |
| **STEP 9** | Underwriting Rules UI retired |
| **STEP 10** | Policy Set hidden / deprecated from lender UX |

**Refinement note:** STEP 2–3 may proceed before Entity Type enum expansion; LLP/Pvt/Public expansion is compatible anytime after STEP 1 but is not a blocker for selection APIs.

---

## CONTRACT STATUS

| Item | Status |
|---|---|
| Design lock complete | **YES** |
| Code / DB / data / deploy / push in this task | **NO** |
| Ready for reconciliation **implementation** (STEP 1+) | **YES** |
| Ready for shadow routing | **NO** |
| Ready for live routing | **NO** |

---

*End of LOS_CREDIT_ARCHITECTURE_DESIGN_LOCK_1*
