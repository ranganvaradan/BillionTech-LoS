-- W1 — Workflow resolution hygiene
-- Persist resolved Workflow Version metadata on loan_applications.
-- Does NOT bind Customer Category to Workflow.

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS workflow_version INT,
    ADD COLUMN IF NOT EXISTS workflow_resolution_source VARCHAR(40),
    ADD COLUMN IF NOT EXISTS workflow_resolved_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS workflow_content_hash VARCHAR(64);

COMMENT ON COLUMN loan_applications.workflow_id IS
    'Resolved Workflow Version identity (workflow_configs.id). Once set, orchestration must not re-discover another active workflow.';
COMMENT ON COLUMN loan_applications.workflow_version IS
    'Snapshot of workflow_configs.version at resolution time.';
COMMENT ON COLUMN loan_applications.workflow_resolution_source IS
    'EXPLICIT | DEFAULT | LEGACY_EXISTING | MIGRATED';
COMMENT ON COLUMN loan_applications.workflow_resolved_at IS
    'When the Workflow Version was first resolved/persisted for this application.';
COMMENT ON COLUMN loan_applications.workflow_content_hash IS
    'SHA-256 of workflow steps+intake at resolve time; detects in-place definition mutation (P1 full immutability).';

-- Backfill metadata for applications that already have workflow_id (preserve binding; no journey switch).
UPDATE loan_applications la
SET
    workflow_version = wc.version,
    workflow_resolution_source = COALESCE(la.workflow_resolution_source, 'LEGACY_EXISTING'),
    workflow_resolved_at = COALESCE(la.workflow_resolved_at, COALESCE(la.created_at, NOW()))
FROM workflow_configs wc
WHERE la.workflow_id = wc.id
  AND la.workflow_version IS NULL;
