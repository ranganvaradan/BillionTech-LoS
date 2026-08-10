-- Phase C2: GST fact definitions and metric definitions

INSERT INTO ci_fact_definition (canonical_path, version, description, value_type, domain, repeatable, sensitive, allowed_classifications, status)
VALUES
('gst.registration.available', 1, 'GST registration available', 'BOOLEAN', 'GST', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE'),
('gst.registration.active', 1, 'GST registration active', 'BOOLEAN', 'GST', false, false, '["DERIVED","VERIFIED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('gst.registration.status', 1, 'GST registration status', 'STRING', 'GST', false, false, '["VERIFIED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('gst.registration.age_months', 1, 'GST registration age months', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.registration.gstin_count', 1, 'GSTIN count for borrower', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.return.gstr1.latest_period', 1, 'Latest GSTR1 period', 'STRING', 'GST', false, false, '["DERIVED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('gst.return.gstr1.latest_filed_period', 1, 'Latest filed GSTR1 period', 'STRING', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.return.gstr3b.latest_period', 1, 'Latest GSTR3B period', 'STRING', 'GST', false, false, '["DERIVED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('gst.return.gstr3b.latest_filed_period', 1, 'Latest filed GSTR3B period', 'STRING', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr1.missing_count_6m', 1, 'GSTR1 missing count 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr1.late_count_6m', 1, 'GSTR1 late count 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr1.max_delay_days_6m', 1, 'GSTR1 max delay days 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr3b.missing_count_6m', 1, 'GSTR3B missing count 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr3b.late_count_6m', 1, 'GSTR3B late count 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.filing.gstr3b.max_delay_days_6m', 1, 'GSTR3B max delay days 6m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.monthly', 1, 'GST turnover monthly (latest)', 'DECIMAL', 'GST', false, false, '["EXTRACTED","DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.trailing_3m', 1, 'GST turnover trailing 3m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.trailing_6m', 1, 'GST turnover trailing 6m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.trailing_12m', 1, 'GST turnover trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.current_fy_ytd', 1, 'GST turnover current FY YTD', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.latest_completed_fy', 1, 'GST turnover latest completed FY', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.gstr1.trailing_12m', 1, 'GSTR1 turnover trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.gstr3b.trailing_12m', 1, 'GSTR3B turnover trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.turnover.annualized_current_run_rate', 1, 'Annualized GST run rate', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.tax.liability.trailing_12m', 1, 'Tax liability trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.tax.cash_paid.trailing_12m', 1, 'Cash tax paid trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.tax.itc_utilized.trailing_12m', 1, 'ITC utilized trailing 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.credit_note.ratio_12m', 1, 'Credit note ratio 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.debit_note.ratio_12m', 1, 'Debit note ratio 12m', 'DECIMAL', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.activity.b2b_invoice_count_12m', 1, 'B2B invoice count 12m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('gst.activity.b2c_invoice_count_12m', 1, 'B2C invoice count 12m', 'INTEGER', 'GST', false, false, '["DERIVED"]'::jsonb, 'ACTIVE')
ON CONFLICT (canonical_path, version) DO NOTHING;

INSERT INTO ci_metric_definition (metric_code, version, description, domain, status, definition_json)
VALUES
('gst.turnover.trailing_12m', 'V1', 'Sum of qualifying period turnover over trailing 12 months', 'GST', 'ACTIVE',
 '{"periodBasis":"TRAILING_12M","preferReturnType":"GSTR1","minCompleteness":0.75,"filingNorm":"GST_FILING_STATUS_NORMALIZATION_V1","freshness":"GST_FRESHNESS_POLICY_V1"}'::jsonb),
('gst.turnover.annualized_current_run_rate', 'V1', 'YTD / completed months * 12 when policy permits', 'GST', 'ACTIVE',
 '{"method":"GST_ANNUALIZATION_V1","minMonths":6}'::jsonb),
('gst.filing.timeliness_score', 'V1', 'Deterministic filing timeliness score 0-100', 'GST', 'ACTIVE',
 '{"windowMonths":6,"onTimeWeight":1.0,"latePenalty":10,"missingPenalty":20}'::jsonb),
('gst.gstr1_gstr3b_turnover_variance', 'V1', 'Internal GSTR1 vs GSTR3B turnover reconciliation', 'GST', 'ACTIVE',
 '{"toleranceVersion":"GST_GSTR1_GSTR3B_TOLERANCE_V1"}'::jsonb),
('gst.turnover.trailing_3m', 'V1', 'Trailing 3m turnover', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.turnover.trailing_6m', 'V1', 'Trailing 6m turnover', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.turnover.current_fy_ytd', 'V1', 'Current FY YTD turnover', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.return.missing_count_12m', 'V1', 'Missing return periods in 12m', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.return.late_count_12m', 'V1', 'Late filings in 12m', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.return.max_delay_days_12m', 'V1', 'Max filing delay days in 12m', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.credit_note_ratio_12m', 'V1', 'Credit note / turnover ratio 12m', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.cash_tax_ratio_12m', 'V1', 'Cash tax / liability ratio 12m', 'GST', 'ACTIVE', '{}'::jsonb),
('gst.itc_utilization_ratio_12m', 'V1', 'ITC / liability ratio 12m', 'GST', 'ACTIVE', '{}'::jsonb)
ON CONFLICT (metric_code, version) DO NOTHING;
