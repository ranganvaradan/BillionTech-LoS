-- CAM recommended interest type (UPFRONT | REDUCING)
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS interest_type VARCHAR(20);

COMMENT ON COLUMN credit_appraisal_memos.interest_type IS 'UPFRONT | REDUCING — interest calculation basis for recommended rate';
