# Rule Engine Investigation — Part 2

Factual snapshot of hardRules / parameterDefs authoring, FormulaEvaluator grammar, and ScorecardPolicyEngine.resolve vocabulary. Paths relative to repo root.

---

## 1. Frontend authoring — `UnderwritingRulesPage.tsx`

**File:** `ui-service/src/pages/UnderwritingRulesPage.tsx`

Neither `hardRules` nor `parameterDefs` is authored as a raw JSON textarea. The only textarea in the policy section is **Reasons (one per line)** (lines 1134–1140), which maps to `rulesJson.reasons`, not hard rules or parameter defs.

### `hardRules` — form UI (not raw JSON)

**State / persist:** lines 309, 404–422 (load), 504–512 (save into `rulesJson.hardRules`).

**What the user sees:**

| Lines | Control | Field |
|---|---|---|
| 1144–1360 | “Hard rules” section; expandable cards per rule | — |
| 1179–1224 | Collapsed summary: parameter label, `source · parameter · condition`, decision badge | — |
| 1227–1253 | `<select>` of sources from `scorecardSourceOptionsForLoanProduct` (+ unknown source appended) | `source` |
| 1254–1291 | If `source === 'COMPUTED'`: read-only parameter code + **Edit formula** button; else `<select>` of parameters for that source | `parameter` |
| 1293–1300 | `ScorecardConditionEditor` | `condition` |
| 1301–1317 | `<select>` Reject / Manual review | `decision` |
| 1318–1327 | Text `<input>` “Optional message” | `message` |
| 1328–1334 | **Dependencies** button → modal | `dependsOn` |
| 1335–1342 | Remove rule | — |
| 1350–1360 | **+ Add hard rule** | — |

**Dependencies modal** (lines 1695–1709): `DependencyConditionsEditor` — logic ALL/ANY, per-condition source/parameter/condition selects (`ui-service/src/components/scorecard/DependencyConditionsEditor.tsx`).

**Formula modal** (lines 1651–1693): when source is COMPUTED, **Edit Computed Formula** opens `FormulaEditor` (operand source/parameter dropdowns + `+`/`-`/`*`/`/` operators). Also edits the computed parameter **code** via a text input (lines 1659–1669).

Saved hard-rule payload shape (lines 504–512): `{ id?, parameter, source, condition, decision, message?, dependsOn? }` — `formula` is **not** written onto each hard-rule object; it is stored under `parameterDefs` (see below).

### `parameterDefs` — no dedicated editor on this page

**State / load / save:** lines 310, 387–390 (load from `rulesJson.parameterDefs`), 513 (save if non-empty).

There is **no** standalone form or JSON textarea for browsing/editing the full `parameterDefs` map.

`parameterDefs` is written only as a side effect of the formula modal (lines 1681–1688):

```ts
setParameterDefs((prev) => ({
  ...prev,
  [activeFormulaRule.parameter.trim()]: { inputType: 'formula', formula },
}))
```

So on Underwriting Rules, the only user-facing way to create/update a `parameterDefs` entry is: set hard-rule source to **Computed (formula)** → Edit formula → save rule set. Entries for non-formula custom defs are authored on **Scorecards** via `ScorecardParameterEditor` / `buildParameterDefsFromRows` (`ui-service/src/components/scorecard/ScorecardParameterEditor.tsx` lines 64–90), not on this page.

---

## 2. `FormulaEvaluator` grammar and `dependsOn`

**File:** `los-core-service/src/main/java/com/los/core/service/underwriting/FormulaEvaluator.java`

### Grammar actually supported

| Category | Supported |
|---|---|
| **Binary operators** | `+`, `-`, `*`, `/` (precedence: `*`/`/` over `+`/`-`; left-associative) |
| **Unary operator** | leading `-` on a factor (lines 93–97) |
| **Grouping** | `(` … `)` (lines 99–107) |
| **Literals** | Decimal numbers: digits and `.` only (lines 109–117). No scientific notation, no commas. |
| **Identifiers (variables)** | Start with letter or `_`; continue with letter/digit/`_` (lines 119–128). Matched **case-insensitively** against keys in the variables map. |
| **Functions** | **None.** No `min`, `max`, `if`, etc. |
| **Other** | Whitespace ignored. Any leftover characters after a full parse → `null` (lines 28–30). Division by zero → `null` (line 82). Missing variable → `null` (line 128). |

Class comment (lines 7–10) matches the implementation: `+ - * /`, parentheses, numeric literals, named variable substitution. Arithmetic uses `BigDecimal`, scale 6, `HALF_UP` on divide.

### Parameter references vs `dependsOn`

**Formula variable binding is not `dependsOn`.**

`ScorecardPolicyEngine.resolveComputed()` (lines 504–529):

1. Loads `parameterDefs[param]` with `inputType` = `formula`.
2. For each entry in `formula.operands` (`parameter` + `source`), calls `resolve(opSource, opParam, app, ctx)` and puts `vars.put(opParam, val)`.
3. Calls `FormulaEvaluator.evaluate(expression, vars)`.

The expression string may name those operand parameters (e.g. `PAT / ANNUAL_GST_TURNOVER * 100`). That is identifier → operand map lookup, not `dependsOn`.

**`dependsOn` is a separate gate**, evaluated in `evaluateDependencyGroup()` / `dependencyGroupMatches()` (`ScorecardPolicyEngine.java` lines 566–630).

**Mechanism connecting `dependsOn` to evaluation (not formula order):**

In `UnderwritingRuleEngine.evaluateOne()` (lines 201–214):

1. Resolve hard-rule value (`resolve`, then optionally `resolveComputed` if source is COMPUTED).
2. If `!dependencyGroupMatches(hardRule.dependsOn, …)` → **`continue`** (skip this hard rule entirely).
3. Else if condition matches → fire REJECT / MANUAL_REVIEW.

`evaluateDependencyGroup` (lines 582–630):

- Shape: `{ logic: "ALL"|"ANY", conditions: [{ source, parameter, condition }, …] }`.
- Empty / missing `dependsOn` → `matched = true`.
- Each condition: `resolve(source, parameter)` (plus `resolveComputed` if source is COMPUTED), then `conditionMatchesWithRef`.
- ALL: every condition matched; ANY: at least one.

There is **no** topological evaluation-order pass for `dependsOn`. It does not schedule formula evaluation; it only decides whether the hard rule (or scorecard row / limit-sizing policy) applies.

Nested COMPUTED operands inside a formula: `resolveComputed` calls only `resolve` for operands (line 525), not recursive `resolveComputed`. A COMPUTED operand resolves only if already present in `ctx.scorecard()` or via a non-COMPUTED `resolve` path.

---

## 3. `ScorecardPolicyEngine.resolve()` — sources and params

**File:** `ScorecardPolicyEngine.java` lines 445–498.

Default when `source` is null/blank: treated as `"BUREAU"` (line 450).

### Dedicated branches (source + param)

#### `BUREAU`

| Param | Behavior |
|---|---|
| `BUREAU_SCORE` | `ctx.effectiveBureauScore()` as `BigDecimal` |

(No other param is special-cased under `BUREAU` in `resolve`.)

#### `KYC`

| Param | Behavior |
|---|---|
| `KYC_PASS` | `1` if `ctx.kycPassEffective()`, else `0` |

#### `APPLICATION`

| Param | Behavior |
|---|---|
| `REQUESTED_AMOUNT` | `app.getRequestedAmount()` |
| `TENURE_MONTHS` | `app.getTenureMonths()` as `BigDecimal` |
| Then | `ApplicationScorecardParameterResolver.resolve(param, app, null)` |

**Named cases inside `ApplicationScorecardParameterResolver.resolve`** (`ApplicationScorecardParameterResolver.java` lines 27–31):

| Param | Behavior |
|---|---|
| `AGE` | Years from `personalInfo.dateOfBirth` |
| `APPLICANT_AGE` | same |
| `AGE_YEARS` | same |

**Dynamic APPLICATION fallback** (same class, lines 75–98): if no named hit, looks up `param` (case-insensitive) in:

1. `personalInfo.customFields` map keys  
2. then `personalInfo`, `businessInfo`, `financialInfo` top-level keys  

and coerces to `BigDecimal` when parseable. That set is **open-ended** (any key present on the application JSON bags), not a fixed enum in Java.

(`OCCUPATION` / `LOAN_PURPOSE` / `PURPOSE` are handled by `resolveString()` for string scorecard conditions; they are **not** returned as `BigDecimal` by the named switch in `resolve()`.)

#### `CONTEXT`

| Param | Behavior |
|---|---|
| `MONTHLY_INCOME` | `ctx.effectiveIncome()` |
| `EFFECTIVE_INCOME` | same |
| `MONTHLY_OBLIGATION` | `ctx.effectiveObligation()` |
| `EMI_OBLIGATION` | same |
| `DTI_RATIO` | obligation ÷ income × 100 (or `null` if income missing/≤0 or obligation null) |
| `OBLIGATION_TO_INCOME` | same as `DTI_RATIO` |

### Sources with **no** dedicated branch in `resolve()`

These UI catalog sources (`ui-service/src/lib/credit/scorecardConfig.ts`) have **zero** `if ("SOURCE".equals(src) …)` arms in `resolve()`:

- `BANK_STATEMENT`
- `GST_STATEMENT`
- `ITR`
- `OTHER`
- `SCORECARD`
- `PROGRAM_INPUTS`
- `COMPUTED`

For them, `resolve()` only succeeds via the **universal scorecard-map fallthrough** below (or returns `null`). `COMPUTED` values used at hard-rule time are obtained when the **caller** detects `source == COMPUTED` and calls `resolveComputed` (e.g. `UnderwritingRuleEngine` lines 205–208).

### Universal fallthrough (any source, after the branches above)

Lines 488–496: if `ctx.scorecard()` contains `param` (exact key) or `param.toUpperCase(Locale.ROOT)`, return that `BigDecimal`.

Then line 497: **`return null`**.

Keys that commonly appear in `ctx.scorecard()` are populated by `CreditControlService.resolveEffective` (manual puts, document extract, gap defaults, program vintage, limit sizing) — e.g. `GST_INCOME`, `ANNUAL_GST_TURNOVER`, `LIVE_UNSECURED_LOAN_COUNT`, `SCF_AMOUNT_OVER_STANDARD`, arbitrary keys from `manual.scorecardMetrics` via `mergeScorecardMetricsMap` (lines 362–376), etc. That map is a **runtime bag**, not a closed list inside `resolve()`.

---

## 4. Adding a new parameter without a Java deploy

### Admin API / UI

| Path | Capability |
|---|---|
| Underwriting Rules UI | Can add COMPUTED hard-rule params + formulas → persisted in `rulesJson.parameterDefs` via existing CRUD (`/underwriting/rules`). Does **not** register a new engine source. |
| Scorecards UI | `ScorecardParameterEditor` can author custom OTHER params (`number` / `text` / `dropdown`) and COMPUTED formulas into `scorecardJson.parameterDefs`. |
| Dedicated “parameter catalog” admin API | **None** found that extends `ScorecardPolicyEngine.resolve` vocabulary. |

### Can `parameterDefs` invent a source the engine does not know?

`parameterDefs` entries do **not** declare a new `source` for `resolve()`. They store:

- `inputType: "formula"` + `formula: { expression, operands: [{ parameter, source }, …] }` (Underwriting Rules / COMPUTED), and/or  
- `inputType: "number"|"text"|"dropdown"` + optional `options` (Scorecards custom OTHER).

Operand / hard-rule `source` strings are free text from the UI catalog. If the source has no dedicated `resolve` branch:

1. `resolve(source, param, …)` tries `ctx.scorecard().get(param)` / uppercase.  
2. If missing → **`null`** (no exception, no error log in `resolve`).  
3. Hard rule: `conditionMatchesWithRef` with `v == null` returns **false** → rule **skipped** (`continue`), not treated as a thrown error.  
4. `resolveComputed`: if any operand resolves to `null` → whole formula returns **`null`** (line 527).

### What still requires a backend change

| Goal | Without Java change? |
|---|---|
| New COMPUTED metric from already-resolvable operands | Yes — via UI `parameterDefs` + COMPUTED hard rule / scorecard row |
| New numeric key supplied at underwriting time | Partially — put value under `manual.scorecardMetrics` (merged into scorecard map); hard rule with any source can read it via fallthrough. Manual form fields for brand-new named keys may still need UI wiring. |
| New first-class `source` with dedicated resolution (e.g. new bureau field not in scorecard map) | **No** — needs `resolve()` / `CreditControlService` (or extract) code + deploy |
| New APPLICATION bag field that is already on the application JSON | Often yes via dynamic `ApplicationScorecardParameterResolver` field lookup when `source` is `APPLICATION` |

---

## 5. Concrete jsonb shapes from seeds / fixtures

### `hardRules` — verbatim from migration

**Source:** `los-core-service/src/main/resources/db/migration/V81__scf_invoice_discounting_underwriting.sql` lines 130–148  
(same array also in `V82__scf_underwriting_hard_rules_repair.sql` lines 11–29)

```json
[
  {"id":"scf_hard_abs_cap","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"GT:10000000","decision":"REJECT","message":"Requested amount exceeds absolute SCF ticket cap (₹1 Cr special deviation maximum)"},
  {"id":"scf_hard_over_std","parameter":"SCF_AMOUNT_OVER_STANDARD","source":"SCORECARD","condition":"GT:0","decision":"MANUAL_REVIEW","message":"Requested amount exceeds standard limit (25% of annual turnover capped at ₹50L); special deviation up to ₹1 Cr requires lender approval"},
  {"id":"scf_hard_gst_to","parameter":"ANNUAL_GST_TURNOVER","source":"GST_STATEMENT","condition":"LT:50000000","decision":"REJECT","message":"Annual GST turnover below SCF minimum (₹5 Cr)"},
  {"id":"scf_hard_itr","parameter":"ITR_INCOME","source":"ITR","condition":"LT:300000","decision":"REJECT","message":"ITR income below SCF minimum (₹3 L)"},
  {"id":"scf_hard_pat","parameter":"PAT","source":"OTHER","condition":"LTE:0","decision":"REJECT","message":"PAT must be positive in latest FY"},
  {"id":"scf_hard_bureau","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:650","decision":"REJECT","message":"Bureau score below SCF minimum (650)"},
  {"id":"scf_hard_unsec","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"GT:6","decision":"REJECT","message":"Live unsecured loan count exceeds policy (max 6)"},
  {"id":"scf_hard_enq","parameter":"BUREAU_ENQUIRIES_3M","source":"BUREAU","condition":"GT:21","decision":"REJECT","message":"Bureau enquiries in 3 months exceed policy (max 21)"},
  {"id":"scf_hard_bank_to","parameter":"BANKING_TURNOVER_PCT_GST","source":"BANK_STATEMENT","condition":"LT:75","decision":"REJECT","message":"Banking turnover as % of GST below policy (min 75%)"},
  {"id":"scf_hard_chq3","parameter":"CHEQUE_BOUNCES_3M","source":"BANK_STATEMENT","condition":"GT:0","decision":"REJECT","message":"Cheque bounces in last 3 months not allowed"},
  {"id":"scf_hard_chq12","parameter":"CHEQUE_BOUNCES_12M","source":"BANK_STATEMENT","condition":"GT:6","decision":"REJECT","message":"Non-technical cheque bounces in 12 months exceed policy (max 6)"},
  {"id":"scf_hard_dscr","parameter":"DSCR","source":"SCORECARD","condition":"LT:1.25","decision":"REJECT","message":"DSCR below policy minimum (1.25)"},
  {"id":"scf_hard_ic","parameter":"INTEREST_COVERAGE","source":"SCORECARD","condition":"LT:1.5","decision":"REJECT","message":"Interest coverage below policy minimum (1.5)"},
  {"id":"scf_hard_de","parameter":"DEBT_TO_EQUITY","source":"OTHER","condition":"GT:2","decision":"REJECT","message":"Debt to equity exceeds policy (max 2x)"},
  {"id":"scf_hard_tol","parameter":"TOL_TNW","source":"OTHER","condition":"GT:7","decision":"REJECT","message":"TOL/TNW exceeds policy (max 7x)"},
  {"id":"scf_hard_cc","parameter":"CC_UTILISATION_PCT","source":"BANK_STATEMENT","condition":"GTE:95","decision":"REJECT","message":"CC utilisation at or above 95%"},
  {"id":"scf_hard_biz","parameter":"businessStability","source":"OTHER","condition":"LT:3","decision":"REJECT","message":"Business vintage below 3 years"}
]
```

No `dependsOn` and no `parameterDefs` appear in these SCF rule-set seeds. `V83` / `V85` only add/update `limitSizing` inside `rules_json`.

### `parameterDefs` — no seeded migration example

Repo-wide search of `los-core-service/src/main/resources/db/migration/*.sql`: **no** `parameterDefs` string.

Closest **concrete** shapes:

**A. Shape the Underwriting Rules UI persists** (from `UnderwritingRulesPage.tsx` lines 1684–1687 + `FormulaEditor` output):

```json
{
  "PAT_TURNOVER_RATIO": {
    "inputType": "formula",
    "formula": {
      "expression": "PAT / ANNUAL_GST_TURNOVER * 100",
      "operands": [
        { "parameter": "PAT", "source": "OTHER" },
        { "parameter": "ANNUAL_GST_TURNOVER", "source": "GST_STATEMENT" }
      ]
    }
  }
}
```

(Expression text is built from operand parameter names and operators; exact expression depends on what the user selected.)

**B. Shape from unit test fixture** (`ScorecardPolicyEngineTest.java` lines 89–94) — dropdown custom param on a scorecard, not a Flyway seed:

```json
{
  "OccupationCustom": {
    "inputType": "dropdown",
    "options": [
      { "value": "A", "label": "Option A", "score": 40 },
      { "value": "B", "label": "Option B", "score": 80 }
    ]
  }
}
```

**C. Text-only custom** (same test file lines 125–126):

```json
{
  "NotesCustom": {
    "inputType": "text"
  }
}
```
