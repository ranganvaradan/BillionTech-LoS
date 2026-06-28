-- Active scorecards (priority 200) — Personal, MSME, SME, LAP
-- Flyway: avoid dollar-tag immediately followed by "{" (split across lines so the parser does not see a placeholder).
-- Pattern: open tag, newline, JSON body, close tag, then ::jsonb
INSERT INTO underwriting_scorecards
    (id, name, borrower_type, loan_product, version, priority, min_amount, max_amount, geography,
     scorecard_json, thresholds_json, hard_rules_json, active, created_at)
VALUES
    ('d2000000-0000-0000-0000-000000000001', 'Personal Loan (Individual)', 'INDIVIDUAL', 'Personal', 1, 200, NULL, NULL, NULL,
     $json$
     {"rows":[
       {"id":"p1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:720","weight":1,"score":30,"attachment":"BUREAU_REPORT"},
       {"id":"p2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:650","weight":1,"score":25,"attachment":"BUREAU_REPORT"},
       {"id":"p3","parameter":"KYC_PASS","source":"KYC","condition":"EQ:1","weight":1,"score":15,"attachment":"KYC_OUTCOME"},
       {"id":"p4","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"LTE:3000000","weight":1,"score":20,"attachment":"LOAN_REQUEST"},
       {"id":"p5","parameter":"TENURE_MONTHS","source":"APPLICATION","condition":"BETWEEN:3:60","weight":1,"score":10,"attachment":"LOAN_REQUEST"}]}
     $json$::jsonb,
     '{"approveMinPercent":70,"manualMinPercent":40}'::jsonb,
     $hr$
     {"rules":[{"parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:520","decision":"REJECT","message":"Bureau hard reject"}]}
     $hr$::jsonb,
     true, now()),

    ('d2000000-0000-0000-0000-000000000002', 'MSME — Proprietor', 'PROPRIETOR', 'TERM_LOAN', 1, 200, NULL, NULL, NULL,
     $json$
     {"rows":[
       {"id":"m1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:700","weight":1,"score":30,"attachment":"BUREAU_REPORT"},
       {"id":"m2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:640","weight":1,"score":20,"attachment":"BUREAU_REPORT"},
       {"id":"m3","parameter":"KYC_PASS","source":"KYC","condition":"EQ:1","weight":1,"score":15,"attachment":"KYC_OUTCOME"},
       {"id":"m4","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"LTE:10000000","weight":1,"score":20,"attachment":"LOAN_REQUEST"},
       {"id":"m5","parameter":"TENURE_MONTHS","source":"APPLICATION","condition":"BETWEEN:6:84","weight":1,"score":15,"attachment":"LOAN_REQUEST"}]}
     $json$::jsonb,
     '{"approveMinPercent":70,"manualMinPercent":40}'::jsonb,
     $hr$
     {"rules":[{"parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:540","decision":"REJECT","message":"MSME bureau floor"}]}
     $hr$::jsonb,
     true, now()),

    ('d2000000-0000-0000-0000-000000000003', 'SME — Company', 'COMPANY', 'SME', 1, 200, NULL, NULL, NULL,
     $json$
     {"rows":[
       {"id":"s1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:710","weight":1,"score":28,"attachment":"BUREAU_REPORT"},
       {"id":"s2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:650","weight":1,"score":22,"attachment":"BUREAU_REPORT"},
       {"id":"s3","parameter":"KYC_PASS","source":"KYC","condition":"EQ:1","weight":1,"score":15,"attachment":"KYC_OUTCOME"},
       {"id":"s4","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"LTE:20000000","weight":1,"score":20,"attachment":"LOAN_REQUEST"},
       {"id":"s5","parameter":"TENURE_MONTHS","source":"APPLICATION","condition":"BETWEEN:6:84","weight":1,"score":15,"attachment":"LOAN_REQUEST"}]}
     $json$::jsonb,
     '{"approveMinPercent":70,"manualMinPercent":40}'::jsonb,
     $hr$
     {"rules":[{"parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:560","decision":"MANUAL_REVIEW","message":"Bureau in SME review band"}]}
     $hr$::jsonb,
     true, now()),

    ('d2000000-0000-0000-0000-000000000004', 'Loan Against Property (Individual)', 'INDIVIDUAL', 'LAP', 1, 200, NULL, NULL, NULL,
     $json$
     {"rows":[
       {"id":"l1","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:680","weight":1,"score":28,"attachment":"BUREAU_REPORT"},
       {"id":"l2","parameter":"BUREAU_SCORE","source":"BUREAU","condition":"GTE:620","weight":1,"score":20,"attachment":"BUREAU_REPORT"},
       {"id":"l3","parameter":"KYC_PASS","source":"KYC","condition":"EQ:1","weight":1,"score":12,"attachment":"KYC_OUTCOME"},
       {"id":"l4","parameter":"REQUESTED_AMOUNT","source":"APPLICATION","condition":"LTE:15000000","weight":1,"score":25,"attachment":"LOAN_REQUEST"},
       {"id":"l5","parameter":"TENURE_MONTHS","source":"APPLICATION","condition":"BETWEEN:12:240","weight":1,"score":15,"attachment":"LOAN_REQUEST"}]}
     $json$::jsonb,
     '{"approveMinPercent":70,"manualMinPercent":40}'::jsonb,
     $hr$
     {"rules":[{"parameter":"BUREAU_SCORE","source":"BUREAU","condition":"LT:580","decision":"REJECT","message":"Bureau too low for LAP"}]}
     $hr$::jsonb,
     true, now());
