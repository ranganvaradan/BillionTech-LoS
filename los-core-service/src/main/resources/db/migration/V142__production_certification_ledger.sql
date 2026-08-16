-- WAVE-8 Production Certification Authority
-- Additive only. Empty ledger on install — no inferred historical certifications.
-- Does not mutate GACAT / policies / definitions.

CREATE TABLE IF NOT EXISTS ci_production_certification (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_type               VARCHAR(64)  NOT NULL,
    artifact_id                 VARCHAR(300) NOT NULL,
    artifact_version            VARCHAR(120) NOT NULL,
    scope_type                  VARCHAR(40)  NOT NULL DEFAULT 'PLATFORM',
    scope_id                    VARCHAR(120),
    status                      VARCHAR(40)  NOT NULL DEFAULT 'UNCERTIFIED',
    certified_by                VARCHAR(200),
    certified_at                TIMESTAMPTZ,
    revoked_by                  VARCHAR(200),
    revoked_at                  TIMESTAMPTZ,
    revocation_reason           TEXT,
    evidence_summary            TEXT,
    evidence_json               JSONB        NOT NULL DEFAULT '{}'::jsonb,
    semantic_catalogue_version  VARCHAR(80),
    engine_version              VARCHAR(80),
    producer_version            VARCHAR(80),
    valid_from                  TIMESTAMPTZ,
    valid_until                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_prod_cert_artifact_type CHECK (artifact_type IN (
        'CANONICAL_PARAMETER_PRODUCER',
        'AUTHORED_CALCULATION_DEFINITION',
        'POLICY_VERSION',
        'SCORECARD_VERSION',
        'SOURCE_INTEGRATION')),
    CONSTRAINT ck_prod_cert_scope CHECK (scope_type IN (
        'PLATFORM', 'TENANT', 'PRODUCT', 'PROGRAM')),
    CONSTRAINT ck_prod_cert_status CHECK (status IN (
        'UNCERTIFIED', 'CERTIFIED', 'REVOKED', 'PENDING_REVIEW'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_prod_cert_artifact_scope
    ON ci_production_certification (artifact_type, artifact_id, artifact_version, scope_type, COALESCE(scope_id, ''));

CREATE INDEX IF NOT EXISTS idx_prod_cert_status
    ON ci_production_certification (status, artifact_type);

CREATE INDEX IF NOT EXISTS idx_prod_cert_tenant_scope
    ON ci_production_certification (scope_type, scope_id);

-- Append-only event history (grant/revoke). Never overwrite alone.
CREATE TABLE IF NOT EXISTS ci_production_certification_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    certification_id    UUID NOT NULL REFERENCES ci_production_certification(id),
    event_type          VARCHAR(40)  NOT NULL,
    from_status         VARCHAR(40),
    to_status           VARCHAR(40)  NOT NULL,
    actor               VARCHAR(200),
    reason              TEXT,
    evidence_json       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_prod_cert_event_type CHECK (event_type IN (
        'CERTIFY', 'REVOKE', 'NOTE', 'PENDING_REVIEW'))
);

CREATE INDEX IF NOT EXISTS idx_prod_cert_event_cert
    ON ci_production_certification_event (certification_id, occurred_at);

COMMENT ON TABLE ci_production_certification IS
    'Wave-8 durable production certification ledger. Exact artifact/version/scope. Empty on install.';
COMMENT ON TABLE ci_production_certification_event IS
    'Append-only certification history. Who/when/why for CERTIFY and REVOKE.';
