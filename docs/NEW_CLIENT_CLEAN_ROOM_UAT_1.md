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

```
SOURCE START SHA: b82548e36b3b5ae3b9ca9c9ffd88871980d45468
SOURCE FINAL SHA: be131dbdf5d4403d05d188a1e11d4554f8dd0d52
GITHUB REMOTE SHA: be131dbdf5d4403d05d188a1e11d4554f8dd0d52

CLIENT CLEAN CONFIGURATION ACHIEVED: YES (STARTER LOAN + BANK STARTER ACTIVE; Day-1 remains DRAFT)
LEGACY CLIENT LENDER CONFIG REMOVED/ISOLATED: YES (Day-1 not activated; Live UW Rules + Policy Sets hidden on CLIENT nav)
FOUNDATION DATA PRESERVED: YES (GACAT 169, workflows, bureau/KYC foundations, Flyway 138)

LIVE UNDERWRITING RULES VISIBLE TO CLIENT: NO (hidden when surface=CLIENT / CLIENT_TEST)
POLICY SET VISIBLE TO CLIENT: NO (same)
POLICY IS SOLE LENDER-FACING UW AUTHORITY: YES (Policy Studio + catalogue bind; no lender Policy Set authoring in Client nav)

GACAT REUSED: YES
WORKFLOW REUSED: YES (Default Individual BUSINESS_TERM_LOAN v2 — b0000000-0000-0000-0000-000000000011)
WORKFLOW MODIFIED: NO
IF MODIFIED, WHY: n/a
NEW WORKFLOW ENGINE CREATED: NO

STARTER LOAN CREATED: YES (CC_CLEAN_STARTER_LOAN ACTIVE)
BANK STARTER CREATED: YES (CC_CLEAN_BANK_STARTER ACTIVE)
IDENTICAL DIMENSIONS VALID: YES (BORROWER/INDIVIDUAL/BUSINESS_TERM_LOAN/20k–500k)
SHARED WORKFLOW USED: YES
IF NOT, WHY: n/a

POLICY A CREATED: YES (doc 34e80644… — bureau.score, obligation.ratio/FOIR, application.declared_income)
POLICY B CREATED: YES (doc a622c7da… — bureau.score, banking.avg_daily_balance_3m, banking.monthly_obligation)
POLICY→SCORECARD LINK: YES (scorecard 0b21ff01… linked to Policy A)
POLICY_WEIGHTED_V2 USED: YES
RAW WEIGHTS NORMALIZED: YES (5/3/2 → 50%/30%/20%; rawWeightsMustTotal100=false)

CATEGORY→POLICY VERSION: YES (exact applicability + document + v1 label locked on selection)
CATEGORY→WORKFLOW VERSION: YES (workflowId + version 2 locked; source CATEGORY_SELECTION / AUTO_SINGLE_ELIGIBLE)

CATEGORY SELECTION TESTED: YES
PROGRESSIVE DISAMBIGUATION TESTED: YES (FINANCIAL_DATA_ROUTE → STARTER or BANK)
EXPLICIT SELECTION TESTED: YES (BOTH → EXPLICIT_PROPOSITION_SELECTION_REQUIRED → select STARTER)
NO-MATCH TESTED: YES (PERSONAL_LOAN → NO_ELIGIBLE_CATEGORY)

TEST APPLICATION CREATED: YES (multiple; e.g. 100661aa… STARTER path)
SELECTED CATEGORY VERSION LOCKED: YES
SELECTED POLICY VERSION LOCKED: YES
SELECTED WORKFLOW VERSION LOCKED: YES

CUSTOMER REQUIREMENTS GENERATED: YES (W5 — Declared income DIRECT_INPUT)
DIRECT INPUT TESTED: YES (PROVIDED + PROCESSING)
DOCUMENT UPLOAD TESTED: NOT IN THIS RUN (DIRECT_INPUT path exercised; document path available via W5 modes when planned)
DOCUMENT PROVIDED != DATA READY VERIFIED: YES (customerFulfilment=PROVIDED, dataReadiness=PROCESSING)

KYC EXECUTION: NOT AUTO-TRIGGERED BY CATEGORY LOCK (existing Workflow/KYC orchestration unchanged; not invoked in this controlled API UAT)
W4 PLANNER: YES (plan-from-policy; inventory 3 GACAT params)
W5 CUSTOMER REQUIREMENTS: YES
W6 ACQUISITION: PARTIAL (coordinator dry-run/gate exercised; bureau acquire hit UnexpectedRollbackException on live acquire — gate remained fail-closed)
COMPLETENESS GATE: YES (WAITING_FOR_CUSTOMER → ACQUISITION_IN_PROGRESS; policyAutoExecuted=false)

POLICY TEST/SHADOW EXECUTED: YES (staging-demo test/quick + applications/{id}/shadow-route; allowCanonicalAuthority=false)
SCORECARD TEST/SHADOW EXECUTED: YES (weight-preview POLICY_WEIGHTED_V2 normalize; linked scorecard mode=POLICY_WEIGHTED_V2)
CAM READINESS: NOT DEMONSTRATED
KFS READINESS: NOT DEMONSTRATED
LMS HANDOFF TESTED: NO
REAL DISBURSEMENT PERFORMED: NO

WORKFLOW GAPS FOUND:
- Category lock does not auto-invoke W4/W5/W6 or KYC (handoff only) — intentional for this programme; product UX may need an explicit “continue journey” later
POLICY GAPS FOUND:
- Two ACTIVE catalogue schedules for same product/amount collide (409) — Policy B left APPROVED (still bindable); schedule coexistence needs scope/date hygiene
GACAT/SOURCE GAPS FOUND:
- Bureau auto-source not READY in Client staging (expected without certified provider secrets)
CATEGORY UX GAPS FOUND:
- allowDraftSimulation omitted IN_REVIEW (fixed in be131db)
- Administration copy previously referenced Policy Set (fixed for Client story)
POST-DECISION GAPS FOUND:
- CAM/KFS/LMS not part of this controlled stop point

DB MIGRATION: NONE (still V138)
FLYWAY INTERNAL: 138
FLYWAY CLIENT: 138

TESTS:
- ui-service customerCategoryAdminUi.test.ts (13) PASS
- CleanClientLenderNavContractTest added
- Existing CategorySelectionGoldens / W4–W6 / DP-3 / Policy Studio suites not fully re-run on CI in this session; staging E2E API goldens exercised manually on Client
REGRESSION: Internal/Client actuators healthy on same SHA; Day-1 ACTIVE count remains 0

INTERNAL ACTUATOR SHA: be131dbdf5d4403d05d188a1e11d4554f8dd0d52
CLIENT ACTUATOR SHA: be131dbdf5d4403d05d188a1e11d4554f8dd0d52
SAME ARTIFACT: YES
DATABASES ISOLATED: YES (CC_CLEAN* only on Client; Internal clean count=0)

ARCHITECTURE VIOLATIONS FOUND: NONE intentional
- No new Workflow/KYC/GACAT engines
- No live UW authority cutover (productionAuthority stays DISABLED / allowCanonicalAuthority=false)
- Product Config not used as runtime router
- No Day-1 Category activation

READY FOR HUMAN NEW-LENDER UAT: YES (Client :8085 UI + :8084 API; nav story Data→Workflow→Policy Studio→Categories)
READY FOR POLICY RUNTIME BRIDGE: NO (shadow/test only; do not enable live authority)
READY FOR SHADOW END-TO-END: PARTIAL (shadow-route + quick test OK; full data readiness blocked on bureau/banking sources)
READY FOR LIVE AUTHORITATIVE ROUTING: NO

RECOMMENDED NEXT STEP:
1) Human walk Client Administration as a new lender (confirm Live UW Rules / Policy Sets absent; configure via Policy Studio + Categories).
2) Harden W6 bureau/banking stubs for Client staging so completeness can reach READY_FOR_POLICY without live providers.
3) Optional product gap: explicit post-selection “build requirement plan” affordance (still calling existing W4 — not a new engine).
4) Do NOT cut over production underwriting authority.

STOP.
```

### Gap-fix shipped this task

| Change | Why existing component was insufficient |
|--------|----------------------------------------|
| CLIENT nav filter for `/underwriting-rules` + `/policy-sets` | Catalogue still required for Internal; Client lender story must not present transitional authorities |
| Lending Configuration grouping + copy | Nav did not tell Data → Workflow → Category → Policy Studio story |
| `los.category-selection.allow-draft-simulation: true` (staging) | Needed for clean-room governance without activating Day-1 |
| UI passes `allowDraftSimulation` on staging | Panel otherwise only saw ACTIVE |
| Eligibility includes `IN_REVIEW` under draft simulation | Submit→approve gap left Categories invisible mid-governance |
| Clean Client Policies/Categories/Scorecard via existing APIs | Client had 0 Policy catalogue rows |

Script: `los-core-service/scripts/clean-client-uat-1.sh`

