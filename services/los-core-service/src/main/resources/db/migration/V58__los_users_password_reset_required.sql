-- Auto-provisioned borrower accounts (created when staff submit an application for a borrower
-- who is not yet on LOS) get a temporary, mobile-based password and must set their own password
-- on first login. This flag drives that forced reset; existing/self-registered users default to false.
ALTER TABLE los_users
    ADD COLUMN IF NOT EXISTS password_reset_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN los_users.password_reset_required IS
    'When true, the user signed in with a temporary password and must set a new one before continuing. Set for borrower accounts auto-created from staff application submission.';
