CREATE TABLE IF NOT EXISTS notification_channel_config (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_code      VARCHAR(40) NOT NULL UNIQUE,
    provider          VARCHAR(80),
    config_json       JSONB,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notification_trigger_event (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code        VARCHAR(120) NOT NULL UNIQUE,
    description       VARCHAR(255),
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS workflow_notification_mapping (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_config_id     UUID,
    workflow_step_code     VARCHAR(120) NOT NULL,
    trigger_event_code     VARCHAR(120) NOT NULL,
    channel_code           VARCHAR(40) NOT NULL,
    template_code          VARCHAR(100) NOT NULL,
    recipient_type         VARCHAR(50),
    trigger_timing         VARCHAR(30) NOT NULL DEFAULT 'IMMEDIATE',
    delay_seconds          INTEGER NOT NULL DEFAULT 0,
    condition_expression   TEXT,
    sort_order             INTEGER NOT NULL DEFAULT 1,
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wnm_step_event_active
    ON workflow_notification_mapping(workflow_step_code, trigger_event_code, active);

CREATE INDEX IF NOT EXISTS idx_wnm_template
    ON workflow_notification_mapping(template_code);
