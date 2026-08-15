# DATA-PARAMETERS-DP2B-CONVERGENCE-1

**Mode:** AUDIT + CONTROLLED RECONCILIATION (not DP-3 / not DP-4)  
**Source start SHA:** `fb36872025aa28ca4e5a78f8c4a913df04a2e606`  
**GACAT remains sole canonical parameter catalogue.**

---

## Scope / non-goals

| In scope | Out of scope |
|---|---|
| Re-prove DP-2’s 8 `PRODUCTION_READY_NEEDS_REVIEW` | Policy / Scorecard redesign |
| Audit 10 Policy unresolved tokens (bind only if exact) | Live UW UI enrichment with GACAT |
| Restore `bureau.inquiries.last_3m` seed↔DB parity | Mass migrate ~25+ legacy scorecard keys |
| Reconcile `collateral.ltv` into Java seed | Mass migrate Live UW rule keys |
| Bind `kyc.pan.name_match` to existing KYC emit | Fail-closed Production Ready enforcement (DP-4) |
| Inventory + Policy/Scorecard readiness for DP-3 | New Policy rule graph implementation |

---

## 1. Eight PRODUCTION_READY_NEEDS_REVIEW — re-proof

DP-2 flagged these because `providerFieldPath` was blank **and** workflow/integration provides did not claim them.

| # | Canonical ID | Family | Class | Verdict |
|---:|---|---|---|---|
| 1 | `banking.adjusted_business_credits_12m` | Bank Statement | **A** | PRODUCTION_READY_PROVEN |
| 2 | `banking.monthly_obligation` | Bank Statement | **A** | PRODUCTION_READY_PROVEN |
| 3 | `gst.turnover.trailing_3m` | GST | **A** | PRODUCTION_READY_PROVEN |
| 4 | `gst.turnover.trailing_6m` | GST | **A** | PRODUCTION_READY_PROVEN |
| 5 | `gst.filing.timeliness_score` | GST | **B→A** | METADATA fixed (period TRAILING_6M) + runtime proven |
| 6 | `gst.gstr1_gstr3b_turnover_variance` | GST | **A** | PRODUCTION_READY_PROVEN |
| 7 | `gst.return.missing_count_12m` | GST | **A** | PRODUCTION_READY_PROVEN |
| 8 | `gst.return.late_count_12m` | GST | **A** | PRODUCTION_READY_PROVEN |

**PROD NOT PROVEN (C): 0** — no demotion of `productionReady` proposed; evidence is definitive for calculators + acquisition claims. (Registered `provider_code` / structured `providerFieldPath` remains a DP-2D documentation debt, not fiction.)

### Per-parameter evidence (summary)

| ID | Why `productionReady=true` | Acquisition / source | Runtime | Calculator | Missing-data | Provenance / consumers |
|---|---|---|---|---|---|---|
| `banking.adjusted_business_credits_12m` | Seed `prodBank()`; Gate3 banking goldens | AA + BSA integrations (DP-2B provides) | `BankingMetricService` → `BankAdjustedTurnoverCalculator` (`ADJ_12M`) | Yes | `DATA_INSUFFICIENT` (seed repair) | Reconciliation GST/ITR↔bank (`V97`); shadow `ANNUAL_BANKING_TURNOVER`; fact alias `banking.credit.adjusted_business_12m` |
| `banking.monthly_obligation` | Same | AA + BSA | `BankEmiObligationDetector` / `BANK_EMI_DETECTION_V1` | Yes | `DATA_INSUFFICIENT` | Reconciliation bureau↔bank; legacy compat `MONTHLY_OBLIGATION` |
| `gst.turnover.trailing_3m` | `prodGst()` | Workflow `GST_ANALYSIS` | `GstMetricService.computeTrailingTurnover(..., 3)` | Yes | `DATA_INSUFFICIENT` | Metric code = ID; GSTR1 prefer |
| `gst.turnover.trailing_6m` | Same | `GST_ANALYSIS` | months=6 | Yes | `DATA_INSUFFICIENT` | Same family |
| `gst.filing.timeliness_score` | Same | `GST_ANALYSIS` | `computeTimeliness` **windowMonths=6** | Yes | `DATA_INSUFFICIENT`; score formula always emits | Shadow `GST_FILING_TIMELINESS`; **seed period corrected TRAILING_12M→TRAILING_6M** |
| `gst.gstr1_gstr3b_turnover_variance` | Same | `GST_ANALYSIS` | `computeVariance` | Yes | DI on no overlap | Reconciliation / shadow variance |
| `gst.return.missing_count_12m` | Same | `GST_ANALYSIS` | `computeFilingCount(..., MISSING)` | Yes | Count / PASS | Shadow `GST_MISSING_RETURNS` |
| `gst.return.late_count_12m` | Same | `GST_ANALYSIS` | `computeFilingCount(..., LATE)` | Yes | Count / PASS | Metric + provides; weaker BRE than missing |

**Metadata-only repairs applied (B→A):**  
1. `prodBank()` / `prodGst()` → `missingDataTreatment=DATA_INSUFFICIENT`  
2. `WorkflowParameterProvidesCatalog` — GST_ANALYSIS + AA/BSA claims  
3. Timeliness period honesty (`TRAILING_6M` + capability filter note)

---

## 2. Policy unresolved tokens (10)

**Exactly resolved: 0**  
**Still unresolved: 10**  
No fuzzy / approximate GACAT substitution performed.

| Token | Intended meaning (from Policy snapshot usage) | Possible nearby GACAT ID | Confidence | Exact semantic equivalence? |
|---|---|---|---|---|
| `BANK.INWARD_RETURN_MAX` | Cap on inward returns | `banking.inward_return.ratio_3m` | low | **No** |
| `bank.transaction` | Generic bank txn reference | `bank.transaction.*` RAW family | low | **No** (prefix only) |
| `banking.adb_bulk_deposit_adjustment` | ADB bulk-deposit adjustment | *(none in catalogue; calculator exists outside GACAT)* | n/a | **No** |
| `banking.business_transaction_count.average_monthly_3m` | Business txn avg monthly | `banking.transaction_count.average_monthly_3m` | high name similarity | **No** (business ≠ all) |
| `banking.credit_transaction_count.average_monthly_3m` | Credit txn avg monthly | `banking.transaction_count.average_monthly_3m` | medium | **No** |
| `banking.inward_cheque_return_count_3m` | Inward cheque return count | `banking.cheque_return_count_3m` / ratio | medium | **No** |
| `banking.inward_cheque_return_ratio_3m` | Inward cheque return ratio | `banking.inward_return.ratio_3m` | high name similarity | **No** (cheque vs inward-return naming) |
| `banking.inward_return.count_3m` | Inward return count | companion to `banking.inward_return.ratio_3m` | medium | **No** (count not seeded) |
| `banking.transaction_count.total_3m` | Total txn count 3m | `banking.transaction_count.average_monthly_3m` | medium | **No** (total ≠ average) |
| `banking.transaction_count_3m` | Txn count 3m (ambiguous) | `banking.transaction_count.average_monthly_3m` | medium | **No** |

Concentrated in one Policy document (snapshot harvest). Leave UNRESOLVED for DP-3 exact authoring.

---

## 3. `bureau.inquiries.last_3m`

| Check | Result |
|---|---|
| Canonical definition | Trailing **3 calendar months** enquiry count (`PolicyBureauMetricService.inquiriesLast3Months`) |
| Seed presence | Yes (`studioImpl()`, `productionReady=false`) |
| DB absence (pre-DP-2B) | Yes — SEED_NOT_IN_DB drift |
| Policy Test calculator | Yes — studio/policy helper |
| Runtime availability | Studio calculator path; **not** BureauMetricService production goldens |
| Production readiness | **false** (unchanged — not independently certified) |
| vs `bureau.inquiries.current_month` | Distinct: current evaluation month only |
| vs `bureau.recent_inquiries_90d` | Distinct: trailing **90 days**; productionReady=true |

**Action:** Flyway `V131__gacat_bureau_inquiries_last_3m_upsert.sql` restores DB parity.  
**SEED/DB DRIFT RESOLVED: YES**  
**PRODUCTION FLAG CHANGED: NO**

These three IDs **must remain semantically distinct**. Do not alias `last_3m` ↔ `recent_inquiries_90d` (legacy `BUREAU_ENQUIRIES_3M` name collision risk).

---

## 4. `collateral.ltv`

| Question | Answer |
|---|---|
| Classification | **DB row is canonical**; seed omission was catalogue drift (defect) |
| Obsolete/legacy? | No — used by catalogue capability expressions / Policy authoring |
| Definition unresolved? | No — LTV = loan / eligible collateral × 100; `productionReady=false` |

**Action:** Added matching derived entry to `GacatCatalogueSeed` (mirrors V123). **Not deleted.** No production certification.

---

## 5. `kyc.pan.name_match`

| Aspect | Evidence |
|---|---|
| Producer | `NormalizedKycFactBuilder` — emits **only if** provider `nameMatch` hint present |
| Workflow / source | KYC orchestration remains execution authority (`PAN_VERIFY` / provider payload) |
| Runtime value | Boolean fact `kyc.pan.name_match` when hint supplied; otherwise absent (not invented) |
| Missing-data | Optional / provider-conditional — not auto PASS/FAIL |
| Provenance | `KycFactCatalog` + Decision Policy DSL consumers / golden KYC packages |
| Exact semantic match? | **Yes** — same ID, same boolean meaning |

**Action:** GACAT seed `implemented=true`, binding `NormalizedKycFactBuilder`; **`productionReady=false`**. No new calculator. No KYC workflow redesign.

---

## 6. Legacy inventory for DP-3 only (NO WRITES)

### Scorecard legacy keys (35 observed)

`ABB_OBLIGATION_MULTIPLE`, `ANCHOR_RELATIONSHIP_VINTAGE_MONTHS`, `ANNUAL_GST_TURNOVER`, `AVERAGE_BANK_BALANCE`, `BANKING_TURNOVER_PCT_GST`, `BANK_STATEMENT_INCOME`, `BUREAU_ENQUIRIES_3M`, `BUREAU_SCORE`, `BUSINESS_VINTAGE_MONTHS`, `CC_UTILISATION_PCT`, `CHEQUE_BOUNCES_12M`, `CHEQUE_BOUNCES_3M`, `DEBT_TO_EQUITY`, `DEPENDENCY_VINTAGE_PERCENT`, `DSCR`, `EBITDA_PROXY`, `GST_INCOME`, `INDUSTRY_RISK`, `INTEREST_COVERAGE`, `ITR_INCOME`, `KYC_QUALITY`, `LEVERAGE_RATIO`, `LIVE_UNSECURED_LOAN_COUNT`, `LTV`, `MAX_DPD_6M`, `MONTHLY_INCOME`, `NTC_FLAG`, `OBLIGATION_RATIO`, `PAT`, `PROPERTY_VALUE`, `REPAYMENT_HISTORY`, `REQUESTED_AMOUNT`, `SCF_AMOUNT_OVER_STANDARD`, `TOL_TNW`, `businessStability`

| Legacy key | Possible GACAT | Confidence | Consumers |
|---|---|---|---|
| `BUREAU_SCORE` | `bureau.score` | exact | Scorecards + Live UW |
| `AVERAGE_BANK_BALANCE` | `banking.avg_daily_balance_3m` | high (seed liveScorecard) | Scorecards |
| `ANNUAL_GST_TURNOVER` / `GST_INCOME` | `gst.turnover.trailing_12m` | high / medium | Scorecards + UW |
| `KYC_QUALITY` | `kyc.quality` | exact | Scorecards |
| `OBLIGATION_RATIO` | `obligation.ratio` | exact | Scorecards |
| `REQUESTED_AMOUNT` | `application.requested_amount` | exact (SAFE_ALIAS) | Both |
| `BUSINESS_VINTAGE_MONTHS` | `application.business_vintage_months` | exact (SAFE_ALIAS) | Both |
| `CHEQUE_BOUNCES_3M` | `banking.cheque_return_count_3m` | exact (SAFE_ALIAS) | Both |
| `LIVE_UNSECURED_LOAN_COUNT` | `bureau.live_unsecured_loan_count` | exact | Both |
| `BUREAU_ENQUIRIES_3M` | `bureau.recent_inquiries_90d` *(name risk)* or `bureau.inquiries.last_3m` | **low–medium — do not auto-bind** | Both |
| `MAX_DPD_6M` | `bureau.max_dpd_6m` | high (nonprod) | Scorecards |
| `NTC_FLAG` | `bureau.status_ntc` | medium | Scorecards |
| `LTV` | `collateral.ltv` | medium | Scorecards |
| `DSCR` / `PAT` / `DEBT_TO_EQUITY` / … | financial.* catalogue | low / ambiguous | UW + scorecards |

**~25** remain without safe live-key map — DP-3 consumer proof required. **MASS MIGRATION PERFORMED: NO**

### Live UW distinct keys (17)

`ANNUAL_GST_TURNOVER`, `BANKING_TURNOVER_PCT_GST`, `BUREAU_ENQUIRIES_3M`, `BUREAU_SCORE`, `CC_UTILISATION_PCT`, `CHEQUE_BOUNCES_12M`, `CHEQUE_BOUNCES_3M`, `DEBT_TO_EQUITY`, `DSCR`, `INTEREST_COVERAGE`, `ITR_INCOME`, `LIVE_UNSECURED_LOAN_COUNT`, `PAT`, `REQUESTED_AMOUNT`, `SCF_AMOUNT_OVER_STANDARD`, `TOL_TNW`, `businessStability`

Treat Live UW as **legacy lender-facing authority**. DP-3/Policy publish may bridge into this engine later — no UI enrichment here.

---

## 7. Policy graph readiness for DP-3

| Question | Answer |
|---|---|
| Do persisted Policy Versions contain canonical parameter IDs? | **Partially** — only where snapshot text/tokens already use GACAT IDs; many legacy/unresolved tokens remain |
| Canonical IDs only in snapshots/text? | **Largely yes** — harvestable from `ci_policy_studio_session_snapshot.payload` |
| Is AST persisted? | **Not queryably** — `ci_policy_rule_candidate.expression` can hold AST but table is **0 rows** |
| Is `rule_candidate` genuinely empty? | **Yes** (staging Internal audit) |
| Authoritative object today | **`ci_policy_studio_session_snapshot`** (full session: `ruleCandidates`, `metricCandidates`, mappings, …) |
| DP-3 Policy graph target | Normalize / materialize **`ruleCandidates`** (or equivalent) into a queryable persisted graph keyed by **canonical GACAT IDs**, without changing rule semantics |
| Can current versions migrate without semantic change? | **Yes in principle** if migration is lossless extract from existing snapshot `ruleCandidates` + resolve only exact GACAT IDs; unresolved tokens stay unresolved until authoring |

**No new graph implemented in DP-2B.**

---

## 8. Scorecard readiness for DP-3

| Topic | Current state |
|---|---|
| Scorecards | **42** |
| Canonical GACAT-stamped factors | **7** (`bureau.score`, `banking.avg_daily_balance_3m`, `gst.turnover.trailing_12m`, `kyc.quality`, `obligation.ratio`, `application.business_vintage_months`, `bureau.credit_after_overdue.clean_history_months`) |
| Legacy factors | Dominant (~35 distinct legacy keys) |
| Attached vs independent | Mix — some attached to Policy products; many independently configured |
| Factor definition duplication | Policy snapshot tokens vs scorecard factor keys often parallel / legacy-keyed |
| Weight storage | Per-band `weight` in scorecard JSON |
| Normalization | Live formula is **points-based**: `normalizedPercent = HALF_UP(100 * earned / maxPoints)`; weights tagged `METADATA_ONLY_NOT_USED_IN_FORMULA` |
| Missing-factor | `OPTIONAL_SKIP` shrinks earned **and** max; `OPTIONAL_DEPRESS` adds to max/earn 0; `REQUIRED` missing → DI / fail (must not silently renormalise) |

### DP-3 target contract (confirmed; not implemented)

1. Policy defines/owns decision parameters.  
2. Scorecard attached to Policy may reference those Policy parameters.  
3. Scorecard adds weight / scoring configuration.  
4. Raw weights need **not** total 100.  
5. Evaluation normalises **applicable** weights to 100.  
6. A **required** missing parameter must **not** silently disappear and cause remaining factors to be renormalised.

---

## 9. Live Underwriting Rules

- **40** rule sets; **17** distinct legacy parameter keys.  
- Parallel consumer plane outside GACAT.  
- DP-3/Policy publishing will eventually need a compile/bridge into this engine.  
- **LIVE UW CHANGED: NO** (audit only).

---

## 10. Safety / regression expectations

| Guardrail | Expectation |
|---|---|
| Bureau certification | Intact (goldens unchanged; inquiry triad remains distinct) |
| DP-1 readiness projection | Intact (+ DP-2B assertions) |
| Policy semantics | Unchanged (no token auto-bind; no AST rewrite) |
| Scorecard semantics | Unchanged (inventory only) |
| Customer Category | Unchanged |
| Workflow / KYC execution | Unchanged (catalogue bind only) |
| Live UW | Unchanged |
| Internal / Client parity | Same artifact; DBs isolated |

---

## 11. Code / DB changes in this step

| Change | Purpose |
|---|---|
| `GacatCatalogueSeed` | missing-data on bank/GST prod helpers; timeliness period; `kyc.pan.name_match` bind; `collateral.ltv` seed |
| `WorkflowParameterProvidesCatalog` | GST_ANALYSIS + AA/BSA provides for the 8 |
| `GacatParameterReadinessProjection.knownCatalogueDriftNotes` | Remove resolved seed/DB drift notes; note unresolved Policy tokens |
| `V131__gacat_bureau_inquiries_last_3m_upsert.sql` | DB parity for `bureau.inquiries.last_3m` |
| Tests | DP-2B projection / provides / inquiry / KYC / LTV assertions |

---

## Appendix — classification counts (of the original 8)

| Class | Count |
|---|---:|
| A PRODUCTION_READY_PROVEN | **8** |
| B METADATA_INCOMPLETE_BUT_RUNTIME_PROVEN (repaired) | **8** metadata fixes (all graduated to A) |
| C PRODUCTION_READY_NOT_PROVEN | **0** |

**READY FOR DP-3 POLICY/GACAT/SCORECARD CONVERGENCE:** YES (with unresolved Policy tokens and legacy scorecard/UW keys as explicit DP-3 inputs)  
**DP-4 STILL BLOCKED: YES**
