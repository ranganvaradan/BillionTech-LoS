# WAVE 10A — Policy Studio Resolver Audit

## 1. What resolver existed

Policy Studio already had a multi-path resolution experience for rules/parameters that need lender action — not a single dead-end badge.

Primary surfaces:

| Surface | Role |
| --- | --- |
| `CiParameterResolverPanel` | Map parameter / search / propose definition / manual input (`RESOLVE_PARAMETER_*`) |
| `SuggestCalculationWorkflow` | Authored derived-calc research → clarify → accept proposal / confirm existing |
| `CiDataCalcResolutionPanel` | Grouped data/calc resolution kinds |
| `CiBoundaryResolverPanel` | Open boundary ambiguity |
| Rule action bar in `CiPolicyRulesTab` | Accept / Edit / Ignore / Delete / Manual input |

## 2. Implementing components / services

**UI**

- `ui-service/src/pages/creditIntelligence/CiPolicyRulesTab.tsx`
- `ui-service/src/pages/creditIntelligence/CiParameterResolverPanel.tsx`
- `ui-service/src/pages/creditIntelligence/CiDataCalcResolutionPanel.tsx`
- `ui-service/src/pages/creditIntelligence/CiBoundaryResolverPanel.tsx`
- `ui-service/src/components/dataParameters/SuggestCalculationWorkflow.tsx`

**API / backend**

- `PolicyAuthoringCompleteness.attachLifecycle` → `PolicyRuleLifecycleProjection`
- `RuleOperandPresenter` + `AuthoredDerivedCalculationSupport.overlayOperand`
- `BusinessCalculationAssistant` / `DerivedCalculationResearchService`
- `DerivedCalculationDefinitionService` (accept / create definition)
- Session resolution persistence under `data/policy-studio-resolutions/`
- Review actions via Credit Intelligence rule review API (`uiAction`: `ACCEPT`, `RESOLVE_PARAMETER_MAP`, `RESOLVE_PARAMETER_MANUAL`, `RESOLVE_PARAMETER_USE_PROPOSAL`, …)

## 3. Resolution actions currently present

- Resolve / change parameter or source (`RESOLVE_PARAMETER_MAP`, Change parameter button)
- Manual input (`RESOLVE_PARAMETER_MANUAL`)
- Propose / use proposed definition (`RESOLVE_PARAMETER_USE_PROPOSAL`)
- Work it out / research calculation (`suggestDerivedCalculation`)
- Accept / edit derived calculation proposal
- Confirm existing calculation meaning (`CONFIRM_EXISTING` / knownExisting path)
- Accept rule / Ignore / Delete / Edit threshold
- Boundary ambiguity resolution

## 4. API endpoints (representative)

- Derived calc: suggest / latest / accept / edit proposal APIs (`ui-service/src/api/derivedCalculations`)
- Parameter catalogue / search / propose definition (`creditIntelligence` API)
- Policy rule review / resolution persistence (Policy Studio session APIs)

## 5. Persisted state modified

- Session rule disposition / metadata (accept, ignore, edit)
- Parameter resolution maps (mapped id, manual capture fields)
- Derived calculation research proposals (`READY_FOR_REVIEW`, etc.)
- Authored calculation definitions (only after explicit accept — not auto)

Canonical truth recomputes from CPES + definitions + certification after mutations.

## 6. Why resolver is not rendered / mis-signalled now

Root cause is **consumer conflation**, not deletion of the resolver:

1. Rule chip uses `lifecycle.lenderStateLabel` / `statusChip`. Unresolved **rule review** and **calculation setup** both collapse toward “Needs your input” (including UI remap of “Needs Review” → “Needs your input”).
2. `SuggestCalculationWorkflow` mounts when `op.calculationRequired === true` from **catalogue honesty flags**, even when CPES capability is true (DPD30). The idle panel title is literally **“Needs your input”**, so lenders see calculation-setup language for an executable parameter.
3. `forbidNeedsInputWhenAcceptedReady` does **not** cover `READY_FOR_CONFIRMATION`, so setup workflow still appears while the rule only needs Accept.
4. Primary D&P badges still render legacy `parameterSupport` / “Available for policy design”, so truth and actions diverge across surfaces.
5. Resolver panels (`CiParameterResolverPanel`) remain wired; they are under-triggered when the wrong badge/workflow steals attention, or over-triggered for executable params.

## 7. Still valid

- `CiParameterResolverPanel` mapping / manual / proposal paths
- `SuggestCalculationWorkflow` research + accept proposal (when calculation truly needs setup)
- `PolicyRuleLifecycleProjection` facts machine (with separation of axes)
- `CanonicalParameterTruthProjection` + `LenderTruthDisplayMapper`
- Derived calculation proposal lifecycle (`READY_FOR_REVIEW` ≠ executable)

## 8. Legacy / incorrect for primary lender truth

- Catalogue `derivationDefined && !implemented` as primary “Needs your input”
- `parameterSupport` SUPPORTED_* as primary execution readiness
- List completeness denominators (`28/28`) as capability
- Filters: Legacy readiness / Catalogue Production Ready on ordinary bar
- Single badge collapsing rule lifecycle + calculation setup

## 9. Canonical truth consumption after Wave 10A

- Parameter readiness label = `canonicalTruth.primaryStatusLabel` only
- Show calculation resolver **only** when execution capability is false / calculation required by truth
- If `READY_FOR_REVIEW` proposal exists → action **Review proposed calculation**
- If parameter executable and rule unaccepted → Parameter **Ready to test** (or data-unavailable wording) + Rule **Needs review** separately
- Manual class → **Needs manual input** + existing manual resolver

## 10. Unreachable accidentally?

Capability was not deleted. It became **mis-gated**:

- Executable parameters incorrectly open the calc-setup “Needs your input” panel
- Genuine unresolved cases still have Resolve / Change / Suggest paths, but the wrong primary message makes the experience look like a dead-end badge

Wave 10A restores **state-driven** presentation without inventing a parallel resolver.
