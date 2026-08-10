-- Borrower delegated intake and admin review workflow

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS intake_owner VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    ADD COLUMN IF NOT EXISTS intake_completed_step INTEGER,
    ADD COLUMN IF NOT EXISTS borrower_sent_back_notes TEXT;

CREATE INDEX IF NOT EXISTS idx_loan_applications_intake_owner ON loan_applications (intake_owner);
