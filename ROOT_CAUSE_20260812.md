# ROOT_CAUSE_20260812 — Policy Studio (report only; no fixes)

**Stamp context:** staging `actuator-info-ui-redeploy 20260812T170409Z` / running actuator git `b48a8da…` (dirty).  
**Mode:** root-cause and severity classification only — **no code changes in this pass**.

Evidence on staging host:
- `/tmp/rc_persist/experiment.json` (NORMAL upload OR × restart)
- `/tmp/rc_authoring/probe.json` (items 32 / 38 / 40 + comparison phrases)
- `/tmp/rc_persist/durable_overlay.json` (resolution overlay for the test document)

---

## 1. Persistence — NORMAL (non-scratch, non-demo) upload vs restart

### Experiment (executed)

| Step | Result |
|------|--------|
| Create via **NORMAL upload** `POST …/staging-demo/policy-studio/upload` | `kind=upload`, `demo=false` |
| Preview + confirm (`confirm:true`) explicit OR | `READY` / `OR(GT(bureau.score,700), EQ(bureau.score,-1))` |
| Save draft | HTTP 200 |
| Pre-restart GET by documentId | HTTP 200, `underwritingRuleCount=1`, OR AST present |
| `docker compose restart los-core` | container restarted; health 200 |
| Post-restart GET same documentId | **HTTP 404** — `Policy document session not found` |
| Durable file on disk | **exists** at `/app/data/policy-studio-resolutions/{documentId}.json` |

**Policy ID:** `3b94d818-6e48-4254-a1a8-388297f1371b`

**Verdict:** the real saved **upload** policy (not scratch, not Banking/Bureau demo) **does not** survive container restart when reloaded by documentId. Preview AST / underwriting rule are gone with the JVM session map.

### Is scratch/demo “intentionally in-memory-only”?

**Partially — but that framing is too narrow.**

All Policy Studio working sessions (upload, scratch, demo) share the same store:

- `PolicyStudioPersistenceService` — `ConcurrentHashMap` `storeByDocumentId` / cache; class comment: *“In-memory store for active sessions; resolution overlays also written to PolicyStudioDurableResolutionStore…”*
- `simulateProcessRestart()` explicitly wipes in-memory maps and documents that durable overlays remain for **demo reopen rebound**, not full session restore by id.
- `PolicyDocumentService.create` builds an in-memory `CiPolicyDocument` only — **no JPA write** despite `CiPolicyDocumentRepository` existing.
- Upload path sets `meta.kind=upload`, `meta.demo=false` (`StagingPolicyStudioDemoService.upload`) — still only `saveSessionSnapshot` → JVM map + resolution overlay.
- Scratch path (`createFromScratch`) sets `kind=scratch`, `demo=false` with an explicit lifecycle comment that scratch sessions *may lack a durable `ci_policy_document` row* — same in-memory fate for rules.

**Durable overlay contents for the upload test document** (keys only):  
`documentId`, `contentHash`, `demoKind` (null), `lineageRootId`, `ambiguityResolutions` (empty), `ruleResolutions` (empty), `savedAt`, `policyName`.  
**No underwriting expression / OR AST.** Overlay is resolution-identity shaped (`PolicyResolutionIdentity.extractBundle`), not a session snapshot.

### Documentation of expected behavior

| Source | What it claims |
|--------|----------------|
| Stabilisation plan §3 (12 Aug) | Prior **POLICY-RESOLUTION-PERSISTENCE-P0**: durable **resolution** bundles; Banking demo resume after restart; selective invalidation. Gates framed around **resolutions**, not “reload any documentId and get underwriting rules back.” |
| Code comments on `PolicyStudioPersistenceService` / `PolicyStudioDurableResolutionStore` | Durable = resolution overlays + demo-kind latest index; session = cache/working copy. |
| `REGRESSION_PROGRESS_20260812T172200Z.md` | Scratch staging-demo sessions die on restart (items 98–100). |
| Product UI (`ui-service` `creditIntelligence.ts`) | All Studio calls go through `internal/credit-intelligence/staging-demo` — there is no separate durable “non-demo” authoring API enabled on staging (`/policy-studio` admin returns **503 Policy Studio disabled**). |

There is **no** product doc that says NORMAL upload / Save Draft sessions rehydrate by documentId after `los-core` restart. The prior “persistence P0” that “passed restart” was **resolution overlay + demo open rebound**, not this path.

### Severity / root-cause class

| Field | Classification |
|-------|----------------|
| **Severity** | **P0** for CM expectation “Save Draft → restart → open same policy by ID and see OR rule” |
| **Root-cause class** | **Incomplete persistence scope** (not a silent revert of a shipped full-session persist). Prior P0 never persisted underwriting rule candidates / expressions for documentId reload. |
| **Exact code path** | `saveSessionSnapshot` → JVM maps + `PolicyStudioDurableResolutionStore.saveFromSession` (identity bundle only) → restart clears maps → `requireSession` / `loadSession` → null → 404 `Policy document session not found` |
| **What changed since original fix** | Clause-OR authoring (b48a8da lineage) correctly **writes** OR into the in-memory session; it did **not** extend durability. Restart failure for OR rules is the long-standing session architecture, now visible because OR rules are finally confirmable. |
| **Not** | “Scratch-only ephemeral by design, uploads are durable.” Uploads are equally ephemeral for session/rules. |

---

## 2. Item 38 — `less than or equal` / clause-OR false positive

### Exact classification path

1. `CmRuleAuthoringService.preview` → `CompoundPlainEnglishParser.looksLikeMultiClause(text)`
2. `looksLikeMultiClause` → `hasClauseLevelOr(text)` / `hasClauseLevelAnd(text)`
3. `hasClauseLevelOr` → `CLAUSE_OR.matcher(text).find()`

```java
// CompoundPlainEnglishParser.java
private static final Pattern CLAUSE_OR = Pattern.compile(
    "(?i)\\s+or\\s+(?!more\\b|above\\b|below\\b|higher\\b|greater\\b)");
```

Comment says: *“Clause-level OR — not or more/above/below/higher/greater.”*  
**`equal` is not excluded.**

### Substring / boundary behavior

- Matches whitespace-bounded ` or ` (not bare substring inside a word).
- Uses a **negative lookahead** allowlist of *following* words — **not** full phrase-context / comparison-operator awareness.
- Therefore **`less than or equal`**, **`greater than or equal`**, **`more than or equal`** all match as clause-level OR.

### Live probe (staging)

| Text | Result |
|------|--------|
| `FOIR must be less than or equal to 50%` | `complete=false`, `NEEDS_CLARIFICATION`, message *“Some parts of this rule have not been mapped yet.”* |
| `FOIR less than or equal to 50%` | same |
| `interest rate more than or equal to 10%` | same |
| `FOIR must be greater than or equal to 10%` | same |
| `Bureau score should be 650 or above` | **Ready** (`>= 650`) — `or above` correctly excluded by lookahead |

Routing: false-positive `hasClauseLevelOr` → compound `parse()` → `parseDisjunction` splits on ` or ` → arms fail to map as two clauses → NEEDS_CLARIFICATION.  
Note: `detectBoundaryOperator` already understands `less than or equal` / `greater than or equal` for flat DESCRIBE, but **never runs** once multi-clause routing wins.

### Proposed fix (do not implement yet)

**Preferred:** extend CLAUSE_OR negative lookahead (and keep tests) for comparison tails:

- `equal`, `equals` (covers “or equal to”)
- optionally phrase-level pre-check allowlist before OR/AND detection:
  - `less than or equal (to)?`
  - `greater than or equal (to)?`
  - `more than or equal (to)?`
  - `fewer than or equal (to)?`
  - symbolic `<=` / `>=` already handled elsewhere

**Alternative:** grammar-level split — only treat ` or ` as clause OR when **both** sides look like independent conditions (parameter + operator + value), not when `or` sits inside a known comparison-operator phrase.

Regression must keep real clause OR (`bureau score > 700 OR bureau score IS EQUAL TO -1`) green.

---

## 3. Item 32 — `unless` today

### Observed

Text: `Reject if FOIR exceeds 50% unless bureau score is at least 750`

Staging preview: **`complete=true`**, `parameterId=bureau.score`, `operator=>=`, `value=750`, message *Ready to confirm*.  
FOIR threshold and `unless` are **not** in the AST — **silent semantic loss (fail-open Ready)**, not a routed compound tree.

### Exact path

1. `looksLikeIfBranchWording` does **not** treat a single `if` + `unless` as multi-branch IF (only multi-`if`, semicolon branches, etc.).
2. `hasClauseLevelOr` / `hasClauseLevelAnd` do **not** see `unless` (no ` or ` / ` and ` connective).
3. Flat `fromPlainEnglish` → `matchParameters`:
   - **Priority early return** on `bureau score` / score+bureau **before** FOIR:
   - returns **only** `bureau.score`, ignoring FOIR entirely.
4. `fillOpValue` / `detectBoundaryOperator` → `at least` → `>= 750`.
5. `failClosedIfResidualConnective` → `hasResidualLogicalConnective` → **only** `hasClauseLevelOr` \|\| `hasClauseLevelAnd`.

### Does the §16 residual guard cover `unless` / `except` / `either`?

| Keyword | Covered by residual fail-closed? | Today |
|---------|----------------------------------|--------|
| `OR` / `AND` (clause-level) | **Yes** | fail-closed if residual after flat complete |
| `unless` | **No** | silently dropped; Ready with score arm |
| `except` | **No** (as residual connective) | item 31 uses concept/write-off path (separate) |
| `either` | **No** as residual; **yes** for multi-clause *routing* via `looksLikeMultiClause` (`contains(" either ")`) + `parseNestedAndOr` | nested either/or can succeed when routed |

**Same missing-guard class as §16 correction 3**, applied to a different keyword family: unconsumed logical content (`unless` / exception language) is not treated as residual.

---

## 4. Item 40 — `Allow NTC` vs known live NTC / GAP_DEFAULT P1

### Observed incomplete behavior

Standalone DESCRIBE `Allow NTC` (and `Also allow NTC` without a `proposedModel`):

- `complete=false`
- `parameterId=bureau.thin_file_indicator`
- message **`Numeric value required`**

So flat authoring binds the **derived-defined thin-file indicator** (numeric-shaped control) instead of the Gate-1 boolean fact `bureau.status_ntc`.  
Compound / amendment path (`Also allow NTC` **with** `proposedModel`) is covered by unit tests and maps `bureau.status_ntc` — that path is not what the regression probe exercises.

### Same known P1 or new gap?

| Topic | Status |
|-------|--------|
| **Known P1** (stabilisation plan §8 / §17): `bureau.status_ntc` — Policy Test yes; runtime non-prod helper; **production not closed**; live **GAP_DEFAULT** risk | Still open; about **live underwriting / scorecard provenance**, not this preview failure |
| **Item 40 (this probe)** | **Distinct authoring/routing gap**: DESCRIBE never reaches boolean NTC; selects `bureau.thin_file_indicator` + demands a number |

**Classification:** **related family (NTC surface incomplete), but not the same bug as live GAP_DEFAULT semantics.** Item 40 is pre-runtime authoring incompleteness; the known P1 is production/runtime authority of NTC facts when wired.

GACAT seed places both parameters with overlapping aliases (`ntc`, `thin file`); thin-file is `derivedDefined` / not production-ready; `bureau.status_ntc` is the intended authored Fact id for compounds.

---

## Severity summary (for triage before any fix)

| Item | Severity | Class |
|------|----------|--------|
| Persistence after restart (NORMAL upload by documentId) | **P0** | Incomplete session durability (prior P0 scoped to resolution overlays + demo resume only) |
| 38 — `or equal` false positive | **P0/P1** (authoring regression risk from clause-OR fix) | Over-broad `CLAUSE_OR` lookahead |
| 32 — `unless` silent Ready | **P0** (semantic loss) | Missing residual / connective guard; score-priority param match |
| 40 — Allow NTC | **P1 authoring gap** (distinct from live GAP_DEFAULT P1) | Wrong parameter + numeric gate |

---

## Explicit non-actions this pass

- No parser / persistence code changes  
- No deploys  
- No commits  

END
