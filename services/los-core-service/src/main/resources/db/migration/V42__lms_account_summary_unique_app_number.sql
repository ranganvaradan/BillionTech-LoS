ALTER TABLE lms_account_summary
    ADD CONSTRAINT uq_lms_account_summary_app_number UNIQUE (application_number);
