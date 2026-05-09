-- H2-compatible shape mirroring V14 (CLOB for JSONB columns) — JDBC smoke only; production uses Flyway + PostgreSQL jsonb
DROP TABLE IF EXISTS esign_requests;
DROP TABLE IF EXISTS aggregator_configs;

CREATE TABLE aggregator_configs (
    id                  UUID NOT NULL,
    provider_name       VARCHAR(100) NOT NULL,
    step_type           VARCHAR(50),
    base_url            VARCHAR(2000),
    auth_type           VARCHAR(40),
    client_id_enc       TEXT,
    client_secret_enc   TEXT,
    token_url           VARCHAR(2000),
    extra_config        CLOB,
    timeout_ms          INTEGER,
    retry_count         INTEGER,
    webhook_secret      TEXT,
    environment         VARCHAR(40),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX idx_aggregator_configs_provider ON aggregator_configs (provider_name, is_active);
CREATE INDEX idx_aggregator_configs_step ON aggregator_configs (step_type);

CREATE TABLE esign_requests (
    id                      UUID NOT NULL,
    application_id          UUID NOT NULL,
    document_type           VARCHAR(100) NOT NULL,
    provider                VARCHAR(100) NOT NULL,
    provider_request_id     VARCHAR(200),
    signing_url             TEXT,
    status                  VARCHAR(50) NOT NULL,
    signer_name             VARCHAR(200),
    signer_aadhaar_last4    VARCHAR(4),
    signed_document_url     TEXT,
    signed_at               TIMESTAMP WITH TIME ZONE,
    expires_at              TIMESTAMP WITH TIME ZONE,
    raw_response            CLOB,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_esign_requests_app ON esign_requests (application_id);
CREATE INDEX idx_esign_requests_provider_id ON esign_requests (provider, provider_request_id);
CREATE INDEX idx_esign_requests_status ON esign_requests (status);
