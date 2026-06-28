-- Active anchor (invoice discounting) onboarding workflows — one per (borrower_type, loan_product, ANCHOR).

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at, intake_segment
)
SELECT 'a0000001-0000-4000-a000-000000000001'::uuid,
       'Anchor ID — Proprietor — Invoice Discounting',
       'PROPRIETOR',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
      {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
      {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
      {"step":"GSTIN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":4,"provider":"PERFIOS"},
      {"step":"AML_SCREENING","mandatory":true,"order":5,"provider":"PERFIOS"}
    ]'::jsonb,
       true,
       1,
       now(),
       now(),
       'ANCHOR'
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'PROPRIETOR'
                    AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
                    AND c.intake_segment = 'ANCHOR'
                    AND c.active = true);

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at, intake_segment
)
SELECT 'a0000001-0000-4000-a000-000000000002'::uuid,
       'Anchor ID — Partnership — Invoice Discounting',
       'PARTNERSHIP',
       'BUSINESS_WC_INVOICE_DISCOUNTING',
       '[
      {"step":"MOBILE_OTP","mandatory":true,"order":1,"provider":"PERFIOS"},
      {"step":"PAN_VERIFY","mandatory":true,"order":2,"provider":"PERFIOS"},
      {"step":"GSTIN_VERIFY","mandatory":true,"order":3,"provider":"PERFIOS"},
      {"step":"BANK_PENNY_DROP","mandatory":true,"order":4,"provider":"PERFIOS"},
      {"step":"AML_SCREENING","mandatory":true,"order":5,"provider":"PERFIOS"}
    ]'::jsonb,
       true,
       1,
       now(),
       now(),
       'ANCHOR'
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'PARTNERSHIP'
                    AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
                    AND c.intake_segment = 'ANCHOR'
                    AND c.active = true);

INSERT INTO workflow_configs (
    id, name, borrower_type, loan_product, steps, active, version, created_at, updated_at, intake_segment
)
SELECT 'a0000001-0000-4000-a000-000000000003'::uuid,
       'Anchor ID — Company — Invoice Discounting',
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
       true,
       1,
       now(),
       now(),
       'ANCHOR'
WHERE NOT EXISTS (SELECT 1
                  FROM workflow_configs c
                  WHERE c.borrower_type = 'COMPANY'
                    AND c.loan_product = 'BUSINESS_WC_INVOICE_DISCOUNTING'
                    AND c.intake_segment = 'ANCHOR'
                    AND c.active = true);
