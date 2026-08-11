# LOS-CUSTOMER-CUTOVER-1 — Billionloans (final business decisions applied)

**Customer:** Billionloans Financial Services Private Limited  
**Deployment:** SINGLE_CLIENT_DEPLOYMENT / `SINGLE_TENANT_DEPLOYMENT`  
**Target go-live:** 1 September 2026  
**customerConfigSupplied (business):** true  
**allowCanonicalAuthority:** false · Policy Studio = GOVERNANCE/SHADOW  

**Gate status:** Day-1 routing certified for listed ranges; **SAFE TO GO LIVE = NO** (providers, RISK_MANAGER user, Individual×ID×Anchor WF, full goldens B–F).

**Product capabilities removed this gate:** **0**

---

## Final business decisions (recorded)

| Decision | Outcome |
|----------|---------|
| Business Loan | `BUSINESS_TERM_LOAN` |
| LLP | **Out of Day-1** — backlog; do not add domain in this gate |
| Products | TERM_LOAN, BUSINESS_TERM_LOAN, BUSINESS_WC_INVOICE_DISCOUNTING |
| Amount | Aspiration ₹30k–₹50cr; **certify only existing bands** |
| Intake | Borrower direct + RM-assisted (same BORROWER WF) |
| Invoice channels | **BORROWER + ANCHOR** (both required) |
| Dual rule/scorecard | Preserve; document deterministic runtime |
| LMS | Show configured codes; backlog separate Encore codes |
| PLP | Not Day-1 dependency unless route requires; DRAFT programs ≠ prod |
| MANUAL_AUTHORISED | No blanket enable |
| eSign | Optional Day-1; capability preserved |
| Sanction role | `RISK_MANAGER` — user must exist (currently **0**) |

---

## Ambiguity audit (no deactivation)

### PROPRIETOR × TERM_LOAN — 2 ACTIVE Live Rule Sets

| Set | Priority | Amount cols | JSON semantics |
|-----|----------|-------------|----------------|
| Default policy — PROPRIETOR — TERM_LOAN | 100 | 50k–50M | MANUAL_REVIEW, minBureau 650, requireKyc |
| Proprietor Term — small business | 80 | (none = all) | MANUAL_REVIEW, minBureau 660, maxLoanAmount 5M in JSON |

**Runtime:** `UnderwritingRuleEngine.evaluateAll` runs **all** matching sets; aggregate REJECT > MANUAL > APPROVE.  
**Combined:** both MANUAL_REVIEW → aggregate MANUAL_REVIEW when both match; stricter bureau intent 660 when small-business set matches.  
**Verdict:** intentional cumulative soft policies — **READY** (deterministic). **Not deactivated.**

### COMPANY × BUSINESS_TERM_LOAN — 2 ACTIVE scorecards

| Card | Priority | Band |
|------|----------|------|
| SME — Company | 200 | ₹5L–₹1cr |
| Default scorecard — COMPANY — BUSINESS_TERM_LOAN | 100 | ₹50k–₹5cr |

**Runtime:** priority DESC then amount scope.  
- ₹50k–₹5L → Default · ₹5L–₹1cr → SME · ₹1cr–₹5cr → Default  
**Product Config:** after intake-scoped conflict fix, dual scorecards are **informational** when PC matches runtime. **Not deactivated.**

### Invoice Discounting BORROWER vs ANCHOR

| Type | BORROWER WF | ANCHOR WF |
|------|-------------|-----------|
| COMPANY | MATCH | MATCH |
| PROPRIETOR | MATCH | MATCH |
| PARTNERSHIP | MATCH | MATCH |
| INDIVIDUAL | MATCH | **NO MATCH** (no Anchor WF row) |

Product Config previously counted BORROWER+ANCHOR as one ambiguity — **fixed** (intake filter). Capabilities preserved.

---

## Day-1 routing matrix (READY = compose ready @ certified range)

Certified ranges = intersection of ACTIVE scorecard bands ∩ rule amount filters (not the ₹30k–₹50cr aspiration).

| Borrower | Product | Channel | Certified ₹ | WF | Live Rules | Scorecard | LMS | PLP | Status |
|----------|---------|---------|-------------|----|------------|-----------|-----|-----|--------|
| INDIVIDUAL | TERM_LOAN | BORROWER | 50k–5cr | Default Individual Term v2 | Default policy | Default sc | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| INDIVIDUAL | BUSINESS_TERM_LOAN | BORROWER | 50k–5cr | Default Individual BTL v2 | Default policy | Default sc | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| INDIVIDUAL | ID | BORROWER | 50k–1cr | Default Individual ID v1 | SCF policy | SCF sc | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| INDIVIDUAL | ID | ANCHOR | — | — | SCF policy exists | SCF sc exists | — | — | **NOT READY** — NO_WORKFLOW |
| PROPRIETOR | TERM_LOAN | BORROWER | 50k–5cr | Default Proprietor Term v2 | 2 sets (cumul.) | MSME@mid / Default bands | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| PROPRIETOR | BUSINESS_TERM_LOAN | BORROWER | 50k–5cr | Default Proprietor BTL v4 | Default policy | Default sc | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| PROPRIETOR | ID | BORROWER | 50k–1cr | Default Proprietor ID v4 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| PROPRIETOR | ID | ANCHOR | 50k–1cr | Anchor ID Proprietor v1 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| PARTNERSHIP | TERM_LOAN | BORROWER | 50k–5cr | Default Partnership Term v2 | Default | Default | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| PARTNERSHIP | BUSINESS_TERM_LOAN | BORROWER | 50k–5cr | Default Partnership BTL v2 | Default | Default | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| PARTNERSHIP | ID | BORROWER | 50k–1cr | Default Partnership ID v1 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| PARTNERSHIP | ID | ANCHOR | 50k–1cr | Anchor ID Partnership v1 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| COMPANY | TERM_LOAN | BORROWER | 50k–5cr | Default Company Term v2 | Default | Default v2 ACTIVE | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| COMPANY | BUSINESS_TERM_LOAN | BORROWER | 50k–5cr | Default Company BTL v2 | Default | Default@&lt;5L / SME@5L–1cr | IPPOPAYM01 | NOT_REQUIRED | **READY** |
| COMPANY | ID | BORROWER | 50k–1cr | Default Company ID v1 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |
| COMPANY | ID | ANCHOR | 50k–1cr | Anchor ID Company v5 | SCF | SCF | IPPOPAYM01 | NOT_REQUIRED* | **READY** |

\*PLP programs exist as DRAFT with `plp_program_id` — **PLP product capability PRESERVED**; Day-1 route classified PLP NOT_REQUIRED for execution until production PLP programs are configured. Do not disable PLP.

**OUT OF CERTIFIED RANGE:** amounts &lt; ₹50,000 or &gt; certified max → Product Config match=false / NOT CERTIFIED (proven for COMPANY TERM_LOAN: 49999 and 50000001).

**RM-assisted:** same BORROWER workflow — SUPPORTED_AND_CERTIFIED (channel overlay).

---

## Amount boundary proof (COMPANY × TERM_LOAN)

| Amount | ready | PC↔runtime match |
|--------|-------|------------------|
| 49,999 | false | false |
| 50,000 | true | true |
| 250,000 | true | true |
| 50,000,000 | true | true |
| 50,000,001 | false | false |

---

## Required data / providers (Day-1 Term/Business typical)

Compose requiredParameters: `kyc.quality` (DERIVED/KYC), `bureau.score` (RAW/Bureau).  
Providers: **PRODUCTION_CREDENTIALS_REQUIRED** (not proven this gate).  
MANUAL_AUTHORISED: **none broadly enabled** — inventory empty for blanket; capture only if explicitly staged later.

---

## LMS

All Day-1 active workflows: `IPPOPAYM01` from `WORKFLOW_LMS_PRODUCT_CODE`.  
Backlog: three distinct production Encore codes when Billionloans supplies them.  
Fail-closed: `LMS_PRODUCT_MAPPING_MISSING` preserved.

---

## Goldens

| ID | Result |
|----|--------|
| A COMPANY TERM_LOAN underwrite | **PASSED** — APPROVED → CAM_READY; eval `394a56db-…`; snapshot present; app `e0e892ab-…` |
| B MANUAL REVIEW | Unit ScorecardSafety / dual Proprietor rules semantics — staging app-specific not re-run |
| C HARD REJECT | Unit ScorecardSafety GOLDEN F — staging not re-run |
| D MISSING DATA | Unit GOLDEN D/E — no synthetic approve |
| E LMS missing | Code path `LMS_PRODUCT_MAPPING_MISSING` + LmsProductMappingP0Test |
| F ID BORROWER | **NOT RUN** — no staging borrower ID application fixture |
| G ID ANCHOR | Config READY; standard `/underwrite` correctly returns **422 UNDERWRITING_NOT_APPLICABLE** → Anchor uses due-diligence/credit-rating path (capability preserved). Not a Term-Loan-style UW golden. |
| Snapshot repro | MD5 stable on re-read `1aab9d94754127de2e63c55817406cb3`; unit HistoricalDecisionReproducibilityTest |

---

## RBAC / RISK_MANAGER

`los_users` with `primary_los_role=RISK_MANAGER`: **0**  
→ CUSTOMER CONFIG action: create/assign RISK_MANAGER before launch. Not a platform defect.

---

## Product capability preservation

| Capability | Before | After | Preserved | Day-1 status |
|------------|--------|-------|-----------|--------------|
| Borrower types (4+enum) | present | present | YES | LLP OUT_OF_DAY1; others CERTIFIED where routed |
| Loan products catalog | present | present | YES | 3 CERTIFIED; others SUPPORTED_NOT_YET_CERTIFIED |
| BORROWER / ANCHOR WFs | present | present | YES | Anchor Individual ID CONFIGURATION_REQUIRED |
| RM-assisted | present | present | YES | CERTIFIED via BORROWER WF |
| Policy Studio | present | present | YES | SHADOW |
| Live Rule Sets / Scorecards | present | present | YES | multi-config PRESERVED |
| GACAT | present | present | YES | — |
| Providers / Manual / CAM / Sanction / KFS | present | present | YES | — |
| eSign | present | present | YES | OPTIONAL Day-1 |
| LMS / PLP / Disbursement | present | present | YES | PLP DRAFT; disbursement out of goldens |
| Notifications / Audit / Product Config | present | present | YES | — |

**PRODUCT CAPABILITIES REMOVED: 0**

---

## Code change this continuation

`ProductConfigurationComposeService.detectActiveConflicts`: intake-scoped workflow uniqueness; dual scorecards/rulesets informational when deterministic; only true blockers fail READY.  
`CustomerGoLiveReadinessValidator`: Day-1 layers, liveRuleSetId fix, Billionloans metadata.

---

## Remaining P0 (customer cutover)

1. Production provider credentials (KYC, Bureau, LMS) for Day-1  
2. Create/assign at least one **RISK_MANAGER** user  
3. Individual × Invoice Discounting × **ANCHOR** workflow (config) — or accept OUT_OF_DAY1 for that cell only  
4. Complete goldens B/C/D/F on staging fixtures  
5. Prod-profile go-live validator (staging profile fails SECURITY_* by design)

## Remaining P1

- Separate production Encore product codes (3)  
- PLP DRAFT → production if ID needs PLP later  
- eSign production certification (optional)  
- Amount aspiration ₹30k / ₹50cr scorecard widening (business product change — not this gate)  
- LLP domain support (post-launch backlog)

---

## Human UI walkthrough (≤15 steps)

1. Login as Credit Manager / Admin  
2. Admin → Product Configuration → select COMPANY / TERM_LOAN / BORROWER / amount ₹2,50,000  
3. Confirm Workflow, Live Rule Set, Scorecard, LMS `IPPOPAYM01`, Policy Studio = shadow  
4. Create/open borrower application (borrower direct or RM-assisted — same BORROWER WF)  
5. Complete KYC / bureau acquisition steps in workflow  
6. Run Underwrite → expect decision + CAM_READY on approve path  
7. Open evaluation → Decision snapshot / historical explanation (`DECISION_SNAPSHOT`)  
8. CAM as Credit Manager  
9. Sanction as **RISK_MANAGER** (user must exist) → KFS/docs (eSign optional)  
10. Confirm LMS mapping on workflow before any open-account  
11. Admin → Product Configuration → COMPANY / Invoice Discounting / **ANCHOR**  
12. Confirm Anchor WF “Anchor ID — Company — Invoice Discounting”  
13. Open Anchor ID application → underwrite boundary only — **no disbursement**  
14. Audit log / admin audit for the application  
15. Record go-live checklist: providers + RISK_MANAGER + backup SHA  

---

## Validator layers (staging probe)

PLATFORM_READY: NO (staging demo flags) — prod dry-run prior gate YES  
CUSTOMER_CONFIG_READY: YES  
PROVIDER_READY: NO  
ROUTE_READY: PARTIAL (15/16 Day-1 cells)  
GOLDENS_READY: PARTIAL (A + amount + snapshot)  
OPERATIONS_READY: NO (RISK_MANAGER=0)
