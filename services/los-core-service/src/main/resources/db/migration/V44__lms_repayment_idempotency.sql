ALTER TABLE lms_repayment_callback
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS idx_lms_repayment_idempotency_key
    ON lms_repayment_callback (idempotency_key)
    WHERE idempotency_key IS NOT NULL AND idempotency_key <> '';
