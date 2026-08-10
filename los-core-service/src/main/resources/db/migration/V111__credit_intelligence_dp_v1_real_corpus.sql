-- DP-V1 — Decision Policy real/stored validation corpus (validation-only tables).
-- Never authoritative. Does not alter operational loan_applications.

CREATE TABLE IF NOT EXISTS ci_dp_v1_corpus_application (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL,
    application_token           VARCHAR(120) NOT NULL,
    origin_classification       VARCHAR(60) NOT NULL,
    product_code                VARCHAR(80),
    borrower_type               VARCHAR(40),
    evaluation_business_date    DATE,
    requested_amount            NUMERIC(18, 2),
    requested_tenure_months     INTEGER,
    anonymisation_version       VARCHAR(40) NOT NULL DEFAULT 'DP_V1_ANON_V1',
    schema_version              VARCHAR(40) NOT NULL DEFAULT 'DP_V1_CORPUS_SCHEMA_V1',
    usable                      BOOLEAN NOT NULL DEFAULT TRUE,
    counts_toward_certification BOOLEAN NOT NULL DEFAULT FALSE,
    payload                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(80)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_dp_v1_corpus_token_tenant
    ON ci_dp_v1_corpus_application (tenant_id, application_token);

CREATE INDEX IF NOT EXISTS idx_ci_dp_v1_corpus_origin
    ON ci_dp_v1_corpus_application (origin_classification);

CREATE INDEX IF NOT EXISTS idx_ci_dp_v1_corpus_product
    ON ci_dp_v1_corpus_application (product_code);

CREATE TABLE IF NOT EXISTS ci_dp_v1_validation_run (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at            TIMESTAMPTZ,
    status                  VARCHAR(40) NOT NULL DEFAULT 'RUNNING',
    certification_status    VARCHAR(60) NOT NULL DEFAULT 'INSUFFICIENT_EVIDENCE',
    real_stored_count       INTEGER NOT NULL DEFAULT 0,
    usable_count            INTEGER NOT NULL DEFAULT 0,
    replay_pass_rate        NUMERIC(8, 4),
    summary                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    export_spec_snapshot    JSONB NOT NULL DEFAULT '{}'::jsonb,
    allow_canonical_authority BOOLEAN NOT NULL DEFAULT FALSE,
    shadow_only             BOOLEAN NOT NULL DEFAULT TRUE,
    created_by              VARCHAR(80)
);

CREATE INDEX IF NOT EXISTS idx_ci_dp_v1_run_tenant
    ON ci_dp_v1_validation_run (tenant_id, started_at DESC);

CREATE TABLE IF NOT EXISTS ci_dp_v1_validation_defect (
    id                  UUID PRIMARY KEY,
    run_id              UUID REFERENCES ci_dp_v1_validation_run (id) ON DELETE CASCADE,
    application_token   VARCHAR(120),
    severity            VARCHAR(40) NOT NULL,
    component           VARCHAR(80),
    defect_type         VARCHAR(80) NOT NULL,
    description         TEXT,
    root_cause          TEXT,
    recommended_action  TEXT,
    blocking            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ci_dp_v1_defect_run ON ci_dp_v1_validation_defect (run_id);
CREATE INDEX IF NOT EXISTS idx_ci_dp_v1_defect_blocking
    ON ci_dp_v1_validation_defect (blocking) WHERE blocking = TRUE;
