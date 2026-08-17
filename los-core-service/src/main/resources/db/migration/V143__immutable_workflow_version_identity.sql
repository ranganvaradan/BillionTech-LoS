-- IMMUTABLE-WORKFLOW-VERSION-MODEL-1
-- workflow_configs.id becomes an immutable Workflow Version identity.
-- workflow_family_id is the stable journey identity across versions.
-- Does NOT activate Customer Categories or rewrite pins to "latest".

ALTER TABLE workflow_configs
    ADD COLUMN IF NOT EXISTS workflow_family_id UUID NULL,
    ADD COLUMN IF NOT EXISTS publication_status VARCHAR(20) NULL;

UPDATE workflow_configs
SET workflow_family_id = id
WHERE workflow_family_id IS NULL;

UPDATE workflow_configs
SET publication_status = CASE WHEN active THEN 'ACTIVE' ELSE 'DRAFT' END
WHERE publication_status IS NULL;

ALTER TABLE workflow_configs
    ALTER COLUMN workflow_family_id SET NOT NULL,
    ALTER COLUMN publication_status SET NOT NULL;

COMMENT ON COLUMN workflow_configs.workflow_family_id IS
    'Stable journey identity. Multiple immutable Workflow Version rows share one family.';
COMMENT ON COLUMN workflow_configs.id IS
    'Immutable exact Workflow Version identity. Never reused for a later version.';
COMMENT ON COLUMN workflow_configs.version IS
    'Human-facing monotonic version number within workflow_family_id. Not an identity authority.';
COMMENT ON COLUMN workflow_configs.publication_status IS
    'DRAFT | ACTIVE | SUPERSEDED | RETIRED. ACTIVE/SUPERSEDED rows are immutable.';

CREATE INDEX IF NOT EXISTS idx_workflow_family_id
    ON workflow_configs (workflow_family_id);

COMMENT ON COLUMN customer_category.workflow_id IS
    'Exact immutable Workflow Version pointer (workflow_configs.id). Not a family/latest pointer.';

-- Reconstruct overwritten Vikasam versions from entity_record_audit when present.
-- Original id keeps historical v1 content so existing category pins/hashes stay truthful.
-- Current live content is copied to a new v3 row; in-flight applications follow that copy.
DO $$
DECLARE
    family uuid := '3b1e0488-c33a-48d5-b494-5b77eec30b8c';
    v3_id uuid;
    v2_id uuid;
    snap record;
    live_ver int;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM workflow_configs WHERE id = family) THEN
        RETURN;
    END IF;
    IF EXISTS (
        SELECT 1 FROM workflow_configs
        WHERE workflow_family_id = family AND id <> family
    ) THEN
        RETURN;
    END IF;
    SELECT version INTO live_ver FROM workflow_configs WHERE id = family;
    IF live_ver IS NULL OR live_ver <= 1 THEN
        RETURN;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM entity_record_audit
        WHERE entity_type = 'WORKFLOW_CONFIG' AND entity_id = family::text
          AND action = 'UPDATE'
          AND (old_row->>'version') = '1'
          AND (new_row->>'version') = '2'
    ) THEN
        RETURN;
    END IF;

    SELECT old_row, new_row INTO snap
    FROM entity_record_audit
    WHERE entity_type = 'WORKFLOW_CONFIG' AND entity_id = family::text
      AND action = 'UPDATE'
      AND (old_row->>'version') = '1'
      AND (new_row->>'version') = '2'
    ORDER BY created_at DESC
    LIMIT 1;

    v3_id := gen_random_uuid();
    v2_id := gen_random_uuid();

    INSERT INTO workflow_configs (
        id, name, borrower_type, loan_product, steps, active, version,
        created_at, updated_at, sla_hours_per_step, escalation_emails, conditional_rules,
        parallel_groups, vkyc_trigger_condition, workflow_position,
        process_notification_mappings, manual_override_policies, intake_segment,
        intake_identity_schema, lms_product_code, lms_tenure_unit, intake_config,
        bureau_enabled, auto_pull_bureau_after_kyc_success,
        workflow_family_id, publication_status
    )
    SELECT
        v3_id, name, borrower_type, loan_product, steps, TRUE, version,
        created_at, updated_at, sla_hours_per_step, escalation_emails, conditional_rules,
        parallel_groups, vkyc_trigger_condition, workflow_position,
        process_notification_mappings, manual_override_policies, intake_segment,
        intake_identity_schema, lms_product_code, lms_tenure_unit, intake_config,
        bureau_enabled, auto_pull_bureau_after_kyc_success,
        family, 'ACTIVE'
    FROM workflow_configs
    WHERE id = family;

    INSERT INTO workflow_configs (
        id, name, borrower_type, loan_product, steps, active, version,
        created_at, updated_at, sla_hours_per_step, escalation_emails, conditional_rules,
        parallel_groups, vkyc_trigger_condition, workflow_position,
        process_notification_mappings, manual_override_policies,
        intake_segment, intake_identity_schema, lms_product_code, lms_tenure_unit,
        intake_config, bureau_enabled, auto_pull_bureau_after_kyc_success,
        workflow_family_id, publication_status
    )
    VALUES (
        v2_id,
        snap.new_row->>'name',
        snap.new_row->>'borrowerType',
        snap.new_row->>'loanProduct',
        COALESCE(snap.new_row->'steps', '[]'::jsonb),
        FALSE,
        2,
        now(),
        now(),
        snap.new_row->'slaHoursPerStep',
        snap.new_row->'escalationEmails',
        snap.new_row->'conditionalRules',
        snap.new_row->'parallelGroups',
        snap.new_row->'vkycTriggerCondition',
        snap.new_row->>'workflowPosition',
        snap.new_row->'processNotificationMappings',
        snap.new_row->'manualOverridePolicies',
        COALESCE(snap.new_row->>'intakeSegment', 'BORROWER'),
        snap.new_row->'intakeIdentitySchema',
        snap.new_row->>'lmsProductCode',
        snap.new_row->>'lmsTenureUnit',
        snap.new_row->'intakeConfig',
        COALESCE((snap.new_row->>'bureauEnabled')::boolean, TRUE),
        COALESCE((snap.new_row->>'autoPullBureauAfterKycSuccess')::boolean, TRUE),
        family,
        'SUPERSEDED'
    );

    UPDATE workflow_configs SET
        name = snap.old_row->>'name',
        borrower_type = snap.old_row->>'borrowerType',
        loan_product = snap.old_row->>'loanProduct',
        steps = COALESCE(snap.old_row->'steps', '[]'::jsonb),
        active = FALSE,
        version = 1,
        updated_at = now(),
        sla_hours_per_step = snap.old_row->'slaHoursPerStep',
        escalation_emails = snap.old_row->'escalationEmails',
        conditional_rules = snap.old_row->'conditionalRules',
        parallel_groups = snap.old_row->'parallelGroups',
        vkyc_trigger_condition = snap.old_row->'vkycTriggerCondition',
        workflow_position = snap.old_row->>'workflowPosition',
        process_notification_mappings = snap.old_row->'processNotificationMappings',
        manual_override_policies = snap.old_row->'manualOverridePolicies',
        intake_segment = COALESCE(snap.old_row->>'intakeSegment', 'BORROWER'),
        intake_identity_schema = snap.old_row->'intakeIdentitySchema',
        lms_product_code = snap.old_row->>'lmsProductCode',
        lms_tenure_unit = snap.old_row->>'lmsTenureUnit',
        intake_config = snap.old_row->'intakeConfig',
        bureau_enabled = COALESCE((snap.old_row->>'bureauEnabled')::boolean, TRUE),
        auto_pull_bureau_after_kyc_success = COALESCE((snap.old_row->>'autoPullBureauAfterKycSuccess')::boolean, TRUE),
        workflow_family_id = family,
        publication_status = 'SUPERSEDED'
    WHERE id = family;

    -- Preserve currently executing content for applications bound to the old mutable row.
    -- Do not rewrite customer_category pins, status, policy links, or hashes.
    UPDATE loan_applications
    SET workflow_id = v3_id,
        workflow_version = 3
    WHERE workflow_id = family;

    UPDATE program_masters
    SET workflow_config_id = v3_id
    WHERE workflow_config_id = family;

    UPDATE requirement_plan
    SET workflow_id = v3_id
    WHERE workflow_id = family;
END $$;

-- Unique (family, version) after reconstruction so v3 copy does not collide with the original row.
CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_family_version
    ON workflow_configs (workflow_family_id, version);

CREATE UNIQUE INDEX IF NOT EXISTS uq_workflow_family_one_active
    ON workflow_configs (workflow_family_id)
    WHERE publication_status = 'ACTIVE';
