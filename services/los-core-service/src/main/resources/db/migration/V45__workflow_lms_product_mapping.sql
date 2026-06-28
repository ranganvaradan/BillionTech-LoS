-- Workflow segment → Encore productCode (decoupled from workflow_configs.steps JSON).
-- Natural key matches loan_applications.borrower_type + loan_applications.loan_product (V32 canonical catalog).

CREATE TABLE IF NOT EXISTS workflow_lms_product_mapping (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    borrower_type VARCHAR(30) NOT NULL,
    loan_product VARCHAR(100) NOT NULL,
    encore_product_code VARCHAR(100) NOT NULL,
    encore_product_type VARCHAR(50),
    branch_set_code VARCHAR(50),
    mapping_source VARCHAR(20) NOT NULL DEFAULT 'HEURISTIC',
    confidence NUMERIC(4, 2),
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_workflow_lms_mapping_bt_lp UNIQUE (borrower_type, loan_product)
);

CREATE INDEX IF NOT EXISTS idx_workflow_lms_mapping_encore_code
    ON workflow_lms_product_mapping (encore_product_code);

COMMENT ON TABLE workflow_lms_product_mapping IS
    'Maps LOS segment (borrower_type + loan_product) to Encore LMS productCode; see docs/lms-workflow-product-mapping.md.';

ALTER TABLE loan_applications
    ADD COLUMN IF NOT EXISTS lms_encore_customer_id VARCHAR(100);

COMMENT ON COLUMN loan_applications.lms_encore_customer_id IS
    'Reserved for Encore customer/party id after idempotent LMS customer create (optional parity with bl-core).';

-- Heuristic seeds for V32 canonical products (4 borrower types × 8 products). Business must review before production.
INSERT INTO workflow_lms_product_mapping (borrower_type, loan_product, encore_product_code, encore_product_type,
                                          branch_set_code, mapping_source, confidence, notes)
VALUES
    ('INDIVIDUAL', 'PERSONAL_LOAN', 'Indiviual_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.65,
     'Vendor display string uses typo Indiviual; confirm with Encore catalogue.'),
    ('INDIVIDUAL', 'BUSINESS_WC_OD', 'Working Capital Loan_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.70,
     'Working capital / OD style product in Encore export.'),
    ('INDIVIDUAL', 'BUSINESS_WC_INVOICE_DISCOUNTING', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.45,
     'No dedicated invoice product in heuristic export; placeholder.'),
    ('INDIVIDUAL', 'BUSINESS_TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, 'Generic MGPP term baseline.'),
    ('INDIVIDUAL', 'TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, 'Generic MGPP term baseline.'),
    ('INDIVIDUAL', 'LOAN_AGAINST_PROPERTY', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.35,
     'Weak match — assign dedicated LAP Encore product when available.'),
    ('INDIVIDUAL', 'LOAN_AGAINST_SECURITIES', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.30,
     'LAS / securities — placeholder.'),
    ('INDIVIDUAL', 'LOAN_AGAINST_GOLD', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.25,
     'No gold-specific string in export sample — placeholder.'),

    ('PROPRIETOR', 'PERSONAL_LOAN', 'Indiviual_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.65, 'Same as INDIVIDUAL until policy differs.'),
    ('PROPRIETOR', 'BUSINESS_WC_OD', 'Working Capital Loan_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.70, NULL),
    ('PROPRIETOR', 'BUSINESS_WC_INVOICE_DISCOUNTING', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.45, NULL),
    ('PROPRIETOR', 'BUSINESS_TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('PROPRIETOR', 'TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('PROPRIETOR', 'LOAN_AGAINST_PROPERTY', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.35, NULL),
    ('PROPRIETOR', 'LOAN_AGAINST_SECURITIES', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.30, NULL),
    ('PROPRIETOR', 'LOAN_AGAINST_GOLD', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.25, NULL),

    ('PARTNERSHIP', 'PERSONAL_LOAN', 'Indiviual_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.60, NULL),
    ('PARTNERSHIP', 'BUSINESS_WC_OD', 'Working Capital Loan_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.70, NULL),
    ('PARTNERSHIP', 'BUSINESS_WC_INVOICE_DISCOUNTING', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.45, NULL),
    ('PARTNERSHIP', 'BUSINESS_TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('PARTNERSHIP', 'TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('PARTNERSHIP', 'LOAN_AGAINST_PROPERTY', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.35, NULL),
    ('PARTNERSHIP', 'LOAN_AGAINST_SECURITIES', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.30, NULL),
    ('PARTNERSHIP', 'LOAN_AGAINST_GOLD', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.25, NULL),

    ('COMPANY', 'PERSONAL_LOAN', 'Indiviual_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, 'Unusual segment; confirm policy.'),
    ('COMPANY', 'BUSINESS_WC_OD', 'Working Capital Loan_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.70, NULL),
    ('COMPANY', 'BUSINESS_WC_INVOICE_DISCOUNTING', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.45, NULL),
    ('COMPANY', 'BUSINESS_TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('COMPANY', 'TERM_LOAN', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.55, NULL),
    ('COMPANY', 'LOAN_AGAINST_PROPERTY', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.35, NULL),
    ('COMPANY', 'LOAN_AGAINST_SECURITIES', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.30, NULL),
    ('COMPANY', 'LOAN_AGAINST_GOLD', 'EE_10106', 'Loans', 'MGPP', 'HEURISTIC', 0.25, NULL)
ON CONFLICT (borrower_type, loan_product) DO NOTHING;
