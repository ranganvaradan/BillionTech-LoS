# 22 — Tax Shadow Comparison

## Adapter overlay (`use-for-shadow-rules`)

When enabled, `LegacyUnderwritingContextAdapter` may overlay (shadow path only):

| Scorecard key | Canonical source |
|---------------|------------------|
| `ITR_INCOME` | `itr.business.turnover.latest_fy` else `itr.total_income.latest_fy` if PASS |
| `PAT` | `itr.business.pat.latest_fy` if PASS |
| `TOL` | `itr.balance.tol.latest_fy` if PASS |
| `TNW` | `itr.balance.tnw.latest_fy` if PASS |

`DATA_INSUFFICIENT` / `NOT_APPLICABLE` → keep legacy + flag fallback. Production CreditControl unchanged.

## Shadow metadata

`ShadowCreditEvaluationService` writes `metadata.taxComparison` with:

- Canonical tax rule outcomes (`CanonicalTaxRuleEvaluator`)
- Legacy vs canonical ITR_INCOME
- Mismatch classifications (`TaxMismatchClassification`)
- Frozen parser/normalizer/metric versions

## Rules (shadow-only)

`DQ_ITR_AVAILABLE`, `DQ_ITR_FRESHNESS`, `DQ_ITR_PAN_MATCH`, `ITR_MIN_ANNUAL_INCOME`, `ITR_MIN_BUSINESS_TURNOVER`, `ITR_PROFITABILITY`, `ITR_NET_WORTH`, `ITR_FILING_CONSISTENCY`, `ITR_REVISED_RETURN_REVIEW`, `ITR_OUTSTANDING_TAX_DEMAND`, `XSRC_ITR_26AS_TDS_VARIANCE`, `XSRC_ITR_AIS_INCOME_VARIANCE`.

Presumptive: profitability / net-worth → `NOT_APPLICABLE`.
