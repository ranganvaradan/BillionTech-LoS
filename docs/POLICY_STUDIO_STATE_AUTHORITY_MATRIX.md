# POLICY_STUDIO_STATE_AUTHORITY_MATRIX

Task: POLICY-STUDIO-END-TO-END-STATE-INVARIANT-1  
Baseline SHA: `c1b0d1746da29d1ccf1181411b7867a7cabb19b5`  
DB: V142  
Vikasam: `4543e643-c3a0-4a57-a92c-370dff8b2fa9` (13 rules, 12 unique inventory parameters)

Diagnosis is read-only. This matrix is the authority contract the product must consume.
No new readiness engine. No Wave 11. No Vikasam mutation.

## Axes (must remain distinct)

| AXIS | AUTHORITY | ANSWERS |
|------|-----------|---------|
| 1 Parameter readiness | `CanonicalParameterStateService` | Can the platform structurally obtain/calculate this parameter? READY / NOT_READY |
| 2 Rule lifecycle | `PolicyRuleLifecycleProjection` / `PolicyRuleState` | Authored/reviewed status of this rule |
| 3 Policy Test input/runtime | `PolicyStudioTestExperienceService` test-input state | Is an actual value present for this test/application? |
| 4 Policy version lifecycle | `PolicyLifecycleService` `businessStatus` | DRAFT → IN_REVIEW → APPROVED → SCHEDULED → ACTIVE (historical/governance) |
| 5 Policy execution readiness | `PolicyExecutionReadiness.evaluate` | Is this version structurally executable **now**? READY / BLOCKED |
| 6 Scorecard | CPS for picker; `CanonicalScorecardValueResolver` VALUE_AVAILABLE for scoring | Unchanged numerical behaviour |
| 7 Certification / live | existing live authority LEGACY_FROZEN | Unchanged |

READY ≠ VALUE_AVAILABLE.  
ACTIVE ≠ currently executable.  
APPROVED (applicability/approval) ≠ ACTIVE (effective lifecycle).

## Surface matrix (pre-fix observed)

| SURFACE | UI_FIELD | CURRENT_VALUE | BACKEND_FIELD | PRODUCER_SERVICE | CANONICAL_AUTHORITY | STATE_AXIS | CURRENT_OR_HISTORICAL | CORRECT_AUTHORITY? | DEFECT? |
|---------|----------|---------------|---------------|------------------|---------------------|------------|----------------------|--------------------|---------|
| Policy List | Status | APPROVED | `status` from `CiPolicyApplicability.businessStatus` | `PolicyStudioDurableLandingListService.buildRow` | applicability row, not Versions lifecycle | AXIS 4 mixed | Stale vs Versions ACTIVE | NO | YES — prefers applicability over `PolicyLifecycleService` |
| Policy List | Need input | 0 | `needsInputCount` | `PolicyExecutionReadiness.countNeedsBusinessInput` | EXEC but skips IGNORED / KEEP_AS | AXIS 5 | Current | NO | YES — independent skip hides 2 NOT_READY params |
| Policy List | Rules | 13 | `underwritingRuleCount` | graph / session count | session | count | Current | YES as count | NO |
| Scope | products / effective | configured | applicability | `PolicyLifecycleService` / applicability | AXIS 4 | Current+historical | YES | NO |
| Rules | Chip | Not applicable (DPD30, CC overdue, Overdue) | `lifecycle.ruleLifecycleLabel` | `PolicyRuleLifecycleProjection.deriveState` | AXIS 2 | Current | NO | YES — `!includedForActivation` → NOT_APPLICABLE |
| Rules | Parameter: | Needs review (DPD30) | `derivePolicyStudioOperandPresentation` / `lenderPrimaryFromTruth` fallback | presenter + UI fallback | AXIS 1 leak from AXIS 2 | Current | NO | YES — null `parameterId` → "Needs review" |
| Rules | Parameter: | Calculation not defined (CC/overdue) | CPS stamp | `CanonicalParameterStateService` | AXIS 1 | Current | YES | NO (label ok; chip N/A is the defect) |
| Scorecard | picker labels | CPS | `ScorecardConvergenceService` | AXIS 1 | Current | YES | NO numerical change |
| Test | Filled automatically | on blank READY fields | `status=AUTOMATIC_DERIVED` | `applyCanonicalTestStatus` + `StatusPill` | AXIS 1 misused as AXIS 3 | Current | NO | YES — READY ≠ value present |
| Test | required / automatic / unresolved | 13 / 11 / 2 | `readinessSummary` | Test inventory | mixed AXIS 1+3 | Current | partial | automatic ≠ populated (ok if labelled); 13 vs 12 explained below |
| Versions | Current status | ACTIVE | `businessStatus` | `PolicyLifecycleService.settingsView` | AXIS 4 | Historical/effective | YES | NO — keep ACTIVE |
| Versions | Required parameters resolved | ✓ | `evaluate().requiredParametersResolved` | `PolicyExecutionReadiness` skipping ignored | AXIS 5 | Current | NO | YES — green while 2 NOT_READY |
| Versions | Test completed | ✓ | GOV tests+simulation | historical event | AXIS 4/GOV | Historical | mixed | Must not imply current executable |
| Versions | execution-ready | 10 rules ✓ | included-executable only | AXIS 5 partial | Current | NO | hides ignored-rule param blockers |
| Maker-checker | CM / Checker | Approved | reviews | GOV | Historical | YES | NO rewrite |
| Scheduled Policies | n/a for Vikasam ACTIVE | lifecycle | AXIS 4 | Historical | YES | NO |

## NOT_APPLICABLE audit

Intended meaning: rule is **out of underwriting-automation scope** (classification-only, data-requirement-only, metric adjustment, compound child, deleted).

| Class | Meaning | Vikasam DPD30 / CC overdue / Overdue amount |
|-------|---------|-----------------------------------------------|
| A. LEGITIMATE_NOT_APPLICABLE | Out of UW automation scope | NO — these are genuine policy rules |
| B. FALLBACK_FOR_MISSING_RULE_STATE | Missing lifecycle → N/A | Partial — KEEP_AS / IGNORED mapped via `includedForActivation=false` |
| C. FALLBACK_FOR_PARAMETER_NOT_READY | NOT_READY → N/A | YES for CC overdue / Overdue (also ignored) |
| D. FALLBACK_FOR_UNRESOLVED | unbound operand → N/A | YES for DPD30 (`parameterId` null, KEEP_AS) |
| E. OTHER | — | — |

After correction: FALSE_NOT_APPLICABLE_RULE_COUNT = 0.  
Legitimate N/A retained for classification / data-requirement / metric-adjustment / compound-child / deleted.

## POLICY_TEST_COUNT_EXPLANATION

Observed: 13 required · 11 automatic · 2 unresolved.

- Inventory unique canonical parameters on Vikasam graph: **12**
- Policy Test unique canonical inputs: **13**
- Extra test-only input: `bureau.inquiries.current_month` (not a duplicate alias of another required id)
- POLICY_TEST_DUPLICATE_CANONICAL_INPUT_COUNT = 0
- Do not force 12 = 13
- `required` = unique canonical test inputs
- `automatic` = structurally automatic (CPS READY derived) — **not** “value already populated”
- `unresolved` = structural blockers only (CALCULATION_NOT_DEFINED / unbound) — **not** missing runtime data on a READY parameter

## APPROVED vs ACTIVE

Both stored states are valid and distinct:

- **POLICY_LIST_STATUS_MEANING (after fix)** = version **lifecycle** (`PolicyLifecycleService.businessStatus`) = ACTIVE for Vikasam
- **Approval** = applicability / approval record may still say APPROVED
- **VERSION_STATUS_MEANING** = same lifecycle ACTIVE
- Do not merge stored fields. Do not rewrite ACTIVE or approval history.
- Present: Lifecycle = ACTIVE; Approval = APPROVED (secondary)

## Vikasam 13-rule trace (pre-fix, condensed)

| Rule | Param IDs | CPS | RuleState (observed chip) | Test input | Scorecard | Version relevance |
|------|-----------|-----|---------------------------|------------|-----------|-------------------|
| 10 ACCEPTED UW rules | bound READY ids | READY | ACCEPTED_READY_TO_TEST | WAITING_FOR_DATA (blank) mislabelled Filled automatically | picker CPS | included-executable |
| CM_BUREAU_CC_OVERDUE_AMOUNT_GTE | bureau.cc_overdue_amount | NOT_READY / CALCULATION_NOT_DEFINED | IGNORED → **Not applicable** (false) | CALCULATION_REQUIRED | no numerical change | skipped by Need Input |
| CM_BUREAU_OVERDUE_AMOUNT_LTE | bureau.overdue.amount | NOT_READY / CALCULATION_NOT_DEFINED | IGNORED → **Not applicable** (false) | CALCULATION_REQUIRED | no numerical change | skipped |
| CM_BUREAU_DPD_30_PLUS_COUNT_6M_GTE | operandKey dpd_30_plus_count_6m, parameterId null; canonical `bureau.dpd_30_plus_count_6m` READY | leak **Needs review** | KEEP_AS → **Not applicable** (false) | would be automatic once bound | no numerical change | skipped |

Pre-fix aggregates (independent counters — the defect):

- RULE_COUNT=13; RULES_READY_COUNT≈10; RULES_NEED_ATTENTION shown as N/A not attention
- UNIQUE_PARAMETER_COUNT inventory=12; PARAMETERS_NOT_READY=2 (CC overdue, overdue amount); DPD30 READY
- POLICY_TEST_UNRESOLVED=2
- POLICY_LIST_NEED_INPUT=0 **mismatch**
- VERSION_REQUIRED_PARAMETERS_RESOLVED=true **mismatch**

## Independent counters to collapse

`countNeedsBusinessInput`, `evaluate().requiredParametersResolved`, Versions checklist “Required parameters resolved”, and Policy List Need Input must all consume **the same** `PolicyExecutionReadiness` current-parameter-blocker projection (unique NOT_READY / unbound operands on all non-deleted UW rules, including IGNORED / KEEP_AS).

Included-executable counts remain for **automation inclusion**. They must not overwrite current structural blockers.

## Stale tests (disposition)

| Test | Disposition |
|------|-------------|
| `PolicyStudioGate3ExecutabilityTest` expecting `PRODUCTION_READY` + `productionReady=true` | **B** obsolete vs honesty model (`ParameterExecutabilitySupport` always `productionReady=false`). Update expectations to current product. Workflow-provides cases remain **A** valid. |
| `PolicyLifecycleServiceTest.criticalDataReadiness_blocksApproval` expecting `"EDI"` | **C** fixture stale: `application.proposed_edi` is MANUAL authorised and no longer blocks. Replace with CASE F: new DRAFT + NOT_READY calculation operand blocked by `PolicyExecutionReadiness`. Do not restore EDI block. |
| `SurfaceConvergenceAcceptanceTest.golden12` expecting `bureau.inquiry.purpose` not spine-capable | **B** obsolete: Equifax-normalized RAW fields are registered on `RawFactProducer` via `PlatformNormalizedRawFieldCatalog`. Product is correct; test now asserts surface=spine and RAW-mapped IDs are capable. |
| `PolicyStudioSessionPersistenceP0Test` mocking `save()` only | **C** stale fixture: persist path uses `saveAndFlush`. Stub both. |
| `PolicyReadinessConvergenceTest` asserting mixed `card.status` Ready / Needs your input | **B** obsolete mixed-axis: `card.status` is now the rule-lifecycle chip. Execution readiness is `executionReady`. |
