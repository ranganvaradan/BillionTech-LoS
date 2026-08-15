# NEW-CLIENT-CLEAN-ROOM-UAT-1

**Mode:** Controlled configuration + gap-fix + end-to-end UAT (Client surface)  
**Start SHA:** `b82548e36b3b5ae3b9ca9c9ffd88871980d45468`  
**Flyway at start:** V138  
**Does not:** live UW authority cutover, Day-1 15 Category activation, Internal/Client DB merge, real disbursement, new Workflow/KYC/GACAT engines

---

## 1. First audit (before configuration)

### A. What exists on Client (`los_core_client` / `:8084`)

| Area | Finding |
|------|---------|
| Flyway | **138** (same as Internal) |
| Actuator | `b82548e…` (same artifact as Internal) |
| Applications | **0** |
| Customer Categories | **15** Day-1 seed, all **DRAFT**, **no** Policy/Workflow binds |
| Policy Sets | **12** DRAFT (transitional) |
| Underwriting rule sets | **44** (runtime foundation / transitional) |
| Scorecards | **45** (41 ACTIVE / 4 DRAFT) — legacy production path |
| Workflows | **39** total / **35** active — platform foundation |
| GACAT (`ci_gacat_canonical_parameter`) | **169** — foundation |
| Policy Studio documents | **8** orphan/demo docs |
| Policy applicability / versions / packages | **0 / 0 / 0** — **no lender Policy catalogue** |
| CI tables | **134** present (platform) |

Internal (`los_core_staging`) has Policy catalogue rows (applicability=13, versions=7) — **must not be copied into Client**.

### B. Classification

| Object | Class |
|--------|--------|
| GACAT + source bindings | Foundation — **preserve** |
| Canonical workflows (e.g. Individual BUSINESS_TERM_LOAN) | Foundation — **reuse** |
| Bureau/KYC/provider foundations | Foundation — **preserve** |
| Day-1 15 Categories (DRAFT, unbound) | Day-1 seed / demo — **leave DRAFT; do not activate** |
| Policy Sets / Live UW rule sets | Transitional runtime — **keep tables; hide from Client lender UI** |
| Orphan `ci_policy_document` rows | Legacy/demo — leave; create new CLEAN UAT docs |
| Flyway / system config | Infrastructure — **preserve** |

### C. Safe for clean-lender simulation

- Create **new** Policy Studio docs + catalogue applicability (Client only)
- Create **STARTER LOAN** + **BANK STARTER** Categories (new codes)
- Create test applications
- Do **not** truncate GACAT, workflows, rule sets, scorecards, migrations

### D. Must remain

GACAT, workflows, bureau/KYC foundations, migrations, Live UW tables (backend), Internal DB isolation.

### E. Lender UI surfaces (pre-change)

Administration › Decision Configuration exposed **Live Underwriting Rules** and **Policy Sets** on both surfaces via `workspaceNav.ts` / `AdministrationPage.tsx` — **no surface filter**. Customer Categories copy still referenced Policy Set in places.

---

## 2. Gap-fixes (minimal)

1. **CLIENT-only hide** of Live Underwriting Rules + Policy Sets from Administration nav/page (routes remain; Internal unchanged).
2. **Nav story:** Lending Configuration group = Data & Parameters → Workflows → Customer Categories (+ Product Config as readiness, not router).
3. **Staging** `los.category-selection.allow-draft-simulation: true` + UI passes `allowDraftSimulation` when staging demo enabled.
4. Labels: Categories → Policy Version + Workflow Version; Live Scorecards marked legacy.

**No new Workflow engine. No W4 auto-trigger after Category lock** (existing handoff + manual `plan-from-policy`; documented as UX gap, not redesigned).

---

## 3. Clean configuration (Client)

| Proposition | Dimensions | Policy | Workflow |
|-------------|------------|--------|----------|
| STARTER LOAN | BORROWER / INDIVIDUAL / BUSINESS_TERM_LOAN / ₹20k–₹500k | Policy A (bureau + FOIR + income) | Shared: Default Individual BUSINESS_TERM_LOAN v2 |
| BANK STARTER | **identical** | Policy B (bureau + banking balances/credits) | **Same** Workflow Version |

Disambiguation: SAFE `FINANCIAL_DATA_ROUTE` → FINANCIAL_STATEMENTS vs BANK_AA.

Script: `los-core-service/scripts/clean-client-uat-1.sh`

---

## 4. Final report

*(Filled after ship + UAT run — see bottom of this file after execution.)*
