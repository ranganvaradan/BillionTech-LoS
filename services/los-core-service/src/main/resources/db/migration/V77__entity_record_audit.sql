-- Global entity record audit (applications, workflows, scorecards, users, etc.)
CREATE TABLE IF NOT EXISTS entity_record_audit (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type         VARCHAR(100) NOT NULL,
    entity_id           VARCHAR(255) NOT NULL,
    action              VARCHAR(40) NOT NULL,
    status_at_change    VARCHAR(64),
    performed_by        UUID,
    performed_by_role   VARCHAR(100),
    old_row             JSONB,
    new_row             JSONB,
    changed_fields      TEXT,
    application_id      UUID,
    correlation_id      VARCHAR(64),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_entity_record_audit_entity
    ON entity_record_audit (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_entity_record_audit_app
    ON entity_record_audit (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_entity_record_audit_created
    ON entity_record_audit (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_events_created_at ON audit_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_event_type ON audit_events (event_type);
