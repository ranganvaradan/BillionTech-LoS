# Policy → Scorecard target contract (STEP 2 lock)

**Status:** CONTRACT LOCK — scoring convergence not fully implemented in STEP 2  
**SHA baseline:** post–Category → Policy Version bind  

## Locked chain

```
GACAT  (canonical parameter authority)
  → Policy Version parameter / factor (Policy Studio)
      → optional Scorecard Version participation
          → scoring transformation / bands
          → relative weight
```

## Rules

1. **Policy Version → optional Scorecard Version** (0..1).
2. Scorecard must **not** independently expand the Policy's decision-factor universe.
3. Every Scorecard factor must resolve to a **GACAT canonical parameter** already permitted/referenced by that Policy Version.
4. **Do not** create a second parameter catalogue.
5. Legacy Live Underwriting Rules must **not** receive a new parallel GACAT authoring investment.
6. Existing `UnderwritingRuleEngine` / `ScorecardPolicyEngine` remain execution/compile targets until Policy publish cutover.

## Relative weight semantics (locked)

Scorecard weights are **relative weights**. They are **not** required to total 100.

Example: raw weights A=3, B=2, C=1 → normalized A=50%, B≈33.333%, C≈16.667%.

```
normalizedWeight(i) = rawWeight(i) / sum(applicableRawWeights) * 100
```

Requirements:

| Rule | Locked |
|---|---|
| Preserve raw lender-entered weight | YES |
| Normalized value is calculated | YES |
| Raw total need not equal 100 | YES |
| Negative weight prohibited | YES |
| All applicable weights zero → validation failure | YES |
| Zero-weight factor contributes zero | YES |
| Missing REQUIRED data silently renormalizes remaining factors | **NO** |
| Only genuinely non-applicable factors excluded from denominator | YES |

Missing REQUIRED data follows Policy / data-readiness semantics — not silent renormalization.

**Implementation note:** STEP 2 documents this contract only. Do not change live scoring formulas unless a later convergence task explicitly implements it.

## Category bind note

Customer Category → Policy Version bind (STEP 2) is **configuration only**. It does not:

- enable `allowCanonicalAuthority`
- change live UW / scorecard routing / workflow / KYC
- activate Policies or Categories automatically
