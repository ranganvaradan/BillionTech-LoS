-- V2__add_unique_constraint_account_summary.sql
-- Add UNIQUE constraint on application_number to prevent duplicate rows
-- which would cause IncorrectResultSizeDataAccessException on findByApplicationNumber()

ALTER TABLE lms_account_summary
    ADD CONSTRAINT uq_lms_account_summary_app_number UNIQUE (application_number);
