-- V4__add_password_history_indexes.sql
-- Optimise queries on user_password_history issued during password change.
--
-- Hibernate (@ElementCollection / bag) generates:
--   SELECT password_hash FROM user_password_history WHERE user_id = ?
--   DELETE FROM user_password_history WHERE user_id = ?
--
-- Replace the single-column user_id index with a covering composite index
-- so the SELECT becomes an index-only scan (no heap lookup required).

DROP INDEX IF EXISTS idx_user_password_history_user_id;

CREATE INDEX idx_user_password_history_user_id_hash
    ON user_password_history (user_id, password_hash);
