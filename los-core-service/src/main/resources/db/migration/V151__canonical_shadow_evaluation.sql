-- WAVE 11.3 — canonical shadow evaluation + comparison
-- Separate from loan_applications.credit_decision and underwriting_evaluations live columns.
-- Does not overwrite decision_snapshot_json.

CREATE TABLE IF NOT EXISTS ci_canonical_shadow_evaluation (
    id                          UUID PRIMARY KEY,
    application_id              UUID         NOT NULL REFERENCES loan_applications (id),
    underwriting_evaluation_id  UUID         REFERENCES underwriting_evaluations (id),
    freeze_row_id               UUID         REFERENCES ci_canonical_application_configuration (id),
    identity_hash               VARCHAR(64)  NOT NULL,
    status                      VARCHAR(40)  NOT NULL,
    canonical_decision          VARCHAR(40),
    reason_codes                JSONB        NOT NULL DEFAULT '[]'::jsonb,
    parameter_evidence          JSONB        NOT NULL DEFAULT '[]'::jsonb,
    rule_evidence               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    scorecard_evidence          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    policy_result               JSONB        NOT NULL DEFAULT '{}'::jsonb,
    aggregation                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ci_canonical_shadow_evaluation IS
    'W11.3 canonical shadow underwriting. Never mutates live credit_decision / status / CAM.';

COMMENT ON COLUMN ci_canonical_shadow_evaluation.status IS
    'COMPLETED | NOT_ELIGIBLE | NOT_EXECUTABLE | ERROR';

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_canonical_shadow_eval_evaluation
    ON ci_canonical_shadow_evaluation (underwriting_evaluation_id, identity_hash)
    WHERE underwriting_evaluation_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_canonical_shadow_eval_app_fixture
    ON ci_canonical_shadow_evaluation (application_id, identity_hash)
    WHERE underwriting_evaluation_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_ci_canonical_shadow_eval_app
    ON ci_canonical_shadow_evaluation (application_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_canonical_shadow_comparison (
    id                          UUID PRIMARY KEY,
    shadow_evaluation_id        UUID         NOT NULL REFERENCES ci_canonical_shadow_evaluation (id),
    application_id              UUID         NOT NULL REFERENCES loan_applications (id),
    underwriting_evaluation_id  UUID         REFERENCES underwriting_evaluations (id),
    identity_hash               VARCHAR(64)  NOT NULL,
    comparison_status           VARCHAR(40)  NOT NULL,
    legacy_decision_at          TIMESTAMPTZ,
    canonical_shadow_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    mismatch_counts             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    mismatches                  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    policy_test_equivalence     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    legacy_evidence             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ci_canonical_shadow_comparison IS
    'W11.3 legacy vs canonical shadow comparison. Observational only.';

COMMENT ON COLUMN ci_canonical_shadow_comparison.comparison_status IS
    'MATCH | MISMATCH | CANONICAL_NOT_EXECUTABLE | SHADOW_NOT_ELIGIBLE | ERROR';

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_canonical_shadow_comparison_eval
    ON ci_canonical_shadow_comparison (shadow_evaluation_id);

CREATE INDEX IF NOT EXISTS idx_ci_canonical_shadow_comparison_app
    ON ci_canonical_shadow_comparison (application_id, created_at DESC);
