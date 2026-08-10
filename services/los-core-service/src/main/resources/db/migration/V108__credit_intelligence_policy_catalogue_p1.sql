-- PRODUCTIONISATION P1 — durable policy applicability catalogue + lifecycle events + shadow routing
-- Does NOT enable production authority. Reuses existing policy packages/versions via FKs.

CREATE TABLE IF NOT EXISTS ci_policy_applicability (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                       UUID NOT NULL,
    policy_document_id              UUID REFERENCES ci_policy_document (id),
    draft_package_id                UUID REFERENCES ci_policy_draft_package (id),
    executable_package_id           UUID REFERENCES ci_executable_policy_package (id),
    policy_version_id               UUID REFERENCES ci_policy_version (id),
    policy_name                     VARCHAR(300) NOT NULL,
    policy_version_label            VARCHAR(40) NOT NULL,
    policy_type                     VARCHAR(80) NOT NULL DEFAULT 'CREDIT_POLICY',
    business_status                 VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    products                        JSONB NOT NULL DEFAULT '[]'::jsonb,
    facility_type                   VARCHAR(80),
    customer_segment                VARCHAR(120),
    borrower_type                   VARCHAR(80),
    secured_unsecured               VARCHAR(40),
    program_scheme                  VARCHAR(120),
    min_loan_amount                 NUMERIC(18, 2),
    max_loan_amount                 NUMERIC(18, 2),
    effective_from                  DATE,
    effective_until                 DATE,
    replaces_version                VARCHAR(40),
    replaces_applicability_id       UUID REFERENCES ci_policy_applicability (id),
    reason_for_change               TEXT,
    approved_by                     VARCHAR(120),
    checker                         VARCHAR(120),
    created_by                      VARCHAR(120),
    data_readiness_status           VARCHAR(40),
    tests_status                    VARCHAR(40),
    simulation_review_status        VARCHAR(40),
    content_immutable               BOOLEAN NOT NULL DEFAULT FALSE,
    production_authority_enabled    BOOLEAN NOT NULL DEFAULT FALSE,
    metadata                        JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_policy_app_dates CHECK (
        effective_until IS NULL OR effective_from IS NULL OR effective_until >= effective_from
    ),
    CONSTRAINT chk_ci_policy_app_authority CHECK (production_authority_enabled = FALSE)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_app_tenant ON ci_policy_applicability (tenant_id);
CREATE INDEX IF NOT EXISTS idx_ci_policy_app_status ON ci_policy_applicability (tenant_id, business_status);
CREATE INDEX IF NOT EXISTS idx_ci_policy_app_effective ON ci_policy_applicability (tenant_id, effective_from, effective_until);
CREATE INDEX IF NOT EXISTS idx_ci_policy_app_document ON ci_policy_applicability (policy_document_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_policy_app_doc_version
    ON ci_policy_applicability (tenant_id, policy_document_id, policy_version_label)
    WHERE policy_document_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ci_policy_lifecycle_event (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    applicability_id        UUID NOT NULL REFERENCES ci_policy_applicability (id) ON DELETE CASCADE,
    policy_document_id      UUID,
    event_type              VARCHAR(80) NOT NULL,
    previous_status         VARCHAR(40),
    new_status              VARCHAR(40),
    actor                   VARCHAR(120),
    reason                  TEXT,
    payload                 JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_life_app ON ci_policy_lifecycle_event (applicability_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_policy_life_tenant ON ci_policy_lifecycle_event (tenant_id, created_at DESC);

CREATE TABLE IF NOT EXISTS ci_policy_shadow_routing (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL,
    evaluation_context_id       UUID,
    evaluation_business_date    DATE NOT NULL,
    product_code                VARCHAR(80),
    requested_amount            NUMERIC(18, 2),
    borrower_type               VARCHAR(80),
    intake_segment              VARCHAR(80),
    resolver_outcome            VARCHAR(60) NOT NULL,
    selected_applicability_id   UUID REFERENCES ci_policy_applicability (id),
    selected_policy_version_id  UUID,
    policy_name                 VARCHAR(300),
    policy_version_label        VARCHAR(40),
    applicability_reason        TEXT,
    evidence                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    shadow_only                 BOOLEAN NOT NULL DEFAULT TRUE,
    shadow_evaluation_status    VARCHAR(40),
    shadow_evaluation_error     TEXT,
    routing_latency_ms          BIGINT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ci_shadow_routing_shadow CHECK (shadow_only = TRUE)
);

CREATE INDEX IF NOT EXISTS idx_ci_shadow_route_app ON ci_policy_shadow_routing (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_shadow_route_tenant ON ci_policy_shadow_routing (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_shadow_route_outcome ON ci_policy_shadow_routing (resolver_outcome);
