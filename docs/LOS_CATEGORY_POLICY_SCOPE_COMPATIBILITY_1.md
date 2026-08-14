# Category ↔ Policy Scope Compatibility (COMPATIBILITY-1)

**Status:** CONTRACT + VALIDATOR  
**Mode:** Configuration / governance only — no live routing, shadow routing, or production authority  
**Reuse:** `ci_policy_applicability` (Policy Studio). No duplicate applicability model.

## Part A — Existing Policy Scope fields (authoritative)

Source entity: `CiPolicyApplicability` / catalogue row via `PolicyCatalogueService.toBusinessRow`.

| Dimension | Policy Studio field(s) | Authoritative today? | Notes |
|---|---|---|---|
| Entity Type | `borrower_types` (JSON list), legacy `borrower_type` | **YES** | Empty / ALL = unconstrained |
| Loan Product | `products` (JSON list) | **YES** | Empty = unconstrained (ALL products) |
| Min / Max amount | `min_loan_amount`, `max_loan_amount` | **YES** | NULL = unbounded that side; inclusive |
| Effective dates | `effective_from`, `effective_until` (`LocalDate`) | **YES** | NULL until = open-ended |
| Customer Role / intake | *(no first-class column)* | **NO column** | Optional only if present in `metadata` / `eligibility_detail` as `customerRole(s)` / `intakeSegment(s)` |
| Customer segment | `customer_segment` | Optional Policy constraint | Category does not own this |
| Secured / unsecured | `secured_unsecured` | Optional Policy constraint | Category does not own this |
| Programme / scheme | `program_scheme` | Optional Policy constraint | Category does not own this |
| Facility type | `facility_type` | Optional Policy constraint | Category does not own this |

**Do not invent** Customer Role on Policy Scope. Missing Role on Policy = unconstrained (treated as ANY) for Role check.

## Compatibility statuses

- `COMPATIBLE` — Policy Scope fully covers Category mandatory dimensions; no unproven extra Policy constraints
- `INCOMPATIBLE` — at least one mandatory coverage check failed
- `NEEDS_ADDITIONAL_SCOPE_CONTEXT` — Policy has additional REQUIRED-looking scope (segment / secured / scheme / facility) that Category cannot prove

## Mandatory coverage rules

1. **Customer Role** — if Policy declares Role list in metadata → Category Role must be in list or Policy ANY; else unconstrained → pass  
2. **Entity Type** — Policy `borrowerTypes` / legacy must cover Category Entity Type (`ANY` Category → pass; empty Policy → ANY)  
3. **Product** — Policy `products` must contain Category product (token-normalized); empty Policy products → ANY  
4. **Amount** — Policy must cover **entire** Category range (partial overlap ≠ enough). NULL Policy bound = unbounded that side. Inclusive.  
5. **Effective period** — Category `[from, until]` must fall within Policy `[from, until]`. Category open-ended end requires Policy open-ended end (no hidden future invalidity).

## Typed save / activation reasons

Top-level: `POLICY_SCOPE_INCOMPATIBLE`

Detail reasons: `CUSTOMER_ROLE_NOT_COVERED`, `ENTITY_TYPE_NOT_COVERED`, `PRODUCT_NOT_COVERED`, `AMOUNT_RANGE_NOT_COVERED`, `EFFECTIVE_PERIOD_NOT_COVERED`, `ADDITIONAL_SCOPE_CONTEXT_REQUIRED`

## Single authority

`CustomerCategoryPolicyScopeCompatibility` — used by Policy picker, DRAFT save bind validation, and activation readiness. No divergent definitions.
