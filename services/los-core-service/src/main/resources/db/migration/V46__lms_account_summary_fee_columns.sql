-- Optional Encore fee buckets (bl-core EncoreFindSummariesDto blFeeDue / lenderFeeDue parity)

ALTER TABLE lms_account_summary
    ADD COLUMN IF NOT EXISTS fee_bl_due NUMERIC(15, 2);

ALTER TABLE lms_account_summary
    ADD COLUMN IF NOT EXISTS fee_lender_due NUMERIC(15, 2);

COMMENT ON COLUMN lms_account_summary.fee_bl_due IS 'Aggregated Billion Loans / processing-style fees from Encore findSummaries fees[] when present.';
COMMENT ON COLUMN lms_account_summary.fee_lender_due IS 'Aggregated lender fees from Encore findSummaries fees[] when present.';
