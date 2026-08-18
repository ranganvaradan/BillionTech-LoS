-- WAVE 11.2 — canonical application configuration freeze
-- Immutable identity package for later W11.3 shadow execution.
-- Does not overwrite underwriting_evaluations.decision_snapshot_json.
-- Does not pin latest/current/active authorities onto loan_applications.
-- Does not change live credit-decision authority.

CREATE TABLE IF NOT EXISTS ci_canonical_application_configuration (
    id                  UUID PRIMARY KEY,
    application_id      UUID         NOT NULL REFERENCES loan_applications (id),
    status              VARCHAR(40)  NOT NULL,
    identity_hash       VARCHAR(64)  NOT NULL,
    reason_codes        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    outcomes            JSONB        NOT NULL DEFAULT '{}'::jsonb,
    package_json        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ci_canonical_application_configuration IS
    'W11.2 immutable canonical configuration identity freeze. One RESOLVED row per application; never overwrites legacy decision snapshots.';

COMMENT ON COLUMN ci_canonical_application_configuration.status IS
    'RESOLVED | NOT_RESOLVABLE. RESOLVED rows are immutable.';

COMMENT ON COLUMN ci_canonical_application_configuration.identity_hash IS
    'SHA-256 of pinned identity fields excluding resolutionTimestamp. Retry with same pins returns the same freeze.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_canonical_app_cfg_resolved
    ON ci_canonical_application_configuration (application_id)
    WHERE status = 'RESOLVED';

CREATE INDEX IF NOT EXISTS idx_ci_canonical_app_cfg_application
    ON ci_canonical_application_configuration (application_id, created_at DESC);
