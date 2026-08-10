# 13 — Rules Engine Milestone 1 (Bounded)

**Status:** Recommendation only — no implementation in this investigation  
**Goal:** Establish foundation for the holistic underwriting model and a versioned policy/result path **without** replacing the LOS or changing final credit decisions yet.

---

## Recommended milestone

**Name:** Underwriting Fact Snapshot + Policy Snapshot + Shadow Evaluation  

**Principle:** Keep `LoanApplicationFlowService.underwriteApplication` as the production decision authority. Run a **shadow** evaluator beside it that consumes a frozen fact snapshot and an immutable policy version, and store both for comparison — do not switch aggregate decisions to the new engine in M1.

---

## Scope (in)

1. **Source registry (minimal)**  
   Register each contributing source used at underwrite time: bureau pull step, GST/ITR extracts, manual credit cells, application fields — with `source_id`, type, provider, captured_at, pointer to storage (`kyc_step_results.id`, document id, or financialInfo path).

2. **Underwriting fact snapshot**  
   On each underwrite (or immediately before engines run), persist an immutable snapshot of the values that feed rules today:
   - `EffectiveUnderwritingContext.toMap()` equivalent  
   - Explicit flags per key: `VERIFIED | EXTRACT | MANUAL | GAP_DEFAULT | DEMO`  
   - No silent rewrite of production context yet — snapshot is observational + shadow input  

3. **Metric registry (catalog)**  
   Declarative catalog of known metric codes (`BUREAU_SCORE`, `OBLIGATION_RATIO`, `ANNUAL_GST_TURNOVER`, …) with:
   - owner (LOS)  
   - allowed sources  
   - whether gap defaults are permitted (default: **false** for new shadow path)  
   - mapping to holistic path (`normalized_facts.*` / `analysis.metrics.*`)

4. **Policy version**  
   - On activate/save of rule set or scorecard: write immutable `policy_versions` row containing full JSON (`rules_json` / scorecard triple) + semver or monotonic version + checksum.  
   - Live tables remain editable for authors; runtime/shadow binds to a version id.

5. **Standard rule-result structure**  
   Persist shadow results (and optionally enrich existing evaluation row) with:

   ```text
   rule_id, policy_version_id, actual_value, operator, threshold,
   outcome ∈ {PASS, FAIL, REFER, DATA_INSUFFICIENT},
   severity, reason_code, message,
   input_metric_refs[], evidence_source_refs[],
   executed_at, duration_ms
   ```

6. **Evidence references**  
   Link each shadow result input to source registry ids (e.g. bureau step id for score; document id for GST).

7. **Compatibility adapter**  
   Adapter reads existing `UnderwritingRuleSet` / `UnderwritingScorecard` into the versioned policy document; maps current hardRules/scorecard rows into the standard result shape. Existing Java engines remain the production path.

8. **Shadow evaluation**  
   After production `evaluateAll` / scorecard `evaluate`, run adapter+shadow using the fact snapshot + pinned policy version; store shadow aggregate alongside production aggregate for diff reporting. **Production `credit_decision` / status transitions unchanged.**

---

## Scope (out / exclusions)

- Replacing `UnderwritingRuleEngine` or `ScorecardPolicyEngine` as decision makers  
- Removing gap defaults from production `CreditControlService` (document and flag them in snapshot only)  
- Bureau tradeline persistence / Equifax extract expansion (design note only; separate milestone)  
- AI-LOS accept/reject workflow (see doc 10; depends on fact snapshot)  
- Champion/challenger cutover, back-testing UI, pricing engine  
- Maker-checker for policy publish (can be M1.1)  
- Changing sanction/CAM/VKYC flows  

---

## Components

| Component | Responsibility |
|---|---|
| `SourceRegistryService` | Upsert source records at underwrite |
| `UnderwritingFactSnapshotService` | Build + persist snapshot from `resolveEffective` outputs |
| `MetricCatalog` | Static/DB catalog of metric codes |
| `PolicyVersionService` | Snapshot rule set / scorecard JSON on publish |
| `RuleResultWriter` | Write standard results for shadow run |
| `ShadowPolicyEvaluator` | Adapter over existing engines or re-read JSON against snapshot |
| `UnderwriteShadowHook` | Called from `underwriteApplication` after production eval |
| Admin read API | Diff production vs shadow; list snapshots |

---

## Database changes (proposed)

| Table | Purpose |
|---|---|
| `uw_source_registry` | Sources per application / evaluation |
| `uw_fact_snapshots` | Immutable fact JSON + provenance map; FK application_id, evaluation_id |
| `uw_metric_catalog` | Metric definitions |
| `uw_policy_versions` | Immutable policy JSON + type (RULE_SET\|SCORECARD) + source_entity_id + version |
| `uw_shadow_evaluations` | Shadow aggregate + link to fact_snapshot_id + policy_version_ids |
| `uw_shadow_rule_results` | Standard result rows |

Flyway location: `los-core-service/src/main/resources/db/migration/` next `V{n}__…`.

Optional: add nullable columns on `underwriting_evaluations`: `fact_snapshot_id`, `policy_version_ids`, `shadow_diff_json` — without changing existing columns’ meaning.

---

## APIs (proposed)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/underwriting/evaluations/{id}/snapshot` | Fetch fact snapshot |
| GET | `/api/v1/underwriting/evaluations/{id}/shadow` | Shadow results + diff |
| GET | `/api/v1/underwriting/policy-versions/{id}` | Immutable policy JSON |
| GET | `/api/v1/underwriting/metrics/catalog` | Metric registry |
| POST | `/api/v1/underwriting/rules/{id}/publish` | Create policy version (admin) |
| POST | `/api/v1/underwriting/scorecards/{id}/publish` | Create policy version (admin) |

No change to `POST /flow/{id}/underwrite` contract for clients.

---

## Compatibility requirements

- Existing UI underwrite / approve / reject behavior unchanged.  
- Existing `underwriting_evaluations.rule_results_json` format retained.  
- Shadow failures must not fail underwrite (log + mark shadow status FAILED).  
- Feature flag: `los.underwriting.shadow-evaluation.enabled` (default off until soak).  

---

## Migration approach

1. Ship tables + feature flag off.  
2. Backfill policy versions for all **active** rule sets and scorecards (one-shot publish).  
3. Enable shadow in non-prod; compare aggregates for SCF + personal loan samples.  
4. Enable shadow in prod read-only.  
5. Use diffs to drive M2 (remove gap defaults from shadow metrics; tradeline facts; cutover plan).  

---

## Tests

- Unit: provenance classifier (MANUAL vs GAP_DEFAULT vs EXTRACT) for known keys.  
- Unit: policy version checksum stable for identical JSON.  
- Integration: underwrite creates snapshot + shadow row when flag on.  
- Integration: shadow failure does not roll back production decision.  
- Contract: publish endpoints create immutable rows; updates to live rule set do not mutate prior version.  
- Golden: fixed fact snapshot + fixed policy version → identical shadow results on replay.  

---

## Acceptance criteria

1. Every successful underwrite (flag on) produces a fact snapshot with provenance tags for all scorecard keys present.  
2. Active policies used in shadow are pinned by `policy_version_id`, not live row mutation.  
3. Shadow results use the standard outcome enum including `DATA_INSUFFICIENT` when provenance is missing and gap defaults are disallowed in shadow mode.  
4. Production `credit_decision` and status transitions match pre-M1 behavior (regression suite green).  
5. API can retrieve snapshot + shadow diff for an evaluation id.  
6. Documentation updated: how M2 will switch decision authority.  

---

## Success metric

> For N consecutive underwrites in soak, shadow evaluation completes ≥99% and policy/fact artifacts are sufficient to **replay shadow** to the same aggregate without reading live mutable policy tables.

---

## Follow-on (explicitly not M1)

- M2: Tradeline/enquiry derivation for bureau metrics; kill gap defaults on production path for those keys.  
- M3: Cutover — production engine reads snapshots + policy versions; shadow becomes champion/challenger.  
- M4: AI fact-candidate gating into registry with human accept.  
---

## Mapping to holistic schema (M1 coverage)

| Holistic node | M1 contribution |
|---|---|
| `case` / `loan_request` | Snapshot includes identifiers + request fields |
| `data_sources` | Source registry |
| `normalized_facts` / `analysis` | Fact snapshot + metric catalog (first cut) |
| `policy_evaluation` | Policy versions + shadow results |
| `decision` | Unchanged (production) |
| `audit` | Links evaluation ↔ snapshot ↔ shadow |
