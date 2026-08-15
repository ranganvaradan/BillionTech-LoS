-- W2: Customer Category → exact Workflow Version bind (configuration only).
-- Does NOT activate Categories, change application.workflow_id, or enable live routing.
-- Workflow Version identity = workflow_configs.id (+ denormalized version + content hash).

ALTER TABLE customer_category
    ADD COLUMN IF NOT EXISTS workflow_id UUID NULL,
    ADD COLUMN IF NOT EXISTS workflow_version INTEGER NULL,
    ADD COLUMN IF NOT EXISTS workflow_content_hash VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS workflow_name VARCHAR(100) NULL;

COMMENT ON COLUMN customer_category.workflow_id IS
    'W2 exact Workflow Version pointer (workflow_configs.id). At most one per Category Version.';
COMMENT ON COLUMN customer_category.workflow_version IS
    'Denormalized workflow_configs.version at bind time.';
COMMENT ON COLUMN customer_category.workflow_content_hash IS
    'W1 WorkflowContentHash snapshot at bind; mutation → WORKFLOW_VERSION_MUTATED readiness.';
COMMENT ON COLUMN customer_category.workflow_name IS
    'Denormalized display name at bind time.';

CREATE INDEX IF NOT EXISTS idx_customer_category_workflow_id
    ON customer_category (workflow_id)
    WHERE workflow_id IS NOT NULL;
