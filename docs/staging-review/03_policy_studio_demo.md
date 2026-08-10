# 03 — Policy Studio demo

**Route:** `/credit-intelligence/policy-studio`  
**API:** `GET /api/v1/internal/credit-intelligence/staging-demo/policy-studio/{banking|bureau}`

## Layout

Three columns:

| SOURCE POLICY | INTERPRETATION | EXECUTABLE RULE |
|---------------|----------------|-----------------|
| Clause text from Banking/Bureau BRE fixtures | Classification, ambiguities, mappings | Candidate rule IDs, DSL shape, tests, review status |

## Tabs

- Banking BRE (Starter, DigiLeap, Smart Switch, Reboost, inward returns, adjusted ADB)  
- Bureau BRE (score/NTC, write-off, overdue exception, DPD, statuses, inquiries, …)

Unresolved ambiguities are highlighted. Customer answers are **not** invented.

Ambiguity resolve via full Policy Studio APIs remains available; staging demo resolve endpoint returns guidance (501) to use those APIs when no persisted session exists.
