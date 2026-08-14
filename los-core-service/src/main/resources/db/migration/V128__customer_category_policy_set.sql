-- CUSTOMER-CATEGORY-IMPLEMENTATION-STEP1
-- Policy Set + Customer Category configuration foundation.
-- Does NOT wire into live underwriting routing.

CREATE TABLE policy_set (
    id                          UUID PRIMARY KEY,
    code                        VARCHAR(64)  NOT NULL,
    version_no                  INT          NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    description                 TEXT,
    status                      VARCHAR(20)  NOT NULL,
    primary_rule_set_id         UUID         NOT NULL REFERENCES underwriting_rule_sets (id),
    additional_rule_set_ids     JSONB        NOT NULL DEFAULT '[]'::jsonb,
    scorecard_id                UUID         REFERENCES underwriting_scorecards (id),
    seed_source_rule_set_id     UUID,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(120),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                  VARCHAR(120),
    activated_at                TIMESTAMPTZ,
    activated_by                VARCHAR(120),
    retired_at                  TIMESTAMPTZ,
    retired_by                  VARCHAR(120),
    CONSTRAINT chk_policy_set_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT uq_policy_set_code_version UNIQUE (code, version_no)
);

CREATE UNIQUE INDEX ux_policy_set_one_active
    ON policy_set (code)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_policy_set_status ON policy_set (status);
CREATE INDEX ix_policy_set_seed_source ON policy_set (seed_source_rule_set_id);

CREATE TABLE customer_category (
    id                          UUID PRIMARY KEY,
    code                        VARCHAR(64)  NOT NULL,
    version_no                  INT          NOT NULL,
    name                        VARCHAR(200) NOT NULL,
    description                 TEXT,
    status                      VARCHAR(20)  NOT NULL,
    borrower_type               VARCHAR(32)  NOT NULL,
    loan_product                VARCHAR(80)  NOT NULL,
    intake_segment              VARCHAR(32)  NOT NULL,
    min_amount                  NUMERIC(15, 2),
    max_amount                  NUMERIC(15, 2),
    policy_set_id               UUID         NOT NULL REFERENCES policy_set (id),
    seed_source_rule_set_id     UUID,
    review_status               VARCHAR(40),
    inference_notes             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(120),
    updated_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by                  VARCHAR(120),
    activated_at                TIMESTAMPTZ,
    activated_by                VARCHAR(120),
    retired_at                  TIMESTAMPTZ,
    retired_by                  VARCHAR(120),
    CONSTRAINT chk_customer_category_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_customer_category_amount CHECK (
        (min_amount IS NULL OR min_amount >= 0)
        AND (max_amount IS NULL OR max_amount >= 0)
        AND (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount)
    ),
    CONSTRAINT uq_customer_category_code_version UNIQUE (code, version_no)
);

CREATE UNIQUE INDEX ux_customer_category_one_active
    ON customer_category (code)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_customer_category_status ON customer_category (status);
CREATE INDEX ix_customer_category_match
    ON customer_category (borrower_type, loan_product, intake_segment);
CREATE INDEX ix_customer_category_seed_source ON customer_category (seed_source_rule_set_id);
CREATE INDEX ix_customer_category_policy_set ON customer_category (policy_set_id);

COMMENT ON TABLE policy_set IS
    'Phase-1 Policy Set — references existing live underwriting_rule_sets / scorecards; not a rule engine.';
COMMENT ON TABLE customer_category IS
    'Phase-1 Customer Category routing definition. LIVE UW not wired in Step 1. policy_set_id = exactly one Policy Set.';
COMMENT ON COLUMN customer_category.borrower_type IS
    'BorrowerType enum name or literal ANY';
COMMENT ON COLUMN customer_category.loan_product IS
    'Exact loanProduct string or literal ANY';
COMMENT ON COLUMN customer_category.intake_segment IS
    'IntakeSegment enum name or literal ANY';
COMMENT ON COLUMN customer_category.min_amount IS
    'NULL = unbounded below; inclusive when set';
COMMENT ON COLUMN customer_category.max_amount IS
    'NULL = unbounded above; inclusive when set';
