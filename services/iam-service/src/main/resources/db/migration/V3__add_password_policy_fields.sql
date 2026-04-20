-- V3__add_password_policy_fields.sql
-- Adds password policy support: change tracking and password history

ALTER TABLE users ADD COLUMN password_changed_at TIMESTAMPTZ;

CREATE TABLE user_password_history (
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL
);

CREATE INDEX idx_user_password_history_user_id ON user_password_history(user_id);
