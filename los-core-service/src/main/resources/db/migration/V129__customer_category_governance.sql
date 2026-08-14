-- CUSTOMER-CATEGORY-GOVERNANCE-IMPLEMENTATION-1
-- Lifecycle / maker-checker / effective dating for Customer Category + Policy Set.
-- Does NOT wire into live underwriting routing.

-- Expand status check constraints
ALTER TABLE policy_set DROP CONSTRAINT IF EXISTS chk_policy_set_status;
ALTER TABLE policy_set ADD CONSTRAINT chk_policy_set_status
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED'));

ALTER TABLE customer_category DROP CONSTRAINT IF EXISTS chk_customer_category_status;
ALTER TABLE customer_category ADD CONSTRAINT chk_customer_category_status
    CHECK (status IN ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED'));

-- Policy Set governance / dating / replacement
ALTER TABLE policy_set
    ADD COLUMN IF NOT EXISTS effective_from        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_until       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_by          VARCHAR(120),
    ADD COLUMN IF NOT EXISTS submitted_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by           VARCHAR(120),
    ADD COLUMN IF NOT EXISTS approved_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retirement_reason     TEXT,
    ADD COLUMN IF NOT EXISTS reason_for_change     TEXT,
    ADD COLUMN IF NOT EXISTS replaces_policy_set_id UUID REFERENCES policy_set (id),
    ADD COLUMN IF NOT EXISTS governance_json       JSONB NOT NULL DEFAULT '{}'::jsonb;

-- Customer Category governance / dating / replacement
ALTER TABLE customer_category
    ADD COLUMN IF NOT EXISTS effective_from           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS effective_until          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS submitted_by             VARCHAR(120),
    ADD COLUMN IF NOT EXISTS submitted_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS approved_by              VARCHAR(120),
    ADD COLUMN IF NOT EXISTS approved_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retirement_reason        TEXT,
    ADD COLUMN IF NOT EXISTS reason_for_change        TEXT,
    ADD COLUMN IF NOT EXISTS replaces_category_id     UUID REFERENCES customer_category (id),
    ADD COLUMN IF NOT EXISTS governance_json          JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS ix_policy_set_replaces ON policy_set (replaces_policy_set_id);
CREATE INDEX IF NOT EXISTS ix_customer_category_replaces ON customer_category (replaces_category_id);
CREATE INDEX IF NOT EXISTS ix_customer_category_effective ON customer_category (effective_from, effective_until);

COMMENT ON COLUMN policy_set.governance_json IS
    'Maker/checker history for Policy Set governance. Not wired to live UW.';
COMMENT ON COLUMN customer_category.governance_json IS
    'Maker/checker history for Customer Category governance. Not wired to live UW.';
COMMENT ON COLUMN policy_set.additional_rule_set_ids IS
    'Reserved. Phase-1 governance enforces empty list — Policy Set selects exactly one primary rule set.';
