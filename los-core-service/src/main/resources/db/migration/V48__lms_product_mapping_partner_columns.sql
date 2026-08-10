-- Partner-based Encore product resolution (bl-core parity: productCode from loan_product per partner)

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS partner_code VARCHAR(100);

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS tenure_unit VARCHAR(20);

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS penal_interest_rate NUMERIC(8, 4);

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS moratorium_type VARCHAR(30);

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS moratorium_period_magnitude INTEGER;

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS moratorium_period_unit VARCHAR(20);

ALTER TABLE workflow_lms_product_mapping
    ADD COLUMN IF NOT EXISTS co_lending_applicable BOOLEAN DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_lms_mapping_partner_lp
    ON workflow_lms_product_mapping (partner_code, loan_product)
    WHERE partner_code IS NOT NULL;

COMMENT ON COLUMN workflow_lms_product_mapping.partner_code IS
    'bl-core partner code — primary lookup key. When set, (partner_code, loan_product) takes priority over (borrower_type, loan_product).';
