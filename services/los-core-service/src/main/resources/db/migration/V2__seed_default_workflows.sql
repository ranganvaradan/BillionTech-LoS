-- V2__seed_default_workflows.sql
-- Default KYC workflows per borrower type

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version) VALUES
-- Individual borrower workflow
('b0000000-0000-0000-0000-000000000001', 'Individual - Term Loan', 'INDIVIDUAL', 'TERM_LOAN',
 '[
   {"step": "MOBILE_OTP",    "mandatory": true, "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",   "mandatory": true, "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",    "mandatory": true, "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",    "mandatory": true, "order": 4, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "BUREAU_PULL",   "mandatory": true, "order": 6, "provider": "EQUIFAX"},
   {"step": "CKYC_DOWNLOAD", "mandatory": false, "order": 7, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),

-- Proprietor borrower workflow
('b0000000-0000-0000-0000-000000000002', 'Proprietor - Business Loan', 'PROPRIETOR', 'BUSINESS_LOAN',
 '[
   {"step": "MOBILE_OTP",    "mandatory": true, "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",   "mandatory": true, "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",    "mandatory": true, "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",  "mandatory": true, "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "UDYAM_VERIFY",  "mandatory": false, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",    "mandatory": true, "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true, "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "BUREAU_PULL",   "mandatory": true, "order": 8, "provider": "EQUIFAX"},
   {"step": "CKYC_DOWNLOAD", "mandatory": false, "order": 9, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),

-- Company borrower workflow
('b0000000-0000-0000-0000-000000000003', 'Company - Business Loan', 'COMPANY', 'BUSINESS_LOAN',
 '[
   {"step": "MOBILE_OTP",    "mandatory": true, "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",    "mandatory": true, "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "CIN_MCA21",     "mandatory": true, "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",  "mandatory": true, "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",   "mandatory": true, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",    "mandatory": true, "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true, "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "BUREAU_PULL",   "mandatory": true, "order": 8, "provider": "EQUIFAX"},
   {"step": "AML_SCREENING", "mandatory": true, "order": 9, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1);
