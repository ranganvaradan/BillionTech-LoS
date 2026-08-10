-- Aligns with React / manual entry using loan product "Personal" (Display label) — case- and
-- space-sensitive match on `workflow_configs.loan_product` (see WorkflowEngineServiceImpl#getActiveWorkflow).

INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at)
SELECT
    gen_random_uuid(),
    'INDIVIDUAL_PERSONAL_DISPLAY',
    'INDIVIDUAL',
    'Personal',
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
WHERE NOT EXISTS (
    SELECT 1
    FROM workflow_configs
    WHERE borrower_type = 'INDIVIDUAL'
      AND loan_product = 'Personal'
      AND active = TRUE
);
