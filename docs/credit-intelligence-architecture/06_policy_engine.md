# 06 — Policy Engine

## Purpose

Source-aware, evidence-aware, temporal evaluation of immutable **policy packages**.

## Observed today

- `UnderwritingRuleEngine` hardRules + constraints + scorecardRules  
- `ScorecardPolicyEngine` rows/thresholds  
- Implicit order in `LoanApplicationFlowService`  
- Outcomes: APPROVE / REJECT / MANUAL_REVIEW (no DATA_INSUFFICIENT)  
- `dependsOn` ALL/ANY; FormulaEvaluator arithmetic  

## Target rule types

hard · knockout · soft · referral · data-quality · cross-source · scorecard · pricing · limit · collateral · approval-authority · covenant · monitoring · early-warning  

## Outcomes

```text
PASS | FAIL | REFER | DATA_INSUFFICIENT | NOT_APPLICABLE | ERROR
```

Distinguish: **zero** vs **null** vs **missing** vs **defaulted** vs **N/A** vs **stale** vs **disputed**.

## DSL (JSON sketch)

```json
{
  "rule_id": "HARD_BUREAU_SCORE_MIN",
  "version": "1.0.0",
  "severity": "HARD",
  "expression": {
    "op": "AND",
    "args": [
      { "op": "EXISTS", "metric": "bureau.consumer.score", "classifications_any": ["VERIFIED","MANUAL"] },
      { "op": "GTE", "metric": "bureau.consumer.score", "value": 650 }
    ]
  },
  "on_fail": "FAIL",
  "reason_code": "BUREAU_SCORE_BELOW_MIN",
  "severity": "CRITICAL",
  "override_eligible": false
}
```

Supported: AND/OR/NOT, nested, comparisons, ranges, set membership, existence/null/missing/defaulted/stale/confidence/classification/freshness, dates/periods/trends, aggregations, metric & reconciliation refs, dependencies, reusable expressions, groups, priorities, effective dates, short-circuit, conflict resolution, explainability.

## Example rules (≥20)

| ID | Intent |
|---|---|
| HARD_BORROWER_VINTAGE | Business vintage ≥ N months |
| HARD_BUREAU_SCORE | Score ≥ product min |
| HARD_MAX_DPD | Max DPD ≤ threshold |
| HARD_LIVE_UNSECURED | live_unsecured_loan_count ≤ N (**from tradelines**, not default) |
| HARD_GST_FILING_DELAY | Filing delay days ≤ N |
| XSRC_GST_BANK_VAR | reconciliation GST↔bank not CONFLICT |
| XSRC_ITR_GST_VAR | ITR↔GST ACCEPTABLE_VARIANCE or MATCH |
| SOFT_FOIR | FOIR ≤ policy band else REFER |
| HARD_DSCR | DSCR ≥ min |
| HARD_LTV | LTV ≤ max |
| SOFT_CUST_CONC | Customer HHI → REFER if high |
| HARD_CHEQUE_BOUNCE_3M | Bounce count = 0 |
| SOFT_CASH_DEPOSIT_RATIO | Ratio ≤ max |
| SOFT_ABB | Avg bank balance ≥ floor |
| HARD_REVENUE_DECLINE | YoY revenue drop → FAIL/REFER |
| SOFT_RELATED_PARTY_FLOWS | Ratio → REFER |
| HARD_SANCTIONS_HIT | AML/sanctions FAIL |
| DQ_DATA_FRESHNESS | Bureau/GST as_of within N days else DATA_INSUFFICIENT |
| DQ_MISSING_BUREAU | No bureau source → DATA_INSUFFICIENT |
| GATE_AI_CANDIDATE | AI_CANDIDATE must not be used where HARD requires VERIFIED |

## Policy lifecycle

```text
DRAFT → REVIEW → APPROVED → SCHEDULED → ACTIVE → SUPERSEDED → RETIRED
```

**Policy package freezes:** rule versions, metric versions, scorecard versions, formula versions, pricing, limit-sizing, approval matrix, orchestration version + checksum.

Maker-checker, diff, simulation, impact, back-test, champion/challenger, tenant/lender/product/geo/segment scopes.

## Orchestration stages (declarative)

1 data readiness · 2 identity/KYC · 3 fraud/sanctions · 4 bureau · 5 banking · 6 tax · 7 financial · 8 obligations · 9 WC · 10 collateral · 11 scorecards · 12 eligibility · 13 limit · 14 pricing · 15 covenants · 16 recommendation · 17 approval authority  

Stage config: continue_on_fail?, parallel?, timeout, selective re-eval graph. **Not** Java method order alone.

## Retain

HardRules, ScorecardPolicyEngine traces, FormulaEvaluator core, limitSizing JSON as limit rule pack seed.
