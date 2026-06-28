-- Demo local authentication (password hash + password reset). Production should use IAM/OAuth and secure reset delivery.
ALTER TABLE los_users
    ADD COLUMN IF NOT EXISTS password_hash        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS password_reset_token      VARCHAR(64),
    ADD COLUMN IF NOT EXISTS password_reset_token_expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS primary_los_role     VARCHAR(50);

COMMENT ON COLUMN los_users.password_hash IS 'BCrypt password hash. Demo only — use IAM in production.';
COMMENT ON COLUMN los_users.password_reset_token IS 'Opaque token for /auth/reset-password. Demo: returned in API until email is wired.';
COMMENT ON COLUMN los_users.password_reset_token_expires_at IS 'When the reset token is no longer valid.';
COMMENT ON COLUMN los_users.primary_los_role IS 'Primary LOS role for login/UI (denormalized; user_role_mappings still used for assignment).';

CREATE INDEX IF NOT EXISTS idx_los_users_reset_token ON los_users (password_reset_token) WHERE password_reset_token IS NOT NULL;
