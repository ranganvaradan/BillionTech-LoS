-- Safe bank-grade scorecard seed (replaces V24 placeholder rows when present).
-- Does not use Flyway brace placeholders in SQL. New installs: V24 may still run first; this migration removes
-- the legacy fixed-UUID rows and inserts the full scorecards with idempotency on (name, borrower_type, loan_product).

DELETE FROM underwriting_scorecards WHERE id IN (
    'd2000000-0000-0000-0000-000000000001',
    'd2000000-0000-0000-0000-000000000002',
    'd2000000-0000-0000-0000-000000000003',
    'd2000000-0000-0000-0000-000000000004'
);

INSERT INTO underwriting_scorecards
(
    id,
    name,
    borrower_type,
    loan_product,
    version,
    priority,
    min_amount,
    max_amount,
    geography,
    scorecard_json,
    thresholds_json,
    hard_rules_json,
    active,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'Personal Loan (Individual)',
    'INDIVIDUAL',
    'Personal',
    1,
    200,
    0,
    500000,
    NULL,
    $json$
    {
      "rows": [
        {"id":"pl_bureau_1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:750","weight":35,"score":35,"attachment":"BUREAU_REPORT"},
        {"id":"pl_bureau_2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:650","weight":35,"score":25,"attachment":"BUREAU_REPORT"},
        {"id":"pl_income_1","parameter":"MONTHLY_INCOME","source":"MANUAL_OR_PROVIDER","condition":"GTE:50000","weight":25,"score":25,"attachment":"INCOME_PROOF"},
        {"id":"pl_income_2","parameter":"MONTHLY_INCOME","source":"MANUAL_OR_PROVIDER","condition":"GTE:25000","weight":25,"score":15,"attachment":"INCOME_PROOF"},
        {"id":"pl_obligation_1","parameter":"OBLIGATION_RATIO","source":"SYSTEM","condition":"LTE:40","weight":20,"score":20,"attachment":"BANK_STATEMENT"},
        {"id":"pl_obligation_2","parameter":"OBLIGATION_RATIO","source":"SYSTEM","condition":"LTE:60","weight":20,"score":10,"attachment":"BANK_STATEMENT"},
        {"id":"pl_balance_1","parameter":"AVERAGE_BANK_BALANCE","source":"BANK_STATEMENT","condition":"GTE:20000","weight":10,"score":10,"attachment":"BANK_STATEMENT"},
        {"id":"pl_kyc_1","parameter":"KYC_QUALITY","source":"SYSTEM","condition":"EQ:PASS","weight":10,"score":10,"attachment":"KYC_DOCUMENTS"}
      ]
    }
    $json$::jsonb,
    $json$
    {
      "approveMinPercent": 70,
      "manualMinPercent": 50,
      "rejectBelowPercent": 50
    }
    $json$::jsonb,
    $json$
    {
      "rules": [
        {"id":"pl_hard_kyc_fail","parameter":"KYC_QUALITY","condition":"NE:PASS","decision":"REJECT","reason":"KYC not completed successfully"},
        {"id":"pl_hard_low_bureau","parameter":"BUREAU_SCORE","condition":"LT:600","decision":"REJECT","reason":"Bureau score below minimum policy threshold"}
      ]
    }
    $json$::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM underwriting_scorecards
    WHERE borrower_type = 'INDIVIDUAL'
      AND loan_product = 'Personal'
      AND name = 'Personal Loan (Individual)'
);

INSERT INTO underwriting_scorecards
(
    id,
    name,
    borrower_type,
    loan_product,
    version,
    priority,
    min_amount,
    max_amount,
    geography,
    scorecard_json,
    thresholds_json,
    hard_rules_json,
    active,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'MSME — Proprietor',
    'PROPRIETOR',
    'TERM_LOAN',
    1,
    200,
    50000,
    2500000,
    NULL,
    $json$
    {
      "rows": [
        {"id":"msme_gst_1","parameter":"GST_INCOME","source":"GST","condition":"GTE:1000000","weight":30,"score":30,"attachment":"GST_RETURN"},
        {"id":"msme_gst_2","parameter":"GST_INCOME","source":"GST","condition":"GTE:500000","weight":30,"score":20,"attachment":"GST_RETURN"},
        {"id":"msme_bank_1","parameter":"BANK_STATEMENT_INCOME","source":"BANK_STATEMENT","condition":"GTE:100000","weight":25,"score":25,"attachment":"BANK_STATEMENT"},
        {"id":"msme_bank_2","parameter":"BANK_STATEMENT_INCOME","source":"BANK_STATEMENT","condition":"GTE:50000","weight":25,"score":15,"attachment":"BANK_STATEMENT"},
        {"id":"msme_bureau_1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:700","weight":20,"score":20,"attachment":"BUREAU_REPORT"},
        {"id":"msme_vintage_1","parameter":"BUSINESS_VINTAGE_MONTHS","source":"MANUAL_OR_PROVIDER","condition":"GTE:24","weight":15,"score":15,"attachment":"BUSINESS_PROOF"},
        {"id":"msme_obligation_1","parameter":"OBLIGATION_RATIO","source":"SYSTEM","condition":"LTE:50","weight":10,"score":10,"attachment":"BANK_STATEMENT"}
      ]
    }
    $json$::jsonb,
    $json$
    {
      "approveMinPercent": 70,
      "manualMinPercent": 50,
      "rejectBelowPercent": 50
    }
    $json$::jsonb,
    $json$
    {
      "rules": [
        {"id":"msme_hard_kyc_fail","parameter":"KYC_QUALITY","condition":"NE:PASS","decision":"REJECT","reason":"KYC not completed successfully"},
        {"id":"msme_hard_low_bureau","parameter":"BUREAU_SCORE","condition":"LT:575","decision":"REJECT","reason":"Bureau score below MSME minimum threshold"}
      ]
    }
    $json$::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM underwriting_scorecards
    WHERE borrower_type = 'PROPRIETOR'
      AND loan_product = 'TERM_LOAN'
      AND name = 'MSME — Proprietor'
);

INSERT INTO underwriting_scorecards
(
    id,
    name,
    borrower_type,
    loan_product,
    version,
    priority,
    min_amount,
    max_amount,
    geography,
    scorecard_json,
    thresholds_json,
    hard_rules_json,
    active,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'SME — Company',
    'COMPANY',
    'SME',
    1,
    200,
    500000,
    10000000,
    NULL,
    $json$
    {
      "rows": [
        {"id":"sme_financials_1","parameter":"EBITDA_PROXY","source":"FINANCIALS","condition":"GTE:1000000","weight":30,"score":30,"attachment":"FINANCIAL_STATEMENTS"},
        {"id":"sme_bank_1","parameter":"BANK_STATEMENT_INCOME","source":"BANK_STATEMENT","condition":"GTE:300000","weight":25,"score":25,"attachment":"BANK_STATEMENT"},
        {"id":"sme_bureau_1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:700","weight":15,"score":15,"attachment":"BUREAU_REPORT"},
        {"id":"sme_industry_1","parameter":"INDUSTRY_RISK","source":"MANUAL_OR_SYSTEM","condition":"EQ:LOW","weight":15,"score":15,"attachment":"CREDIT_NOTE"},
        {"id":"sme_leverage_1","parameter":"LEVERAGE_RATIO","source":"FINANCIALS","condition":"LTE:3","weight":15,"score":15,"attachment":"FINANCIAL_STATEMENTS"}
      ]
    }
    $json$::jsonb,
    $json$
    {
      "approveMinPercent": 70,
      "manualMinPercent": 50,
      "rejectBelowPercent": 50
    }
    $json$::jsonb,
    $json$
    {
      "rules": [
        {"id":"sme_hard_kyc_fail","parameter":"KYC_QUALITY","condition":"NE:PASS","decision":"REJECT","reason":"KYC not completed successfully"},
        {"id":"sme_hard_high_leverage","parameter":"LEVERAGE_RATIO","condition":"GT:5","decision":"REJECT","reason":"Leverage exceeds policy threshold"}
      ]
    }
    $json$::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM underwriting_scorecards
    WHERE borrower_type = 'COMPANY'
      AND loan_product = 'SME'
      AND name = 'SME — Company'
);

INSERT INTO underwriting_scorecards
(
    id,
    name,
    borrower_type,
    loan_product,
    version,
    priority,
    min_amount,
    max_amount,
    geography,
    scorecard_json,
    thresholds_json,
    hard_rules_json,
    active,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    'LAP (Individual)',
    'INDIVIDUAL',
    'LAP',
    1,
    200,
    500000,
    20000000,
    NULL,
    $json$
    {
      "rows": [
        {"id":"lap_property_1","parameter":"PROPERTY_VALUE","source":"VALUATION","condition":"GTE:2000000","weight":30,"score":30,"attachment":"PROPERTY_VALUATION"},
        {"id":"lap_ltv_1","parameter":"LTV","source":"SYSTEM","condition":"LTE:60","weight":25,"score":25,"attachment":"PROPERTY_VALUATION"},
        {"id":"lap_ltv_2","parameter":"LTV","source":"SYSTEM","condition":"LTE:75","weight":25,"score":15,"attachment":"PROPERTY_VALUATION"},
        {"id":"lap_income_1","parameter":"MONTHLY_INCOME","source":"MANUAL_OR_PROVIDER","condition":"GTE:75000","weight":20,"score":20,"attachment":"INCOME_PROOF"},
        {"id":"lap_bureau_1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:700","weight":15,"score":15,"attachment":"BUREAU_REPORT"},
        {"id":"lap_repayment_1","parameter":"REPAYMENT_HISTORY","source":"SYSTEM","condition":"EQ:CLEAN","weight":10,"score":10,"attachment":"BANK_STATEMENT"}
      ]
    }
    $json$::jsonb,
    $json$
    {
      "approveMinPercent": 70,
      "manualMinPercent": 50,
      "rejectBelowPercent": 50
    }
    $json$::jsonb,
    $json$
    {
      "rules": [
        {"id":"lap_hard_kyc_fail","parameter":"KYC_QUALITY","condition":"NE:PASS","decision":"REJECT","reason":"KYC not completed successfully"},
        {"id":"lap_hard_high_ltv","parameter":"LTV","condition":"GT:80","decision":"REJECT","reason":"LTV exceeds maximum threshold"}
      ]
    }
    $json$::jsonb,
    TRUE,
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM underwriting_scorecards
    WHERE borrower_type = 'INDIVIDUAL'
      AND loan_product = 'LAP'
      AND name = 'LAP (Individual)'
);
