-- W3: Requirement Plan domain — fulfilment ≠ readiness ≠ source.
-- Config/domain only. No live routing, Category activation, or Policy cutover.

CREATE TABLE IF NOT EXISTS requirement_plan (
    id                        UUID PRIMARY KEY,
    application_id            UUID         NOT NULL,
    customer_category_id      UUID         NULL,
    policy_document_id        UUID         NULL,
    policy_applicability_id   UUID         NULL,
    workflow_id               UUID         NULL,
    plan_version              INT          NOT NULL DEFAULT 1,
    status                    VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    plan_hash                 VARCHAR(64)  NULL,
    metadata                  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_requirement_plan_application_id
    ON requirement_plan (application_id);

COMMENT ON TABLE requirement_plan IS
    'W3 per-application RequirementPlan. Domain/state only — not live acquisition.';

CREATE TABLE IF NOT EXISTS requirement_item (
    id                          UUID PRIMARY KEY,
    plan_id                     UUID         NOT NULL REFERENCES requirement_plan (id) ON DELETE CASCADE,
    item_key                    VARCHAR(120) NOT NULL,
    requirement_type            VARCHAR(40)  NOT NULL,
    requirement_class           VARCHAR(40)  NOT NULL,
    phase                       VARCHAR(40)  NULL,
    canonical_parameter_id      VARCHAR(120) NULL,
    business_name               VARCHAR(200) NULL,
    required                    BOOLEAN      NOT NULL DEFAULT TRUE,
    customer_fulfilment_state  VARCHAR(40)  NOT NULL DEFAULT 'REQUIRED',
    data_readiness_state        VARCHAR(40)  NOT NULL DEFAULT 'NOT_AVAILABLE',
    source_acquisition_state    VARCHAR(40)  NOT NULL DEFAULT 'NOT_STARTED',
    allowed_fulfilment_modes   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    fulfilment_mode_used        VARCHAR(40)  NULL,
    evidence_ref                VARCHAR(255) NULL,
    document_ref                VARCHAR(255) NULL,
    policy_rule_refs            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    source_hints                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    provenance                  JSONB        NOT NULL DEFAULT '{}'::jsonb,
    sort_order                  INT          NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    provided_at                 TIMESTAMPTZ  NULL
);

CREATE INDEX IF NOT EXISTS idx_requirement_item_plan_id
    ON requirement_item (plan_id);

CREATE INDEX IF NOT EXISTS idx_requirement_item_canonical_parameter_id
    ON requirement_item (canonical_parameter_id)
    WHERE canonical_parameter_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_requirement_item_document_ref
    ON requirement_item (document_ref)
    WHERE document_ref IS NOT NULL;

COMMENT ON TABLE requirement_item IS
    'W3 RequirementItem — customer fulfilment independent of data readiness.';

CREATE TABLE IF NOT EXISTS requirement_state_transition (
    id           UUID PRIMARY KEY,
    item_id      UUID         NOT NULL REFERENCES requirement_item (id) ON DELETE CASCADE,
    field_name   VARCHAR(32)  NOT NULL,
    from_state   VARCHAR(40)  NULL,
    to_state     VARCHAR(40)  NOT NULL,
    reason       VARCHAR(500) NULL,
    actor        VARCHAR(120) NULL,
    evidence_ref VARCHAR(255) NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_requirement_state_transition_item_id
    ON requirement_state_transition (item_id);

COMMENT ON TABLE requirement_state_transition IS
    'W3 audit of fulfilment / readiness / source state changes.';

COMMENT ON COLUMN requirement_state_transition.field_name IS
    'FULFILMENT | READINESS | SOURCE';
