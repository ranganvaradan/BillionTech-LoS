-- Phase C5.1: Evaluation purity — EvaluationContext, ConfigFreeze, Metric/Recon result sets, Provider observations

CREATE TABLE IF NOT EXISTS ci_config_freeze (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    config_version      VARCHAR(80) NOT NULL,
    content             JSONB NOT NULL,
    content_hash        VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(40) NOT NULL DEFAULT 'CONFIG_FREEZE_V1',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_config_freeze_hash UNIQUE (tenant_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_ci_config_freeze_tenant ON ci_config_freeze (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_metric_result_set (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    application_id      UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id    UUID,
    metric_result_ids   JSONB NOT NULL DEFAULT '[]'::jsonb,
    metric_codes        JSONB NOT NULL DEFAULT '[]'::jsonb,
    content_hash        VARCHAR(128) NOT NULL,
    schema_version      VARCHAR(40) NOT NULL DEFAULT 'METRIC_RESULT_SET_V1',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_metric_result_set_app
    ON ci_metric_result_set (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_metric_result_set_snapshot
    ON ci_metric_result_set (fact_snapshot_id);

CREATE TABLE IF NOT EXISTS ci_reconciliation_result_set (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id            UUID,
    reconciliation_result_ids   JSONB NOT NULL DEFAULT '[]'::jsonb,
    reconciliation_codes        JSONB NOT NULL DEFAULT '[]'::jsonb,
    definition_versions         JSONB NOT NULL DEFAULT '{}'::jsonb,
    metric_result_set_id        UUID REFERENCES ci_metric_result_set (id),
    content_hash                VARCHAR(128) NOT NULL,
    schema_version              VARCHAR(40) NOT NULL DEFAULT 'RECON_RESULT_SET_V1',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_recon_result_set_app
    ON ci_reconciliation_result_set (application_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_evaluation_context (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    application_id                  UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id                UUID NOT NULL,
    policy_version_id               UUID,
    config_freeze_id                UUID NOT NULL REFERENCES ci_config_freeze (id),
    evaluation_as_of                DATE NOT NULL,
    clock_instant                   TIMESTAMPTZ NOT NULL,
    clock_zone                      VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata',
    metric_result_set_id            UUID REFERENCES ci_metric_result_set (id),
    reconciliation_result_set_id    UUID REFERENCES ci_reconciliation_result_set (id),
    schema_version                  VARCHAR(40) NOT NULL DEFAULT 'EVALUATION_CONTEXT_V1',
    content_hash                    VARCHAR(128) NOT NULL,
    deterministic_evaluation_hash   VARCHAR(128),
    metadata                        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_eval_ctx_hash UNIQUE (tenant_id, application_id, content_hash)
);

CREATE INDEX IF NOT EXISTS idx_ci_eval_ctx_app ON ci_evaluation_context (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_eval_ctx_snapshot ON ci_evaluation_context (fact_snapshot_id);

CREATE TABLE IF NOT EXISTS ci_provider_observation (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    source_record_id            UUID REFERENCES ci_source_record (id),
    provider                    VARCHAR(80) NOT NULL,
    observation_code            VARCHAR(120) NOT NULL,
    value                       JSONB,
    value_type                  VARCHAR(40),
    provider_analysis_version   VARCHAR(80),
    confidence                  NUMERIC(8, 4),
    period_from                 DATE,
    period_to                   DATE,
    evidence_refs               JSONB NOT NULL DEFAULT '[]'::jsonb,
    authoritative               BOOLEAN NOT NULL DEFAULT FALSE,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_provider_obs_app
    ON ci_provider_observation (application_id, provider, observation_code);

-- Shared evidence group already exists as ci_evidence_group (V92). Add nullable linkage helpers.
ALTER TABLE ci_evidence_group
    ADD COLUMN IF NOT EXISTS subject_entity_id UUID,
    ADD COLUMN IF NOT EXISTS source_family VARCHAR(40),
    ADD COLUMN IF NOT EXISTS evidence_type VARCHAR(80);

COMMENT ON TABLE ci_evaluation_context IS 'Phase C5.1 immutable evaluation inputs — sole contract for canonical shadow/replay';
COMMENT ON TABLE ci_config_freeze IS 'Phase C5.1 frozen CI thresholds — never read runtime YAML during replay';
COMMENT ON TABLE ci_provider_observation IS 'Phase C5.1 non-authoritative provider analysis (authoritative=false)';

-- Banking scale: composite indexes for hot-path lookups (partition plan documented separately).
-- application_id lives on ci_bank_account (not ci_bank_transaction) — index via account / tenant.
CREATE INDEX IF NOT EXISTS idx_ci_bank_acct_app_id
    ON ci_bank_account (application_id, id);
CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_tenant_date
    ON ci_bank_transaction (tenant_id, transaction_date DESC);
CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_account_date
    ON ci_bank_transaction (bank_account_id, transaction_date DESC);

-- Metric result set scoping helper index
CREATE INDEX IF NOT EXISTS idx_ci_metric_result_snapshot_code
    ON ci_metric_result (fact_snapshot_id, metric_code)
    WHERE fact_snapshot_id IS NOT NULL;
