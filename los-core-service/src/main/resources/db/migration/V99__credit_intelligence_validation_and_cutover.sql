-- Phase C6: Multi-source validation, policy comparison, cutover readiness, replay manifest, lender matching

CREATE TABLE IF NOT EXISTS ci_validation_run (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    run_code                VARCHAR(80) NOT NULL,
    case_code               VARCHAR(80),
    data_origin             VARCHAR(60) NOT NULL,
    status                  VARCHAR(40) NOT NULL DEFAULT 'COMPLETED',
    application_id          UUID,
    evaluation_context_id   UUID,
    fact_snapshot_id        UUID,
    policy_version_id       UUID,
    config_freeze_id        UUID,
    metric_result_set_id    UUID,
    reconciliation_result_set_id UUID,
    deterministic_evaluation_hash VARCHAR(128),
    replay_hash             VARCHAR(128),
    replay_identical        BOOLEAN,
    evidence_strength_score NUMERIC(8, 2),
    evidence_strength_grade VARCHAR(40),
    cutover_outcome         VARCHAR(40),
    duration_ms             BIGINT,
    summary                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_validation_run_tenant ON ci_validation_run (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_validation_run_case ON ci_validation_run (case_code, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_validation_finding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    validation_run_id   UUID NOT NULL REFERENCES ci_validation_run (id) ON DELETE CASCADE,
    finding_code        VARCHAR(120) NOT NULL,
    severity            VARCHAR(40) NOT NULL DEFAULT 'INFO',
    category            VARCHAR(80) NOT NULL,
    message             TEXT NOT NULL,
    detail              JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_validation_finding_run ON ci_validation_finding (validation_run_id, severity);

CREATE TABLE IF NOT EXISTS ci_policy_binding (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    legacy_parameter        VARCHAR(120) NOT NULL,
    canonical_type          VARCHAR(40) NOT NULL,
    canonical_path          VARCHAR(200),
    allowed_classifications JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_data_policy     VARCHAR(40),
    current_default_origin  VARCHAR(80),
    current_default_value   VARCHAR(120),
    production_rules        JSONB NOT NULL DEFAULT '[]'::jsonb,
    ready                   BOOLEAN NOT NULL DEFAULT FALSE,
    critical                BOOLEAN NOT NULL DEFAULT FALSE,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_binding UNIQUE (tenant_id, legacy_parameter)
);

CREATE TABLE IF NOT EXISTS ci_policy_comparison (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID,
    validation_run_id       UUID REFERENCES ci_validation_run (id) ON DELETE SET NULL,
    evaluation_context_id   UUID,
    rule_id                 VARCHAR(120) NOT NULL,
    legacy_outcome          VARCHAR(40),
    canonical_outcome       VARCHAR(40),
    difference_class        VARCHAR(60) NOT NULL,
    legacy_input            JSONB NOT NULL DEFAULT '{}'::jsonb,
    canonical_input         JSONB NOT NULL DEFAULT '{}'::jsonb,
    explanation             TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_comparison_run ON ci_policy_comparison (validation_run_id);
CREATE INDEX IF NOT EXISTS idx_ci_policy_comparison_app ON ci_policy_comparison (application_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_policy_cutover_readiness (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    scope                   VARCHAR(120) NOT NULL DEFAULT 'PLATFORM',
    outcome                 VARCHAR(40) NOT NULL,
    replay_purity           BOOLEAN NOT NULL DEFAULT FALSE,
    critical_coverage_pct   NUMERIC(8, 4),
    binding_coverage_pct    NUMERIC(8, 4),
    silent_default_deps     INT NOT NULL DEFAULT 0,
    tenant_isolation_ok     BOOLEAN NOT NULL DEFAULT FALSE,
    provider_fixtures_ok    BOOLEAN NOT NULL DEFAULT FALSE,
    multi_source_ok         BOOLEAN NOT NULL DEFAULT FALSE,
    security_critical_open  INT NOT NULL DEFAULT 0,
    blockers                JSONB NOT NULL DEFAULT '[]'::jsonb,
    dimensions              JSONB NOT NULL DEFAULT '{}'::jsonb,
    assessed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_cutover_tenant ON ci_policy_cutover_readiness (tenant_id, assessed_at DESC);

CREATE TABLE IF NOT EXISTS ci_replay_manifest (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID,
    evaluation_context_id       UUID NOT NULL,
    fact_snapshot_hash          VARCHAR(128),
    policy_version_hash         VARCHAR(128),
    config_freeze_hash          VARCHAR(128),
    metric_result_set_hash      VARCHAR(128),
    reconciliation_result_set_hash VARCHAR(128),
    deterministic_evaluation_hash VARCHAR(128),
    parser_versions             JSONB NOT NULL DEFAULT '{}'::jsonb,
    normalizer_versions         JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_references           JSONB NOT NULL DEFAULT '[]'::jsonb,
    schema_version              VARCHAR(40) NOT NULL DEFAULT 'CREDIT_DECISION_REPLAY_MANIFEST_V1',
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_replay_manifest_ctx ON ci_replay_manifest (evaluation_context_id);

CREATE TABLE IF NOT EXISTS ci_lender_identity (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    canonical_name      VARCHAR(200) NOT NULL,
    lender_code         VARCHAR(80),
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_lender_identity UNIQUE (tenant_id, canonical_name)
);

CREATE TABLE IF NOT EXISTS ci_lender_alias (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lender_identity_id  UUID NOT NULL REFERENCES ci_lender_identity (id) ON DELETE CASCADE,
    alias_text          VARCHAR(300) NOT NULL,
    alias_source        VARCHAR(40) NOT NULL DEFAULT 'MANUAL',
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_lender_alias_text ON ci_lender_alias (lower(alias_text));

CREATE TABLE IF NOT EXISTS ci_obligation_match (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    reconciliation_result_id UUID,
    match_status            VARCHAR(40) NOT NULL,
    bureau_lender           VARCHAR(300),
    bank_detected_lender    VARCHAR(300),
    lender_identity_id      UUID REFERENCES ci_lender_identity (id),
    bureau_emi              NUMERIC(18, 2),
    bank_observed_emi       NUMERIC(18, 2),
    absolute_variance       NUMERIC(18, 2),
    percentage_variance     NUMERIC(12, 4),
    confidence              NUMERIC(8, 4),
    signals                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs           JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_obligation_match_app ON ci_obligation_match (application_id, match_status);

COMMENT ON TABLE ci_validation_run IS 'Phase C6 non-authoritative multi-source validation runs';
COMMENT ON TABLE ci_replay_manifest IS 'Phase C6 CreditDecisionReplayManifest — audit/regulator export metadata without raw payloads';
COMMENT ON TABLE ci_policy_cutover_readiness IS 'Phase C6 objective cutover readiness scoring';
