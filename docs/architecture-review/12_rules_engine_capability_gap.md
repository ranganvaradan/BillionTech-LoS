# 12 — Rules Engine Capability Gap

**Status:** Investigation  
**Basis:** Code-validated findings in `11_current_to_holistic_schema_mapping.md` and rule-engine investigation parts 1–3  
**Target principle:** Holistic underwriting model + deterministic, versioned, evidence-linked policy execution in BillionTechLOS

---

## Capability matrix

| Capability | Current Support | Evidence | Gap | Priority |
|---|---|---|---|---|
| Immutable policy versions | **None** for rule sets; **weak** for scorecards (`version` int, mutable in place) | `UnderwritingRuleSet` has no version; `UnderwritingScorecard.version`; evaluations store `scorecard_id` not policy JSON | Clone-on-publish + immutable snapshot per evaluation | **P0** |
| Effective dating | **None** | No effectiveFrom/To on policies | Add validity windows; select by evaluation timestamp | **P0** |
| Hard rules | **Yes** | `rules_json.hardRules`, scorecard `hardRulesJson`, FlowService gates | Standardize result schema; per-rule traces | P1 |
| Soft rules | **Yes** | scorecard rows, `scorecardRules`, weighted aggregate | Unify with hard/soft taxonomy and severity | P1 |
| Referral rules | **Partial** | `MANUAL_REVIEW` only | Distinct REFER vs MANUAL; reason codes | P1 |
| Data-insufficient outcome | **None** | Null → skip/fail; **gap defaults** fill numbers (`CreditControlService` 757–849) | Explicit DATA_INSUFFICIENT; ban silent gap defaults in policy path | **P0** |
| Nested expressions | **Partial** | `dependsOn` ALL/ANY; FormulaEvaluator arithmetic only | Boolean expression trees; richer functions | P2 |
| Derived metrics | **Partial** | Limit sizing, FOIR/LTV/DTI, COMPUTED formulas, CreditControl map | Metric registry with lineage, formulas versioned | **P0** |
| Cross-source reconciliation | **None** as engine | Extract overrides manual for some keys; no reconcile report | Reconciliation engine + conflict statuses | P1 |
| Evidence references | **None** in rule results | Reasons are free text; no document/fact IDs | Evidence refs on every result | **P0** |
| Reason codes | **None** | String `reasons` lists | Stable reason-code catalog | P1 |
| Rule dependencies | **Partial** | `dependsOn` on hard/scorecard/limitSizing | DAG of metrics/rules; evaluation order metadata | P2 |
| Selective re-evaluation | **None** | Full `underwriteApplication` only | Fact→affected rule invalidation | P2 |
| Simulation | **None** in production API | UI match helpers only (`matchScorecard`, `matchUnderwritingRules`) | What-if API with frozen facts + candidate policy | P1 |
| Back-testing | **None** | No historical fact/policy bundles | Requires snapshots first | P2 |
| Champion/challenger policies | **None** | Single active matching by priority | Parallel shadow policies | P2 |
| Overrides | **Partial** | Manual UW approve/reject; process override API; manual credit inputs | Structured override on rule results with authority + audit | P1 |
| Maker-checker | **Partial** | CAM review / sanction roles; not policy-change maker-checker | Dual control for policy publish + high-severity overrides | P1 |
| Policy comparison | **None** | — | Diff two policy versions | P2 |
| Risk-based pricing | **None** (legacy +1.5% only) | `CreditDecisionServiceImpl` | Pricing rule pack | P2 |
| Limit recommendation | **Yes** (SCF-oriented) | `LimitSizingService` + SCF_* keys | Generalize beyond invoice-discounting; version config | P1 |
| Monitoring rules | **None** in LOS UW | AI-LOS EWS webhook unfinished | Post-sanction monitoring separately | P3 |
| Early-warning triggers | **None** in LOS UW | AI-LOS alerter only | Out of UW milestone 1 | P3 |
| AI fact-candidate gating | **None** | AI-LOS ingest ignored (`docs/.../10_...`) | Acceptance workflow before facts | P1 |
| Historical reproducibility | **No** | Mutable policies; gap defaults; no fact snapshot | Snapshot facts + policy + results | **P0** |

Support values: **Yes** / **Partial** / **None** / **weak**.

---

## Architectural constraints (top)

1. **Flat scorecard bag + silent defaults** — Rules consume `Map<String,BigDecimal>` that mixes verified, manual, extract, and gap-filled values without lineage.
2. **Mutable policy-as-JSONB** — Rule sets and scorecards can change after decisions; evaluations do not freeze policy text.
3. **Bureau facts incomplete** — Aggregates in `KycStepResult.parsedData`; policy keys like `LIVE_UNSECURED_LOAN_COUNT` are manual/default, not tradeline-derived.
4. **Implicit orchestration** — Order and precedence hard-coded in `LoanApplicationFlowService`; no declarative pipeline.
5. **Parallel shadow engines** — `CreditRulesEngine` / `CreditEnhancementService` / legacy decision are not the same path as production underwrite, creating policy drift risk.

---

## Components worth retaining

1. **`UnderwritingRuleEngine` + hardRules / dependsOn / PARAM_REF** — Working hard-gate model; extend rather than scrap.
2. **`ScorecardPolicyEngine` + FormulaEvaluator + parameterResults traces** — Strongest explainability path today.
3. **`underwriting_evaluations` append-only table** — Right persistence pattern; enrich schema.
4. **`EffectiveUnderwritingContext` concept** — Correct “resolved inputs” boundary; replace internals with fact/metric snapshot.
5. **Limit sizing as config-driven SCF policy** — Pattern for product-specific derived metrics outside Java hardcoding.

---

## Gap themes → target mapping

| Holistic area | Gap theme |
|---|---|
| `data_sources` / `normalized_facts` | No source registry; defaults erase “missing” |
| `analysis` | Metrics not versioned; no reconciliation |
| `policy_evaluation` | No immutable policy; weak results schema |
| `decision` | MANUAL_REVIEW ≠ structured REFER/override model |
| `audit` | Events exist; not bound to frozen policy+facts |

---

## Priority legend

- **P0** — Required before claiming system-of-record underwriting reproducibility  
- **P1** — Required for safe rearchitecture / AI gating / ops  
- **P2** — Strengthens governance and analytics  
- **P3** — Adjacent (monitoring/EWS), not UW core  

Milestone 1 recommendation: see `13_rules_engine_milestone_1.md` (P0 foundation + shadow evaluation, no cutover of final decisions).
