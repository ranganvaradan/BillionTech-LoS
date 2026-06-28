-- Physical KYC (PKYC) fallback metadata on the loan application (nullable, backward-compatible).
ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS vkyc_completion_mode varchar(10),
    ADD COLUMN IF NOT EXISTS pkyc_reason varchar(80),
    ADD COLUMN IF NOT EXISTS pkyc_comments text,
    ADD COLUMN IF NOT EXISTS pkyc_document_id uuid,
    ADD COLUMN IF NOT EXISTS pkyc_verified_by uuid,
    ADD COLUMN IF NOT EXISTS pkyc_verified_at timestamp with time zone;
