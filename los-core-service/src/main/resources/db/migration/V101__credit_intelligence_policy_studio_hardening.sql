-- P0.1 Policy Studio hardening — sessions, parameters, simulations, optimistic locking

CREATE TABLE IF NOT EXISTS ci_policy_authoring_session (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    policy_document_id              UUID NOT NULL REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    document_version                INT NOT NULL DEFAULT 1,
    product_scope                   VARCHAR(120),
    author                          VARCHAR(120),
    assigned_credit_manager         VARCHAR(120),
    assigned_checker                VARCHAR(120),
    status                          VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    started_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                    TIMESTAMPTZ,
    registry_version                VARCHAR(40) NOT NULL DEFAULT 'POLICY_AUTHORING_REGISTRY_V2',
    interpretation_provider_version VARCHAR(80),
    dsl_version                     VARCHAR(40) NOT NULL DEFAULT 'POLICY_DSL_V1',
    readiness_score                 NUMERIC(8, 2),
    readiness_grade                 VARCHAR(40),
    version                         BIGINT NOT NULL DEFAULT 0,
    metadata                        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_session_tenant
    ON ci_policy_authoring_session (tenant_id, last_updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_policy_session_doc
    ON ci_policy_authoring_session (policy_document_id);

CREATE TABLE IF NOT EXISTS ci_policy_parameter (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    product_scope           VARCHAR(120),
    code                    VARCHAR(120) NOT NULL,
    display_name            VARCHAR(200) NOT NULL,
    description             TEXT,
    value_type              VARCHAR(40) NOT NULL DEFAULT 'DECIMAL',
    unit                    VARCHAR(40) NOT NULL DEFAULT 'INR',
    source                  VARCHAR(80) NOT NULL DEFAULT 'POLICY',
    required                BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    version                 INT NOT NULL DEFAULT 1,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_param UNIQUE (tenant_id, code, product_scope, version)
);

CREATE TABLE IF NOT EXISTS ci_policy_simulation_run (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    session_id              UUID REFERENCES ci_policy_authoring_session (id) ON DELETE SET NULL,
    draft_package_id        UUID REFERENCES ci_policy_draft_package (id) ON DELETE SET NULL,
    simulation_label        VARCHAR(80) NOT NULL DEFAULT 'VALIDATION_FIXTURE_SIMULATION',
    dsl_version             VARCHAR(40) NOT NULL DEFAULT 'POLICY_DSL_V1',
    evaluation_semantics    VARCHAR(80) NOT NULL DEFAULT 'POLICY_DSL_EVALUATION_SEMANTICS_V1',
    summary                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    case_results            JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_sim_session ON ci_policy_simulation_run (session_id);

CREATE TABLE IF NOT EXISTS ci_policy_draft_diff (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    from_package_id         UUID NOT NULL REFERENCES ci_policy_draft_package (id) ON DELETE CASCADE,
    to_package_id           UUID NOT NULL REFERENCES ci_policy_draft_package (id) ON DELETE CASCADE,
    diff_json               JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Optimistic locking / review invalidation helpers on existing tables
ALTER TABLE ci_policy_draft_package
    ADD COLUMN IF NOT EXISTS session_id UUID,
    ADD COLUMN IF NOT EXISTS package_version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS dsl_version VARCHAR(40) DEFAULT 'POLICY_DSL_V1',
    ADD COLUMN IF NOT EXISTS dependency_graph_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS checker_approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS checker_approved_by VARCHAR(120),
    ADD COLUMN IF NOT EXISTS invalidated_by_edit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ci_policy_rule_candidate
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS unit_left VARCHAR(40),
    ADD COLUMN IF NOT EXISTS unit_right VARCHAR(40),
    ADD COLUMN IF NOT EXISTS period_semantics VARCHAR(80),
    ADD COLUMN IF NOT EXISTS validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE ci_policy_test_case
    ADD COLUMN IF NOT EXISTS ai_expected_outcome VARCHAR(40),
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE ci_policy_ambiguity
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS previous_resolution JSONB;

ALTER TABLE ci_policy_vocabulary
    ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS previous_version_id UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS previously_approved_note TEXT;

ALTER TABLE ci_policy_document
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

COMMENT ON TABLE ci_policy_authoring_session IS 'P0.1 persistent Policy Studio authoring session';
COMMENT ON TABLE ci_policy_parameter IS 'P0.1 POLICY_PARAMETER_REF definitions e.g. PROPOSED_EDI';
