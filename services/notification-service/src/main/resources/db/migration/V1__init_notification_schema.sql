-- V1__init_notification_schema.sql

CREATE TABLE notification_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel         VARCHAR(20) NOT NULL,
    recipient       VARCHAR(255) NOT NULL,
    template_code   VARCHAR(100) NOT NULL,
    event_type      VARCHAR(50) NOT NULL,
    template_data   JSONB,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message   VARCHAR(500),
    retry_count     INTEGER NOT NULL DEFAULT 0,
    application_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_notif_application ON notification_logs(application_id);
CREATE INDEX idx_notif_recipient ON notification_logs(recipient);
CREATE INDEX idx_notif_status ON notification_logs(status);
CREATE INDEX idx_notif_created ON notification_logs(created_at);
