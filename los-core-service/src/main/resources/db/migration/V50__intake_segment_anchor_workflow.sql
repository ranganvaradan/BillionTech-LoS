-- Intake segment: BORROWER (default) vs ANCHOR (invoice discounting anchor onboarding).
-- Allows separate active workflows per (borrower_type, loan_product, intake_segment).

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS intake_segment VARCHAR(20) NOT NULL DEFAULT 'BORROWER';

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS intake_segment VARCHAR(20) NOT NULL DEFAULT 'BORROWER';

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS intake_identity_schema JSONB;

COMMENT ON COLUMN loan_applications.intake_segment IS 'BORROWER | ANCHOR — workflow resolution key';
COMMENT ON COLUMN workflow_configs.intake_segment IS 'BORROWER | ANCHOR — one active workflow per (borrower_type, loan_product, intake_segment)';
COMMENT ON COLUMN workflow_configs.intake_identity_schema IS 'Optional JSON array: anchor identity step field defs (key, label, required, visible, inputType)';

-- Replace partial unique: one active workflow per borrower × product × segment
DROP INDEX IF EXISTS uq_workflow_cfg_active_borrower_product;

CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_cfg_active_borrower_product_segment
    ON workflow_configs (borrower_type, loan_product, intake_segment)
    WHERE active = true;
