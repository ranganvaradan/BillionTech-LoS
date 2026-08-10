-- PRODUCTIONISATION P2 — immutable package linkage + validation corpus + defects
-- Does NOT enable production authority.

ALTER TABLE ci_policy_applicability
    ADD COLUMN IF NOT EXISTS linkage_class VARCHAR(60) NOT NULL DEFAULT 'UNLINKED',
    ADD COLUMN IF NOT EXISTS shadow_eligibility VARCHAR(60) NOT NULL DEFAULT 'NOT_ELIGIBLE_FOR_SHADOW_ROUTING',
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS package_content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS shadow_routable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS eligibility_detail JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN ci_policy_applicability.linkage_class IS
    'PROPER_IMMUTABLE_PACKAGE_LINK | DEMO_ONLY_NOT_ROUTABLE | DEMO_ONLY_UNLINKED | INVALID | UNLINKED';
COMMENT ON COLUMN ci_policy_applicability.shadow_eligibility IS
    'ELIGIBLE_FOR_SHADOW_ROUTING | NOT_ELIGIBLE_FOR_SHADOW_ROUTING | DEMO_ONLY_NOT_ROUTABLE | POLICY_PACKAGE_NOT_EXECUTABLE';

-- Mark existing P1 smoke/demo rows as not routable until linked
UPDATE ci_policy_applicability
SET linkage_class = 'DEMO_ONLY_UNLINKED',
    shadow_eligibility = 'DEMO_ONLY_NOT_ROUTABLE',
    shadow_routable = FALSE,
    eligibility_detail = jsonb_build_object(
        'reason', 'P1 smoke entry lacked immutable policy_version_id / executable_package_id',
        'p2', true
    )
WHERE policy_version_id IS NULL
  AND executable_package_id IS NULL
  AND (linkage_class = 'UNLINKED' OR linkage_class IS NULL OR linkage_class = 'UNLINKED');

ALTER TABLE ci_policy_shadow_routing
    ADD COLUMN IF NOT EXISTS executable_package_id UUID,
    ADD COLUMN IF NOT EXISTS content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS evaluation_content_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS shadow_recommendation_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS linkage_outcome VARCHAR(60);

CREATE TABLE IF NOT EXISTS ci_p2_validation_run (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    product_code                VARCHAR(80),
    applicability_id            UUID REFERENCES ci_policy_applicability (id),
    started_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at                TIMESTAMPTZ,
    status                      VARCHAR(40) NOT NULL DEFAULT 'RUNNING',
    certification_status        VARCHAR(40) NOT NULL DEFAULT 'INSUFFICIENT_EVIDENCE',
    summary                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    allow_canonical_authority   BOOLEAN NOT NULL DEFAULT FALSE,
    shadow_only                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_by                  VARCHAR(120),
    CONSTRAINT chk_ci_p2_run_authority CHECK (allow_canonical_authority = FALSE),
    CONSTRAINT chk_ci_p2_run_shadow CHECK (shadow_only = TRUE)
);

CREATE TABLE IF NOT EXISTS ci_p2_validation_case (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id                      UUID NOT NULL REFERENCES ci_p2_validation_run (id) ON DELETE CASCADE,
    application_id              UUID,
    application_token           VARCHAR(120),
    product_code                VARCHAR(80),
    origin_classification       VARCHAR(60) NOT NULL,
    evaluation_business_date    DATE,
    resolver_outcome            VARCHAR(60),
    applicability_id            UUID,
    policy_version_id           UUID,
    executable_package_id       UUID,
    content_hash                VARCHAR(128),
    evaluation_context_id       UUID,
    evaluation_content_hash     VARCHAR(128),
    shadow_recommendation_hash  VARCHAR(128),
    legacy_outcome              VARCHAR(80),
    shadow_outcome              VARCHAR(80),
    comparison_class            VARCHAR(60),
    replay_pass                 BOOLEAN,
    routing_success             BOOLEAN,
    package_exact               BOOLEAN,
    underwriting_hook_used      BOOLEAN NOT NULL DEFAULT FALSE,
    evidence                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_p2_case_run ON ci_p2_validation_case (run_id);
CREATE INDEX IF NOT EXISTS idx_ci_p2_case_origin ON ci_p2_validation_case (origin_classification);

CREATE TABLE IF NOT EXISTS ci_p2_validation_defect (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id              UUID REFERENCES ci_p2_validation_run (id) ON DELETE CASCADE,
    application_id      UUID,
    application_token   VARCHAR(120),
    severity            VARCHAR(40) NOT NULL,
    component           VARCHAR(80) NOT NULL,
    defect_type         VARCHAR(80) NOT NULL,
    root_cause          TEXT,
    recommended_action  TEXT,
    blocking            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    evidence            JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_p2_defect_run ON ci_p2_validation_defect (run_id);
CREATE INDEX IF NOT EXISTS idx_ci_p2_defect_blocking ON ci_p2_validation_defect (blocking) WHERE blocking = TRUE;
