-- V11__seed_individual_personal_loan_kyc_workflow.sql
-- Minimal workflow config to allow KYC workflow execution for INDIVIDUAL / Personal Loan

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'INDIVIDUAL_PERSONAL_LOAN_KYC',
    'INDIVIDUAL',
    'Personal Loan',
    '[
      {"step":"PAN_VERIFY","mandatory":true,"order":1},
      {"step":"AADHAAR_OTP","mandatory":true,"order":2},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":3},
      {"step":"CKYC_DOWNLOAD","mandatory":true,"order":4}
    ]'::jsonb,
    true,
    now(),
    now()
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_configs
    WHERE borrower_type = 'INDIVIDUAL'
      AND loan_product = 'Personal Loan'
      AND active = TRUE
);