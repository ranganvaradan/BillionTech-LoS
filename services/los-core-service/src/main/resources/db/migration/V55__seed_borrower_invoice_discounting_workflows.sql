-- V55: ensure one active BORROWER-segment workflow exists for BUSINESS_WC_INVOICE_DISCOUNTING
-- per supported borrower_type. V32 originally seeded the 4×8 default matrix and V51 added the
-- ANCHOR-segment rows; in environments where the BORROWER row for invoice discounting is missing
-- or inactive, the borrower intake wizard reports
--   "The selected borrower type does not have an active borrower workflow for invoice discounting."
-- This migration is idempotent (NOT EXISTS guards) so it is safe in already-seeded environments.

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version,
    created_at, updated_at, intake_segment
)
SELECT 'b0000001-0000-4000-a000-000000000001'::uuid,
       'Borrower — Individual — Invoice Discounting',
       'INDIVIDUAL',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
         {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":3,"provider":"PERFIOS"},
         {"step":"AML_SCREENING","mandatory":true,"order":4,"provider":"PERFIOS"}
       ]'::jsonb,
       true, 1, now(), now(), 'BORROWER'
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_configs c
    WHERE c.borrower_type = 'INDIVIDUAL'
      AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
      AND c.intake_segment = 'BORROWER'
      AND c.active = true
);

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version,
    created_at, updated_at, intake_segment
)
SELECT 'b0000001-0000-4000-a000-000000000002'::uuid,
       'Borrower — Proprietor — Invoice Discounting',
       'PROPRIETOR',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
         {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
         {"step":"GSTIN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":4,"provider":"PERFIOS"},
         {"step":"AML_SCREENING","mandatory":true,"order":5,"provider":"PERFIOS"}
       ]'::jsonb,
       true, 1, now(), now(), 'BORROWER'
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_configs c
    WHERE c.borrower_type = 'PROPRIETOR'
      AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
      AND c.intake_segment = 'BORROWER'
      AND c.active = true
);

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version,
    created_at, updated_at, intake_segment
)
SELECT 'b0000001-0000-4000-a000-000000000003'::uuid,
       'Borrower — Partnership — Invoice Discounting',
       'PARTNERSHIP',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
         {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
         {"step":"GSTIN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":4,"provider":"PERFIOS"},
         {"step":"AML_SCREENING","mandatory":true,"order":5,"provider":"PERFIOS"}
       ]'::jsonb,
       true, 1, now(), now(), 'BORROWER'
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_configs c
    WHERE c.borrower_type = 'PARTNERSHIP'
      AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
      AND c.intake_segment = 'BORROWER'
      AND c.active = true
);

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version,
    created_at, updated_at, intake_segment
)
SELECT 'b0000001-0000-4000-a000-000000000004'::uuid,
       'Borrower — Company — Invoice Discounting',
       'COMPANY',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
         {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
         {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
         {"step":"GSTIN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
         {"step":"CIN_MCA21","mandatory":true,"order":4,"provider":"PERFIOS"},
         {"step":"BANK_PENNY_DROP","mandatory":true,"order":5,"provider":"PERFIOS"},
         {"step":"AML_SCREENING","mandatory":true,"order":6,"provider":"PERFIOS"}
       ]'::jsonb,
       true, 1, now(), now(), 'BORROWER'
WHERE NOT EXISTS (
    SELECT 1 FROM workflow_configs c
    WHERE c.borrower_type = 'COMPANY'
      AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
      AND c.intake_segment = 'BORROWER'
      AND c.active = true
);
