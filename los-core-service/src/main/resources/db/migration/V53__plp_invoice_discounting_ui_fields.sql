-- PLP invoice discounting: program UI fields and per-step borrower sync columns

ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS interest_rate NUMERIC(6, 4),
    ADD COLUMN IF NOT EXISTS tenure_days INT,
    ADD COLUMN IF NOT EXISTS currency CHAR(3) DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS validity_start_date DATE,
    ADD COLUMN IF NOT EXISTS validity_end_date DATE;

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS plp_borrower_sync_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN IF NOT EXISTS plp_borrower_synced_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS plp_link_sync_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN IF NOT EXISTS plp_link_synced_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS plp_mapping_sync_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SYNCED',
    ADD COLUMN IF NOT EXISTS plp_mapping_synced_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_loan_applications_plp_borrower_sync
    ON loan_applications (plp_borrower_sync_status);
CREATE INDEX IF NOT EXISTS idx_loan_applications_plp_link_sync
    ON loan_applications (plp_link_sync_status);
CREATE INDEX IF NOT EXISTS idx_loan_applications_plp_mapping_sync
    ON loan_applications (plp_mapping_sync_status);
