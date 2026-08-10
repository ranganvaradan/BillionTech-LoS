CREATE TABLE loan_product_repayment_defaults (
    loan_product VARCHAR(80) PRIMARY KEY,
    repayment_mechanism VARCHAR(30) NOT NULL DEFAULT 'SMART_COLLECT',
    pg_provider_code VARCHAR(30),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100)
);

ALTER TABLE loan_product_repayment_defaults
    ADD CONSTRAINT chk_lprd_repayment_mechanism
        CHECK (repayment_mechanism IN ('SMART_COLLECT', 'PAYU_PG', 'API_PG'));

INSERT INTO loan_product_repayment_defaults (loan_product, repayment_mechanism, enabled)
VALUES
    ('PERSONAL_LOAN', 'SMART_COLLECT', TRUE),
    ('BUSINESS_TERM_LOAN', 'SMART_COLLECT', TRUE),
    ('BUSINESS_WC_OD', 'SMART_COLLECT', TRUE),
    ('TERM_LOAN', 'SMART_COLLECT', TRUE),
    ('LOAN_AGAINST_PROPERTY', 'SMART_COLLECT', TRUE),
    ('LOAN_AGAINST_SECURITIES', 'SMART_COLLECT', TRUE),
    ('LOAN_AGAINST_GOLD', 'SMART_COLLECT', TRUE)
ON CONFLICT (loan_product) DO NOTHING;

COMMENT ON TABLE loan_product_repayment_defaults IS
    'LOS global repayment mechanism per loan product (v1 config only; borrower UI wiring is separate)';
