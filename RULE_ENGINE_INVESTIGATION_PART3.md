# Rule Engine Investigation — Part 3

Trace of `LIVE_UNSECURED_LOAN_COUNT` and `BUREAU_ENQUIRIES_3M` into `ctx.scorecard()`. Paths relative to repo root. No code changes.

---

## 1. Trace from `resolveEffective()` to source data

**Assembly entry point:** `CreditControlService.resolveEffective(LoanApplication app, String computedKycOutcome)`  
`los-core-service/src/main/java/com/los/core/service/credit/CreditControlService.java` lines **412–581**.

Scorecard map `sc` is built in that method and passed into `new EffectiveUnderwritingContext(..., sc)` at lines **580–581**.

### `LIVE_UNSECURED_LOAN_COUNT`

| Step | Location | What happens |
|---|---|---|
| Persist (optional) | `mergeManualInputs` → `mergeScorecardFieldsFromRequest` lines **260**, **333–337** | If API request has `liveUnsecuredLoanCount`, stored under `financialInfo.creditControl.manual.liveUnsecuredLoanCount` as `{ value, source: "MANUAL", updatedAt }` |
| Into scorecard | `resolveEffective` line **499** | `putScorecardValue(manual, sc, "LIVE_UNSECURED_LOAN_COUNT", "liveUnsecuredLoanCount")` — copies manual cell to `sc` if present |
| Gap fill | `applyMissingScorecardDefaults` line **832** | If key missing or ≤0: `putBankGapDefault(sc, "LIVE_UNSECURED_LOAN_COUNT", new BigDecimal("2"))` |

**Computing service:** None. No class derives this count from bureau XML/reportData.  
**Underlying data:** Manual credit input (`ManualCreditInputsRequest.liveUnsecuredLoanCount`, DTO lines 96–97) and/or hard-coded gap default `2`. Not `KycStepResult.parsedData`, not a live bureau call at underwriting time.

### `BUREAU_ENQUIRIES_3M`

| Step | Location | What happens |
|---|---|---|
| Persist (optional) | `mergeScorecardFieldsFromRequest` lines **261**, **333–337** | Same pattern for `bureauEnquiries3m` |
| Into scorecard | `resolveEffective` line **500** | `putScorecardValue(manual, sc, "BUREAU_ENQUIRIES_3M", "bureauEnquiries3m")` |
| Gap fill | `applyMissingScorecardDefaults` line **833** | If missing or ≤0: `putBankGapDefault(sc, "BUREAU_ENQUIRIES_3M", new BigDecimal("5"))` |

**Computing service:** None.  
**Underlying data:** Manual credit input and/or gap default `5`. Not bureau `parsedData`.

### Alternate path (same keys, still not bureau-computed)

`mergeScorecardMetricsMap` (lines **362–376**), called from `resolveEffective` at line **514**: if `manual.scorecardMetrics` contains keys literally named `LIVE_UNSECURED_LOAN_COUNT` or `BUREAU_ENQUIRIES_3M`, those values are merged into `sc`. Still operator/API-supplied, not Equifax-derived.

### What the bureau pull actually does (for contrast)

`BureauPullStepExecutor` (`BureauPullStepExecutor.java` lines **115–134**):

- Calls `integrationRouter.routeBureauPull(...)`.
- Saves `bureauResult.reportData()` into `KycStepResult.parsedData` (`KycStepType.BUREAU_PULL`).
- On success, sets only `app.setBureauScore(bureauResult.creditScore())`.

Nothing in that path writes `LIVE_UNSECURED_LOAN_COUNT` or `BUREAU_ENQUIRIES_3M` into financialInfo or the scorecard map.

`resolveEffective` later sets `sc.put("BUREAU_SCORE", …)` at line **577** from `resolveBureau` (app / manual score) — that is the only Equifax-linked scorecard key among the three V81 “BUREAU” hard-rule params; the other two are manual/gap.

---

## 2. Relation to Equifax `reportData` aggregates

### `LIVE_UNSECURED_LOAN_COUNT` vs Equifax account summary

**Not computed from Equifax aggregates.**

`EquifaxBureauProvider.extractAccountSummary` (lines **268–320**) writes:

- `totalAccounts`, `activeAccounts`, `closedAccounts`, `overdueAccounts`
- `totalOutstanding`, `totalSanctioned`, `suitFiled`

There is no unsecured/secured filter, no “live unsecured loan count,” and no mapping from those counters to `LIVE_UNSECURED_LOAN_COUNT`.

### `BUREAU_ENQUIRIES_3M` vs Equifax enquiry summary

**Not computed from Equifax enquiry fields.**

`EquifaxBureauProvider.extractEnquirySummary` (lines **327–337**) writes:

- `enquiryAge30Days` (Past30Days)
- `recentEnquiries` (Recent)
- `enquiries12Months` (Past12Months)
- `enquiries24Months` (Past24Months)

There is **no** 3-month enquiry field and **no** code that maps any of these into `BUREAU_ENQUIRIES_3M`.

Simulated fallback (`simulatedFallback`, lines **365–380**) also only sets the aggregate keys above (plus DPD / suitFiled), not the scorecard policy keys.

### UI labeling vs reality

- V81 hard rules use `"source":"BUREAU"` for these params (UI catalog in `scorecardConfig.ts` lines 29–34).
- `ScorecardPolicyEngine.resolve("BUREAU", …)` only special-cases `BUREAU_SCORE`; other BUREAU params fall through to `ctx.scorecard()`.
- `scorecardParameterSources.ts` documents sources as “Bureau pull” / “Manual credit…” — the pull path is **not implemented** for these two keys today.
- Frontend `creditSummaryBuilder.ts` line 8 treats them as `BUREAU_EFFECTIVE_POLICY_KEYS` for display grouping only.

---

## 3. Exact write sites (template for new metrics)

### Primary write — manual → scorecard

**Method:** `CreditControlService.putScorecardValue`  
**Lines:** **347–355**  
**Call sites for these keys:** `resolveEffective` **499–500**

```text
putScorecardValue(manual, sc, "LIVE_UNSECURED_LOAN_COUNT", "liveUnsecuredLoanCount");
putScorecardValue(manual, sc, "BUREAU_ENQUIRIES_3M", "bureauEnquiries3m");
```

`putScorecardValue` does not calculate; it reads `manual.get(manKey)`, unwraps `{value:…}`, coerces via `toYesNoOrNumberBd`, then `out.put(key, b)`.

### Upstream of manual values

| Method | Lines | Role |
|---|---|---|
| `mergeScorecardMetricField` | 333–337 | Writes one manual cell |
| `mergeScorecardFieldsFromRequest` | 260–261 | Invoked when saving manual credit API payload |

### Secondary write — gap defaults (not a calculation from bureau)

**Method:** `CreditControlService.applyMissingScorecardDefaults`  
**Lines:** **832–833** (helper `putBankGapDefault` at **1029–1034**)

```text
applied |= putBankGapDefault(sc, "LIVE_UNSECURED_LOAN_COUNT", new BigDecimal("2"));
applied |= putBankGapDefault(sc, "BUREAU_ENQUIRIES_3M", new BigDecimal("5"));
```

Called from `resolveEffective` at line **544** after manual/extract merges.

### Optional merge — free-form metrics bag

**Method:** `mergeScorecardMetricsMap`  
**Lines:** **362–376**  
**Called from:** `resolveEffective` line **514**

Can overwrite/set the same scorecard keys if present under `manual.scorecardMetrics`.

### Calculation sites

**None** for either key. There is no method that “calculates” them from bureau data. Closest related bureau math elsewhere (`CreditDecisionServiceImpl.evaluate`, lines **47–86**) reads `parsedData` fields `creditScore`, `overdueAccounts`, `dpd90Plus`, `willfulDefaulter` for a separate legacy decision path — it does **not** touch `LIVE_UNSECURED_LOAN_COUNT` / `BUREAU_ENQUIRIES_3M` or `ctx.scorecard()`.

---

## 4. Is there a single place to add a new bureau-derived scorecard metric?

**No single place today that turns Equifax `reportData` / `KycStepResult.parsedData` into scorecard keys.**

Values labeled or catalogued as BUREAU-sourced for policy are assembled in **multiple** places, and almost all are **not** bureau-pull-derived:

| # | Location | What it contributes |
|---|---|---|
| 1 | `CreditControlService.resolveEffective` + `putScorecardValue` (~499–500 pattern) | Manual → scorecard for named keys including `LIVE_UNSECURED_LOAN_COUNT`, `BUREAU_ENQUIRIES_3M`, `NTC_FLAG` |
| 2 | `CreditControlService.mergeScorecardMetricsMap` (362–376) | Arbitrary manual `scorecardMetrics` keys → scorecard |
| 3 | `CreditControlService.applyMissingScorecardDefaults` (832–833, etc.) | Hard-coded gap defaults when missing/zero |
| 4 | `CreditControlService.resolveEffective` line **577** | `BUREAU_SCORE` from `resolveBureau` (app `bureauScore` / manual override) — **only** scorecard key filled from the bureau pull’s credit score |
| 5 | `BureauPullStepExecutor` (123, 131–133) | Persists full `reportData` on `KycStepResult` and `bureauScore` on the application — **does not** map aggregates into scorecard |
| 6 | `EquifaxBureauProvider` extract methods | Builds aggregate `reportData` only |
| 7 | `CreditDecisionServiceImpl.evaluate` | Reads some `parsedData` aggregates for a different decision API; **not** wired into `EffectiveUnderwritingContext.scorecard()` |

**Implication for adding a new bureau-derived metric:** there is no existing mapper from bureau `parsedData` → scorecard to extend. A new metric would need new wiring (typically: derive in/after bureau pull or in `resolveEffective` by reading latest `KycStepResult` for `BUREAU_PULL`, then `sc.put(...)`), plus optionally Equifax extract fields if the raw aggregate does not already exist. The current `putScorecardValue` / gap-default pattern is the template for **manual** bureau-policy fields, not for live bureau derivation.
