ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS vkyc_url_generated_at timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_url_expiry_at timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_last_resent_at timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_resend_count integer,
    ADD COLUMN IF NOT EXISTS vkyc_email_sent boolean,
    ADD COLUMN IF NOT EXISTS vkyc_email_sent_at timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_generated_by uuid;
