-- W6: Acquisition attempt ledger for idempotent source orchestration.
-- Source execution state remains on requirement_item.source_acquisition_state (W3 separation).
-- This table records why/when a provider was called and prevents duplicate pulls.

CREATE TABLE IF NOT EXISTS requirement_acquisition_attempt (
    id                      UUID PRIMARY KEY,
    plan_id                 UUID         NOT NULL REFERENCES requirement_plan (id) ON DELETE CASCADE,
    item_id                 UUID         NOT NULL REFERENCES requirement_item (id) ON DELETE CASCADE,
    application_id          UUID         NOT NULL,
    plan_version            INT          NOT NULL DEFAULT 1,
    canonical_parameter_id  VARCHAR(120) NULL,
    source_key              VARCHAR(80)  NOT NULL,
    fulfilment_mode         VARCHAR(40)  NOT NULL,
    execution_key           VARCHAR(200) NOT NULL,
    status                  VARCHAR(40)  NOT NULL,
    provider_ref            VARCHAR(120) NULL,
    external_ref            VARCHAR(255) NULL,
    failure_class           VARCHAR(40)  NULL,
    failure_reason          VARCHAR(500) NULL,
    previous_source_key     VARCHAR(80)  NULL,
    fallback_source_key     VARCHAR(80)  NULL,
    attempt_number          INT          NOT NULL DEFAULT 1,
    result_summary          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    provenance              JSONB        NOT NULL DEFAULT '{}'::jsonb,
    started_at              TIMESTAMPTZ  NULL,
    finished_at             TIMESTAMPTZ  NULL,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_requirement_acquisition_execution_key
    ON requirement_acquisition_attempt (execution_key);

CREATE INDEX IF NOT EXISTS idx_requirement_acquisition_attempt_plan_id
    ON requirement_acquisition_attempt (plan_id);

CREATE INDEX IF NOT EXISTS idx_requirement_acquisition_attempt_item_id
    ON requirement_acquisition_attempt (item_id);

CREATE INDEX IF NOT EXISTS idx_requirement_acquisition_attempt_application_id
    ON requirement_acquisition_attempt (application_id);

COMMENT ON TABLE requirement_acquisition_attempt IS
    'W6 idempotent acquisition attempts — application+item+source+planVersion identity.';

COMMENT ON COLUMN requirement_acquisition_attempt.execution_key IS
    'Deterministic key: appId|planVersion|itemKey|sourceKey — prevents duplicate Bureau/AA/GST/OCR calls.';
