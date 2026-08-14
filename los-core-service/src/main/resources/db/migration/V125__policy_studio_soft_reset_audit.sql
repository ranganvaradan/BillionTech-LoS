-- Admin soft-reset audit trail (Policy Studio drafts/reviews wipe).
-- Soft reset never deletes ACTIVE/RETIRED/APPROVED/SCHEDULED by design.

CREATE TABLE IF NOT EXISTS ci_policy_studio_env_reset_audit (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID,
    environment_name    VARCHAR(120) NOT NULL,
    mode                VARCHAR(40)  NOT NULL DEFAULT 'SOFT',
    actor               VARCHAR(160) NOT NULL,
    confirmation_token  VARCHAR(240) NOT NULL,
    backup_path         TEXT         NOT NULL,
    backup_sha256       VARCHAR(64)  NOT NULL,
    backup_bytes        BIGINT       NOT NULL,
    deleted_count       INT          NOT NULL DEFAULT 0,
    protected_skipped   INT          NOT NULL DEFAULT 0,
    deleted_ids_sha256  VARCHAR(64)  NOT NULL,
    detail_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    outcome             VARCHAR(40)  NOT NULL,
    error_message       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ci_ps_env_reset_audit_created
    ON ci_policy_studio_env_reset_audit (created_at DESC);

COMMENT ON TABLE ci_policy_studio_env_reset_audit IS
    'Audit log for admin Policy Studio soft-reset (drafts/reviews + overlays; excludes ACTIVE/RETIRED).';
