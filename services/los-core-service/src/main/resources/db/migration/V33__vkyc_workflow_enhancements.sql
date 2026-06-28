ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS vkyc_trigger_condition jsonb,
    ADD COLUMN IF NOT EXISTS workflow_position varchar(50);

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS vkyc_required boolean,
    ADD COLUMN IF NOT EXISTS vkyc_status varchar(30),
    ADD COLUMN IF NOT EXISTS vkyc_completed_at timestamp,
    ADD COLUMN IF NOT EXISTS vkyc_agent_id uuid,
    ADD COLUMN IF NOT EXISTS vkyc_auditor_id uuid,
    ADD COLUMN IF NOT EXISTS vkyc_reference_id varchar(150),
    ADD COLUMN IF NOT EXISTS vkyc_url text;
