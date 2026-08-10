-- Bureau pull is a post-KYC flow step (FlowStepType.BUREAU_PULL), not part of the KYC sub-workflow loop.
-- KYC JSON may still list BUREAU_PULL for ordering in WorkflowFlowStepOrderResolver, with provider EQUIFAX.

UPDATE workflow_configs
SET steps = '[
      {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"AUTHBRIDGE"},
      {"step":"AADHAAR_OTP","mandatory":true,"order":2,"provider":"AUTHBRIDGE"},
      {"step":"PAN_VERIFY","mandatory":true,"order":3,"provider":"AUTHBRIDGE"},
      {"step":"FACE_MATCH","mandatory":true,"order":4,"provider":"HYPERVERGE"},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"AUTHBRIDGE"},
      {"step":"CKYC_DOWNLOAD","mandatory":false,"order":6,"provider":"AUTHBRIDGE"},
      {"step":"BUREAU_PULL","mandatory":true,"order":20,"provider":"EQUIFAX"}
    ]'::jsonb,
    updated_at = now()
WHERE borrower_type = 'INDIVIDUAL'
  AND loan_product = 'Personal'
  AND active = TRUE;
