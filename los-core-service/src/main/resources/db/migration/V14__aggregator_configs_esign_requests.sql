-- LOS_Design_v2: provider connection configuration (distinct from aggregator_routing priority/fallback rules).

CREATE TABLE IF NOT EXISTS aggregator_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(100) NOT NULL,
    step_type           VARCHAR(50),
    base_url            VARCHAR(2000),
    auth_type           VARCHAR(40),
    client_id_enc       TEXT,
    client_secret_enc   TEXT,
    token_url           VARCHAR(2000),
    extra_config        JSONB,
    timeout_ms          INTEGER,
    retry_count         INTEGER,
    webhook_secret      TEXT,
    environment         VARCHAR(40),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_aggregator_configs_provider
    ON aggregator_configs (provider_name, is_active);
CREATE INDEX IF NOT EXISTS idx_aggregator_configs_step
    ON aggregator_configs (step_type);

-- eSign request audit trail (supplements loan_applications.esign_transaction_id and flow maps).

CREATE TABLE IF NOT EXISTS esign_requests (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id          UUID         NOT NULL,
    document_type           VARCHAR(100) NOT NULL,
    provider                VARCHAR(100) NOT NULL,
    provider_request_id     VARCHAR(200),
    signing_url             TEXT,
    status                  VARCHAR(50)  NOT NULL,
    signer_name             VARCHAR(200),
    signer_aadhaar_last4    VARCHAR(4),
    signed_document_url     TEXT,
    signed_at               TIMESTAMPTZ,
    expires_at              TIMESTAMPTZ,
    raw_response            JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_esign_requests_app ON esign_requests (application_id);
CREATE INDEX IF NOT EXISTS idx_esign_requests_provider_id ON esign_requests (provider, provider_request_id);
CREATE INDEX IF NOT EXISTS idx_esign_requests_status ON esign_requests (status);
