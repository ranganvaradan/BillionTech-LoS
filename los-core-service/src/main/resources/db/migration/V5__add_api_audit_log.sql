-- V5__add_api_audit_log.sql
-- API Audit Log for tracking all integration provider API calls

CREATE TABLE api_audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_name       VARCHAR(50) NOT NULL,
    api_name            VARCHAR(100) NOT NULL,
    request_payload     TEXT,
    response_payload    TEXT,
    status              VARCHAR(20),
    http_status_code    INTEGER,
    error_message       TEXT,
    transaction_id      VARCHAR(100),
    application_id      UUID,
    duration_ms         BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    request_time        TIMESTAMPTZ,
    response_time       TIMESTAMPTZ
);

CREATE INDEX idx_api_audit_provider ON api_audit_log(provider_name);
CREATE INDEX idx_api_audit_api_name ON api_audit_log(api_name);
CREATE INDEX idx_api_audit_application ON api_audit_log(application_id);
CREATE INDEX idx_api_audit_created ON api_audit_log(created_at);
CREATE INDEX idx_api_audit_status ON api_audit_log(status);
