-- P2 Shadow Decision Engine — recommendations only (never authoritative / never auto human decision)

CREATE TABLE IF NOT EXISTS ci_decision_strategy (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    lender_id           UUID,
    product_code        VARCHAR(80),
    strategy_code       VARCHAR(120) NOT NULL,
    version             VARCHAR(40) NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'SHADOW',
    content             JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash        VARCHAR(128),
    effective_from      TIMESTAMPTZ,
    effective_to        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(120),
    published_at        TIMESTAMPTZ,
    published_by        VARCHAR(120),
    CONSTRAINT uq_ci_decision_strategy_tenant_code_ver UNIQUE (tenant_id, strategy_code, version)
);

CREATE INDEX IF NOT EXISTS idx_ci_decision_strategy_tenant ON ci_decision_strategy (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_decision_strategy_status ON ci_decision_strategy (status);

CREATE TABLE IF NOT EXISTS ci_decision_input (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    application_id                  UUID,
    evaluation_context_id           UUID,
    policy_evaluation_id            UUID,
    policy_package_id               UUID,
    fact_snapshot_id                UUID,
    metric_result_set_id            UUID,
    reconciliation_result_set_id    UUID,
    score_result_ids                JSONB NOT NULL DEFAULT '[]'::jsonb,
    credit_evidence_summary_id      UUID,
    lender_id                       UUID,
    product_code                    VARCHAR(80),
    decision_as_of                  DATE,
    decision_engine_version         VARCHAR(80),
    frozen_payload                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash                    VARCHAR(128),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_decision_input_tenant ON ci_decision_input (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_decision_input_app ON ci_decision_input (application_id);
CREATE INDEX IF NOT EXISTS idx_ci_decision_input_ctx ON ci_decision_input (evaluation_context_id);

CREATE TABLE IF NOT EXISTS ci_credit_recommendation (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    application_id                  UUID,
    decision_input_id               UUID REFERENCES ci_decision_input (id),
    decision_strategy_id            UUID REFERENCES ci_decision_strategy (id),
    recommendation_version          VARCHAR(40),
    recommendation_outcome          VARCHAR(40),
    recommended_facility_type       VARCHAR(80),
    recommended_amount              NUMERIC(18, 2),
    recommended_tenure_months       INT,
    recommended_repayment_frequency VARCHAR(40),
    recommended_emi                 NUMERIC(18, 2),
    recommended_base_rate           NUMERIC(12, 6),
    recommended_risk_premium        NUMERIC(12, 6),
    recommended_final_rate          NUMERIC(12, 6),
    recommended_processing_fee      NUMERIC(18, 2),
    recommended_collateral          JSONB NOT NULL DEFAULT '{}'::jsonb,
    recommended_ltv                 NUMERIC(12, 6),
    recommended_guarantors          JSONB NOT NULL DEFAULT '[]'::jsonb,
    conditions_precedent            JSONB NOT NULL DEFAULT '[]'::jsonb,
    conditions_subsequent           JSONB NOT NULL DEFAULT '[]'::jsonb,
    covenants                       JSONB NOT NULL DEFAULT '[]'::jsonb,
    approval_authority_level        VARCHAR(80),
    human_review_required           BOOLEAN NOT NULL DEFAULT TRUE,
    reason_codes                    JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_refs                   JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence                      NUMERIC(8, 4),
    limitations                     JSONB NOT NULL DEFAULT '[]'::jsonb,
    status                          VARCHAR(40) NOT NULL DEFAULT 'RECOMMENDED',
    authoritative                   BOOLEAN NOT NULL DEFAULT FALSE,
    deterministic_decision_hash     VARCHAR(128),
    dimensions                      JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_credit_rec_tenant ON ci_credit_recommendation (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_credit_rec_app ON ci_credit_recommendation (application_id);
CREATE INDEX IF NOT EXISTS idx_ci_credit_rec_input ON ci_credit_recommendation (decision_input_id);
CREATE INDEX IF NOT EXISTS idx_ci_credit_rec_strategy ON ci_credit_recommendation (decision_strategy_id);

CREATE TABLE IF NOT EXISTS ci_limit_method_result (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL REFERENCES ci_credit_recommendation (id) ON DELETE CASCADE,
    method_code         VARCHAR(80) NOT NULL,
    eligible_amount     NUMERIC(18, 2),
    inputs              JSONB NOT NULL DEFAULT '{}'::jsonb,
    formula_version     VARCHAR(80),
    data_status         VARCHAR(40),
    evidence_refs       JSONB NOT NULL DEFAULT '[]'::jsonb,
    detail              JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_limit_method_rec ON ci_limit_method_result (recommendation_id);

CREATE TABLE IF NOT EXISTS ci_pricing_component_result (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL REFERENCES ci_credit_recommendation (id) ON DELETE CASCADE,
    component_code      VARCHAR(80) NOT NULL,
    value_bps           NUMERIC(12, 4),
    basis               TEXT,
    rule_version        VARCHAR(80),
    reason_code         VARCHAR(120),
    evidence_refs       JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_pricing_comp_rec ON ci_pricing_component_result (recommendation_id);

CREATE TABLE IF NOT EXISTS ci_condition_recommendation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id       UUID NOT NULL REFERENCES ci_credit_recommendation (id) ON DELETE CASCADE,
    condition_type          VARCHAR(40) NOT NULL,
    code                    VARCHAR(120),
    description             TEXT,
    reason                  TEXT,
    source                  VARCHAR(120),
    causing_rule_or_recon   VARCHAR(200),
    mandatory_before        VARCHAR(80),
    evidence_refs           JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_condition_rec ON ci_condition_recommendation (recommendation_id);

CREATE TABLE IF NOT EXISTS ci_covenant_recommendation (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recommendation_id   UUID NOT NULL REFERENCES ci_credit_recommendation (id) ON DELETE CASCADE,
    covenant_code       VARCHAR(120),
    description         TEXT,
    threshold_json      JSONB NOT NULL DEFAULT '{}'::jsonb,
    reason              TEXT,
    evidence_refs       JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_covenant_rec ON ci_covenant_recommendation (recommendation_id);

CREATE TABLE IF NOT EXISTS ci_policy_deviation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    recommendation_id       UUID REFERENCES ci_credit_recommendation (id) ON DELETE CASCADE,
    policy_rule_id          VARCHAR(120),
    original_outcome        VARCHAR(40),
    requested_override      VARCHAR(120),
    reason                  TEXT,
    severity                VARCHAR(40),
    compensating_factors    JSONB NOT NULL DEFAULT '[]'::jsonb,
    requested_by            VARCHAR(120),
    approved_by             VARCHAR(120),
    authority_required      VARCHAR(80),
    status                  VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_dev_tenant ON ci_policy_deviation (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_policy_dev_rec ON ci_policy_deviation (recommendation_id);

CREATE TABLE IF NOT EXISTS ci_approval_authority_matrix (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    matrix_code     VARCHAR(120) NOT NULL,
    version         VARCHAR(40) NOT NULL,
    content         JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash    VARCHAR(128),
    status          VARCHAR(40) NOT NULL DEFAULT 'SHADOW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_auth_matrix_tenant ON ci_approval_authority_matrix (tenant_id);

CREATE TABLE IF NOT EXISTS ci_decision_historical_replay (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    strategy_id     UUID REFERENCES ci_decision_strategy (id),
    context_ids     JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary         JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    created_by      VARCHAR(120)
);

CREATE INDEX IF NOT EXISTS idx_ci_decision_hist_strategy ON ci_decision_historical_replay (strategy_id);

-- Future handoff table — P2 never writes automatically
CREATE TABLE IF NOT EXISTS ci_human_credit_decision (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    application_id      UUID,
    recommendation_id   UUID REFERENCES ci_credit_recommendation (id),
    human_decision      VARCHAR(40),
    final_amount        NUMERIC(18, 2),
    final_tenure        INT,
    final_rate          NUMERIC(12, 6),
    conditions          JSONB NOT NULL DEFAULT '[]'::jsonb,
    deviations          JSONB NOT NULL DEFAULT '[]'::jsonb,
    decision_maker      VARCHAR(120),
    authority           VARCHAR(80),
    decision_at         TIMESTAMPTZ,
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_human_decision_tenant ON ci_human_credit_decision (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_human_decision_app ON ci_human_credit_decision (application_id);
CREATE INDEX IF NOT EXISTS idx_ci_human_decision_rec ON ci_human_credit_decision (recommendation_id);

COMMENT ON TABLE ci_decision_strategy IS 'P2 shadow decision strategies — SHADOW only, never production authority';
COMMENT ON TABLE ci_credit_recommendation IS 'P2 shadow credit recommendations — authoritative=false always';
COMMENT ON TABLE ci_human_credit_decision IS 'Future human decision handoff — P2 never writes automatically';
COMMENT ON TABLE ci_policy_deviation IS 'P2 identified deviations — never auto APPROVED';
