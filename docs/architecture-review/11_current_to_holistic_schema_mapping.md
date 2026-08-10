# 11 — Current → Holistic Underwriting Schema Mapping

**Status:** Investigation (validated against code Aug 2026)  
**Scope:** BillionTechLOS only  
**Prior docs validated:** `RULE_ENGINE_INVESTIGATION.md` Parts 1–3; no prior `02`/`03`/`05` architecture-review files existed.

---

## Validation note on prior investigations

| Prior claim | Code check |
|---|---|
| Rule-set schema = filters + `rulesJson` only | Confirmed (`UnderwritingRuleSet.java`, V17) |
| `evaluateOne` cascade + hardRules | Confirmed (`UnderwritingRuleEngine.java` 164–265) |
| `LIVE_UNSECURED_LOAN_COUNT` / `BUREAU_ENQUIRIES_3M` not from Equifax | Confirmed (manual + gap defaults only) |
| AI-LOS not in rule path | Confirmed |

---

## Part 1 — Actual rule-execution path (summary)

### Sequence (standard borrower)

```mermaid
sequenceDiagram
  participant UI as UnderwritingSection
  participant Ctrl as LoanApplicationFlowController
  participant Flow as LoanApplicationFlowService
  participant Kyc as IKycOrchestrationService
  participant CC as CreditControlService
  participant URE as UnderwritingRuleEngine
  participant SPE as ScorecardPolicyEngine
  participant LSS as LimitSizingService
  participant UES as UnderwritingEvaluationService
  participant ARS as AssignmentRuleApplicationService
  participant CAM as CreditAppraisalService
  participant Audit as AuditService

  UI->>Ctrl: POST /api/v1/flow/{id}/underwrite
  Ctrl->>Flow: underwriteApplication(id, userId)
  Flow->>Kyc: computeKycOutcome(id)
  Flow->>CC: resolveEffective(app, outcome)
  Note over CC,LSS: applyComputedMetrics fills SCF_* scorecard keys
  Flow->>Flow: gates KYC / GST / bureauScore>0
  Flow->>Flow: setStatus(UNDERWRITING)
  Flow->>URE: evaluateAll(app, ctx, outcome)
  Flow->>SPE: evaluate(app, ctx, outcome)
  Note over Flow: HARD_RULE wins; else scorecard; else rules; else legacy
  Flow->>UES: record → underwriting_evaluations
  Flow->>ARS: applyAfterUnderwriting
  alt APPROVED
    Flow->>LSS: applySanctionCapIfConfigured
    Flow->>CAM: ensureCamForApplication
    Flow->>Flow: status CAM_READY
  end
  Flow->>Audit: UNDERWRITING_* events
```

### Step table

| Step | File | Class | Method | Input | Output | Tables / persistence | Status / audit |
|---|---|---|---|---|---|---|---|
| UI run | `ui-service/.../UnderwritingSection.tsx` | — | `onStartUnderwriting` | applicationId | — | — | — |
| API | `LoanApplicationFlowController.java` | controller | `underwrite` | path id + `X-User-Id` | Map | — | — |
| Orchestrate | `LoanApplicationFlowService.java` ~348–607 | service | `underwriteApplication` | app + userId | decision map | `loan_applications` | → `UNDERWRITING`; `PREREQUISITE_BLOCK` / `FLOW` |
| Context | `CreditControlService.java` ~412–581 | service | `resolveEffective` | app + KYC outcome | `EffectiveUnderwritingContext` | reads `financial_info` jsonb | — |
| Limit metrics | `LimitSizingService.java` | service | `applyComputedMetrics` | app + scorecard map | SCF_* keys | in-memory map | — |
| Rules | `UnderwritingRuleEngine.java` | service | `evaluateAll` / `evaluateOne` | app + ctx | `MultiRuleEvalResult` | reads `underwriting_rule_sets` | — |
| Scorecard | `ScorecardPolicyEngine.java` | service | `evaluate` / `build` | app + ctx | `ScorecardEvalResult` | reads `underwriting_scorecards` | — |
| Persist eval | `UnderwritingEvaluationService.java` | service | `record` | multi + ctx + params | entity | `underwriting_evaluations` | append row |
| Meta | `LoanApplicationFlowService` ~610–653 | service | `mergeUnderwritingMetaMulti` | multi | — | `loan_applications.financial_info.underwritingMeta` | — |
| Assign | `AssignmentRuleApplicationService.java` | service | `applyAfterUnderwriting` | app + ctx | assignee | `assigned_to`, `assignmentInfo` | — |
| Cap | `LimitSizingService` | service | `applySanctionCapIfConfigured` | app | sanctioned amount | `loan_applications` | on APPROVE |
| CAM | `CreditAppraisalService` | service | `ensureCamForApplication` | app | CAM doc | CAM tables/docs | → `CAM_READY` |
| Manual approve | `LoanApplicationFlowService` ~720–751 | service | `completeManualUnderwritingDecision` | approve/reject | — | app status/decision | `MANUAL_UW_RESOLVED` |
| Sanction | `LoanApplicationFlowService` ~880+ | service | `sanctionApplication` | — | — | status `SANCTIONED` | `SANCTION*` |

**Decision precedence** (`LoanApplicationFlowService` 423–435): any rule `kind=HARD_RULE` → use rule multi (keep scorecard params for UI); else scorecard if matched; else rule multi; else `CreditDecisionServiceImpl.evaluate` (legacy).

---

## Part 2 — Rule-type inventory (condensed)

| Rule ID/Name | Business meaning | File/Table | Inputs | Operators | Outcome | Hard/Soft | Product-specific | Versioned | Audited |
|---|---|---|---|---|---|---|---|---|---|
| UW hardRules | Force reject/manual on parameter condition | `rules_json.hardRules` / `UnderwritingRuleEngine` | source+parameter via resolve/ctx | GTE/GT/LTE/LT/EQ/NE/BETWEEN/PARAM_REF + dependsOn ALL/ANY | REJECT / MANUAL_REVIEW | Hard | Yes (borrower+product) | No | Eval row + reasons |
| UW minBureauScore | Bureau floor | `rules_json` | effectiveBureauScore | `<` | REJECT | Hard constraint | Yes | No | Yes |
| UW maxLoanAmount | Ticket cap | `rules_json` | requestedAmount | `>` | REJECT | Hard constraint | Yes | No | Yes |
| UW requireKycSuccess | KYC must pass | `rules_json` | kycPassEffective | boolean | REJECT | Hard constraint | Yes | No | Yes |
| UW scorecardRules | Weighted approve/manual/reject buckets | `rules_json.scorecardRules` | resolveParameter / scorecard map | GTE/LTE thresholds | Aggregate by weight | Soft | Yes | No | Weak (no per-line trace) |
| UW default decision | Policy fallback | `rules_json.decision` | — | enum | APPROVE/REJECT/MANUAL | Soft/policy | Yes | No | Yes |
| Scorecard hard rules | Same as hard gates on scorecard entity | `hard_rules_json` | resolve + COMPUTED | Same DSL | REJECT/MANUAL | Hard | Yes | Scorecard `version` int | parameter_results |
| Scorecard rows | Weighted scoring | `scorecard_json.rows` | resolve + MATCH_OPTION | conditions + thresholds % | APPROVE/MANUAL/REJECT | Soft | Yes | version int | Yes |
| Scorecard thresholds | % cutoffs | `thresholds_json` | earned/max | compare to 70/40 defaults | decision | Soft | Yes | version | Yes |
| Limit sizing | Standard / deviation / absolute cap | `rules_json.limitSizing` | turnover param, amount | arithmetic modes | SCF_* metrics; optional sanction cap | Soft (+ hardRules on SCF keys) | Via rule set | No | underwritingMeta |
| Assignment rules | Route to user/role | `assignment_rule_sets` | amount/tenure/geo/product | ranges/equality | assignedTo | Soft | Yes | No | assignmentInfo |
| Pre-UW KYC gate | Block underwrite | FlowService | kycPassEffective | boolean | exception | Hard gate | — | — | PREREQUISITE_BLOCK |
| Pre-UW GST gate | Block if GST on workflow incomplete | FlowService + GstAnalysisService | step result | complete? | exception | Hard gate | Workflow | — | PREREQUISITE_BLOCK |
| Pre-UW bureau gate | Block if score ≤0 | FlowService | effectiveBureauScore | `>`0 | exception | Hard gate | — | — | PREREQUISITE_BLOCK |
| VKYC conditions | When VKYC required | workflow `vkycTriggerCondition` | amount, bureau, product, financialInfo | == != > < >= <= IN NOT_IN | eligible | Hard gate later | Via fields | With workflow | VKYC_CONDITION_EVAL |
| Anchor rating | Checklist score → A–D | `anchor_rating_templates` | answers | option scores / bands | rating → approve/reject/manual | Hard/soft by band | ID product | template version | Anchor audits |
| Legacy CreditDecision | Fallback if no policy match | `CreditDecisionServiceImpl` | bureau KYC step, FOIR | hardcoded 650 / 0.50 FOIR | APPROVED / CONDITIONS / REJECTED | Soft | Partial | No | CREDIT_DECISION |
| CreditRulesEngine | Standalone FOIR/product policy API | `CreditRulesEngine` | request DTO | product table | decision | Soft | In-memory products | No | CREDIT_EVALUATION |
| Deviation matrix | Approval authority by FOIR/LTV/DPD | `CreditEnhancementService` | score, foir, ltv, dpd, amount | bands | authority level | Soft | No | No | API only — **not** on underwrite path |
| Fraud rules | — | AML as KYC step only | — | — | — | — | — | — | **No UW fraud DSL** |
| Pricing rules | — | +1.5% in legacy only | — | — | — | — | — | — | **No pricing engine** |
| Sanction rules | Status/VKYC/role gates | FlowService sanction* | status, VKYC | workflow | SANCTIONED | Hard gates | — | — | SANCTION* |

---

## Part 3 — Rule data context (object graph)

### Available at evaluation

```text
LoanApplication
  borrowerType, loanProduct, requestedAmount, tenureMonths, interestRate
  bureauScore, manualBureauScore
  personalInfo{}, businessInfo{}, financialInfo{}, collateralInfo{}
EffectiveUnderwritingContext
  effectiveBureauScore, kycPassEffective
  effectiveIncome, effectiveObligation
  effectiveState, effectiveCity
  bureauSource, incomeSource, kycSource
  scorecard: Map<String,BigDecimal>   ← primary rule vocabulary
KycStepResult (indirect)
  BUREAU_PULL.parsedData aggregates (NOT mapped to LIVE_UNSECURED_* etc.)
  GST/ITR mappedMetrics → scorecard via CreditControlService
```

### Rule-input lineage table (representative; not every scorecard key)

| Rule Input | Source | Model/Table | File | Raw/Norm/Derived/Manual | Nullable | Default Applied | Lineage Available |
|---|---|---|---|---|---|---|---|
| BUREAU_SCORE | app bureau / manual / ctx | loan_applications + scorecard | CreditControlService ~577 | Derived | No (gate >0) | Demo 720 | Sources flags only |
| LIVE_UNSECURED_LOAN_COUNT | manual / gap | financialInfo.manual | CreditControlService 499, 832 | Manual / Default | Yes→default | **2** | No bureau lineage |
| BUREAU_ENQUIRIES_3M | manual / gap | financialInfo.manual | 500, 833 | Manual / Default | Yes→default | **5** | No bureau lineage |
| MONTHLY_INCOME | provider/manual/demo/gap | financialInfo / scorecard | resolveEffective | Derived | Often gap | 85000 / demo 80000 | incomeSource |
| EMI_OBLIGATION | same | same | same | Derived | Often gap | 15000 / demo 20000 | incomeSource |
| OBLIGATION_RATIO | obl÷inc or manual | scorecard | 531–543 | Derived | Gap 25% | **25** | Weak |
| ANNUAL_GST_TURNOVER | extract/manual/SCF gap | docs / scorecard | extract + 822 | Norm/Default | Yes | SCF gap 5.2Cr | extract override flag weak |
| ITR_INCOME | extract/manual/gap | scorecard | 824 | Norm/Default | Yes | 450000 | Weak |
| REQUESTED_AMOUNT | application | loan_applications | ApplicationScorecardParameterResolver | Raw | Yes | — | Yes |
| AGE | DOB | personalInfo | ApplicationScorecardParameterResolver | Derived | Yes | — | No DOB snapshot |
| KYC_PASS | KYC outcome / manual | kyc + decisionSources | resolveKyc | Derived | — | Demo pass | kycSource |
| SCF_AMOUNT_OVER_STANDARD | limit sizing | rules_json.limitSizing | LimitSizingService | Derived | — | — | Meta snapshot |
| Geography state/city | personal / manual | personalInfo / manual | matchesGeography | Raw/Manual | Yes | — | — |
| COMPUTED formula params | parameterDefs | rules_json / scorecard_json | resolveComputed | Derived | null if operand missing | — | formulaBreakdown (scorecard path) |

**Flagged defaults:** extensive gap/demo defaults in `CreditControlService` (income, FOIR, bank metrics, SCF ratios, LIVE_UNSECURED=2, BUREAU_ENQUIRIES=5, etc.) and Equifax simulated report (score 720). Missing data often becomes a **passing-looking number**, not DATA_INSUFFICIENT.

---

## Part 4 — Rule representation

| Representation | Where | Schema |
|---|---|---|
| Java cascade | UnderwritingRuleEngine, CreditDecisionServiceImpl, FlowService gates | Code |
| JSONB policy | `underwriting_rule_sets.rules_json` | hardRules[], scorecardRules[], constraints, limitSizing, parameterDefs |
| JSONB scorecard | `scorecard_json`, `thresholds_json`, `hard_rules_json` | rows[], {approveMinPercent,manualMinPercent}, {rules:[]} |
| Expression formula | parameterDefs / FormulaEditor | `{expression, operands:[{parameter,source}]}` |
| Workflow JSON | vkycTriggerCondition | array of {field, op, value} |
| Assignment DB | assignment_rule_sets | columns + scope JSON |
| Anchor template | config_json | questions + bands |
| In-memory maps | CreditRulesEngine, CreditEnhancementService | Java constants |

### Operator / capability support

| Capability | Support |
|---|---|
| Nested AND/OR | Partial — `dependsOn` ALL/ANY only; no nested boolean trees of hard rules |
| NOT | Via NE / NOT_CONTAINS / NOT_IN (VKYC); no general NOT group |
| Comparisons | GTE GT LTE LT EQ NE BETWEEN |
| Set membership | VKYC IN/NOT_IN; scorecard MATCH_OPTION |
| Existence / missing | Missing → null → condition false / skip / gap default — **no DATA_INSUFFICIENT** |
| Ranges | BETWEEN; amount/tenure filters |
| Dates / periods | AGE from DOB only; no period DSL |
| Aggregates / trends | Not in FormulaEvaluator; bank keys pre-aggregated elsewhere |
| Ratios | Hand-coded (FOIR, LTV, DTI, formula `/`) |
| Cross-source compare | PARAM_REF |
| Source confidence | Not in rule DSL |
| Rule dependencies | dependsOn on hard/scorecard/limitSizing |
| Rule groups | Multiple matching rule sets aggregated |
| Reusable expressions | parameterDefs formulas (limited) |
| Variables | Formula operand names |
| Effective dates | **No** |
| Priorities | Rule set / scorecard `priority` |
| Short-circuit | First matching hard rule returns |
| Conflict resolution | Aggregate: reject > manual > all approve > mixed manual; hard rule > scorecard |

---

## Part 5 — Rule result / explainability

### Present (`PerRuleEval` / evaluation row / scorecard parameterResults)

- ruleId, ruleName, policyDecision, creditDecision, riskScore, reasons, kind  
- matchedConditions, sourceValuesUsed (ctx.toMap)  
- Scorecard path: valueUsed, points, dependencyOutcome, formulaBreakdown, hardRule flag  
- evaluated_at, evaluated_by, scorecard_id, aggregate_*  

### Missing

- Policy / rule-set **version** and immutable **policy JSON snapshot**  
- Actual vs threshold structured fields (often only in reason text)  
- Explicit pass/fail/refer/**data-insufficient** enum  
- Severity / reason **codes** (free-text reasons)  
- Evidence / source document references  
- Calculation version / duration  
- Override eligibility + override details on the rule result  
- Per hard-rule / per scorecardRules-line full trace on rule-set path  

---

## Part 6 — Versioning & reproducibility

| Artifact | Versioned? | Snapshot at decision? |
|---|---|---|
| Rule sets | No | No rules_json clone |
| Scorecards | Integer `version` | Version number in matched map; not full JSON |
| Evaluations | Append-only | effective values + outcomes; not policy text |
| Application facts | Mutable jsonb | No underwriting fact snapshot table |

**Answer:** If a loan was approved six months ago, the platform **cannot reliably** rerun the exact same data through the exact same policy and guarantee the same result.

**Why:** Policy rows are mutable without mandatory versioning/snapshots; gap defaults may refill missing keys; bureau tradeline facts were never stored as rule inputs; scorecard version may be reused after in-place edits; no immutable application/policy bundle is tied to the evaluation row.

---

## Part 7 — Orchestration order (actual)

```text
1. KYC outcome + resolveEffective (includes extract merge, gap defaults, limit sizing metrics)
2. Gates: KYC pass → GST complete (if on workflow) → bureau > 0
3. Status UNDERWRITING
4. Evaluate ALL matching underwriting rule sets (hardRules → constraints → scorecardRules → decision)
5. Evaluate highest-priority matching scorecard (hard → rows → thresholds)
6. Merge: HARD_RULE > scorecard > rules > legacy
7. Persist evaluation + underwritingMeta + assignment
8. On APPROVE: sanction cap → CAM → CAM_READY
9. Human MANUAL_REVIEW approve/reject
10. CAM review → sanction pending → sanction (VKYC/role gates)
```

| Question | Answer |
|---|---|
| Explicit orchestration? | Implicit in `underwriteApplication` Java |
| Depend on prior outputs? | Limit sizing metrics available before rules; hard rules short-circuit within a set; scorecard after rules but may win unless hard fired |
| Partial evaluation? | No selective DAG |
| Failed rules stop later? | Hard rule stops that rule set’s further keys; other rule sets still evaluated; scorecard still evaluated for params |
| REFER? | MANUAL_REVIEW only (no distinct REFER/DATA_INSUFFICIENT) |
| Unavailable data? | Gap defaults or null→skip/fail condition |
| Selective re-run? | Full underwrite re-run only |
| Fact change → recompute? | Manual re-invoke underwrite; no incremental engine |

---

## Part 8 — Mapping table (current → holistic)

| Current Field/Model | Current Path | Holistic Schema Path | Mapping Quality | Transformation Needed | Data Owner | Issues |
|---|---|---|---|---|---|---|
| LoanApplication | `loan_applications` | `case` | TRANSFORM | Split case vs parties vs request | LOS | Overloaded entity |
| applicationNumber / id | columns | `case.identifiers` | DIRECT | — | LOS | Dual IDs vs AI-LOS loan_id |
| status / credit_decision | columns | `decision.status` / `decision.outcome` | TRANSFORM | Separate workflow vs credit | LOS | Status encodes many phases |
| personalInfo | jsonb | `applicant.*` | AMBIGUOUS | Normalize schema | LOS | Free-form keys |
| related parties | sparse / PLP | `related_parties[]` | MISSING / PROVIDER_COUPLED | New model | LOS/PLP | Incomplete in UW context |
| loanProduct, amount, tenure | columns | `loan_request` | DIRECT | — | LOS | — |
| consents (AA etc.) | AA/consent flows | `consents[]` | TRANSFORM | Link to fact sources | LOS | Not in rule context |
| KycStepResult | `kyc_step_results` | `data_sources[]` | TRANSFORM | Source registry | LOS | Mixed KYC+bureau |
| Bureau reportData | parsedData jsonb | `data_sources.bureau` + `bureau.tradelines` | PROVIDER_COUPLED | Persist tradelines | LOS | Aggregates only; no tradelines |
| Bank OCR / AA | docs + scorecard keys | `normalized_facts.banking` | DERIVE | Canonical facts | LOS | Gap defaults pollute |
| GST / ITR extracts | mappedMetrics → scorecard | `normalized_facts.gst` / `.itr` | DERIVE | — | LOS | Override vs manual unclear |
| Financial statements | partial manuals | `normalized_facts.financials` | MISSING | New ingestion | LOS | Thin |
| Invoices (ID) | PLP / ID modules | `normalized_facts.invoices` | PROVIDER_COUPLED | — | PLP | Parallel domain |
| Obligations | effectiveObligation + EMI keys | `normalized_facts.obligations` | DERIVE | — | LOS | Defaults |
| Collateral | collateralInfo | `normalized_facts.collateral` | TRANSFORM | — | LOS | Sparse |
| Income | effectiveIncome + scorecard | `normalized_facts.income` | DERIVE | Multi-source reconcile | LOS | Defaults / demo |
| Scorecard map | ctx.scorecard | `analysis.metrics[]` | TRANSFORM | Metric registry + lineage | LOS | Flat BigDecimal bag |
| UnderwritingRuleSet | table + rules_json | `policy_evaluation.policy` | TRANSFORM | Versioned policy | LOS | No version snapshot |
| Scorecard entity | underwriting_scorecards | `policy_evaluation.scorecard` | TRANSFORM | Immutable versions | LOS | Mutable version int |
| MultiRuleEvalResult | underwriting_evaluations | `policy_evaluation.results[]` | TRANSFORM | Standard result schema | LOS | Explainability gaps |
| underwritingMeta | financial_info | `policy_evaluation.summary` | DUPLICATED | Dedupe with evaluations | LOS | Dual stores |
| credit_decision | column | `decision.recommendation` | DIRECT | — | LOS | — |
| Manual override | override API | `decision.overrides[]` | TRANSFORM | Structured override model | LOS | Partial |
| Manual credit | financialInfo.creditControl.manual | `normalized_facts` + `audit` | MANUAL | Mark provenance | LOS | Can masquerade as bureau |
| Assignment | assignmentInfo | `decision.routing` | TRANSFORM | — | LOS | — |
| CAM / sanction | flow + docs | `decision.sanction` | TRANSFORM | — | LOS | Post-UW |
| AuditEvent | audit tables | `audit[]` | TRANSFORM | Link to evaluation id | LOS | Event types fragmented |
| AI-LOS outputs | not stored | `analysis.ai_candidates[]` | MISSING | Acceptance gate | LOS | See doc 10 |

### Mapping-quality legend (used above)

DIRECT · TRANSFORM · DERIVE · MANUAL · PROVIDER_COUPLED · MISSING · DUPLICATED · AMBIGUOUS
