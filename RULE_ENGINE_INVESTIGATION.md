# Rule Engine Investigation

Factual snapshot of the underwriting rule engine as of investigation date. Paths are relative to the repo root.

---

## 1. UnderwritingRuleSet entity schema

**File:** `los-core-service/src/main/java/com/los/core/model/entity/UnderwritingRuleSet.java` (lines 15–74)

**Table:** `underwriting_rule_sets` (created in `los-core-service/src/main/resources/db/migration/V17__underwriting_rule_sets.sql`, lines 2–17)

**Current columns / fields (entity ↔ DB):**

| Field | Column | Notes |
|---|---|---|
| `id` | `id` UUID PK | |
| `name` | `name` | |
| `borrowerType` | `borrower_type` | |
| `loanProduct` | `loan_product` | |
| `minAmount` / `maxAmount` | `min_amount` / `max_amount` | |
| `geography` | `geography` jsonb | Optional map, e.g. `state` / `city` (comment at lines 43–46) |
| `minTenureMonths` / `maxTenureMonths` | `min_tenure_months` / `max_tenure_months` | |
| `priority` | `priority` | default 0 |
| `active` | `active` | default false |
| `rulesJson` | `rules_json` jsonb | blob; default empty map |
| `createdAt` / `updatedAt` | `created_at` / `updated_at` | |

**Schema change since V17:** No. Later migrations (`V18`, `V32`, `V78`, `V81`, `V82`, `V83`, `V85`) only `INSERT`/`UPDATE` rows or mutate `rules_json` contents. There is no `ALTER TABLE underwriting_rule_sets` adding columns.

**Applicability / scope:** No separate applicability or scope column or entity field. Matching remains filter columns on the row (`borrowerType`, `loanProduct`, amount/tenure ranges, `geography`) plus `active` / `priority`. Engine selection: `UnderwritingRuleEngine.matchesFilters()` at lines 107–126; repository query at lines 63–65. UI copy uses the word “scope” for the same filters (`ui-service/src/components/credit/UnderwritingPolicyMapView.tsx`, `ui-service/src/pages/UnderwritingRulesPage.tsx` ~1623–1628) but that is presentation only — not persisted schema.

**Related (not on this entity):** `UnderwritingScorecard` (`underwriting_scorecards`) is a separate entity with its own `scorecard_json` / `thresholds_json` / `hard_rules_json` and similar match filters (`UnderwritingScorecard.java` lines 15–80).

---

## 2. UnderwritingRuleEngine.evaluateOne() — rulesJson keys and dispatch

**File:** `los-core-service/src/main/java/com/los/core/service/underwriting/UnderwritingRuleEngine.java`  
**Method:** `evaluateOne()` at lines 164–265.

**`templateId` + generic parameter dispatch:** Not present on rule-set evaluation. No `templateId` (or similar) is read from `rulesJson`.

**Current control flow (still a keyed cascade, with one added branch):**

1. Read `parameterDefs` (map) — lines 167–175  
2. Read `reasons` (list) — lines 177–180  
3. Iterate `hardRules` (list of maps) — lines 198–227  
   - Each item: `parameter`, `source`, `condition`, `decision`, `message`, `dependsOn`  
   - Values via `ScorecardPolicyEngine.resolve` / `resolveComputed` / `conditionMatchesWithRef` / `dependencyGroupMatches`  
4. Legacy constraints (unchanged style):  
   - `minBureauScore` — lines 229–235  
   - `maxLoanAmount` — lines 237–242  
   - `requireKycSuccess` — lines 244–247  
5. `scorecardRules` (weighted list) — lines 249–251 → `evaluateScorecardRules()`  
6. Else top-level `decision` (`APPROVE` / `REJECT` / `MANUAL_REVIEW` / default) — lines 253–264  

**Distinct top-level `rulesJson` keys understood by `evaluateOne()`:**

| Key | Role |
|---|---|
| `parameterDefs` | Map of parameter metadata (used for COMPUTED / `dependsOn`) |
| `reasons` | Optional reason strings prepended to outcomes |
| `hardRules` | List of hard-gate rule objects |
| `minBureauScore` | Legacy bureau floor |
| `maxLoanAmount` | Legacy amount ceiling |
| `requireKycSuccess` | Legacy KYC gate |
| `scorecardRules` | Legacy weighted scorecard lines |
| `decision` | Fallback policy decision when no scorecardRules |

**Note:** `limitSizing` appears inside some seeded `rules_json` blobs (e.g. `V83__underwriting_limit_sizing_config.sql`) and is consumed by `LimitSizingService` (`LimitSizingService.java` ~247), **not** by `evaluateOne()`.

**Nested keys inside `hardRules` items (not top-level):** `parameter`, `source`, `condition`, `decision`, `message`, `dependsOn` (and related fields used by `ScorecardPolicyEngine`).

**Nested keys inside `scorecardRules` items:** `parameter`, `weight`, `mode`, `approveAt`, `manualAt` (`evaluateScorecardRules`, lines 268–344).

---

## 3. resolveParameter() and parameter resolution elsewhere

### `UnderwritingRuleEngine.resolveParameter()` — still hardcoded

**File:** `UnderwritingRuleEngine.java` lines 346–363

Still:

- `BUREAU_SCORE` → `ctx.effectiveBureauScore()`
- `MONTHLY_INCOME` → `ctx.effectiveIncome()`
- `EMI_OBLIGATION` / `MONTHLY_OBLIGATION` → `ctx.effectiveObligation()`
- Else → flat `ctx.scorecard().get(param)` (or null)

Used by legacy `scorecardRules` evaluation only (line 295).

### No named primitive registry

Repo-wide search for `primitive` (case-insensitive, excluding `node_modules` / `target` / `build`): **no matches**.

### Related hard-coded resolvers (not a registry)

| Component | Path | Behavior |
|---|---|---|
| `ScorecardPolicyEngine.resolve(source, param, …)` | `ScorecardPolicyEngine.java` 445–498 | Hardcoded by `source` (`BUREAU`, `KYC`, `APPLICATION`, `CONTEXT`) + parameter name; then fallback to `ctx.scorecard()` map |
| `ScorecardPolicyEngine.resolveComputed(…)` | same file 504–530 | Reads `parameterDefs[param].formula` and evaluates via `FormulaEvaluator` |
| `ApplicationScorecardParameterResolver` | `ApplicationScorecardParameterResolver.java` | Switch / field-bag lookup for APPLICATION-sourced params (`AGE`, occupation/purpose strings, custom fields) |
| `FormulaEvaluator` | `FormulaEvaluator.java` | Arithmetic expression evaluator over a variable map — not a function registry |

Hard rules in `evaluateOne` call `ScorecardPolicyEngine.resolve` / `resolveComputed` (lines 205–207), not `resolveParameter()`.

---

## 4. EquifaxBureauProvider — account extraction and persistence

**File:** `los-core-service/src/main/java/com/los/core/service/integration/providers/impl/EquifaxBureauProvider.java`

### `extractAccountSummary` (lines 268–325)

Loops `//sch:Account` nodes and accumulates only:

- `totalAccounts`, `activeAccounts`, `closedAccounts`, `overdueAccounts`
- `totalOutstanding`, `totalSanctioned`
- `suitFiled` (single boolean; any account with SuitFiledStatus = Yes)

Per-account identity / history is not retained.

### `extractAccountDetails` (lines 343–355)

Despite the name, only reads OtherKeyInd aggregates:

- `dpd30Plus`, `dpd60Plus`, `dpd90Plus`

No per-account or month-by-month payment history.

### Where aggregate report data goes

`BureauPullStepExecutor` stores `bureauResult.reportData()` into `KycStepResult.parsedData` (jsonb on `kyc_step_results`) — still the aggregate map, not normalized tradelines.

### Search: BureauAccount / Tradeline / AccountHistory / PaymentHistory

| Pattern | Result |
|---|---|
| `BureauAccount` | No entity/table/class |
| `Tradeline` | No matches in Java/TS/SQL source |
| `AccountHistory` | No matches |
| `PaymentHistory` / `getPaymentHistory` | LMS loan repayment history only — `LmsService.getPaymentHistory` (`LmsService.java` ~601–603), `LmsAdapterController` (~53–55), borrower portal consumers. Not bureau tradeline history. |
| `REPAYMENT_HISTORY` | Scorecard/manual credit flag (`CLEAN` etc.), not bureau payment strings |
| Entities resembling bureau accounts | None. Closest unrelated: `LmsAccountSummary` (LMS) |

**Partial / in-progress work for per-account bureau persistence:** None found.

---

## 5. scorecardParameterSources.ts

**File:** `ui-service/src/lib/scorecardParameterSources.ts`

Still a **static hand-written** `SCORECARD_PARAMETER_SOURCE_HELP` map (lines 5–242) plus `helpForParameter()` (lines 244–254).

Comment at lines 1–3: orientation help for credit managers; “not a second policy engine.”

**Consumers:** `ui-service/src/lib/credit/scorecardSummaryLayout.ts` imports `helpForParameter`. No API call or backend catalog/metadata source is wired in.

---

## 6. Keyword search: template / primitive / AI draft / clause / policy engine

Search scope: whole repo, case-insensitive; excluded `node_modules`, `target`, `build`, and (for noise) `package-lock.json`.

### `primitive`

**No hits.**

### `AI draft` / `ai draft` / `AiDraft`

**No hits.**

(`AiLosIntegrationService.java` is an external AI-LOS loan-review deep-link/integration — not rule drafting.)

### `clause`

**No meaningful source hits** (only npm license text strings like “BSD-2-Clause” in lockfiles when not excluded).

### `policy engine` / `PolicyEngine`

| Path | One-line description |
|---|---|
| `los-core-service/.../underwriting/ScorecardPolicyEngine.java` | Evaluates active scorecards (hard rules, parameters, thresholds) into `MultiRuleEvalResult` |
| `los-core-service/.../underwriting/ScorecardPolicyEngineTest.java` | Unit tests for `ScorecardPolicyEngine` |
| `los-core-service/.../underwriting/UnderwritingRuleEngine.java` | Delegates hard-rule resolution to `ScorecardPolicyEngine` static helpers |
| `los-core-service/.../underwriting/AnchorRatingPolicyEngine.java` | Scores anchor due-diligence answers from active `AnchorRatingTemplate` config |
| `los-core-service/.../underwriting/AnchorDueDiligenceService.java` | Uses `AnchorRatingPolicyEngine` for UI questions / compute / submit |
| `los-core-service/.../loan/LoanApplicationFlowService.java` | Calls `scorecardPolicyEngine.evaluate(...)` during underwriting flow |
| `los-core-service/.../credit/LimitSizingService.java` | Reuses `ScorecardPolicyEngine.dependencyGroupMatches` for limit sizing |
| `los-core-service/.../loan/PostCreditFlowTransitionsTest.java` | Mocks `ScorecardPolicyEngine` |
| `ui-service/src/lib/scorecardParameterSources.ts:3` | Comment: not a second policy engine |
| `ui-service/src/components/UnderwritingSection.tsx:1186` | UI copy: “runs the policy engine or legacy scoring” |

### `template` (grouped; no underwriting-rule `templateId` dispatch)

**Underwriting-adjacent (anchor rating templates — separate from rule sets):**

| Path | One-line description |
|---|---|
| `los-core-service/.../entity/AnchorRatingTemplate.java` | JPA entity for `anchor_rating_templates` |
| `los-core-service/.../db/migration/V59__anchor_rating_templates.sql` | Creates `anchor_rating_templates` |
| `los-core-service/.../db/migration/V60__seed_anchor_rating_template.sql` | Seeds default template |
| `los-core-service/.../underwriting/AnchorRatingTemplateAdminService.java` | CRUD/activate for templates |
| `los-core-service/.../controller/AnchorRatingTemplateController.java` | REST API for templates |
| `ui-service/src/pages/AnchorRatingTemplatesPage.tsx` | Admin UI for anchor rating templates |
| `ui-service/src/api/anchorRatingTemplates.ts` | Client API for same |
| `AnchorDueDiligenceService.java` (~194) | Puts `templateId` of active anchor template into result payload |

**Notifications / workflow event templates:**

| Path | One-line description |
|---|---|
| `notification-service/.../entity/NotificationTemplate.java` | Email/SMS template rows |
| `notification-service/.../template/NotificationTemplateEngine.java` | Renders notification bodies |
| `notification-service/.../template/StyledWorkflowEmailTemplates.java` | Hardcoded styled HTML email bodies |
| `notification-service/.../db/migration/V2`–`V7` `*template*` SQL | Schema/seeds for notification templates and event mappings |
| `ui-service/.../workflowEventTemplateMappings.ts` (+ fallback, WorkflowsPage, WorkflowStepEditorPanel) | Catalog of workflow event → notification `templateCode` |

**KFS / eSign / other:**

| Path | One-line description |
|---|---|
| `los-core-service/.../entity/KfsTemplate.java` + `KfsService` / `KfsController` | Key Fact Statement document templates |
| `.../kfs/edi/EdiKfsTemplateContext*.java` | EDI KFS DOCX merge context |
| `.../providers/impl/EmsignerESignProvider.java` | Selects Emsigner signing `TemplateId` by page count |
| Various `*Notifier*.java`, `Esign*`, `Vkyc*` | Pass notification `templateCode` when sending |

**Not rule-engine work:** CSS `grid-template-*`, Vite/React “this template provides…”, Babel `@babel/template` in lockfiles.

**Bottom line for item 6:** No evidence of a started underwriting-rule “template + primitive + AI draft + clause” redesign. Existing “template” and “policy engine” names refer to scorecards, anchor rating, notifications, KFS, or eSign.

---

## 7. Schema / database migration tool

**Tool:** Flyway (`flyway-core` + `flyway-database-postgresql`, version managed as `10.20.1` in parent POMs).

**Primary app (LOS):**

- Config: `los-core-service/src/main/resources/application.yml` lines 32–35  
  - `spring.flyway.enabled: true`  
  - `locations: classpath:db/migration`  
  - `baseline-on-migrate: true`
- Migrations: `los-core-service/src/main/resources/db/migration/`  
  - Naming: `V{n}__{description}.sql`  
  - Highest version observed: **V85** (`V85__proprietor_limit_sizing_cap_mode.sql`); ~85 SQL files in that folder  
  - Underwriting rule table created at **V17**

**Notification service (separate Flyway history):**

- Config: `notification-service/src/main/resources/application.yml` lines 18–22  
- Migrations: `notification-service/src/main/resources/db/migration/` (V1–V7)

**Not used for these schemas:** Liquibase (no Liquibase dependency/config found for these services).

**Convention for new tables:** Add the next `V{n}__….sql` under the owning service’s `src/main/resources/db/migration/` (typically `los-core-service` for underwriting/bureau entities), matching existing PostgreSQL + jsonb style.
