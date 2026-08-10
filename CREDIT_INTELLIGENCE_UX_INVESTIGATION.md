# Credit Intelligence UX Investigation

**Date:** 2026-08-08  
**Scope:** Ambiguous Terms resolution UI, missing-measure / Business Measure creation path, relationship to `LOS_APP-credinnov`  
**Mode:** Read-only investigation (no product code changes)  
**Repos root investigated:** `D:\CurrentAug032025\Platform\BillionTechLoS`

Screenshots: **not captured** — see Part A §4 and Part B §4.

---

## PART A — Ambiguous Terms resolution screen

### A1. Frontend component(s)

| Role | Path |
|------|------|
| Tab chrome / host | `ui-service/src/pages/creditIntelligence/CiPolicyStudioPage.tsx` — tab id `'ambiguities'`, nav label **"Ambiguous Terms"** (line 52); renders tab at ~788–795 |
| Resolution UI | `ui-service/src/pages/creditIntelligence/CiPolicyAmbiguitiesTab.tsx` |
| Card payload builder (backend → UI) | `los-core-service/src/main/java/com/los/core/creditintelligence/staging/ProspectDay2ViewBuilder.java` (`toAmbiguityCard`, `choicesFor`, `labelForOption`, `whyNeeded`, …) |
| Ambiguity detection (fixture terms) | `los-core-service/src/main/java/com/los/core/creditintelligence/policystudio/service/AmbiguityDetector.java` |
| Resolve API wiring | `StagingPolicyStudioDemoService.resolveAmbiguity` → `PolicyReviewService.resolveAmbiguity` |

Route: Policy Studio → session → **Ambiguous Terms** tab  
(`http://88.198.239.170/credit-intelligence/policy-studio` after opening Banking/Bureau demo or upload).

### A2. Literal on-screen copy (from frontend source)

Static chrome and action labels from `CiPolicyAmbiguitiesTab.tsx` (verbatim):

**Page / section**
- Executive summary title: `"Summary"`
- When open items exist: `` `${openCount} item${…} need Credit Head confirmation before rules can be approved.` ``
- Next-step line: `"Next: resolve blocking items first."`
- Section title: `"Ambiguous business terms"`
- Section description: `"Confirm unclear policy language. Decision required on each open item."`
- Filters: `"All"`, `"Blocking"`, `"Non-blocking"`, `"Resolved"`

**Per-card field labels**
- `"Business term"`
- `"Blocking"` / `"Resolved"` (status chips)
- `"Source clause"`
- `"Why AI is unsure"`
- `"AI understanding"`
- `"Recommended interpretation:"`
- `"Confidence:"`
- `"Verified business mapping"`
- `"Business impact if unresolved"`
- `"Decision required from Credit Head"`
- `"Alternative interpretations"`
- Badge on recommended radio: `"Recommended"`

**Remember / scope help**
- Checkbox: `"Remember this definition for future policies"`
- Default helper (if API omits `vocabularyNote`):  
  `"Appears as a previous approved suggestion — not automatically authoritative."`
- Scope options: `"This Policy Only"`, `"This Product"`, `"This Lender/Tenant"`

**Button labels (verbatim)**
- `"Accept Recommendation"`
- `"Select Different Meaning"`
- `"Create new business measure"`
- `"Create policy parameter"`
- `"Ask Customer / Keep Unresolved"`
- `"View Original Clause"` / `"Hide original clause"`
- Technical: `"Technical Details"` (via `CiTechnicalDetails`; hidden in Prospect Demo Mode)

**Empty state**
- Title: `"No items match this filter"`
- Detail: `"Try All or Blocking, or continue to proposed business rules when open items are cleared."`

**Dynamic prompt / choice text** is **not** hard-coded in the React file. It is rendered from API fields:

- `unclearTerm`, `whyConfirmationNeeded`, `systemInterpretation`, `recommendedLabel`, `canonicalMappingBusiness`, `impactIfUnresolved`, `policyContext.headline` / `bullets`, and each `choices[].label` / `businessMapping`

Built in `ProspectDay2ViewBuilder.toAmbiguityCard` (approx. lines 132–174).

### A3. Real fixture example — Bureau BRE (NTC / CLEAN / DBT / PWOS / LSS)

**Source of truth in tests:**  
`BureauBreGoldenInterpretationTest.ambiguitiesForNtcCleanDbtPwosLssAndMissingMetrics`  
(`los-core-service/src/test/java/com/los/core/creditintelligence/policystudio/BureauBreGoldenInterpretationTest.java` lines 50–61) asserts ambiguities for **NTC**, **CLEAN**, **DBT**, **PWOS**, **LSS**, plus ≥3 `MISSING_METRIC`.

**Detection seeds** (`AmbiguityDetector.java` 127–182), e.g. NTC:

```text
phrase: NTC
type: UNKNOWN_BUSINESS_TERM
description: NTC canonical representation may differ across Equifax / SurePass CIBIL / CRIF — use bureau.status_ntc
candidateOptions: bureau.status_ntc, provider_raw_ntc, ASK_CUSTOMER
recommended: bureau.status_ntc
```

CLEAN:

```text
phrase: CLEAN string
candidateOptions: DPD_EQ_0, DPD_LTE_30, NO_ADVERSE_BUREAU_STATUS, CUSTOMER_COMPOSITE_RULE, OTHER
recommended: ASK_CUSTOMER
```

**Live staging sample** (fetched 2026-08-08 from  
`GET http://88.198.239.170/api/v1/internal/credit-intelligence/staging-demo/policy-studio/bureau`,  
`allowCanonicalAuthority=false`, 10 ambiguity cards):

#### Example 1 — NTC (verbatim API → UI labels)

| Field | Value |
|-------|--------|
| Business term | `NTC` |
| Type / category | `Unclear business term` / `Terminology` |
| Why AI is unsure | `NTC (new-to-credit) flags differ across bureau providers. Confirm which representation this lender uses.` |
| AI understanding | `Score allowed only if -1 OR NTC OR score >= 650 (not simply >=650)` |
| Recommended interpretation | `Bureau NTC status flag` (`bureau.status_ntc`) |
| Business impact if unresolved | `Executable draft policy readiness remains reduced until this is resolved or parked for customer confirmation.` |

**Alternative interpretations presented:**

| Radio label (UI) | value |
|------------------|--------|
| Bureau NTC status flag **(Recommended)** | `bureau.status_ntc` |
| Provider-specific NTC code | `provider_raw_ntc` |
| Ask customer / keep unresolved | `ASK_CUSTOMER` |

#### Example 2 — CLEAN string

| Field | Value |
|-------|--------|
| Business term | `CLEAN string` |
| Why AI is unsure | `CLEAN history drives overdue exceptions. Different lenders treat CLEAN as zero DPD, mild delinquency tolerance, or a composite bureau status — we need your definition.` |
| Policy context headline | `CLEAN history is not defined in measurable terms` |
| Bullets | `Policy says: new loan should have 6 months CLEAN history`; `BillionTech will not choose a definition automatically.` |
| Impact | `Overdue exception rules that depend on CLEAN history cannot be approved.` |

**Choices (UI labels):**

1. `No DPD > 0` (`DPD_EQ_0`)  
2. `No DPD > 30` (`DPD_LTE_30`)  
3. `No adverse bureau status` (`NO_ADVERSE_BUREAU_STATUS`)  
4. `Customer-defined composite rule` (`CUSTOMER_COMPOSITE_RULE`)  
5. `Other` (`OTHER`)  
6. `Other` (`ASK_CUSTOMER`) — recommended option key is `ASK_CUSTOMER`; label collapses to `"Other"` for CLEAN via `labelForOption` (`ProspectDay2ViewBuilder.java` 296–304)

#### Example 3 — DBT / PWOS / LSS

Each presents a **single** choice:

- Label: `Ask customer / keep unresolved`  
- value: `ASK_CUSTOMER`  
- Recommended: true  

Why (DBT example): `DBT string meaning requires customer/vocabulary confirmation`

### A4. Screenshots

- **Playwright / Cypress / Puppeteer:** not configured in `ui-service/package.json` (scripts: `dev`, `build`, `lint`, `test`/`vitest`, `preview` only). No `playwright.config.*` or `cypress/` tree under BillionTechLoS.
- **Staging instance:** reachable over HTTP (`health` + bureau Policy Studio API succeeded).
- **Screenshot files:** **not created** (`ambiguous_terms_screen.png` absent). Standing up new browser automation solely for this ask was out of scope per instructions.

---

## PART B — Business Measures / missing-measure creation flow

### B1. Code path when a clause cannot match an existing measure

| Step | File | Method / notes |
|------|------|----------------|
| Detect missing measure as ambiguity | `.../policystudio/service/AmbiguityDetector.java` | `detectBureau` / banking settlement branch — `AmbiguityType.MISSING_METRIC`, recommended `NEW_METRIC_CANDIDATE` (e.g. lines 104–108, 165–182) |
| Create metric **candidates** on upload | `.../policystudio/service/MetricCandidateFactory.java` | `create(clauses, kind)` — stamps `metadata.NEW_METRIC_CANDIDATE=true`, expression often `{"op":"NEW_METRIC_CANDIDATE",...}` |
| Orchestrate on upload | `.../policystudio/service/PolicyStudioOrchestrator.java` | `processUpload` adds `metricCandidateFactory.create(...)`; also `createCustomMetric` (lines 271–291) |
| Surface on Day-2 cards | `.../staging/ProspectDay2ViewBuilder.java` | `MISSING_METRIC` → typeLabel `"Missing metric"`, category `"Missing Data"`; choices via `choicesFor` |
| Resolve “create new metric” from UI | `.../staging/StagingPolicyStudioDemoService.java` | maps `uiAction: "CREATE_NEW_METRIC"` → `AmbiguityResolutionAction.CREATE_NEW_METRIC` (~167) |
| Persist resolution | `.../policystudio/service/PolicyReviewService.java` | `resolveAmbiguity` case `CREATE_NEW_METRIC` (164–168): sets status `RESOLVED`, `resolvedOption`, `metricCreated=true` — **does not** open a designer form |
| Admin custom metric API | `.../policystudio/api/PolicyStudioAdminController.java` | `POST .../documents/{id}/metrics/custom` → `orchestrator.createCustomMetric` (163–175) |

Doc map: `docs/credit-intelligence-implementation/51_policy_vocabulary_and_ambiguity.md` — flowchart edge `CREATE_NEW_METRIC → Metric candidate`.

### B2. What is required to “define” a new measure today?

**Policy Studio Credit UI (Ambiguous Terms):**

There is **no** business-facing multi-field form (no parameter picker UI, no plain-language formula builder). Clicking **"Create new business measure"** posts:

```json
{
  "uiAction": "CREATE_NEW_METRIC",
  "resolvedOption": "CREATE_NEW_METRIC",
  "unclearTerm": "<phrase from card>",
  "notes": "Create new metric"
}
```

(`CiPolicyAmbiguitiesTab.tsx` 340–346.)

Backend effect (`PolicyReviewService` 164–168): marks ambiguity resolved and sets `metricCreated: true`. It does **not** collect a formula from the Credit Head in that step.

**Selecting a radio such as `NEW_METRIC_CANDIDATE` / a proposed code** uses `ACCEPT_RECOMMENDATION` / `SELECT_DIFFERENT_MEANING` → `SELECT_CANDIDATE`, storing the option string (e.g. `bureau.overdue.age_months` or `NEW_METRIC_CANDIDATE`).

**Engineering-shaped custom metric API** (not wired in Policy Studio React UI):

`PolicyStudioOrchestrator.createCustomMetric` body fields (271–287):

| Body key | Default / behaviour |
|----------|---------------------|
| `clauseId` | first clause UUID if omitted |
| `metricName` | `"CUSTOM_METRIC"` |
| `code` / `systemMetricId` | `"CUSTOM_METRIC"` |
| `expression` | **Map** (technical expression object); empty map if omitted |
| `exclusions` | List |
| `createdBy` | used to invalidate checker approval |

So a full custom measure via admin API **does** accept a technical `expression` map (not Java class name in the form, but still an engineering structure). **No Credit Manager form exists in `ui-service` for this endpoint.**

**Live missing-metric card example** (`overdue reporting date age` from staging bureau demo):

Choices shown:

1. `Overdue age months` [`bureau.overdue.age_months`]  
2. `Create new metric` [`NEW_METRIC_CANDIDATE`] **(Recommended)**  
3. `Ask customer / keep unresolved` [`ASK_CUSTOMER`]

Plus the five global action buttons from Part A (Accept Recommendation, Select Different Meaning, Create new business measure, Create policy parameter, Ask Customer / Keep Unresolved).

### B3. Who is authorized?

| Layer | Check | Finding |
|-------|--------|---------|
| Staging demo API | `StagingDemoController.assertInternalToken` | Requires `X-Internal-Token` when configured; if blank, allows call (local/staging). **No Spring `@PreAuthorize` role gate** on ambiguity resolve. |
| Default actor | `StagingPolicyStudioDemoService` | `resolvedBy` defaults to `"credit_manager"` |
| Review role constants | `PolicyReviewService` | `ROLE_CREDIT_MANAGER = "CREDIT_MANAGER"`, also `POLICY_AUTHOR`, `POLICY_CHECKER` — used for maker/checker approval flows, **not** as a hard RBAC deny on `CREATE_NEW_METRIC` |
| UI | Policy Studio Ambiguities tab | Available to the same logged-in Policy Studio user as other Credit Intelligence tabs (staging demo login); button is not hidden behind an “engineering-only” flag |

**Conclusion:** Completing “Create new business measure” / accepting `NEW_METRIC_CANDIDATE` is treated as a **Credit Manager / Policy Studio authoring** action in staging, not an admin-only RBAC surface. The separate `metrics/custom` admin endpoint is token-gated like other internal CI admin APIs and is **not** exposed in the Credit Head React flow.

### B4. Screenshots

Same as Part A: **no** repo browser automation; **`missing_measure_screen.png` not created**. Staging API confirms missing-metric cards exist on the Bureau demo (e.g. `overdue reporting date age`, `credit after overdue`, `clean history months`).

---

## PART C — Relationship to `LOS_APP-credinnov`

### C1. Same repo or separate?

| Artifact | Location | Relationship |
|----------|----------|--------------|
| **Credit Intelligence / Policy Studio (this investigation)** | `D:\CurrentAug032025\Platform\BillionTechLoS` | Primary codebase; packages under `com.los.core` including `com.los.core.creditintelligence.**` and legacy `com.los.core.service.underwriting.**` |
| **`LOS_APP-credinnov`** | **Not found** on disk under `D:\CurrentAug032025` (depth-limited search) | Name as given does not resolve to a directory |
| Closest similarly named tree | `D:\CurrentAug032025\Platform\PLP-APP-credinnov` | **Separate** “Program Lending Platform (PLP)” — Pay Day / Invoice Discounting microservices (`iam-service`, `lending-service`, …). **Not** the BillionTech LOS core |
| Sibling workspace | `D:\CurrentAug032025\Platform\AI-LOS` | Separate AI-LOS / existing-los tree; not the CI Policy Studio host used on staging `88.198.239.170` |

**Verdict:** Credit Intelligence Platform work lives in **BillionTechLoS**, which is **not** the same repository as `PLP-APP-credinnov`. No `LOS_APP-credinnov` checkout was present to compare git history.

Shared package root **within BillionTechLoS:** `com.los.core` (yes). That is internal to this LOS monorepo/service, not evidence of identity with PLP-APP-credinnov.

### C2 / C3. How CI relates to `UnderwritingRuleEngine` / `ScorecardPolicyEngine`

Both engines live **in the same BillionTechLoS tree**:

- `los-core-service/src/main/java/com/los/core/service/underwriting/UnderwritingRuleEngine.java`
- `los-core-service/src/main/java/com/los/core/service/underwriting/ScorecardPolicyEngine.java`

Per `docs/current-platform-review/01_what_we_have_built.md`:

- **Legacy production (authoritative):** `CreditControlService` → `UnderwritingRuleEngine` → `ScorecardPolicyEngine` → CAM / sanction.
- **Credit Intelligence (shadow / flags off):** separate path — Policy Studio packages, Policy Engine, Decision Engine, dual-run compare; **does not replace** live underwriting authority while `allowCanonicalAuthority=false`.

Architecture review (`docs/Architecture_Review_Before_Production.md`) describes CI as a **shadow dual-write sidecar** on the still-authoritative legacy stack; shadow evaluation historically still touches live `UnderwritingRuleEngine` / live rules in places — i.e. **alongside / dual**, not a cutover replacement.

**Integration with PLP-APP-credinnov:** none identified in this investigation (different product: program lending microservices vs BillionTech LOS + CI).

**Staging UI host for CI:** `88.198.239.170` serving BillionTechLoS `ui-service` + `los-core-service` staging-demo APIs.

---

## Summary table

| Question | Answer |
|----------|--------|
| Ambiguous Terms UI | `CiPolicyAmbiguitiesTab.tsx` hosted by `CiPolicyStudioPage.tsx` |
| Choice text origin | Backend `ProspectDay2ViewBuilder` + `AmbiguityDetector` |
| NTC choices (live) | Bureau NTC status flag; Provider-specific NTC code; Ask customer / keep unresolved |
| Missing measure UI | Same Ambiguities tab; type `Missing metric`; often recommends `NEW_METRIC_CANDIDATE` |
| Business form for new measure? | **No** dedicated Credit form; one-click resolve or radio select; admin custom metric API takes technical `expression` map |
| Role | Staging Credit Manager / authoring actor; not engineering-only RBAC on the button |
| Screenshots | **Not taken** (no Playwright/Cypress in repo) |
| vs LOS_APP-credinnov | Name not found; CI is **BillionTechLoS**; closest `PLP-APP-credinnov` is a **separate** PLP repo |
| vs UnderwritingRuleEngine | **Same** BillionTechLoS codebase; CI runs as **shadow / dual** path; production authority remains legacy engines |

---

## References (key citations)

- `ui-service/src/pages/creditIntelligence/CiPolicyAmbiguitiesTab.tsx` — UI copy & buttons  
- `ui-service/src/pages/creditIntelligence/CiPolicyStudioPage.tsx:52`, `:788+` — Ambiguous Terms tab  
- `los-core-service/.../ProspectDay2ViewBuilder.java:132–318`, `:376–491` — card text & choice labels  
- `los-core-service/.../AmbiguityDetector.java:104–182` — NTC/CLEAN/DBT/PWOS/LSS/MISSING_METRIC seeds  
- `los-core-service/.../PolicyReviewService.java:27–29`, `:164–168` — roles & CREATE_NEW_METRIC  
- `los-core-service/.../PolicyStudioOrchestrator.java:271–291` — `createCustomMetric` request shape  
- `los-core-service/.../MetricCandidateFactory.java:20–116` — NEW_METRIC_CANDIDATE candidates  
- `docs/current-platform-review/01_what_we_have_built.md:10–36` — legacy vs CI architecture  
- Staging probe: `GET .../staging-demo/policy-studio/bureau` (2026-08-08)

### Screenshot attachments

- `ambiguous_terms_screen.png` — **not produced**  
- `missing_measure_screen.png` — **not produced**
