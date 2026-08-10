ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS process_notification_mappings JSONB;

