-- V6: Add columns for end-to-end loan flow orchestration
-- sanctioned_amount, approved_rate, disbursed_amount, disbursed_at, lms_reference_id, esign_transaction_id

ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS sanctioned_amount NUMERIC(15,2);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS approved_rate NUMERIC(5,2);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS disbursed_amount NUMERIC(15,2);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS disbursed_at TIMESTAMPTZ;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS lms_reference_id VARCHAR(100);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS esign_transaction_id VARCHAR(100);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS bureau_score INTEGER;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS credit_decision VARCHAR(30);
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS credit_risk_score INTEGER;

CREATE INDEX idx_app_lms_ref ON loan_applications(lms_reference_id);
CREATE INDEX idx_app_credit_decision ON loan_applications(credit_decision);
