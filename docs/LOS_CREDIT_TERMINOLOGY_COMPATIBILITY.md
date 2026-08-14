# LOS Credit Terminology Compatibility

**Status:** STEP 1 — compatibility period (non-destructive)  
**Contract:** `docs/LOS_CREDIT_ARCHITECTURE_DESIGN_LOCK_1.md`  
**Scope:** Domain aliases + API dual fields + lender UI labels for Customer Category admin.  
**Out of scope here:** DB column renames, live routing, Policy authority convergence, KYC Profile, activations.

## Precedence (API requests)

For Customer Category create/update:

1. If **only** the canonical field is supplied → use it.
2. If **only** the transitional field is supplied → use it.
3. If **both** are supplied and agree (case-insensitive after trim / `ANY` normalisation) → accept.
4. If **both** are supplied and **disagree** → fail closed:
   - `TERMINOLOGY_CONFLICT_CUSTOMER_ROLE`
   - `TERMINOLOGY_CONFLICT_ENTITY_TYPE`

Storage columns remain transitional (`intake_segment`, `borrower_type`). Response bodies expose **both** canonical and transitional fields with the **same** resolved value.

New Customer Category / admin clients should prefer:

- `customerRole`
- `entityType`

Existing clients may continue to send `intakeSegment` / `borrowerType`.

## Compatibility matrix

| CURRENT TERM | CANONICAL TERM | CODE ALIAS | DB FIELD | API FIELD (transitional) | API FIELD (canonical) | DEPRECATION PHASE |
|---|---|---|---|---|---|---|
| Intake Segment / `intakeSegment` | **Customer Role** | `CustomerRole` ↔ `IntakeSegment` | `customer_category.intake_segment` (unchanged) | `intakeSegment` | `customerRole` | Phase A — dual API; Phase B — prefer canonical in clients; Phase C — remove transitional (not declared) |
| Borrower Type / `borrowerType` | **Entity Type** | `EntityType` ↔ `BorrowerType` | `customer_category.borrower_type` (unchanged) | `borrowerType` | `entityType` | Phase A — dual API; Phase B — prefer canonical; Phase C — remove transitional (not declared) |
| Underwriting Rules / `underwriting_rule_sets` | *(legacy live UW representation)* | `UnderwritingRuleSet` | `underwriting_rule_sets` | existing UW / Policy Studio surfaces | n/a (not renamed yet) | Phase A — keep live authority; later steps converge Policy without removing table in STEP 1 |
| Policy Set | **Policy** *(target)* / Policy Set *(bridge)* | `PolicySetEntity` / package `customercategory` | `policy_set` | `policySetId` / Policy Sets admin | future Policy Version bind | Phase A — transitional internal package; **not removed**; later hide from lender UX after Category→Policy |
| Underwriting Rules (UI label on legacy screens) | Policy *(target authority)* | Policy Studio / rule-set editors | n/a | n/a | n/a | Do **not** globally rename legacy live screens to “Policy” until authority convergence |

## Customer Role values

| Storage / API value | `CustomerRole` |
|---|---|
| `BORROWER` | `CustomerRole.BORROWER` |
| `ANCHOR` | `CustomerRole.ANCHOR` |
| `ANY` | match wildcard (categories only) |

## Entity Type values (STEP 1)

| Storage / API value | `EntityType` |
|---|---|
| `INDIVIDUAL` | `EntityType.INDIVIDUAL` |
| `PROPRIETOR` | `EntityType.PROPRIETOR` |
| `PARTNERSHIP` | `EntityType.PARTNERSHIP` |
| `COMPANY` | `EntityType.COMPANY` |
| `ANY` | match wildcard (categories only) |

LLP / PRIVATE_LIMITED / PUBLIC_LIMITED are **not** expanded in STEP 1.

## Explicit non-claims

- Legacy items are **not** removed.
- `underwriting_rule_sets` is **not** dropped.
- Policy Set is **not** deleted.
- Live underwriting decision path is **unchanged**.
- No Flyway rename migration in STEP 1.
