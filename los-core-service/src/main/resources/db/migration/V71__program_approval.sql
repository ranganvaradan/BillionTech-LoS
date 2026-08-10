-- Program L1/L2 approval workflow (LOS-side gate before PLP activation)

ALTER TABLE program_masters
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS assigned_l1_user_id UUID,
    ADD COLUMN IF NOT EXISTS assigned_l2_user_id UUID,
    ADD COLUMN IF NOT EXISTS approval_notes TEXT,
    ADD COLUMN IF NOT EXISTS approval_history_json TEXT,
    ADD COLUMN IF NOT EXISTS anchor_application_id UUID,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by_user_id UUID;

CREATE INDEX IF NOT EXISTS idx_program_masters_approval_status ON program_masters (approval_status);
CREATE INDEX IF NOT EXISTS idx_program_masters_assigned_l1 ON program_masters (assigned_l1_user_id);
CREATE INDEX IF NOT EXISTS idx_program_masters_assigned_l2 ON program_masters (assigned_l2_user_id);

CREATE TABLE IF NOT EXISTS program_approval_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    l1_role         VARCHAR(50) NOT NULL DEFAULT 'CREDIT_OFFICER',
    l2_role         VARCHAR(50) NOT NULL DEFAULT 'CREDIT_MANAGER',
    l1_user_id      UUID,
    l2_user_id      UUID,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO program_approval_config (l1_role, l2_role, active)
SELECT 'CREDIT_OFFICER', 'CREDIT_MANAGER', TRUE
WHERE NOT EXISTS (SELECT 1 FROM program_approval_config WHERE active = TRUE);
