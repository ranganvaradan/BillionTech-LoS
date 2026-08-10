-- Secured individual loan products: ensure they appear in borrower/staff product dropdowns (workflow_configs, active)
-- and use KYC steps consistent with V16: PAN/Aadhaar/bank + post-KYC BUREAU_PULL (EQUIFAX).

-- Canonical KYC + bureau (same shape as V16 for INDIVIDUAL "Personal" + N16)
-- Steps: MOBILE_OTP, AADHAAR_OTP, PAN_VERIFY, FACE_MATCH, BANK_PENNY_DROP, optional CKYC, BUREAU_PULL (order 20, EQUIFAX)

UPDATE workflow_configs
SET
    steps = '[
      {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
      {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
      {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
      {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
      {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
      {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
    ]'::jsonb,
    version    = version + 1,
    updated_at = now()
WHERE id = 'b0000000-0000-0000-0000-000000000014';

-- Insert missing secured products (V7 had LAP; others may be absent in dev DBs)
INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
SELECT 'c4000000-0000-0000-0000-000000000001',
       'INDIVIDUAL - Loan Against Property',
       'INDIVIDUAL',
       'Loan Against Property',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
         {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
         {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
         {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
         {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
         {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
       ]'::jsonb,
       true,
       1,
       now(),
       now()
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'INDIVIDUAL'
                    AND c.loan_product = 'Loan Against Property');

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
SELECT 'c4000000-0000-0000-0000-000000000002',
       'INDIVIDUAL - Loan Against Shares',
       'INDIVIDUAL',
       'Loan Against Shares',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
         {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
         {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
         {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
         {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
         {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
       ]'::jsonb,
       true,
       1,
       now(),
       now()
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'INDIVIDUAL'
                    AND c.loan_product = 'Loan Against Shares');

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
SELECT 'c4000000-0000-0000-0000-000000000003',
       'INDIVIDUAL - LAS',
       'INDIVIDUAL',
       'LAS',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
         {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
         {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
         {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
         {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
         {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
       ]'::jsonb,
       true,
       1,
       now(),
       now()
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'INDIVIDUAL'
                    AND c.loan_product = 'LAS');

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
SELECT 'c4000000-0000-0000-0000-000000000004',
       'INDIVIDUAL - Gold Loan',
       'INDIVIDUAL',
       'Gold Loan',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
         {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
         {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
         {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
         {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
         {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
       ]'::jsonb,
       true,
       1,
       now(),
       now()
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'INDIVIDUAL'
                    AND c.loan_product = 'Gold Loan');

-- Align any pre-existing row for the same product names (e.g. inactive draft) to canonical steps
UPDATE workflow_configs
SET
    steps      = '[
      {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
      {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
      {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
      {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
      {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
      {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
    ]'::jsonb,
    version    = version + 1,
    active     = true,
    updated_at = now()
WHERE borrower_type = 'INDIVIDUAL'
  AND loan_product IN ('Loan Against Property', 'Loan Against Shares', 'LAS', 'Gold Loan');
