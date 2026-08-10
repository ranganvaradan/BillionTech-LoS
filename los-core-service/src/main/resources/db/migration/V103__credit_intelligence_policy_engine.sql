-- P1 Shadow Policy Engine — executable packages + evaluations (never production ACTIVE authority)

CREATE TABLE IF NOT EXISTS ci_executable_policy_package (
    id                                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                           UUID NOT NULL,
    lender_id                           UUID,
    product_code                        VARCHAR(80),
    policy_code                         VARCHAR(120) NOT NULL,
    version                             VARCHAR(40) NOT NULL,
    status                              VARCHAR(40) NOT NULL DEFAULT 'SHADOW',
    effective_from                      TIMESTAMPTZ,
    effective_to                        TIMESTAMPTZ,
    dsl_version                         VARCHAR(40) NOT NULL DEFAULT 'POLICY_DSL_V1',
    evaluation_semantics_version        VARCHAR(80) NOT NULL DEFAULT 'POLICY_DSL_EVALUATION_SEMANTICS_V1',
    orchestration_version               VARCHAR(40) NOT NULL DEFAULT 'ORCHESTRATION_V1',
    fact_registry_version               VARCHAR(40),
    metric_registry_version             VARCHAR(40),
    reconciliation_registry_version     VARCHAR(40),
    content                             JSONB NOT NULL,
    content_hash                        VARCHAR(128),
    test_suite_hash                     VARCHAR(128),
    source_document_refs                JSONB NOT NULL DEFAULT '[]'::jsonb,
    policy_studio_draft_package_id      UUID,
    approval_metadata                   JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                          VARCHAR(120),
    published_at                        TIMESTAMPTZ,
    published_by                        VARCHAR(120),
    CONSTRAINT uq_ci_exec_pkg_tenant_code_ver UNIQUE (tenant_id, policy_code, version)
);

CREATE INDEX IF NOT EXISTS idx_ci_exec_pkg_tenant ON ci_executable_policy_package (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_exec_pkg_status ON ci_executable_policy_package (status);

CREATE TABLE IF NOT EXISTS ci_policy_evaluation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_context_id   UUID,
    policy_package_id       UUID REFERENCES ci_executable_policy_package (id),
    policy_version          TEXT,
    status                  VARCHAR(40) NOT NULL DEFAULT 'COMPLETED',
    overall_outcome         VARCHAR(40),
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,
    deterministic_hash      VARCHAR(128),
    stage_count             INT NOT NULL DEFAULT 0,
    rule_count              INT NOT NULL DEFAULT 0,
    pass_count              INT NOT NULL DEFAULT 0,
    fail_count              INT NOT NULL DEFAULT 0,
    refer_count             INT NOT NULL DEFAULT 0,
    di_count                INT NOT NULL DEFAULT 0,
    evidence_refs           JSONB NOT NULL DEFAULT '[]'::jsonb,
    explanation             JSONB NOT NULL DEFAULT '{}'::jsonb,
    score_result            JSONB NOT NULL DEFAULT '{}'::jsonb,
    comparison_summary      JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_eval_ctx ON ci_policy_evaluation (evaluation_context_id);
CREATE INDEX IF NOT EXISTS idx_ci_policy_eval_pkg ON ci_policy_evaluation (policy_package_id);

CREATE TABLE IF NOT EXISTS ci_policy_stage_result (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id   UUID NOT NULL REFERENCES ci_policy_evaluation (id) ON DELETE CASCADE,
    stage_code      VARCHAR(80) NOT NULL,
    sequence        INT NOT NULL DEFAULT 0,
    outcome         VARCHAR(40),
    continue_flag   BOOLEAN NOT NULL DEFAULT TRUE,
    rule_count      INT NOT NULL DEFAULT 0,
    detail          JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_stage_eval ON ci_policy_stage_result (evaluation_id);

CREATE TABLE IF NOT EXISTS ci_score_result (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id           UUID NOT NULL REFERENCES ci_policy_evaluation (id) ON DELETE CASCADE,
    scorecard_code          VARCHAR(120),
    score                   NUMERIC(18, 6),
    grade                   VARCHAR(40),
    component_results       JSONB NOT NULL DEFAULT '[]'::jsonb,
    weight_used             NUMERIC(18, 6),
    weight_unavailable      NUMERIC(18, 6),
    data_completeness       NUMERIC(8, 4),
    reason_codes            JSONB NOT NULL DEFAULT '[]'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_score_result_eval ON ci_score_result (evaluation_id);

CREATE TABLE IF NOT EXISTS ci_policy_package_test_run (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id          UUID NOT NULL REFERENCES ci_executable_policy_package (id) ON DELETE CASCADE,
    test_suite_hash     VARCHAR(128),
    passed              BOOLEAN NOT NULL DEFAULT FALSE,
    results             JSONB NOT NULL DEFAULT '{}'::jsonb,
    ran_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ran_by              VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS ci_policy_historical_replay (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_package_id   UUID REFERENCES ci_executable_policy_package (id),
    context_ids         JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary             JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    created_by          VARCHAR(120)
);

CREATE TABLE IF NOT EXISTS ci_policy_package_diff (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    left_package_id     UUID NOT NULL REFERENCES ci_executable_policy_package (id),
    right_package_id    UUID NOT NULL REFERENCES ci_executable_policy_package (id),
    diff                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Extend rule results with P1 refs (V86 did not include these columns)
ALTER TABLE ci_standard_rule_result ADD COLUMN IF NOT EXISTS metric_refs JSONB DEFAULT '[]'::jsonb;
ALTER TABLE ci_standard_rule_result ADD COLUMN IF NOT EXISTS reconciliation_refs JSONB DEFAULT '[]'::jsonb;
ALTER TABLE ci_standard_rule_result ADD COLUMN IF NOT EXISTS evidence_refs JSONB DEFAULT '[]'::jsonb;
ALTER TABLE ci_standard_rule_result ADD COLUMN IF NOT EXISTS input_refs JSONB DEFAULT '[]'::jsonb;

COMMENT ON TABLE ci_executable_policy_package IS 'P1 shadow executable policy packages — ACTIVE status never production authority';
COMMENT ON TABLE ci_policy_evaluation IS 'P1 shadow policy evaluations — non-authoritative';
