# Customer Category Step 1 — Implementation Note (Part A)

**Scope:** Schema + domain + safe seed only. Live underwriting unchanged.

## Verified live sources (actual fields)

### `underwriting_rule_sets` (`UnderwritingRuleSet`)

| Column | Used for seed? | Notes |
|--------|----------------|-------|
| `id` | YES | Stable seed key (`CC_SEED_<uuid>` / `PS_SEED_<uuid>`) |
| `name` | YES | Display / description only |
| `borrower_type` | YES | Exact enum name; blank → `ANY` |
| `loan_product` | YES | Exact product code; blank → `ANY` |
| `min_amount` / `max_amount` | YES | NULL = unbounded (same as category semantics) |
| `geography` | NO | Not an approved Phase-1 category dimension |
| `min/max_tenure_months` | NO | Not approved |
| `priority` | NO | Live UW ranking only; categories have no priority |
| `active` | YES | Seed only from `active = true` |
| `rules_json` | NO | Not copied into Policy Set |

**Absent on rule sets:** `intake_segment`. Live rule-set matching also does not filter intake → seed uses literal `ANY` with `REVIEW_REQUIRED` + inference note `ABSENT_ON_RULE_SET`.

### `underwriting_scorecards`

Attached on seed only when **exactly one** ACTIVE scorecard matches the same `borrower_type` + `loan_product`. Otherwise `scorecard_id = null` and inference `NEEDS_REVIEW`.

### Not invented

No fabrication of geography, tenure, or intake constraints beyond the `ANY` semantics that already match live UW for intake.

## Persistence decision (Part D)

**Separate link table: NO.**

`customer_category.policy_set_id` FK → `policy_set.id` (exactly one Policy Set). Versioning/audit live on both entity tables via `code` + `version_no` + lifecycle columns.

## Lifecycle

Both Policy Set and Customer Category: `DRAFT` → `ACTIVE` → `RETIRED`. Seed creates **DRAFT only**. ACTIVE matching criteria are immutable in place.
