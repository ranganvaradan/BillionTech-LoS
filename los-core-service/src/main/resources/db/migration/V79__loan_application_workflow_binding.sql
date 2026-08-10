ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS workflow_id UUID;

CREATE INDEX IF NOT EXISTS idx_loan_applications_workflow_id
    ON loan_applications (workflow_id);
