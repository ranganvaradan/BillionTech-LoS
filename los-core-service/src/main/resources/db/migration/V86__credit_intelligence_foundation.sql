-- Phase F: Credit Intelligence Foundation (source registry, fact snapshots, policy versions, shadow evaluations)
-- Non-destructive; does not alter existing underwriting tables.

CREATE TABLE IF NOT EXISTS ci_source_record (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    application_id      UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    entity_id           UUID,
    source_type         VARCHAR(64) NOT NULL,
    provider_code       VARCHAR(64) NOT NULL,
    provider_reference  VARCHAR(200),
    purpose             VARCHAR(100) NOT NULL DEFAULT 'UNDERWRITING',
    consent_id          UUID,
    status              VARCHAR(40) NOT NULL DEFAULT 'CAPTURED',
    quality_score       NUMERIC(8, 4),
    as_of               TIMESTAMPTZ,
    stale_after         TIMESTAMPTZ,
    checksum            VARCHAR(128),
    idempotency_key     VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(64),
    metadata            JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_source_idempotency
    ON ci_source_record (tenant_id, application_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ci_source_app ON ci_source_record (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_source_tenant ON ci_source_record (tenant_id, application_id);

CREATE TABLE IF NOT EXISTS ci_source_artifact (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_record_id    UUID NOT NULL REFERENCES ci_source_record (id) ON DELETE CASCADE,
    content_reference   VARCHAR(500) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    checksum            VARCHAR(128) NOT NULL,
    immutable           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_artifact_source ON ci_source_artifact (source_record_id);

CREATE TABLE IF NOT EXISTS ci_fact_definition (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    canonical_path          VARCHAR(200) NOT NULL,
    version                 INT NOT NULL DEFAULT 1,
    description             TEXT,
    value_type              VARCHAR(40) NOT NULL,
    domain                  VARCHAR(64) NOT NULL,
    repeatable              BOOLEAN NOT NULL DEFAULT FALSE,
    sensitive               BOOLEAN NOT NULL DEFAULT FALSE,
    allowed_classifications JSONB NOT NULL DEFAULT '[]'::jsonb,
    status                  VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_fact_def_path_ver UNIQUE (canonical_path, version)
);

CREATE TABLE IF NOT EXISTS ci_fact_snapshot (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    snapshot_version        INT NOT NULL,
    status                  VARCHAR(40) NOT NULL,
    created_reason          VARCHAR(100) NOT NULL,
    previous_snapshot_id    UUID REFERENCES ci_fact_snapshot (id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(64),
    schema_version          VARCHAR(20) NOT NULL DEFAULT 'F1',
    facts_hash              VARCHAR(128),
    source_context_hash     VARCHAR(128),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT uq_ci_fact_snapshot_app_ver UNIQUE (application_id, snapshot_version)
);

CREATE INDEX IF NOT EXISTS idx_ci_fact_snapshot_app ON ci_fact_snapshot (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_fact_snapshot_tenant ON ci_fact_snapshot (tenant_id, application_id);

CREATE TABLE IF NOT EXISTS ci_underwriting_fact (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    snapshot_id             UUID NOT NULL REFERENCES ci_fact_snapshot (id) ON DELETE CASCADE,
    canonical_path          VARCHAR(200) NOT NULL,
    subject_entity_id       UUID,
    value_type              VARCHAR(40) NOT NULL,
    value                   JSONB NOT NULL,
    period_start            TIMESTAMPTZ,
    period_end              TIMESTAMPTZ,
    as_of                   TIMESTAMPTZ,
    classification          VARCHAR(40) NOT NULL,
    confidence              NUMERIC(8, 4),
    quality_status          VARCHAR(40) NOT NULL DEFAULT 'OK',
    source_record_ids       JSONB NOT NULL DEFAULT '[]'::jsonb,
    derivation_reference    VARCHAR(200),
    normalizer_version      VARCHAR(40) NOT NULL DEFAULT 'F1',
    supersedes_fact_id      UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_uw_fact_snapshot ON ci_underwriting_fact (snapshot_id);
CREATE INDEX IF NOT EXISTS idx_ci_uw_fact_path ON ci_underwriting_fact (snapshot_id, canonical_path);

-- Prevent mutation of facts belonging to FROZEN snapshots
CREATE OR REPLACE FUNCTION ci_prevent_frozen_fact_mutation()
RETURNS TRIGGER AS $$
DECLARE
    snap_status TEXT;
BEGIN
    SELECT status INTO snap_status FROM ci_fact_snapshot WHERE id = COALESCE(NEW.snapshot_id, OLD.snapshot_id);
    IF snap_status = 'FROZEN' THEN
        RAISE EXCEPTION 'Cannot modify facts on FROZEN snapshot %', COALESCE(NEW.snapshot_id, OLD.snapshot_id);
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ci_uw_fact_immutable ON ci_underwriting_fact;
CREATE TRIGGER trg_ci_uw_fact_immutable
    BEFORE UPDATE OR DELETE ON ci_underwriting_fact
    FOR EACH ROW EXECUTE FUNCTION ci_prevent_frozen_fact_mutation();

CREATE OR REPLACE FUNCTION ci_prevent_frozen_snapshot_update()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'FROZEN' AND (
        NEW.facts_hash IS DISTINCT FROM OLD.facts_hash
        OR NEW.source_context_hash IS DISTINCT FROM OLD.source_context_hash
        OR NEW.snapshot_version IS DISTINCT FROM OLD.snapshot_version
        OR NEW.application_id IS DISTINCT FROM OLD.application_id
    ) THEN
        RAISE EXCEPTION 'Cannot mutate FROZEN snapshot %', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ci_fact_snapshot_immutable ON ci_fact_snapshot;
CREATE TRIGGER trg_ci_fact_snapshot_immutable
    BEFORE UPDATE ON ci_fact_snapshot
    FOR EACH ROW EXECUTE FUNCTION ci_prevent_frozen_snapshot_update();

CREATE TABLE IF NOT EXISTS ci_policy_package (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    product_code        VARCHAR(80) NOT NULL,
    lender_id           UUID,
    policy_identifier   VARCHAR(120) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    status              VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(64),
    CONSTRAINT uq_ci_policy_package UNIQUE (tenant_id, product_code, policy_identifier)
);

CREATE TABLE IF NOT EXISTS ci_policy_version (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    policy_package_id       UUID NOT NULL REFERENCES ci_policy_package (id) ON DELETE CASCADE,
    version                 INT NOT NULL,
    effective_from          TIMESTAMPTZ NOT NULL DEFAULT now(),
    effective_to            TIMESTAMPTZ,
    status                  VARCHAR(40) NOT NULL,
    policy_content          JSONB NOT NULL,
    content_hash            VARCHAR(128) NOT NULL,
    source_policy_references JSONB NOT NULL DEFAULT '[]'::jsonb,
    orchestration_version   VARCHAR(80) NOT NULL DEFAULT 'LEGACY_UNDERWRITE_APPLICATION_V1',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by              VARCHAR(64),
    published_at            TIMESTAMPTZ,
    published_by            VARCHAR(64),
    CONSTRAINT uq_ci_policy_version UNIQUE (policy_package_id, version)
);

CREATE INDEX IF NOT EXISTS idx_ci_policy_version_hash ON ci_policy_version (content_hash);
CREATE INDEX IF NOT EXISTS idx_ci_policy_version_pkg ON ci_policy_version (policy_package_id, version DESC);

CREATE OR REPLACE FUNCTION ci_prevent_published_policy_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status IN ('PUBLISHED', 'ACTIVE', 'SCHEDULED', 'SUPERSEDED') THEN
        IF NEW.policy_content IS DISTINCT FROM OLD.policy_content
           OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
           OR NEW.version IS DISTINCT FROM OLD.version THEN
            RAISE EXCEPTION 'Cannot mutate published/immutable policy version %', OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ci_policy_version_immutable ON ci_policy_version;
CREATE TRIGGER trg_ci_policy_version_immutable
    BEFORE UPDATE ON ci_policy_version
    FOR EACH ROW EXECUTE FUNCTION ci_prevent_published_policy_mutation();

CREATE TABLE IF NOT EXISTS ci_credit_evaluation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    fact_snapshot_id        UUID NOT NULL REFERENCES ci_fact_snapshot (id),
    policy_version_id       UUID NOT NULL REFERENCES ci_policy_version (id),
    evaluation_type         VARCHAR(40) NOT NULL,
    status                  VARCHAR(40) NOT NULL,
    authoritative           BOOLEAN NOT NULL DEFAULT FALSE,
    production_evaluation_id UUID,
    orchestration_version   VARCHAR(80) NOT NULL,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at            TIMESTAMPTZ,
    overall_outcome         VARCHAR(40),
    comparison_status       VARCHAR(40),
    error_code              VARCHAR(80),
    error_details           JSONB,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_credit_eval_app ON ci_credit_evaluation (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_credit_eval_snap ON ci_credit_evaluation (fact_snapshot_id);
CREATE INDEX IF NOT EXISTS idx_ci_credit_eval_type ON ci_credit_evaluation (evaluation_type, application_id);

CREATE TABLE IF NOT EXISTS ci_evaluation_stage (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id   UUID NOT NULL REFERENCES ci_credit_evaluation (id) ON DELETE CASCADE,
    stage_code      VARCHAR(80) NOT NULL,
    sequence_no     INT NOT NULL,
    status          VARCHAR(40) NOT NULL,
    outcome         VARCHAR(40),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    trace           JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_eval_stage ON ci_evaluation_stage (evaluation_id, sequence_no);

CREATE TABLE IF NOT EXISTS ci_standard_rule_result (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluation_id           UUID NOT NULL REFERENCES ci_credit_evaluation (id) ON DELETE CASCADE,
    evaluation_stage_id     UUID REFERENCES ci_evaluation_stage (id),
    rule_id                 VARCHAR(120) NOT NULL,
    rule_version            VARCHAR(40) NOT NULL DEFAULT '1',
    policy_version_id       UUID NOT NULL REFERENCES ci_policy_version (id),
    category                VARCHAR(80),
    rule_type               VARCHAR(40),
    engine_name             VARCHAR(80) NOT NULL,
    engine_version          VARCHAR(40) NOT NULL DEFAULT 'LEGACY',
    outcome                 VARCHAR(40) NOT NULL,
    severity                VARCHAR(40),
    actual_value            JSONB,
    operator                VARCHAR(40),
    threshold_value         JSONB,
    reason_code             VARCHAR(80),
    explanation             TEXT,
    fact_references         JSONB NOT NULL DEFAULT '[]'::jsonb,
    source_references       JSONB NOT NULL DEFAULT '[]'::jsonb,
    data_status             VARCHAR(40) NOT NULL DEFAULT 'AVAILABLE',
    confidence              NUMERIC(8, 4),
    override_allowed        BOOLEAN NOT NULL DEFAULT FALSE,
    human_review_required   BOOLEAN NOT NULL DEFAULT FALSE,
    executed_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    execution_duration_ms   BIGINT,
    trace                   JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_ci_rule_result_eval ON ci_standard_rule_result (evaluation_id);

COMMENT ON TABLE ci_source_record IS 'Phase F source provenance registry';
COMMENT ON TABLE ci_fact_snapshot IS 'Immutable underwriting fact snapshots (FROZEN)';
COMMENT ON TABLE ci_policy_version IS 'Immutable published policy content for shadow/replay';
COMMENT ON TABLE ci_credit_evaluation IS 'PRODUCTION_REFERENCE / SHADOW evaluations';
