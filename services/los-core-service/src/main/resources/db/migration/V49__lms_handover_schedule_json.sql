-- Store Encore request/response payloads and repayment schedule JSON after loan creation (bl-core parity)

ALTER TABLE lms_loan_handover
    ADD COLUMN IF NOT EXISTS encore_repayment_schedule_json TEXT;

ALTER TABLE lms_loan_handover
    ADD COLUMN IF NOT EXISTS encore_open_account_request_json TEXT;

ALTER TABLE lms_loan_handover
    ADD COLUMN IF NOT EXISTS encore_open_account_response_json TEXT;

COMMENT ON COLUMN lms_loan_handover.encore_repayment_schedule_json IS
    'Raw JSON repayment schedule fetched from Encore findSummary after loan creation.';

COMMENT ON COLUMN lms_loan_handover.encore_open_account_request_json IS
    'JSON payload sent to Encore openLoanAccount (for debugging/audit).';

COMMENT ON COLUMN lms_loan_handover.encore_open_account_response_json IS
    'Raw JSON response from Encore openLoanAccount (for debugging/audit).';
