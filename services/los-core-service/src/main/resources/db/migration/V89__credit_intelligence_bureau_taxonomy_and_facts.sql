-- Phase C1: bureau fact definitions, metric definitions, Equifax product taxonomy mappings

INSERT INTO ci_fact_definition (canonical_path, version, description, value_type, domain, repeatable, sensitive, allowed_classifications, status)
VALUES
('bureau.report.available', 1, 'Bureau report available', 'BOOLEAN', 'BUREAU', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE'),
('bureau.report.report_date', 1, 'Bureau report date', 'DATE', 'BUREAU', false, false, '["VERIFIED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('bureau.report.age_days', 1, 'Bureau report age in days', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.report.quality_status', 1, 'Bureau report quality status', 'STRING', 'BUREAU', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE'),
('bureau.consumer.score_type', 1, 'Consumer score type', 'STRING', 'BUREAU', false, false, '["VERIFIED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('bureau.tradeline.count_total', 1, 'Total tradelines', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.tradeline.count_live', 1, 'Live tradelines', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.tradeline.count_live_secured', 1, 'Live secured tradelines', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.tradeline.count_live_unsecured', 1, 'Live unsecured tradelines', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.tradeline.count_unknown_security', 1, 'Unknown security tradelines', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.exposure.total_live', 1, 'Total live exposure', 'DECIMAL', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.exposure.secured_live', 1, 'Secured live exposure', 'DECIMAL', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.exposure.unsecured_live', 1, 'Unsecured live exposure', 'DECIMAL', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.exposure.overdue', 1, 'Overdue exposure', 'DECIMAL', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.obligation.monthly_total', 1, 'Monthly obligation total', 'DECIMAL', 'BUREAU', false, false, '["DERIVED","EXTRACTED"]'::jsonb, 'ACTIVE'),
('bureau.dpd.max_12m', 1, 'Max DPD 12 months', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.dpd.max_24m', 1, 'Max DPD 24 months', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.dpd.count_30_plus_12m', 1, 'Count 30+ DPD in 12m', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.dpd.count_60_plus_12m', 1, 'Count 60+ DPD in 12m', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.dpd.count_90_plus_24m', 1, 'Count 90+ DPD in 24m', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.accounts.restructured_count', 1, 'Restructured accounts', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.accounts.settled_count', 1, 'Settled accounts', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.accounts.written_off_count', 1, 'Written-off accounts', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.accounts.suit_filed_count', 1, 'Suit-filed accounts', 'INTEGER', 'BUREAU', false, false, '["DERIVED"]'::jsonb, 'ACTIVE'),
('bureau.inquiries.count_30d', 1, 'Inquiries 30d', 'INTEGER', 'BUREAU', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE'),
('bureau.inquiries.count_90d', 1, 'Inquiries 90d', 'INTEGER', 'BUREAU', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE'),
('bureau.inquiries.count_180d', 1, 'Inquiries 180d', 'INTEGER', 'BUREAU', false, false, '["DERIVED","VERIFIED"]'::jsonb, 'ACTIVE')
ON CONFLICT (canonical_path, version) DO NOTHING;

-- Update existing live_unsecured fact to allow DERIVED
UPDATE ci_fact_definition
SET allowed_classifications = '["MANUAL","DEFAULTED","VERIFIED","DERIVED","EXTRACTED"]'::jsonb,
    description = 'Live unsecured loan count (canonical metric preferred)'
WHERE canonical_path = 'bureau.live_unsecured_loan_count' AND version = 1;

INSERT INTO ci_metric_definition (metric_code, version, description, domain, status, definition_json)
VALUES
('bureau.live_unsecured_loan_count', 'V1', 'Count LIVE unsecured tradelines under BUREAU_LIVE_ACCOUNT_DEFINITION_V1', 'BUREAU', 'ACTIVE',
 '{"liveDefinition":"BUREAU_LIVE_ACCOUNT_DEFINITION_V1","taxonomyVersion":"EQUIFAX_TAXONOMY_V1","excludeUnknownSecurity":true,"excludeDuplicates":true}'::jsonb),
('bureau.total_live_exposure', 'V1', 'Sum of current balance on LIVE tradelines', 'BUREAU', 'ACTIVE',
 '{"liveDefinition":"BUREAU_LIVE_ACCOUNT_DEFINITION_V1"}'::jsonb),
('bureau.secured_live_exposure', 'V1', 'Sum of current balance on LIVE secured tradelines', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.unsecured_live_exposure', 'V1', 'Sum of current balance on LIVE unsecured tradelines', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.total_monthly_obligation', 'V1', 'Sum EMI on LIVE tradelines; estimated only when policy permits', 'BUREAU', 'ACTIVE',
 '{"emiPriority":["providerEmi","normalizedRepayment"],"allowEstimate":false}'::jsonb),
('bureau.max_dpd_12m', 'V1', 'Max normalized DPD in 12 months from report date', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.max_dpd_24m', 'V1', 'Max normalized DPD in 24 months from report date', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.recent_inquiries_90d', 'V1', 'Inquiry count in 90 days', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.settled_account_count', 'V1', 'Settled account count', 'BUREAU', 'ACTIVE', '{}'::jsonb),
('bureau.written_off_account_count', 'V1', 'Written-off account count', 'BUREAU', 'ACTIVE', '{}'::jsonb)
ON CONFLICT (metric_code, version) DO NOTHING;

-- Equifax product taxonomy (mapping version EQUIFAX_TAXONOMY_V1)
-- Unknown codes intentionally omitted — runtime maps to UNKNOWN
INSERT INTO ci_bureau_product_mapping
(provider_code, provider_product_code, provider_product_desc, canonical_category, secured, revolving, applicability, mapping_version)
VALUES
('EQUIFAX', '01', 'Auto Loan', 'AUTO_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '02', 'Housing Loan', 'HOME_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '03', 'Property Loan', 'LAP', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '04', 'Loan Against Shares', 'OTHER_SECURED', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '05', 'Personal Loan', 'PERSONAL_LOAN', false, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '06', 'Consumer Loan', 'CONSUMER_DURABLE', false, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '07', 'Gold Loan', 'GOLD_LOAN', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '08', 'Education Loan', 'EDUCATION_LOAN', false, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '09', 'Loan to Professional', 'BUSINESS_LOAN_UNSECURED', false, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '10', 'Credit Card', 'CREDIT_CARD', false, true, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '11', 'Lease', 'OTHER_SECURED', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '12', 'Overdraft', 'OVERDRAFT', false, true, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '13', 'Two-wheeler Loan', 'AUTO_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '14', 'Non-Funded Credit Facility', 'GUARANTEE', false, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '15', 'Loan Against Bank Deposits', 'OTHER_SECURED', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '16', 'Fleet Card', 'CREDIT_CARD', false, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '17', 'Commercial Vehicle Loan', 'COMMERCIAL_VEHICLE', true, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '18', 'Used Car Loan', 'AUTO_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '19', 'Construction Equipment Loan', 'OTHER_SECURED', true, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '20', 'Tractor Loan', 'AGRICULTURE_LOAN', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '21', 'Corporate Credit Card', 'CREDIT_CARD', false, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '23', 'Kisan Credit Card', 'AGRICULTURE_LOAN', false, true, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '24', 'Loan Against Property', 'LAP', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '31', 'Business Loan Secured', 'BUSINESS_LOAN_SECURED', true, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '32', 'Business Loan Unsecured', 'BUSINESS_LOAN_UNSECURED', false, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '33', 'Working Capital Secured', 'WORKING_CAPITAL_SECURED', true, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '34', 'Working Capital Unsecured', 'WORKING_CAPITAL_UNSECURED', false, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '35', 'Cash Credit', 'CASH_CREDIT', false, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '36', 'Term Loan Secured', 'TERM_LOAN_SECURED', true, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '37', 'Term Loan Unsecured', 'TERM_LOAN_UNSECURED', false, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '40', 'Microfinance', 'MICROFINANCE', false, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', '50', 'Other', 'OTHER_UNSECURED', false, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
-- Description-based fallbacks (provider_product_code null, match on desc)
('EQUIFAX', NULL, 'PERSONAL LOAN', 'PERSONAL_LOAN', false, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'CREDIT CARD', 'CREDIT_CARD', false, true, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'HOME LOAN', 'HOME_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'HOUSING LOAN', 'HOME_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'AUTO LOAN', 'AUTO_LOAN', true, false, 'CONSUMER', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'GOLD LOAN', 'GOLD_LOAN', true, false, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'BUSINESS LOAN', 'BUSINESS_LOAN_UNSECURED', false, false, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'OVERDRAFT', 'OVERDRAFT', false, true, 'BOTH', 'EQUIFAX_TAXONOMY_V1'),
('EQUIFAX', NULL, 'CASH CREDIT', 'CASH_CREDIT', false, true, 'COMMERCIAL', 'EQUIFAX_TAXONOMY_V1');
