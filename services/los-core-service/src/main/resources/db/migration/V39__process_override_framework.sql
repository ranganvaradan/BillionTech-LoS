ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS manual_override_policies jsonb;

CREATE TABLE IF NOT EXISTS process_override_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id uuid NOT NULL,
    process_code varchar(80) NOT NULL,
    failure_code varchar(120) NOT NULL,
    previous_status varchar(40),
    new_status varchar(40),
    override_reason varchar(1000) NOT NULL,
    remarks varchar(2000),
    approval_reference varchar(200),
    overridden_by uuid,
    overridden_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_override_history_application
    ON process_override_history (application_id, overridden_at DESC);
