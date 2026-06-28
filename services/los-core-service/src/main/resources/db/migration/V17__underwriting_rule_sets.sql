-- Configurable underwriting rule sets (LOS_Design_v2) — match by borrower/product, ranges, geography; drive recommendation via rules_json.
CREATE TABLE underwriting_rule_sets (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(200) NOT NULL,
    borrower_type      VARCHAR(30)  NOT NULL,
    loan_product       VARCHAR(50)  NOT NULL,
    min_amount         NUMERIC(15, 2),
    max_amount         NUMERIC(15, 2),
    geography          JSONB,
    min_tenure_months  INTEGER,
    max_tenure_months  INTEGER,
    priority           INTEGER      NOT NULL DEFAULT 0,
    active             BOOLEAN      NOT NULL DEFAULT FALSE,
    rules_json         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ
);

CREATE INDEX idx_uw_rules_borrower_product_active
    ON underwriting_rule_sets (borrower_type, loan_product, active);
CREATE INDEX idx_uw_rules_priority
    ON underwriting_rule_sets (priority DESC);

COMMENT ON TABLE underwriting_rule_sets IS 'Demo/configurable credit policy. Selection: match filters, highest priority, active only.';
