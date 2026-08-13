# ROOT_CAUSE_20260813 — Policy Studio (report only; no fixes)

**Stamp context:** staging actuator git `3d166fce995e72527b3c0e65f87f00ff997f36bd` / `dirty=false` (matches prior deploy).  
**Mode:** root-cause only — **no code changes in this pass**.  
**Baseline:** `REGRESSION_FULL_RUN_1_20260813T025242Z.md`  
**Live probe evidence:** host `/tmp/rc_20260813/probe.json` (local `_tmp_rc_20260813_probe.json`)

---

## 1. Items 24–25 — nested `AND(X, OR(Y,Z))` collapses to top-level `op=OR`

### Cases tested (staging preview)

| Item | Input | Preview | Top combinator / `expression.op` | Children observed |
|------|-------|---------|----------------------------------|-------------------|
| **24** | `bureau score >= 700 AND (FOIR <= 50 OR LTV <= 60)` | READY / complete | `combinator=ANY`, **`op=OR`** | `bureau.score`, `collateral.ltv` — **FOIR dropped** |
| **25** | `(bureau score >= 700 OR bureau score = -1) AND (FOIR <= 50 OR LTV <= 60)` | READY / complete | `combinator=ANY`, **`op=OR`** | score≥700, **score≤50** (FOIR misbound), LTV≤60 — **-1 lost**, groups flattened |
| Control | `bureau score >= 700 and either FOIR <= 50 or LTV <= 60` | READY | **`combinator=ALL`, `op=AND`** | leaf score + nested OR(FOIR, LTV) — **correct** |

### Same CLAUSE_OR / clause-routing as items 16/17 — not a separate nested path

**Verdict: same family as the original clause-OR routing, misapplied because nesting never claims the text.**

Order in `CompoundPlainEnglishParser.parse`:

1. `parseNestedAndOr` — **only** matches `^(.+) and either (.+) or ((?:foir|ltv|bureau|…)\b.+)$`
2. Parenthesized / bare `AND (… OR …)` / `(… OR …) AND (… OR …)` → **no match** → empty result
3. `parseDisjunction` runs **before** `parseConjunction` whenever `CLAUSE_OR` finds a clause-level `or`
4. `parseDisjunction` → `finalizeGroup(..., COMBINATOR_ANY, children)` → top-level **OR**

Exact wrong assignment:

```java
// CompoundPlainEnglishParser.parseDisjunction — line ~446
return finalizeGroup(r, CompoundExpressionAuthoringSupport.COMBINATOR_ANY, children);
```

`CLAUSE_OR` itself is the item-38-hardened pattern (excludes `or more/below/equal…`). It correctly treats the **inner** `OR` as clause-level — then the **whole sentence** is routed through flat OR splitting. That is the same connective detector / disjunction path that fixed 16/17 for top-level `A OR B`; it was never taught to preserve an outer AND around parenthesized groups.

`parseNestedAndOr` is a **narrow separate path** (the `"and either"` control proves it works when its regex matches). Items 24–25 never enter it.

### Do 24 and 25 fail the same way?

| | Same? | Detail |
|---|-------|--------|
| **Routing** | **Yes** | Both: nested miss → `parseDisjunction` → top `ANY`/`OR` |
| **Mangled AST** | **Differently** | 24 loses FOIR; 25 flattens both OR groups, rebinds FOIR’s `50` onto `bureau.score`, drops `= -1` |

**Severity / class:** P0 authoring correctness — READY-to-confirm with wrong combinator (silent semantic loss).  
**Root-cause class:** Incomplete nesting in compound parse order (CLAUSE_OR routing wins over outer AND).

---

## 2. Item 31 — `PlainEnglishConsumptionGuard` residual on write-offs except CC

### Hypothesis from baseline note

> Residual-content guard likely over-firing on legitimate `except` phrasing.

### Confirmed, with a precise residual token

Concept resolution **succeeds** first:

- `BusinessConceptResolver` → `bureau.accounts.writeoff_non_cc`, `READY_DERIVED`
- Suggested expr `LTE(writeoff_non_cc, 0)`; exception model already encodes CC exclusion
- `draftFromConceptResolution` sets `complete=true`, then calls `failClosedIfResidualConnective`

Guard then fail-closes:

| Field | Value |
|-------|--------|
| `complete` | `false` |
| `status` | `NEEDS_CLARIFICATION` |
| `message` | `Some parts of this rule have not been mapped yet.` |
| `missing` | **`["residualConnective"]`** |
| `semanticLoss` | `true` |
| `parameterId` | still `bureau.accounts.writeoff_non_cc` |

### Exact residual (not bare `except`)

`PlainEnglishConsumptionGuard.normalize` keeps hyphens (`NON_ALNUM = [^a-z0-9.%\-]+`).

Source `no write-offs except credit cards` → residual after strips ≈ **`no write-offs`**.

- Phrase strips remove space forms: `write offs`, `except credit cards`, etc.
- They **do not** remove hyphenated **`write-offs`**
- Token `write-offs` (len ≥ 3) → `hasUnconsumedSubstantiveContent` = true  
- `except` is in `CLAUSE_MARKERS` but is consumed by `except credit cards` / `except for credit cards` strips when those phrases match

### Variant matrix (same staging session)

| Text | Result |
|------|--------|
| `no write-offs except credit cards` | **fail** (`residualConnective`) |
| `no write-offs except for credit cards` | **fail** (`residualConnective`) |
| `no write offs except credit cards` | **pass** (complete; concept message) |

**Severity / class:** P1 authoring false fail-closed — legitimate concept mapping undone by residual guard.  
**Root-cause class:** Normalize/strip mismatch (hyphenated `write-offs` vs space-only consumable phrases), not “except is unmapped clause content” in the sense of a second condition.

---

## 3. Item 70 / exact-100 → ratio branch vs executability “undefined”

### Was “exact 100 deliberately resolved to the ratio branch” implemented?

**Yes — as a resolution/patch path, not as the Banking reset seed.**

| Location | What it does |
|----------|----------------|
| `InwardReturnCompoundSupport.OPTION_RATIO` = `treat_100_as_ratio_branch` | Documented choice |
| `InwardReturnCompoundSupport.patchBoundary` | `OPTION_RATIO` → condition `op=GTE` (100 in ratio branch) |
| `PolicyReviewService.resolveAmbiguity` | On phrase containing `100` + RESOLVED → `patchInwardReturnBoundaryExpression` + regenerate boundary tests |
| `CmRuleAuthoringService` | Can also call `patchBoundary` when defining boundary from authoring |

Stabilisation plan §4.1 describes a **completed walkthrough** outcome. That is not the same as auto-applying ratio on every Banking BRE open.

### What this policy instance has after `/policy-studio/banking/reset`

| Observation | Value |
|-------------|--------|
| Rule | `BANK_INWARD_RETURN_BRANCHED_100` |
| `expression.condition.op` | **`GT`** (seed from `DeterministicGoldenInterpretationProvider`: “exactly 100 unresolved”) |
| `metadata.boundaryResolved` / `boundaryOption` | **null** |
| Ambiguity rows with applied ratio | none found on GET |
| Choice strings / docs mentioning ratio | may appear in blob (`treat_100_as_ratio_branch` text present) without being applied |

So: **implementation exists on the resolve path; this instance is not wired to it** until CM resolves the 100 ambiguity to `treat_100_as_ratio_branch`.

### Why item 70 still fails submit — not the 100 boundary

Submit HTTP **409**:

> Cannot submit for review. Resolve: Starter — banking capacity (ADB ≥ EDI) — **Proposed EDI has no runtime source.**; DigiLeap — …; Settlement…; Reboost…; Adjusted ADB — Bulk deposit >10× adjustment…

`PolicyLifecycleService` submit blockers use `PolicyExecutionReadiness.sessionExecutionBlockers`. For capacity rules, `RuleOperandPresenter` surfaces **Proposed EDI** via `unresolvedOrMapped`; without a stored session resolution face, the operand stays **`unresolved=true`**, which formats as **“has no runtime source.”**

`application.proposed_edi` **is** seeded in GACAT as **MANUAL**, and `ParameterExecutabilitySupport` would treat MANUAL as authorised — but readiness blockers here follow the **unresolved operand face**, not that catalogue MANUAL path. Separate from 100-boundary patching.

**Verdict for the contradiction:**

1. Ratio-branch closure **is implemented** (`patchBoundary` / ambiguity resolve).  
2. Banking reset **does not** apply it — seed remains `GT` / exact-100 open.  
3. Item 70’s submit failure is **Proposed EDI (and bulk ADB) executability**, not an “exact 100 still undefined” message — though 100 is indeed still unpatched on this instance.

**Root-cause class:** Unwired instance (seed + no resolve call) for 100; independent operand-resolution gap for Proposed EDI on capacity rules.

---

## 4. Items 86 / 87 / 90 / 91 — probe artifacts vs product state machine

### Baseline failures (regression probe)

| Item | Probe behaviour | Message |
|------|-----------------|---------|
| 86 | `POST …/lifecycle/approve` with `role=CREDIT_MANAGER` (no Policy Test / no CM+checker stamps) | 409 — Test not completed; CM required; Checker required |
| 87 | Same endpoint with `role=CHECKER` | Same 409 (preconditions still unmet) |
| 90 | `…/lifecycle/retire` **without** role in body/headers | 403 — Authentication required — governance role |
| 91 | `…/lifecycle/delete-draft` **without** role | 403 — Authentication required — Administrator |

### Product API shape (relevant)

- **CM / Checker approvals:** `/approvals/credit-manager` and `/approvals/checker` (`StagingProspectApprovalService`) — **not** `/lifecycle/approve`.
- **`/lifecycle/approve`:** final “Business Approve Policy” — requires Policy Test + **already-recorded** CM + Checker + other schedule readiness blockers.
- **Retire / delete-draft:** `mergeActor` from `X-User-Role` **or** body `role` / `actorRole` / `reviewerRole`; allowed sets include `CREDIT_MANAGER` / `ADMINISTRATOR` (retire also `POLICY_CHECKER`, etc.). Role `CHECKER` (probe) ≠ `POLICY_CHECKER`.

### Corrected re-run (staging, same SHA)

Document `c1710720-…` (scratch + `bureau score >= 650`), then:

| Step | Endpoint | HTTP | Result |
|------|----------|------|--------|
| Save draft + submit | lifecycle save/submit | 200 | IN REVIEW |
| Policy Test | `test/quick` | 200 | run recorded |
| Mark sim reviewed | `simulation/reviewed` | 200 | reviewed |
| **86 corrected** | `approvals/credit-manager` | **200** | CM approval recorded |
| **87 corrected** | `approvals/checker` | **200** | Checker approval recorded |
| Business approve | `lifecycle/approve` | **200** | APPROVED |
| **90 corrected** | `lifecycle/retire` + `CREDIT_MANAGER` role/headers | **200** | RETIRED |
| **91 corrected** | fresh draft + `delete-draft` + `ADMINISTRATOR` | **200** | deleted |

Baseline-style contrast on a fresh doc (wrong endpoint / missing role) still reproduced 409 / 403-class failures.

### Verdict

| Item | Probe artifact? | Product defect? |
|------|-----------------|-----------------|
| **86** | **Yes** — wrong endpoint + skipped Policy Test / approval stamps | No defect found when CM endpoint + preconditions used |
| **87** | **Yes** — same; also wrong role name for checker path | No defect found on `/approvals/checker` |
| **90** | **Yes** — missing governance role on retire | Retire works with `CREDIT_MANAGER` after APPROVED |
| **91** | **Yes** — missing admin role on delete | Delete works with `ADMINISTRATOR` |

**Root-cause class:** Regression harness incomplete preconditions / wrong approval surface — **not** a broken maker-checker state machine on the corrected path.

---

## Summary table

| Item(s) | Root cause | Class |
|---------|------------|-------|
| **24–25** | `parseNestedAndOr` miss → `parseDisjunction` sets top `COMBINATOR_ANY` / `op=OR` (same CLAUSE_OR family as 16/17; nesting path unused) | Parser / semantic-model P0 |
| **31** | `PlainEnglishConsumptionGuard` residual token **`write-offs`** (hyphen) after successful `writeoff_non_cc` concept map | Residual-guard false fail-closed P1 |
| **70 / 100** | Ratio patch **implemented** on resolve; Banking instance still **`GT` / unwired**; submit blocked by **Proposed EDI unresolved operand** (and bulk ADB), not by 100 text | Unwired seed + separate EDI readiness |
| **86/87/90/91** | Probe used `/lifecycle/approve` without Policy Test/CM/checker; retire/delete without roles | Probe artifact — state machine OK when wired correctly |

**No fixes applied in this pass.**
