-- Phase C5: Cross-source Reconciliation Engine tables

CREATE TABLE IF NOT EXISTS ci_reconciliation_definition (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_code         VARCHAR(80) NOT NULL,
    version                     VARCHAR(40) NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    description                 TEXT,
    category                    VARCHAR(40) NOT NULL,
    left_operand_definition     JSONB NOT NULL DEFAULT '{}'::jsonb,
    right_operand_definition    JSONB NOT NULL DEFAULT '{}'::jsonb,
    period_alignment_strategy   VARCHAR(80) NOT NULL,
    normalization_strategy      VARCHAR(80) NOT NULL DEFAULT 'NONE',
    variance_method             VARCHAR(80) NOT NULL,
    warning_tolerance           NUMERIC(12, 4),
    material_tolerance          NUMERIC(12, 4),
    allowed_data_statuses       JSONB NOT NULL DEFAULT '[]'::jsonb,
    minimum_completeness        NUMERIC(8, 4),
    minimum_confidence          NUMERIC(8, 4),
    missing_data_policy         VARCHAR(40) NOT NULL DEFAULT 'DATA_INSUFFICIENT',
    explanation_strategy_version VARCHAR(40) NOT NULL DEFAULT 'RECON_EXPLANATION_V1',
    dependency_metric_codes     JSONB NOT NULL DEFAULT '[]'::jsonb,
    effective_from              TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to                TIMESTAMPTZ,
    status                      VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_recon_def UNIQUE (reconciliation_code, version)
);

CREATE INDEX IF NOT EXISTS idx_ci_recon_def_status ON ci_reconciliation_definition (status, reconciliation_code);

CREATE TABLE IF NOT EXISTS ci_reconciliation_result (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    application_id                  UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id                UUID,
    evaluation_id                   UUID,
    reconciliation_definition_id    UUID REFERENCES ci_reconciliation_definition (id),
    reconciliation_code             VARCHAR(80) NOT NULL,
    definition_version              VARCHAR(40) NOT NULL,
    left_metric_code                VARCHAR(120),
    left_metric_version             VARCHAR(40),
    left_value                      NUMERIC(18, 2),
    left_period_from                DATE,
    left_period_to                  DATE,
    left_completeness               NUMERIC(8, 4),
    left_confidence                 NUMERIC(8, 4),
    left_source_refs                JSONB NOT NULL DEFAULT '[]'::jsonb,
    right_metric_code               VARCHAR(120),
    right_metric_version            VARCHAR(40),
    right_value                     NUMERIC(18, 2),
    right_period_from               DATE,
    right_period_to                 DATE,
    right_completeness              NUMERIC(8, 4),
    right_confidence                NUMERIC(8, 4),
    right_source_refs               JSONB NOT NULL DEFAULT '[]'::jsonb,
    normalized_left_value           NUMERIC(18, 2),
    normalized_right_value          NUMERIC(18, 2),
    absolute_variance               NUMERIC(18, 2),
    percentage_variance             NUMERIC(12, 4),
    variance_denominator_method     VARCHAR(80),
    outcome                         VARCHAR(40) NOT NULL,
    severity                        VARCHAR(40),
    explanation                     TEXT,
    explanation_codes               JSONB NOT NULL DEFAULT '[]'::jsonb,
    probable_causes                 JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_group_ids              JSONB NOT NULL DEFAULT '[]'::jsonb,
    human_review_required           BOOLEAN NOT NULL DEFAULT FALSE,
    data_status                     VARCHAR(40) NOT NULL DEFAULT 'COMPLETE',
    subject_match_status            VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    confidence                      NUMERIC(8, 4),
    tolerance_version               VARCHAR(80),
    executed_at                     TIMESTAMPTZ NOT NULL DEFAULT now(),
    execution_duration_ms           BIGINT,
    trace                           JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata                        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_recon_result_app
    ON ci_reconciliation_result (application_id, reconciliation_code, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_recon_result_eval
    ON ci_reconciliation_result (evaluation_id);
CREATE INDEX IF NOT EXISTS idx_ci_recon_result_snapshot
    ON ci_reconciliation_result (fact_snapshot_id);

CREATE TABLE IF NOT EXISTS ci_reconciliation_evidence (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    reconciliation_result_id    UUID NOT NULL REFERENCES ci_reconciliation_result (id) ON DELETE CASCADE,
    evidence_kind               VARCHAR(80) NOT NULL,
    evidence_group_id           UUID,
    summary                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    detail                      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_recon_evidence_result
    ON ci_reconciliation_evidence (reconciliation_result_id);

CREATE TABLE IF NOT EXISTS ci_credit_evidence_summary (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id            UUID,
    evaluation_id               UUID,
    turnover_evidence           JSONB NOT NULL DEFAULT '{}'::jsonb,
    obligation_evidence         JSONB NOT NULL DEFAULT '{}'::jsonb,
    income_evidence             JSONB NOT NULL DEFAULT '{}'::jsonb,
    tax_evidence                JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_quality              JSONB NOT NULL DEFAULT '{}'::jsonb,
    reconciliation_quality      JSONB NOT NULL DEFAULT '{}'::jsonb,
    material_conflicts          JSONB NOT NULL DEFAULT '[]'::jsonb,
    data_gaps                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_strength_score     NUMERIC(8, 2),
    evidence_strength_grade     VARCHAR(40),
    method_version              VARCHAR(40) NOT NULL DEFAULT 'CREDIT_EVIDENCE_SUMMARY_V1',
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_credit_ev_sum_app
    ON ci_credit_evidence_summary (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_credit_ev_sum_eval
    ON ci_credit_evidence_summary (evaluation_id);

COMMENT ON TABLE ci_reconciliation_definition IS 'Phase C5 versioned reconciliation definitions (immutable when ACTIVE)';
COMMENT ON TABLE ci_reconciliation_result IS 'Phase C5 append-only cross-source reconciliation results';
COMMENT ON TABLE ci_credit_evidence_summary IS 'Phase C5 deterministic credit evidence summary (not a credit risk score)';
