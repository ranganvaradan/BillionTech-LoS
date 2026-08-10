-- KYC-5: durable shadow KYC Decision Policy evaluation (never authoritative)
CREATE TABLE IF NOT EXISTS ci_kyc_policy_evaluation (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID,
    application_id              UUID NOT NULL,
    evaluation_business_date    DATE,
    policy_version_id           UUID,
    executable_package_id       UUID,
    policy_code                 VARCHAR(120),
    policy_version              VARCHAR(40),
    policy_content_hash         VARCHAR(128),
    routing_outcome             VARCHAR(80),
    overall_outcome             VARCHAR(40) NOT NULL,
    production_kyc_outcome      VARCHAR(40),
    comparison_class            VARCHAR(80),
    review_required             BOOLEAN NOT NULL DEFAULT FALSE,
    review_reason               VARCHAR(200),
    deterministic_hash          VARCHAR(128) NOT NULL,
    frozen_facts                JSONB NOT NULL DEFAULT '{}'::jsonb,
    application_inputs          JSONB NOT NULL DEFAULT '{}'::jsonb,
    rule_results                JSONB NOT NULL DEFAULT '[]'::jsonb,
    comparison                  JSONB NOT NULL DEFAULT '{}'::jsonb,
    refer_payload               JSONB NOT NULL DEFAULT '{}'::jsonb,
    workflow_provenance         JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_refs               JSONB NOT NULL DEFAULT '[]'::jsonb,
    missing_facts               JSONB NOT NULL DEFAULT '[]'::jsonb,
    fail_reasons                JSONB NOT NULL DEFAULT '[]'::jsonb,
    refer_reasons               JSONB NOT NULL DEFAULT '[]'::jsonb,
    authoritative               BOOLEAN NOT NULL DEFAULT FALSE,
    shadow                      BOOLEAN NOT NULL DEFAULT TRUE,
    certification_status        VARCHAR(40) NOT NULL DEFAULT 'SHADOW_EVALUATION_READY',
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ci_kyc_pol_eval_app_created
    ON ci_kyc_policy_evaluation (application_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ci_kyc_pol_eval_hash
    ON ci_kyc_policy_evaluation (deterministic_hash);

COMMENT ON TABLE ci_kyc_policy_evaluation IS
    'KYC-5 shadow Decision Policy KYC evaluation — authoritative=false, does not affect production KYC';
