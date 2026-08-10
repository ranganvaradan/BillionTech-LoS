-- Provider routing: which provider to attempt first, and optional KYC step scope.

CREATE TABLE IF NOT EXISTS aggregator_routing (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(40)  NOT NULL,
    integration_type    VARCHAR(20)  NOT NULL,
    kyc_step_type       VARCHAR(40),
    priority            INTEGER      NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    allow_fallback      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_agg_routing_type_active
    ON aggregator_routing (integration_type, active);

-- KYC: global order; step-specific rules can be added with kyc_step_type set.
INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, priority, active, allow_fallback) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'KARZA',        'KYC', NULL, 100, TRUE, TRUE),
    ('a0000000-0000-0000-0000-000000000002', 'AUTHBRIDGE',  'KYC', NULL,  90, TRUE, TRUE),
    ('a0000000-0000-0000-0000-000000000003', 'HYPERVERGE',  'KYC', NULL,  80, TRUE, TRUE);

INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, priority, active, allow_fallback) VALUES
    ('a0000000-0000-0000-0000-000000000010', 'EQUIFAX', 'BUREAU', NULL, 100, TRUE, TRUE);

INSERT INTO aggregator_routing (id, provider_name, integration_type, kyc_step_type, priority, active, allow_fallback) VALUES
    ('a0000000-0000-0000-0000-000000000020', 'EMSIGNER', 'ESIGN', NULL, 100, TRUE, FALSE);
