-- Workflow-driven intake rules (field visibility, OR groups, documents, age, tenure).
-- Existing workflows default to LEGACY policy so current intake behavior is unchanged.

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS intake_config jsonb;

UPDATE workflow_configs
SET intake_config = jsonb_build_object('policy', 'LEGACY')
WHERE intake_config IS NULL;
