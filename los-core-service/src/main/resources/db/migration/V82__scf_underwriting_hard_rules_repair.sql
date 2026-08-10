-- V82: Ensure SCF invoice-discounting rule sets carry hardRules (repair if V81 UI/seed was applied without hardRules).
UPDATE underwriting_rule_sets u
SET rules_json = COALESCE(u.rules_json, '{}'::jsonb)
    || jsonb_build_object(
        'minBureauScore', 650,
        'maxLoanAmount', 10000000,
        'requireKycSuccess', true,
        'decision', 'MANUAL_REVIEW',
        'reasons', jsonb_build_array('SCF Invoice Discounting policy — credit manager review'),
        'hardRules', $hard$
        [
          {"id":"scf_hard_abs_cap","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"GT:10000000","decision":"REJECT","message":"Requested amount exceeds absolute SCF ticket cap (₹1 Cr special deviation maximum)"},
          {"id":"scf_hard_over_std","parameter":"SCF_AMOUNT_OVER_STANDARD","source":"SCORECARD","condition":"GT:0","decision":"MANUAL_REVIEW","message":"Requested amount exceeds standard limit (25% of annual turnover capped at ₹50L); special deviation up to ₹1 Cr requires lender approval"},
          {"id":"scf_hard_gst_to","parameter":"ANNUAL_GST_TURNOVER","source":"GST_STATEMENT","condition":"LT:50000000","decision":"REJECT","message":"Annual GST turnover below SCF minimum (₹5 Cr)"},
          {"id":"scf_hard_itr","parameter":"ITR_INCOME","source":"ITR","condition":"LT:300000","decision":"REJECT","message":"ITR income below SCF minimum (₹3 L)"},
          {"id":"scf_hard_pat","parameter":"PAT","source":"OTHER","condition":"LTE:0","decision":"REJECT","message":"PAT must be positive in latest FY"},
          {"id":"scf_hard_bureau","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:650","decision":"REJECT","message":"Bureau score below SCF minimum (650)"},
          {"id":"scf_hard_unsec","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"GT:6","decision":"REJECT","message":"Live unsecured loan count exceeds policy (max 6)"},
          {"id":"scf_hard_enq","parameter":"BUREAU_ENQUIRIES_3M","source":"BUREAU","condition":"GT:21","decision":"REJECT","message":"Bureau enquiries in 3 months exceed policy (max 21)"},
          {"id":"scf_hard_bank_to","parameter":"BANKING_TURNOVER_PCT_GST","source":"BANK_STATEMENT","condition":"LT:75","decision":"REJECT","message":"Banking turnover as % of GST below policy (min 75%)"},
          {"id":"scf_hard_chq3","parameter":"CHEQUE_BOUNCES_3M","source":"BANK_STATEMENT","condition":"GT:0","decision":"REJECT","message":"Cheque bounces in last 3 months not allowed"},
          {"id":"scf_hard_chq12","parameter":"CHEQUE_BOUNCES_12M","source":"BANK_STATEMENT","condition":"GT:6","decision":"REJECT","message":"Non-technical cheque bounces in 12 months exceed policy (max 6)"},
          {"id":"scf_hard_dscr","parameter":"DSCR","source":"SCORECARD","condition":"LT:1.25","decision":"REJECT","message":"DSCR below policy minimum (1.25)"},
          {"id":"scf_hard_ic","parameter":"INTEREST_COVERAGE","source":"SCORECARD","condition":"LT:1.5","decision":"REJECT","message":"Interest coverage below policy minimum (1.5)"},
          {"id":"scf_hard_de","parameter":"DEBT_TO_EQUITY","source":"OTHER","condition":"GT:2","decision":"REJECT","message":"Debt to equity exceeds policy (max 2x)"},
          {"id":"scf_hard_tol","parameter":"TOL_TNW","source":"OTHER","condition":"GT:7","decision":"REJECT","message":"TOL/TNW exceeds policy (max 7x)"},
          {"id":"scf_hard_cc","parameter":"CC_UTILISATION_PCT","source":"BANK_STATEMENT","condition":"GTE:95","decision":"REJECT","message":"CC utilisation at or above 95%"},
          {"id":"scf_hard_biz","parameter":"businessStability","source":"OTHER","condition":"LT:3","decision":"REJECT","message":"Business vintage below 3 years"}
        ]
        $hard$::jsonb
    ),
    max_amount = 10000000,
    priority = 300,
    active = true,
    updated_at = NOW()
WHERE u.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
  AND u.name LIKE 'SCF Invoice Discounting policy%'
  AND (
    u.rules_json IS NULL
    OR NOT (u.rules_json ? 'hardRules')
    OR jsonb_array_length(COALESCE(u.rules_json->'hardRules', '[]'::jsonb)) = 0
  );
