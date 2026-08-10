-- LMS product code and tenure unit per workflow (defaults) and per application (overrides).

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS lms_product_code VARCHAR(50) DEFAULT 'IPPOPAYM01',
    ADD COLUMN IF NOT EXISTS lms_tenure_unit VARCHAR(20) DEFAULT 'Month';

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS lms_product_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS lms_tenure_unit VARCHAR(20);

-- Backfill non–invoice-discounting workflows with current Encore defaults.
UPDATE workflow_configs
SET lms_product_code = 'IPPOPAYM01',
    lms_tenure_unit = 'Month'
WHERE loan_product <> 'BUSINESS_WC_INVOICE_DISCOUNTING'
  AND (lms_product_code IS NULL OR lms_product_code = '');
