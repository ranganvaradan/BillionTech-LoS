-- Raw Encore findSummaries accountStatementEntries[] (JSON) for parity / audits

ALTER TABLE lms_account_summary
    ADD COLUMN encore_account_statement_entries_json TEXT;
