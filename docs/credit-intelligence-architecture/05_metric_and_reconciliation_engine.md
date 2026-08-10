# 05 — Metric and Reconciliation Engine

## Metric Engine

Deterministic, versioned, DAG-executed metrics over fact snapshots.

### Metric definition

```text
MetricDefinition {
  metric_code, version, description, formula_ast,
  input_fact_paths[], input_metric_codes[],
  period_logic, aggregation, missing_data_policy,
  allowed_classifications[], rounding, unit,
  source_precedence[], dependencies[],
  effective_from, effective_to, test_cases[]
}
```

**Missing-data policy:** FAIL_CLOSED → DATA_INSUFFICIENT | SKIP | USE_NULL (never silent invent).

### Execution

- Build DAG from dependencies; topological evaluate  
- Selective re-eval when fact changes invalidate dependents  
- Persist `MetricResult` with snapshot_id, metric_version, value, outcome, evidence  

### Initial catalogue (minimum)

**Banking:** adjusted_banking_turnover, avg_monthly_credits, avg_bank_balance, min_balance, cash_deposit_ratio, cheque_return_ratio, emi_bounce_count, od_cc_utilisation, peak_utilisation, days_above_90pct_util, related_party_transfer_ratio, circular_flow_indicator  

**Bureau:** total_live_exposure, secured_exposure, unsecured_exposure, overdue_exposure, max_dpd, recent_inquiries_3m, restructured_count, settled_count, writeoff_count, repayment_track_score, bureau_emi_obligation, live_unsecured_loan_count  

**Financial/tax:** revenue_cagr, ebitda_margin, pat_margin, current_ratio, quick_ratio, debt_equity, tol_tnw, interest_coverage, dscr, cash_dscr, foir, effective_tax_rate, gst_filing_timeliness, itr_filing_consistency  

**Working capital:** receivable_days, inventory_days, payable_days, cash_conversion_cycle, collection_efficiency, invoice_dilution, overdue_ageing_gt90, customer_concentration_hhi, supplier_concentration_hhi  

**Structuring:** eligible_limit_turnover, eligible_limit_banking, eligible_limit_cashflow, eligible_invoice_finance_limit, collateral_limit, ltv, recommended_emi, recommended_tenure, pricing_risk_premium  

### Retain / evolve

- `FormulaEvaluator` → expand AST (still deterministic)  
- `LimitSizingService` → metric + limit rule pack versions  
- FOIR/LTV/DTI in CreditControl → registered metrics  

---

## Reconciliation Engine

First-class cross-source comparisons.

### Pairings

Bank↔GST · Bank↔ITR · GST↔ITR · GST↔financials · ERP↔GST · invoices↔GST · collections↔bank · bureau EMI↔bank EMI · declared obl↔bureau · MCA charges↔borrowings · tax payments↔bank · 26AS↔ITR · AIS↔ITR · PO↔invoice↔GRN · collateral declared↔valuation  

### Result

```text
ReconciliationResult {
  reconciliation_id, definition_version,
  sources[], periods, left_value, right_value,
  variance, tolerance, confidence,
  outcome, explanation, unmatched_items[],
  evidence_refs[], human_review_required
}
```

### Outcomes

```text
MATCH | ACCEPTABLE_VARIANCE | MATERIAL_VARIANCE | CONFLICT | DATA_INSUFFICIENT | NOT_APPLICABLE
```

Policy Engine may reference reconciliation outcomes (e.g. MATERIAL_VARIANCE → REFER).
