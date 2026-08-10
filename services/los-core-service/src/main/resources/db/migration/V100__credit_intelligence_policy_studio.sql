-- P0 AI Policy Studio — authoring tables (non-authoritative drafts only)

CREATE TABLE IF NOT EXISTS ci_policy_document (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    lender_id               UUID,
    product_scope           VARCHAR(120),
    name                    VARCHAR(300) NOT NULL,
    document_type           VARCHAR(40) NOT NULL DEFAULT 'TXT',
    original_file_reference VARCHAR(500),
    content_hash            VARCHAR(128) NOT NULL,
    uploaded_by             VARCHAR(120),
    uploaded_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    status                  VARCHAR(40) NOT NULL DEFAULT 'UPLOADED',
    document_version        INT NOT NULL DEFAULT 1,
    language                VARCHAR(20) NOT NULL DEFAULT 'en',
    source_text             TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_doc_hash UNIQUE (tenant_id, content_hash, document_version)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_doc_tenant ON ci_policy_document (tenant_id, uploaded_at DESC);

CREATE TABLE IF NOT EXISTS ci_policy_clause (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_document_id      UUID NOT NULL REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    clause_number           VARCHAR(40),
    parent_clause_id        UUID,
    section                 VARCHAR(200),
    page                    INT,
    source_text             TEXT NOT NULL,
    normalized_text         TEXT,
    clause_type             VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    product_scope           VARCHAR(120),
    effective_scope         JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_location         VARCHAR(200),
    extraction_confidence   NUMERIC(8, 4),
    status                  VARCHAR(40) NOT NULL DEFAULT 'EXTRACTED',
    sort_order              INT NOT NULL DEFAULT 0,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_clause_doc ON ci_policy_clause (policy_document_id, sort_order);

CREATE TABLE IF NOT EXISTS ci_policy_interpretation (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id                   UUID NOT NULL REFERENCES ci_policy_clause (id) ON DELETE CASCADE,
    interpretation_version      INT NOT NULL DEFAULT 1,
    interpreted_clause_type     VARCHAR(40),
    natural_language_meaning    TEXT,
    candidate_expression        JSONB NOT NULL DEFAULT '{}'::jsonb,
    candidate_inputs            JSONB NOT NULL DEFAULT '[]'::jsonb,
    candidate_outputs           JSONB NOT NULL DEFAULT '[]'::jsonb,
    candidate_product_scope     JSONB NOT NULL DEFAULT '[]'::jsonb,
    candidate_period            VARCHAR(80),
    candidate_thresholds        JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence                  NUMERIC(8, 4),
    confidence_breakdown        JSONB NOT NULL DEFAULT '{}'::jsonb,
    limitations                 TEXT,
    ai_model                    VARCHAR(120),
    ai_model_version            VARCHAR(80),
    prompt_version              VARCHAR(80),
    provider_code               VARCHAR(80) NOT NULL DEFAULT 'DETERMINISTIC_GOLDEN_V1',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_interp_clause ON ci_policy_interpretation (clause_id, interpretation_version DESC);

CREATE TABLE IF NOT EXISTS ci_policy_mapping_candidate (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interpretation_id       UUID NOT NULL REFERENCES ci_policy_interpretation (id) ON DELETE CASCADE,
    source_phrase           VARCHAR(500) NOT NULL,
    candidate_type          VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    canonical_path          VARCHAR(300),
    canonical_object_type   VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    confidence              NUMERIC(8, 4),
    match_basis             VARCHAR(80),
    rank                    INT NOT NULL DEFAULT 1,
    selected                BOOLEAN NOT NULL DEFAULT FALSE,
    selection_source        VARCHAR(40),
    selected_by             VARCHAR(120),
    selected_at             TIMESTAMPTZ,
    review_notes            TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_map_interp ON ci_policy_mapping_candidate (interpretation_id, rank);

CREATE TABLE IF NOT EXISTS ci_policy_ambiguity (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id               UUID NOT NULL REFERENCES ci_policy_clause (id) ON DELETE CASCADE,
    ambiguity_type          VARCHAR(60) NOT NULL,
    phrase                  VARCHAR(500),
    description             TEXT NOT NULL,
    candidate_options       JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommended_option      VARCHAR(500),
    confidence              NUMERIC(8, 4),
    severity                VARCHAR(40) NOT NULL DEFAULT 'MATERIAL',
    resolution_status       VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    resolved_option         VARCHAR(500),
    resolved_by             VARCHAR(120),
    resolved_at             TIMESTAMPTZ,
    resolution_notes        TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_amb_clause ON ci_policy_ambiguity (clause_id, resolution_status);

CREATE TABLE IF NOT EXISTS ci_policy_vocabulary (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID,
    scope_level             VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',
    product_code            VARCHAR(80),
    term                    VARCHAR(200) NOT NULL,
    canonical_meaning       TEXT,
    canonical_path          VARCHAR(300),
    object_type             VARCHAR(40),
    synonyms                JSONB NOT NULL DEFAULT '[]'::jsonb,
    context                 VARCHAR(200),
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to            TIMESTAMPTZ,
    approved_by             VARCHAR(120),
    status                  VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_vocab_term ON ci_policy_vocabulary (lower(term), scope_level);

CREATE TABLE IF NOT EXISTS ci_policy_metric_candidate (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id               UUID NOT NULL REFERENCES ci_policy_clause (id) ON DELETE CASCADE,
    metric_name             VARCHAR(200) NOT NULL,
    base_metric             VARCHAR(200),
    expression              JSONB NOT NULL DEFAULT '{}'::jsonb,
    exclusions              JSONB NOT NULL DEFAULT '[]'::jsonb,
    inclusions              JSONB NOT NULL DEFAULT '[]'::jsonb,
    period                  VARCHAR(80),
    aggregation             VARCHAR(80),
    dependencies            JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_data_policy     VARCHAR(40) NOT NULL DEFAULT 'DATA_INSUFFICIENT',
    candidate_canonical_code VARCHAR(200),
    confidence              NUMERIC(8, 4),
    review_status           VARCHAR(40) NOT NULL DEFAULT 'AI_DRAFTED',
    system_metric_id        VARCHAR(120),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS ci_policy_rule_candidate (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id               UUID NOT NULL REFERENCES ci_policy_clause (id) ON DELETE CASCADE,
    system_rule_id          VARCHAR(120) NOT NULL,
    rule_version            VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    rule_type               VARCHAR(40) NOT NULL DEFAULT 'HARD',
    scope                   JSONB NOT NULL DEFAULT '{}'::jsonb,
    expression              JSONB NOT NULL DEFAULT '{}'::jsonb,
    on_true                 VARCHAR(40) NOT NULL DEFAULT 'FAIL',
    on_false                VARCHAR(40) NOT NULL DEFAULT 'PASS',
    on_missing              VARCHAR(40) NOT NULL DEFAULT 'DATA_INSUFFICIENT',
    confidence              NUMERIC(8, 4),
    review_status           VARCHAR(40) NOT NULL DEFAULT 'AI_DRAFTED',
    lineage                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_policy_rule_sys UNIQUE (system_rule_id, rule_version)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_rule_clause ON ci_policy_rule_candidate (clause_id);

CREATE TABLE IF NOT EXISTS ci_policy_test_case (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    clause_id               UUID REFERENCES ci_policy_clause (id) ON DELETE SET NULL,
    rule_candidate_id       UUID REFERENCES ci_policy_rule_candidate (id) ON DELETE CASCADE,
    name                    VARCHAR(300) NOT NULL,
    input_facts             JSONB NOT NULL DEFAULT '{}'::jsonb,
    input_metrics           JSONB NOT NULL DEFAULT '{}'::jsonb,
    expected_outcome        VARCHAR(40) NOT NULL,
    boundary_case           BOOLEAN NOT NULL DEFAULT FALSE,
    generated_by            VARCHAR(80) NOT NULL DEFAULT 'SYSTEM',
    generation_confidence   NUMERIC(8, 4),
    review_status           VARCHAR(40) NOT NULL DEFAULT 'AI_DRAFTED',
    reviewed_by             VARCHAR(120),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_test_rule ON ci_policy_test_case (rule_candidate_id);

CREATE TABLE IF NOT EXISTS ci_policy_review (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    policy_document_id      UUID NOT NULL REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    subject_type            VARCHAR(40) NOT NULL,
    subject_id              UUID NOT NULL,
    review_state            VARCHAR(40) NOT NULL,
    original_proposal       JSONB NOT NULL DEFAULT '{}'::jsonb,
    human_changes           JSONB NOT NULL DEFAULT '{}'::jsonb,
    reviewer                VARCHAR(120) NOT NULL,
    reviewer_role           VARCHAR(40),
    reason                  TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_review_doc ON ci_policy_review (policy_document_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_policy_draft_package (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    policy_document_id      UUID NOT NULL REFERENCES ci_policy_document (id) ON DELETE CASCADE,
    package_status          VARCHAR(40) NOT NULL DEFAULT 'DRAFT_ONLY',
    content                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    content_hash            VARCHAR(128),
    completeness_status     VARCHAR(40),
    completeness_summary    JSONB NOT NULL DEFAULT '{}'::jsonb,
    approval_history        JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by              VARCHAR(120),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_draft_doc ON ci_policy_draft_package (policy_document_id, created_at DESC);

COMMENT ON TABLE ci_policy_document IS 'P0 Policy Studio — immutable uploaded policy documents';
COMMENT ON TABLE ci_policy_draft_package IS 'P0 draft-only packages — never production-active';
