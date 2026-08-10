-- A1 Assistive AI Underwriter — suggestions only (never authoritative / never mutate canonical)

CREATE TABLE IF NOT EXISTS ci_ai_prompt_template (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code       VARCHAR(120) NOT NULL,
    version             VARCHAR(40) NOT NULL,
    content             TEXT NOT NULL,
    model_provider      VARCHAR(80),
    model_name          VARCHAR(120),
    temperature         NUMERIC(6, 4),
    settings            JSONB NOT NULL DEFAULT '{}'::jsonb,
    effective_from      TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_ai_prompt_template_code_ver UNIQUE (template_code, version)
);

CREATE INDEX IF NOT EXISTS idx_ci_ai_prompt_template_code ON ci_ai_prompt_template (template_code);

CREATE TABLE IF NOT EXISTS ci_ai_analysis_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID,
    evaluation_context_id   UUID,
    policy_evaluation_id    UUID,
    recommendation_id       UUID,
    idempotency_key         VARCHAR(128) NOT NULL,
    context_version         VARCHAR(40),
    requested_output_types  JSONB NOT NULL DEFAULT '[]'::jsonb,
    context_payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    prompt_versions         JSONB NOT NULL DEFAULT '{}'::jsonb,
    status                  VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    failure_code            VARCHAR(80),
    model_provider          VARCHAR(80),
    model_name              VARCHAR(120),
    model_version           VARCHAR(80),
    created_by              VARCHAR(120),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,
    CONSTRAINT uq_ci_ai_analysis_request_idem UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_ai_analysis_req_tenant ON ci_ai_analysis_request (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_analysis_req_app ON ci_ai_analysis_request (application_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_analysis_req_status ON ci_ai_analysis_request (status);

CREATE TABLE IF NOT EXISTS ci_ai_underwriting_suggestion (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID,
    analysis_request_id     UUID REFERENCES ci_ai_analysis_request (id),
    evaluation_context_id   UUID,
    policy_evaluation_id    UUID,
    recommendation_id       UUID,
    type                    VARCHAR(80) NOT NULL,
    title                   VARCHAR(400),
    content                 TEXT,
    structured_payload      JSONB NOT NULL DEFAULT '{}'::jsonb,
    confidence              NUMERIC(8, 4),
    model_confidence        NUMERIC(8, 4),
    grounding_coverage      NUMERIC(8, 4),
    evidence_completeness   NUMERIC(8, 4),
    limitations             JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_refs           JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_refs             JSONB NOT NULL DEFAULT '[]'::jsonb,
    model_metadata          JSONB NOT NULL DEFAULT '{}'::jsonb,
    prompt_version          VARCHAR(80),
    output_marker           VARCHAR(40) NOT NULL DEFAULT 'AI_SUGGESTION',
    authoritative           BOOLEAN NOT NULL DEFAULT FALSE,
    human_review_required   BOOLEAN NOT NULL DEFAULT TRUE,
    status                  VARCHAR(40) NOT NULL DEFAULT 'GENERATED',
    grounding_status        VARCHAR(40),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_ai_suggestion_tenant ON ci_ai_underwriting_suggestion (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_suggestion_app ON ci_ai_underwriting_suggestion (application_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_suggestion_req ON ci_ai_underwriting_suggestion (analysis_request_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_suggestion_status ON ci_ai_underwriting_suggestion (status);

CREATE TABLE IF NOT EXISTS ci_ai_review (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suggestion_id       UUID NOT NULL REFERENCES ci_ai_underwriting_suggestion (id) ON DELETE CASCADE,
    tenant_id           UUID NOT NULL,
    action              VARCHAR(40) NOT NULL,
    feedback_code       VARCHAR(40),
    edited_content      TEXT,
    reviewer            VARCHAR(120),
    reason              TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_ai_review_suggestion ON ci_ai_review (suggestion_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_review_tenant ON ci_ai_review (tenant_id);

CREATE TABLE IF NOT EXISTS ci_ai_scenario (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID,
    analysis_request_id     UUID REFERENCES ci_ai_analysis_request (id),
    suggestion_id           UUID REFERENCES ci_ai_underwriting_suggestion (id),
    request_payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
    deterministic_result    JSONB NOT NULL DEFAULT '{}'::jsonb,
    ai_explanation          TEXT,
    status                  VARCHAR(40) NOT NULL DEFAULT 'COMPUTED',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_ai_scenario_tenant ON ci_ai_scenario (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_ai_scenario_app ON ci_ai_scenario (application_id);

-- Seed prompt templates (versioned)
INSERT INTO ci_ai_prompt_template (id, template_code, version, content, model_provider, model_name, temperature, settings, effective_from)
VALUES
    (gen_random_uuid(), 'UNDERWRITING_SUMMARY_V1', '1',
     'Summarize the underwriting case using only supplied CreditEvidenceView, policy outcome, and recommendation. Mark every claim with evidence refs. Never invent metrics or approve/reject.',
     'stub', 'stub-grounded-v1', 0.0, '{"outputMarker":"AI_SUGGESTION","authoritative":false}'::jsonb, now()),
    (gen_random_uuid(), 'POLICY_EXPLANATION_V1', '1',
     'Explain policy/reconciliation outcomes using only deterministic rule IDs and evidence present in context. If unsupported, say so.',
     'stub', 'stub-grounded-v1', 0.0, '{"outputMarker":"AI_SUGGESTION","authoritative":false}'::jsonb, now()),
    (gen_random_uuid(), 'CAM_DRAFT_V1', '1',
     'Draft CAM narrative sections from CreditEvidenceView / PolicyEvaluation / CreditDecisionView. Non-authoritative; human review required. Do not mutate CAM fields.',
     'stub', 'stub-grounded-v1', 0.0, '{"outputMarker":"AI_SUGGESTION","authoritative":false}'::jsonb, now()),
    (gen_random_uuid(), 'INVESTIGATION_QUESTIONS_V1', '1',
     'Refine deterministic investigation questions. Preserve original discrepancy and evidence refs. Never suppress underlying issues.',
     'stub', 'stub-grounded-v1', 0.0, '{"outputMarker":"AI_SUGGESTION","authoritative":false}'::jsonb, now()),
    (gen_random_uuid(), 'ALTERNATE_STRUCTURE_V1', '1',
     'Suggest alternate structures comparing against canonical recommendation only. Never overwrite recommendation. Output ALTERNATE_STRUCTURE_SUGGESTION.',
     'stub', 'stub-grounded-v1', 0.0, '{"outputMarker":"AI_SUGGESTION","authoritative":false}'::jsonb, now())
ON CONFLICT (template_code, version) DO NOTHING;
