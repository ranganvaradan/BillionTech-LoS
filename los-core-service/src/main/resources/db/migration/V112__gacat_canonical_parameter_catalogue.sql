-- GACAT-PERSISTENCE-1 — durable canonical parameter catalogue (global, versioned).
-- Does not alter policies, applications, scorecards, or underwriting authority.

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter (
    id                      UUID PRIMARY KEY,
    canonical_id            VARCHAR(160) NOT NULL,
    display_name            VARCHAR(255) NOT NULL,
    description             TEXT,
    source_family           VARCHAR(80)  NOT NULL,
    parameter_kind          VARCHAR(32)  NOT NULL,
    value_type              VARCHAR(64),
    unit                    VARCHAR(64),
    period                  VARCHAR(64),
    availability            VARCHAR(64),
    status                  VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    source_available        BOOLEAN      NOT NULL DEFAULT FALSE,
    normalized              BOOLEAN      NOT NULL DEFAULT FALSE,
    derivation_defined      BOOLEAN      NOT NULL DEFAULT FALSE,
    implemented             BOOLEAN      NOT NULL DEFAULT FALSE,
    production_ready        BOOLEAN      NOT NULL DEFAULT FALSE,
    manual_input_allowed    BOOLEAN      NOT NULL DEFAULT FALSE,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    live_rule_parameter     VARCHAR(160),
    live_scorecard_parameter VARCHAR(160),
    capability_schema       VARCHAR(64),
    cardinality             VARCHAR(32),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ci_gacat_canonical_id UNIQUE (canonical_id),
    CONSTRAINT ck_ci_gacat_parameter_kind CHECK (parameter_kind IN ('RAW', 'DERIVED', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_ci_gacat_param_source ON ci_gacat_canonical_parameter (source_family);
CREATE INDEX IF NOT EXISTS idx_ci_gacat_param_kind ON ci_gacat_canonical_parameter (parameter_kind);
CREATE INDEX IF NOT EXISTS idx_ci_gacat_param_active ON ci_gacat_canonical_parameter (active);

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter_version (
    id                      UUID PRIMARY KEY,
    parameter_id            UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    version_no              INTEGER      NOT NULL,
    effective_from          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_to            TIMESTAMPTZ,
    definition_status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    calculation_definition  TEXT,
    missing_data_treatment  VARCHAR(64),
    period_definition       VARCHAR(64),
    implementation_binding  VARCHAR(255),
    filters_eligibility     TEXT,
    transformation          TEXT,
    aggregation             TEXT,
    metadata_json           TEXT,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(128),
    CONSTRAINT uq_ci_gacat_param_version UNIQUE (parameter_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_ci_gacat_version_status
    ON ci_gacat_canonical_parameter_version (parameter_id, definition_status);

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter_alias (
    id              UUID PRIMARY KEY,
    parameter_id    UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    alias           VARCHAR(255) NOT NULL,
    alias_type      VARCHAR(32)  NOT NULL DEFAULT 'SYNONYM',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_ci_gacat_alias UNIQUE (parameter_id, alias)
);

CREATE INDEX IF NOT EXISTS idx_ci_gacat_alias_lookup
    ON ci_gacat_canonical_parameter_alias (LOWER(alias));

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter_allowed_value (
    id                      UUID PRIMARY KEY,
    parameter_id            UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    parameter_version_id    UUID         REFERENCES ci_gacat_canonical_parameter_version (id),
    value_code              VARCHAR(128) NOT NULL,
    display_value           VARCHAR(255) NOT NULL,
    sort_order              INTEGER      NOT NULL DEFAULT 0,
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_ci_gacat_allowed_value UNIQUE (parameter_id, value_code)
);

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter_lineage (
    id                      UUID PRIMARY KEY,
    derived_parameter_id    UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    input_parameter_id      UUID         REFERENCES ci_gacat_canonical_parameter (id),
    input_ref               VARCHAR(255),
    relationship_type       VARCHAR(64)  NOT NULL DEFAULT 'REQUIRES',
    sequence_no             INTEGER      NOT NULL DEFAULT 0,
    required                BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata_json           TEXT
);

CREATE INDEX IF NOT EXISTS idx_ci_gacat_lineage_derived
    ON ci_gacat_canonical_parameter_lineage (derived_parameter_id);

CREATE TABLE IF NOT EXISTS ci_gacat_canonical_parameter_source_binding (
    id                      UUID PRIMARY KEY,
    parameter_id            UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    source_family           VARCHAR(80),
    provider_code           VARCHAR(80),
    provider_field_path     VARCHAR(512),
    normalizer_binding      VARCHAR(255),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    metadata_json           TEXT
);

CREATE INDEX IF NOT EXISTS idx_ci_gacat_source_binding_param
    ON ci_gacat_canonical_parameter_source_binding (parameter_id);

-- Conservative tenant overlay — empty at seed; do not copy global rows per tenant.
CREATE TABLE IF NOT EXISTS ci_gacat_tenant_parameter_configuration (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    parameter_id            UUID         NOT NULL REFERENCES ci_gacat_canonical_parameter (id),
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    provider_code           VARCHAR(80),
    manual_capture_type     VARCHAR(64),
    manual_capture_actor    VARCHAR(128),
    configuration_json      TEXT,
    effective_from          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    effective_to            TIMESTAMPTZ,
    CONSTRAINT uq_ci_gacat_tenant_param UNIQUE (tenant_id, parameter_id, effective_from)
);

CREATE TABLE IF NOT EXISTS ci_gacat_catalogue_meta (
    meta_key    VARCHAR(64) PRIMARY KEY,
    meta_value  TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO ci_gacat_catalogue_meta (meta_key, meta_value)
VALUES ('inventory_version', 'GACAT-PERSISTENCE-1')
ON CONFLICT (meta_key) DO UPDATE SET meta_value = EXCLUDED.meta_value, updated_at = NOW();
