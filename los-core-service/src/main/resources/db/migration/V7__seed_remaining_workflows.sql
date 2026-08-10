-- V7__seed_remaining_workflows.sql
-- Seed workflow configs for ALL borrower-type + loan-product combinations
-- that were missing from V2 (which only covered INDIVIDUAL/TERM_LOAN,
-- PROPRIETOR/BUSINESS_LOAN, COMPANY/BUSINESS_LOAN).

-- Also add missing DB columns used by the LoanApplication entity.
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS sla_deadline TIMESTAMPTZ;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS current_step_started_at TIMESTAMPTZ;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS escalated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMPTZ;

-- Also add missing columns on workflow_configs for parallel groups, SLA, escalation, conditional rules
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS sla_hours_per_step JSONB;
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS escalation_emails JSONB;
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS conditional_rules JSONB;
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS parallel_groups JSONB;

-- ─── Individual workflows (missing: BUSINESS_LOAN, PERSONAL_LOAN, WORKING_CAPITAL, LAP) ───
INSERT INTO workflow_configs (id, name, borrower_type, loan_product, steps, active, version) VALUES
('b0000000-0000-0000-0000-000000000011', 'Individual - Business Loan', 'INDIVIDUAL', 'BUSINESS_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 4, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 6, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000012', 'Individual - Personal Loan', 'INDIVIDUAL', 'PERSONAL_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 4, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 6, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000013', 'Individual - Working Capital', 'INDIVIDUAL', 'WORKING_CAPITAL',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 4, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 6, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000014', 'Individual - LAP', 'INDIVIDUAL', 'LAP',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 4, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 6, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),

-- ─── Proprietor workflows (missing: TERM_LOAN, PERSONAL_LOAN, WORKING_CAPITAL, LAP) ───
('b0000000-0000-0000-0000-000000000021', 'Proprietor - Term Loan', 'PROPRIETOR', 'TERM_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "UDYAM_VERIFY",    "mandatory": false, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000022', 'Proprietor - Personal Loan', 'PROPRIETOR', 'PERSONAL_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 7, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000023', 'Proprietor - Working Capital', 'PROPRIETOR', 'WORKING_CAPITAL',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "UDYAM_VERIFY",    "mandatory": false, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000024', 'Proprietor - LAP', 'PROPRIETOR', 'LAP',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 7, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),

-- ─── Partnership workflows (ALL products — none existed) ───
('b0000000-0000-0000-0000-000000000031', 'Partnership - Term Loan', 'PARTNERSHIP', 'TERM_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000032', 'Partnership - Business Loan', 'PARTNERSHIP', 'BUSINESS_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000033', 'Partnership - Personal Loan', 'PARTNERSHIP', 'PERSONAL_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 7, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000034', 'Partnership - Working Capital', 'PARTNERSHIP', 'WORKING_CAPITAL',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "UDYAM_VERIFY",    "mandatory": false, "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000035', 'Partnership - LAP', 'PARTNERSHIP', 'LAP',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 5, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 6, "provider": "AUTHBRIDGE"},
   {"step": "CKYC_DOWNLOAD",   "mandatory": false, "order": 7, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),

-- ─── Company workflows (missing: TERM_LOAN, PERSONAL_LOAN, WORKING_CAPITAL, LAP) ───
('b0000000-0000-0000-0000-000000000041', 'Company - Term Loan', 'COMPANY', 'TERM_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "CIN_MCA21",       "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000042', 'Company - Personal Loan', 'COMPANY', 'PERSONAL_LOAN',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "CIN_MCA21",       "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000043', 'Company - Working Capital', 'COMPANY', 'WORKING_CAPITAL',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "CIN_MCA21",       "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1),
('b0000000-0000-0000-0000-000000000044', 'Company - LAP', 'COMPANY', 'LAP',
 '[
   {"step": "MOBILE_OTP",      "mandatory": true,  "order": 1, "provider": "AUTHBRIDGE"},
   {"step": "PAN_VERIFY",      "mandatory": true,  "order": 2, "provider": "AUTHBRIDGE"},
   {"step": "CIN_MCA21",       "mandatory": true,  "order": 3, "provider": "AUTHBRIDGE"},
   {"step": "GSTIN_VERIFY",    "mandatory": true,  "order": 4, "provider": "AUTHBRIDGE"},
   {"step": "AADHAAR_OTP",     "mandatory": true,  "order": 5, "provider": "AUTHBRIDGE"},
   {"step": "FACE_MATCH",      "mandatory": true,  "order": 6, "provider": "HYPERVERGE"},
   {"step": "BANK_PENNY_DROP", "mandatory": true,  "order": 7, "provider": "AUTHBRIDGE"},
   {"step": "AML_SCREENING",   "mandatory": true,  "order": 8, "provider": "AUTHBRIDGE"}
 ]'::jsonb, TRUE, 1);

-- ─── Fix V2 workflows: remove BUREAU_PULL from KYC step lists ───
-- BUREAU_PULL is handled separately by the Flow Controller (Step 3: Pull Bureau),
-- not by KYC providers. No IKycProvider supports BUREAU_PULL, so it always fails
-- as a mandatory step and halts the entire KYC workflow.
UPDATE workflow_configs
SET steps = (
  SELECT jsonb_agg(elem)
  FROM jsonb_array_elements(steps) elem
  WHERE elem->>'step' != 'BUREAU_PULL'
)
WHERE id IN (
  'b0000000-0000-0000-0000-000000000001',
  'b0000000-0000-0000-0000-000000000002',
  'b0000000-0000-0000-0000-000000000003'
);
