-- NBFC-style CAM: status, version, recommended terms, conditions, approval trail
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS cam_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS cam_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS editable_sections_json JSONB;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS recommended_amount NUMERIC(15, 2);
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS recommended_tenure_months INTEGER;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS recommended_rate NUMERIC(5, 2);
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS conditions_precedent_json JSONB;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS conditions_subsequent_json JSONB;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS credit_officer_remarks TEXT;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS credit_manager_remarks TEXT;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS submitted_at TIMESTAMPTZ;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS approved_by_user_id UUID;
ALTER TABLE credit_appraisal_memos
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;

UPDATE credit_appraisal_memos SET cam_status = 'APPROVED' WHERE cam_reviewed = TRUE;

COMMENT ON COLUMN credit_appraisal_memos.cam_status IS 'DRAFT | SUBMITTED | APPROVED | REJECTED | SENT_BACK';
