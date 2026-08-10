-- Allow more than one active workflow for the same borrower type / loan product / intake segment.
-- Resolution prefers the highest version when an application is not bound to a specific workflow_id.
DROP INDEX IF EXISTS uq_workflow_cfg_active_borrower_product_segment;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS workflow_id UUID;

CREATE INDEX IF NOT EXISTS idx_loan_applications_workflow_id
    ON loan_applications (workflow_id);
