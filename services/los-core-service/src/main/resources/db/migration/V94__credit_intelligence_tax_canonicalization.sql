-- Phase C4: ITR / AIS / Form 26AS canonicalization tables

CREATE TABLE IF NOT EXISTS ci_itr_return (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    subject_entity_id           UUID,
    subject_scope               VARCHAR(40) NOT NULL DEFAULT 'BORROWER_ENTITY',
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    pan_hash                    VARCHAR(128),
    pan_last4                   VARCHAR(8),
    assessment_year             VARCHAR(9) NOT NULL,
    financial_year              VARCHAR(9),
    itr_form                    VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    filing_date                 DATE,
    filing_section              VARCHAR(80),
    ack_reference_masked        VARCHAR(80),
    filing_status               VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    return_version_type         VARCHAR(40) NOT NULL DEFAULT 'ORIGINAL',
    is_effective                BOOLEAN NOT NULL DEFAULT TRUE,
    revised_of_return_id        UUID,
    tax_regime                  VARCHAR(40),
    residential_status          VARCHAR(40),
    audit_applicable            BOOLEAN,
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version              VARCHAR(40) NOT NULL,
    normalizer_version          VARCHAR(40) NOT NULL,
    idempotency_key             VARCHAR(300),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_itr_return_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_itr_return_app ON ci_itr_return (application_id, assessment_year DESC);
CREATE INDEX IF NOT EXISTS idx_ci_itr_return_source ON ci_itr_return (source_record_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_itr_effective
    ON ci_itr_return (application_id, subject_scope, assessment_year)
    WHERE is_effective = TRUE;

CREATE TABLE IF NOT EXISTS ci_itr_income (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    itr_return_id               UUID NOT NULL REFERENCES ci_itr_return (id) ON DELETE CASCADE,
    salary_income               NUMERIC(18, 2),
    house_property_income       NUMERIC(18, 2),
    business_profession_income  NUMERIC(18, 2),
    capital_gains               NUMERIC(18, 2),
    other_sources               NUMERIC(18, 2),
    gross_total_income          NUMERIC(18, 2),
    chapter_via_deductions      NUMERIC(18, 2),
    total_income                NUMERIC(18, 2),
    exempt_income               NUMERIC(18, 2),
    agricultural_income         NUMERIC(18, 2),
    foreign_income              NUMERIC(18, 2),
    extraction_quality          VARCHAR(40) NOT NULL DEFAULT 'OK',
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_itr_income UNIQUE (itr_return_id)
);

CREATE TABLE IF NOT EXISTS ci_itr_business_financials (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    itr_return_id               UUID NOT NULL REFERENCES ci_itr_return (id) ON DELETE CASCADE,
    gross_receipts              NUMERIC(18, 2),
    sales_turnover              NUMERIC(18, 2),
    gross_profit                NUMERIC(18, 2),
    ebitda                      NUMERIC(18, 2),
    depreciation                NUMERIC(18, 2),
    finance_cost                NUMERIC(18, 2),
    profit_before_tax           NUMERIC(18, 2),
    profit_after_tax            NUMERIC(18, 2),
    inventory                   NUMERIC(18, 2),
    trade_receivables           NUMERIC(18, 2),
    cash_and_bank               NUMERIC(18, 2),
    fixed_assets                NUMERIC(18, 2),
    total_assets                NUMERIC(18, 2),
    trade_payables              NUMERIC(18, 2),
    short_term_borrowings       NUMERIC(18, 2),
    long_term_borrowings        NUMERIC(18, 2),
    total_borrowings            NUMERIC(18, 2),
    total_liabilities           NUMERIC(18, 2),
    capital                     NUMERIC(18, 2),
    net_worth                   NUMERIC(18, 2),
    extraction_quality          VARCHAR(40) NOT NULL DEFAULT 'OK',
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_itr_biz UNIQUE (itr_return_id)
);

CREATE TABLE IF NOT EXISTS ci_itr_presumptive_income (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    itr_return_id               UUID NOT NULL REFERENCES ci_itr_return (id) ON DELETE CASCADE,
    applicable_section          VARCHAR(40) NOT NULL,
    gross_receipts              NUMERIC(18, 2),
    digital_receipts            NUMERIC(18, 2),
    cash_receipts               NUMERIC(18, 2),
    declared_presumptive_income NUMERIC(18, 2),
    declared_margin             NUMERIC(8, 4),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_itr_presumptive ON ci_itr_presumptive_income (itr_return_id);

CREATE TABLE IF NOT EXISTS ci_itr_tax_summary (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    itr_return_id               UUID NOT NULL REFERENCES ci_itr_return (id) ON DELETE CASCADE,
    tax_liability               NUMERIC(18, 2),
    tax_payable                 NUMERIC(18, 2),
    tax_paid                    NUMERIC(18, 2),
    tds                         NUMERIC(18, 2),
    tcs                         NUMERIC(18, 2),
    advance_tax                 NUMERIC(18, 2),
    self_assessment_tax         NUMERIC(18, 2),
    mat_amt                     NUMERIC(18, 2),
    refund_claimed              NUMERIC(18, 2),
    refund_received             NUMERIC(18, 2),
    outstanding_demand          NUMERIC(18, 2),
    interest_penalty            NUMERIC(18, 2),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_itr_tax UNIQUE (itr_return_id)
);

CREATE TABLE IF NOT EXISTS ci_tax_return_revision (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    assessment_year             VARCHAR(9) NOT NULL,
    original_return_id          UUID REFERENCES ci_itr_return (id),
    effective_return_id         UUID REFERENCES ci_itr_return (id),
    selection_basis             VARCHAR(120) NOT NULL,
    selection_version           VARCHAR(40) NOT NULL DEFAULT 'ITR_EFFECTIVE_RETURN_SELECTION_V1',
    source_references           JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_tax_rev_app ON ci_tax_return_revision (application_id, assessment_year);

CREATE TABLE IF NOT EXISTS ci_ais_summary (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    subject_entity_id           UUID,
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    financial_year              VARCHAR(9) NOT NULL,
    pan_hash                    VARCHAR(128),
    total_reported_value        NUMERIC(18, 2),
    information_source_count    INT NOT NULL DEFAULT 0,
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version              VARCHAR(40) NOT NULL,
    normalizer_version          VARCHAR(40) NOT NULL,
    idempotency_key             VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_ais_sum_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_ais_sum_app ON ci_ais_summary (application_id, financial_year);

CREATE TABLE IF NOT EXISTS ci_ais_information (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ais_summary_id              UUID NOT NULL REFERENCES ci_ais_summary (id) ON DELETE CASCADE,
    category                    VARCHAR(64) NOT NULL DEFAULT 'OTHER',
    information_code            VARCHAR(80),
    reporting_entity            VARCHAR(300),
    period_start                DATE,
    period_end                  DATE,
    amount                      NUMERIC(18, 2),
    accepted_value              NUMERIC(18, 2),
    modified_value              NUMERIC(18, 2),
    status                      VARCHAR(40),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_ais_info ON ci_ais_information (ais_summary_id, category);

CREATE TABLE IF NOT EXISTS ci_form26as_summary (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    subject_entity_id           UUID,
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    financial_year              VARCHAR(9) NOT NULL,
    pan_hash                    VARCHAR(128),
    total_tds                   NUMERIC(18, 2),
    total_tcs                   NUMERIC(18, 2),
    advance_tax                 NUMERIC(18, 2),
    self_assessment_tax         NUMERIC(18, 2),
    refund                      NUMERIC(18, 2),
    other_credits               NUMERIC(18, 2),
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version              VARCHAR(40) NOT NULL,
    normalizer_version          VARCHAR(40) NOT NULL,
    idempotency_key             VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_26as_sum_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_26as_sum_app ON ci_form26as_summary (application_id, financial_year);

CREATE TABLE IF NOT EXISTS ci_form26as_entry (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    form26as_summary_id         UUID NOT NULL REFERENCES ci_form26as_summary (id) ON DELETE CASCADE,
    section_code                VARCHAR(40),
    deductor_name               VARCHAR(300),
    deductor_tan_hash           VARCHAR(128),
    booking_date                DATE,
    amount_paid_credited        NUMERIC(18, 2),
    tax_deducted                NUMERIC(18, 2),
    tax_deposited               NUMERIC(18, 2),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_26as_entry ON ci_form26as_entry (form26as_summary_id);

COMMENT ON TABLE ci_itr_return IS 'Phase C4 provider-neutral ITR return';
COMMENT ON TABLE ci_ais_summary IS 'Phase C4 AIS summary (future-ready; may be empty until provider wired)';
COMMENT ON TABLE ci_form26as_summary IS 'Phase C4 Form 26AS summary (future-ready)';
