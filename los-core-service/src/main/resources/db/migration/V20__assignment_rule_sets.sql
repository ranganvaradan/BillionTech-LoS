-- Assignment routing for manual review / underwriting queue (Credit Manager setup).
CREATE TABLE assignment_rule_sets (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    borrower_type       VARCHAR(30)  NOT NULL,
    loan_product        VARCHAR(50)  NOT NULL,
    min_amount          NUMERIC(15, 2),
    max_amount          NUMERIC(15, 2),
    geography           JSONB,
    min_tenure_months   INTEGER,
    max_tenure_months   INTEGER,
    assigned_role       VARCHAR(80)  NOT NULL,
    assigned_user_id    UUID,
    priority            INTEGER      NOT NULL DEFAULT 0,
    active              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ
);

CREATE INDEX idx_assign_rules_match ON assignment_rule_sets (borrower_type, loan_product, active);
CREATE INDEX idx_assign_rules_priority ON assignment_rule_sets (priority DESC);

COMMENT ON TABLE assignment_rule_sets IS 'Routes applications to role/user by borrower/product/ranges/geo; highest priority wins.';
