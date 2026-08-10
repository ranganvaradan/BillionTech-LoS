-- V78: Invoice Discounting (SCF) borrower underwriting scorecards, rule sets, and mandatory docs.
-- Additive only — does not delete V32 generic ID scorecards (higher priority 300 wins).

-- ─── High-priority SCF Invoice Discounting scorecards (per borrower type) ───
DO
$$
    DECLARE
        v_bt   text;
        v_name text;
        v_sj   jsonb;
        v_tj   jsonb;
        v_hj   jsonb;
        v_rec  record;
    BEGIN
        v_sj := $json$
        {
          "rows": [
            {"id":"scf_bureau_1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:750","weight":8,"score":8,"attachment":"BUREAU_REPORT"},
            {"id":"scf_bureau_2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:650","weight":8,"score":5,"attachment":"BUREAU_REPORT"},
            {"id":"scf_ntc_1","parameter":"NTC_FLAG","source":"BUREAU","condition":"EQ:0","weight":4,"score":4,"attachment":"BUREAU_REPORT"},
            {"id":"scf_live_unsec_1","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"LTE:6","weight":4,"score":4,"attachment":"BUREAU_REPORT"},
            {"id":"scf_enq_1","parameter":"BUREAU_ENQUIRIES_3M","source":"BUREAU","condition":"LTE:21","weight":3,"score":3,"attachment":"BUREAU_REPORT"},
            {"id":"scf_kyc_1","parameter":"KYC_QUALITY","source":"KYC","condition":"EQ:PASS","weight":6,"score":6,"attachment":"KYC_DOCUMENTS"},
            {"id":"scf_gst_to_1","parameter":"ANNUAL_GST_TURNOVER","source":"GST_STATEMENT","condition":"GTE:50000000","weight":8,"score":8,"attachment":"GST_RETURN"},
            {"id":"scf_itr_1","parameter":"ITR_INCOME","source":"ITR","condition":"GTE:500000","weight":6,"score":6,"attachment":"ITR"},
            {"id":"scf_itr_2","parameter":"ITR_INCOME","source":"ITR","condition":"GTE:300000","weight":6,"score":4,"attachment":"ITR"},
            {"id":"scf_pat_1","parameter":"PAT","source":"OTHER","condition":"GT:0","weight":5,"score":5,"attachment":"ITR"},
            {"id":"scf_bank_to_1","parameter":"BANKING_TURNOVER_PCT_GST","source":"BANK_STATEMENT","condition":"GTE:75","weight":5,"score":5,"attachment":"BANK_STATEMENT"},
            {"id":"scf_abb_1","parameter":"ABB_OBLIGATION_MULTIPLE","source":"BANK_STATEMENT","condition":"GTE:1","weight":3,"score":3,"attachment":"BANK_STATEMENT"},
            {"id":"scf_cc_1","parameter":"CC_UTILISATION_PCT","source":"BANK_STATEMENT","condition":"LT:95","weight":3,"score":3,"attachment":"CC_STATEMENT"},
            {"id":"scf_chq_1","parameter":"CHEQUE_BOUNCES_3M","source":"BANK_STATEMENT","condition":"LTE:0","weight":4,"score":4,"attachment":"BANK_STATEMENT"},
            {"id":"scf_dscr_1","parameter":"DSCR","source":"SCORECARD","condition":"GTE:1.25","weight":5,"score":5,"attachment":"ITR"},
            {"id":"scf_ic_1","parameter":"INTEREST_COVERAGE","source":"SCORECARD","condition":"GTE:1.5","weight":4,"score":4,"attachment":"ITR"},
            {"id":"scf_de_1","parameter":"DEBT_TO_EQUITY","source":"OTHER","condition":"LTE:2","weight":4,"score":4,"attachment":"EXISTING_FACILITY_SANCTION"},
            {"id":"scf_tol_1","parameter":"TOL_TNW","source":"OTHER","condition":"LTE:7","weight":3,"score":3,"attachment":"EXISTING_FACILITY_SANCTION"},
            {"id":"scf_biz_stab_1","parameter":"businessStability","source":"OTHER","condition":"GTE:3","weight":4,"score":4,"attachment":"BUSINESS_PROOF"},
            {"id":"scf_dep_vint_1","parameter":"DEPENDENCY_VINTAGE_PERCENT","source":"PROGRAM_INPUTS","condition":"GTE:0","weight":4,"score":4,"attachment":"OTHER"},
            {"id":"scf_anchor_vint_1","parameter":"ANCHOR_RELATIONSHIP_VINTAGE_MONTHS","source":"PROGRAM_INPUTS","condition":"GTE:0","weight":4,"score":4,"attachment":"OTHER"}
          ]
        }
        $json$::jsonb;

        v_tj := $json$
        {
          "approveMinPercent": 70,
          "manualMinPercent": 50,
          "rejectBelowPercent": 50
        }
        $json$::jsonb;

        v_hj := $json$
        {
          "rules": [
            {"id":"scf_hard_gst_to","parameter":"ANNUAL_GST_TURNOVER","source":"GST_STATEMENT","condition":"LT:50000000","decision":"REJECT","reason":"Annual GST turnover below SCF minimum (₹5 Cr)"},
            {"id":"scf_hard_itr","parameter":"ITR_INCOME","source":"ITR","condition":"LT:300000","decision":"REJECT","reason":"ITR income below SCF minimum"},
            {"id":"scf_hard_pat","parameter":"PAT","source":"OTHER","condition":"LTE:0","decision":"REJECT","reason":"PAT must be positive"},
            {"id":"scf_hard_bureau","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:650","decision":"REJECT","reason":"Bureau score below SCF minimum (NTC handled softly)"},
            {"id":"scf_hard_unsec","parameter":"LIVE_UNSECURED_LOAN_COUNT","source":"BUREAU","condition":"GT:6","decision":"REJECT","reason":"Live unsecured loan count exceeds policy"},
            {"id":"scf_hard_enq","parameter":"BUREAU_ENQUIRIES_3M","source":"BUREAU","condition":"GT:21","decision":"REJECT","reason":"Bureau enquiries in 3 months exceed policy"},
            {"id":"scf_hard_bank_to","parameter":"BANKING_TURNOVER_PCT_GST","source":"BANK_STATEMENT","condition":"LT:75","decision":"REJECT","reason":"Banking turnover as % of GST below policy"},
            {"id":"scf_hard_chq3","parameter":"CHEQUE_BOUNCES_3M","source":"BANK_STATEMENT","condition":"GT:0","decision":"REJECT","reason":"Cheque bounces in last 3 months not allowed"},
            {"id":"scf_hard_dscr","parameter":"DSCR","source":"SCORECARD","condition":"LT:1.25","decision":"REJECT","reason":"DSCR below policy minimum"},
            {"id":"scf_hard_ic","parameter":"INTEREST_COVERAGE","source":"SCORECARD","condition":"LT:1.5","decision":"REJECT","reason":"Interest coverage below policy minimum"},
            {"id":"scf_hard_de","parameter":"DEBT_TO_EQUITY","source":"OTHER","condition":"GT:2","decision":"REJECT","reason":"Debt to equity exceeds policy"},
            {"id":"scf_hard_tol","parameter":"TOL_TNW","source":"OTHER","condition":"GT:7","decision":"REJECT","reason":"TOL/TNW exceeds policy"},
            {"id":"scf_hard_cc","parameter":"CC_UTILISATION_PCT","source":"BANK_STATEMENT","condition":"GTE:95","decision":"REJECT","reason":"CC utilisation at or above 95%"},
            {"id":"scf_hard_biz","parameter":"businessStability","source":"OTHER","condition":"LT:3","decision":"REJECT","reason":"Business stability below 3 years"}
          ]
        }
        $json$::jsonb;

        FOR v_rec IN
            SELECT * FROM (VALUES
                ('INDIVIDUAL'),
                ('PROPRIETOR'),
                ('PARTNERSHIP'),
                ('COMPANY')
            ) AS t(code)
        LOOP
            v_bt := v_rec.code;
            v_name := 'SCF Invoice Discounting — ' || v_bt;
            IF NOT EXISTS (
                SELECT 1 FROM underwriting_scorecards s
                WHERE s.borrower_type = v_bt
                  AND s.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
                  AND s.name = v_name
            ) THEN
                INSERT INTO underwriting_scorecards (
                    id, name, borrower_type, loan_product, version, priority,
                    min_amount, max_amount, geography,
                    scorecard_json, thresholds_json, hard_rules_json,
                    active, created_at, updated_at
                ) VALUES (
                    gen_random_uuid(),
                    v_name,
                    v_bt,
                    'BUSINESS_WC_INVOICE_DISCOUNTING',
                    1,
                    300,
                    50000,
                    5000000,
                    NULL,
                    v_sj,
                    v_tj,
                    v_hj,
                    TRUE,
                    NOW(),
                    NOW()
                );
            END IF;
        END LOOP;
    END
$$;

-- ─── SCF rule sets (geography left NULL — engine only matches single state/city) ───
DO
$$
    DECLARE
        v_bt   text;
        v_name text;
        v_j    jsonb := '{"minBureauScore":650,"maxLoanAmount":5000000,"requireKycSuccess":true,"decision":"MANUAL_REVIEW","reasons":["SCF Invoice Discounting policy"]}'::jsonb;
        v_rec  record;
    BEGIN
        FOR v_rec IN
            SELECT * FROM (VALUES
                ('INDIVIDUAL'),
                ('PROPRIETOR'),
                ('PARTNERSHIP'),
                ('COMPANY')
            ) AS t(code)
        LOOP
            v_bt := v_rec.code;
            v_name := 'SCF Invoice Discounting policy — ' || v_bt;
            IF NOT EXISTS (
                SELECT 1 FROM underwriting_rule_sets u
                WHERE u.borrower_type = v_bt
                  AND u.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
                  AND u.name = v_name
            ) THEN
                INSERT INTO underwriting_rule_sets (
                    id, name, borrower_type, loan_product,
                    min_amount, max_amount, geography,
                    min_tenure_months, max_tenure_months,
                    priority, active, rules_json, created_at, updated_at
                ) VALUES (
                    gen_random_uuid(),
                    v_name,
                    v_bt,
                    'BUSINESS_WC_INVOICE_DISCOUNTING',
                    50000,
                    5000000,
                    NULL,
                    NULL,
                    60,
                    300,
                    TRUE,
                    v_j,
                    NOW(),
                    NOW()
                );
            END IF;
        END LOOP;
    END
$$;

-- ─── Workflow mandatory standalone documents for ID borrower workflows ───
-- Core SCF docs required; PHOTOGRAPH/AADHAAR required except COMPANY skips AADHAAR.

UPDATE workflow_configs w
SET intake_config = CASE
    WHEN w.intake_config IS NULL THEN
        jsonb_build_object(
            'policy', 'WORKFLOW_DRIVEN',
            'standaloneDocuments',
            CASE w.borrower_type
                WHEN 'COMPANY' THEN $docs_co$
                [
                  {"documentType":"PAN_CARD","required":true,"label":"PAN card"},
                  {"documentType":"PHOTOGRAPH","required":true,"label":"Photograph"},
                  {"documentType":"CONSTITUTION_DOCS","required":true,"label":"Constitution documents"},
                  {"documentType":"GST_RETURN","required":true,"label":"GST return / GSTR"},
                  {"documentType":"ITR","required":true,"label":"ITR / tax return"},
                  {"documentType":"BANK_STATEMENT","required":true,"label":"Bank statement"},
                  {"documentType":"CC_STATEMENT","required":true,"label":"CC / OD statement"},
                  {"documentType":"PAYABLES_RECEIVABLES_AGEING","required":true,"label":"Payables / receivables ageing"},
                  {"documentType":"PROPERTY_OWNERSHIP_PROOF","required":true,"label":"Property ownership proof"},
                  {"documentType":"EXISTING_FACILITY_SANCTION","required":true,"label":"Existing facility sanction letter"},
                  {"documentType":"EXISTING_FACILITY_STATEMENT","required":true,"label":"Existing facility statement"},
                  {"documentType":"PDC","required":true,"label":"PDC"},
                  {"documentType":"NACH_MANDATE","required":true,"label":"NACH mandate"},
                  {"documentType":"BUREAU_REPORT","required":true,"label":"Bureau report"},
                  {"documentType":"COMMERCIAL_BUREAU_REPORT","required":true,"label":"Commercial bureau report"},
                  {"documentType":"BOARD_RESOLUTION","required":true,"label":"Board resolution"},
                  {"documentType":"PURCHASE_ORDER","required":true,"label":"Purchase order"},
                  {"documentType":"DELIVERY_GRN","required":true,"label":"Delivery / GRN"},
                  {"documentType":"TRADE_PAYMENT_RECORD","required":true,"label":"Trade payment record"},
                  {"documentType":"BUYER_NOC","required":true,"label":"Buyer NOC"},
                  {"documentType":"BUSINESS_PROOF","required":true,"label":"Business proof"}
                ]
                $docs_co$::jsonb
                ELSE $docs_ot$
                [
                  {"documentType":"PAN_CARD","required":true,"label":"PAN card"},
                  {"documentType":"AADHAAR","required":true,"label":"Aadhaar"},
                  {"documentType":"PHOTOGRAPH","required":true,"label":"Photograph"},
                  {"documentType":"CONSTITUTION_DOCS","required":true,"label":"Constitution documents"},
                  {"documentType":"GST_RETURN","required":true,"label":"GST return / GSTR"},
                  {"documentType":"ITR","required":true,"label":"ITR / tax return"},
                  {"documentType":"BANK_STATEMENT","required":true,"label":"Bank statement"},
                  {"documentType":"CC_STATEMENT","required":true,"label":"CC / OD statement"},
                  {"documentType":"PAYABLES_RECEIVABLES_AGEING","required":true,"label":"Payables / receivables ageing"},
                  {"documentType":"PROPERTY_OWNERSHIP_PROOF","required":true,"label":"Property ownership proof"},
                  {"documentType":"EXISTING_FACILITY_SANCTION","required":true,"label":"Existing facility sanction letter"},
                  {"documentType":"EXISTING_FACILITY_STATEMENT","required":true,"label":"Existing facility statement"},
                  {"documentType":"PDC","required":true,"label":"PDC"},
                  {"documentType":"NACH_MANDATE","required":true,"label":"NACH mandate"},
                  {"documentType":"BUREAU_REPORT","required":true,"label":"Bureau report"},
                  {"documentType":"COMMERCIAL_BUREAU_REPORT","required":true,"label":"Commercial bureau report"},
                  {"documentType":"BOARD_RESOLUTION","required":true,"label":"Board resolution"},
                  {"documentType":"PURCHASE_ORDER","required":true,"label":"Purchase order"},
                  {"documentType":"DELIVERY_GRN","required":true,"label":"Delivery / GRN"},
                  {"documentType":"TRADE_PAYMENT_RECORD","required":true,"label":"Trade payment record"},
                  {"documentType":"BUYER_NOC","required":true,"label":"Buyer NOC"},
                  {"documentType":"BUSINESS_PROOF","required":true,"label":"Business proof"}
                ]
                $docs_ot$::jsonb
            END
        )
    ELSE
        jsonb_set(
            CASE
                WHEN COALESCE(w.intake_config->>'policy', '') = '' THEN
                    w.intake_config || jsonb_build_object('policy', 'WORKFLOW_DRIVEN')
                ELSE
                    jsonb_set(w.intake_config, '{policy}', '"WORKFLOW_DRIVEN"'::jsonb, true)
            END,
            '{standaloneDocuments}',
            CASE w.borrower_type
                WHEN 'COMPANY' THEN $docs_co2$
                [
                  {"documentType":"PAN_CARD","required":true,"label":"PAN card"},
                  {"documentType":"PHOTOGRAPH","required":true,"label":"Photograph"},
                  {"documentType":"CONSTITUTION_DOCS","required":true,"label":"Constitution documents"},
                  {"documentType":"GST_RETURN","required":true,"label":"GST return / GSTR"},
                  {"documentType":"ITR","required":true,"label":"ITR / tax return"},
                  {"documentType":"BANK_STATEMENT","required":true,"label":"Bank statement"},
                  {"documentType":"CC_STATEMENT","required":true,"label":"CC / OD statement"},
                  {"documentType":"PAYABLES_RECEIVABLES_AGEING","required":true,"label":"Payables / receivables ageing"},
                  {"documentType":"PROPERTY_OWNERSHIP_PROOF","required":true,"label":"Property ownership proof"},
                  {"documentType":"EXISTING_FACILITY_SANCTION","required":true,"label":"Existing facility sanction letter"},
                  {"documentType":"EXISTING_FACILITY_STATEMENT","required":true,"label":"Existing facility statement"},
                  {"documentType":"PDC","required":true,"label":"PDC"},
                  {"documentType":"NACH_MANDATE","required":true,"label":"NACH mandate"},
                  {"documentType":"BUREAU_REPORT","required":true,"label":"Bureau report"},
                  {"documentType":"COMMERCIAL_BUREAU_REPORT","required":true,"label":"Commercial bureau report"},
                  {"documentType":"BOARD_RESOLUTION","required":true,"label":"Board resolution"},
                  {"documentType":"PURCHASE_ORDER","required":true,"label":"Purchase order"},
                  {"documentType":"DELIVERY_GRN","required":true,"label":"Delivery / GRN"},
                  {"documentType":"TRADE_PAYMENT_RECORD","required":true,"label":"Trade payment record"},
                  {"documentType":"BUYER_NOC","required":true,"label":"Buyer NOC"},
                  {"documentType":"BUSINESS_PROOF","required":true,"label":"Business proof"}
                ]
                $docs_co2$::jsonb
                ELSE $docs_ot2$
                [
                  {"documentType":"PAN_CARD","required":true,"label":"PAN card"},
                  {"documentType":"AADHAAR","required":true,"label":"Aadhaar"},
                  {"documentType":"PHOTOGRAPH","required":true,"label":"Photograph"},
                  {"documentType":"CONSTITUTION_DOCS","required":true,"label":"Constitution documents"},
                  {"documentType":"GST_RETURN","required":true,"label":"GST return / GSTR"},
                  {"documentType":"ITR","required":true,"label":"ITR / tax return"},
                  {"documentType":"BANK_STATEMENT","required":true,"label":"Bank statement"},
                  {"documentType":"CC_STATEMENT","required":true,"label":"CC / OD statement"},
                  {"documentType":"PAYABLES_RECEIVABLES_AGEING","required":true,"label":"Payables / receivables ageing"},
                  {"documentType":"PROPERTY_OWNERSHIP_PROOF","required":true,"label":"Property ownership proof"},
                  {"documentType":"EXISTING_FACILITY_SANCTION","required":true,"label":"Existing facility sanction letter"},
                  {"documentType":"EXISTING_FACILITY_STATEMENT","required":true,"label":"Existing facility statement"},
                  {"documentType":"PDC","required":true,"label":"PDC"},
                  {"documentType":"NACH_MANDATE","required":true,"label":"NACH mandate"},
                  {"documentType":"BUREAU_REPORT","required":true,"label":"Bureau report"},
                  {"documentType":"COMMERCIAL_BUREAU_REPORT","required":true,"label":"Commercial bureau report"},
                  {"documentType":"BOARD_RESOLUTION","required":true,"label":"Board resolution"},
                  {"documentType":"PURCHASE_ORDER","required":true,"label":"Purchase order"},
                  {"documentType":"DELIVERY_GRN","required":true,"label":"Delivery / GRN"},
                  {"documentType":"TRADE_PAYMENT_RECORD","required":true,"label":"Trade payment record"},
                  {"documentType":"BUYER_NOC","required":true,"label":"Buyer NOC"},
                  {"documentType":"BUSINESS_PROOF","required":true,"label":"Business proof"}
                ]
                $docs_ot2$::jsonb
            END,
            true
        )
    END,
    updated_at = NOW()
WHERE w.active = true
  AND w.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
  AND COALESCE(w.intake_segment, 'BORROWER') = 'BORROWER';
