-- Phase C2: Canonical GST registration, return periods, financials, revisions

CREATE TABLE IF NOT EXISTS ci_gst_registration (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    entity_id                   UUID,
    gstin                       VARCHAR(20) NOT NULL,
    pan_hash                    VARCHAR(128),
    legal_name                  VARCHAR(300),
    trade_name                  VARCHAR(300),
    registration_type           VARCHAR(80),
    registration_date           DATE,
    cancellation_date           DATE,
    registration_status         VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    state_code                  VARCHAR(10),
    principal_place             VARCHAR(500),
    business_constitution       VARCHAR(120),
    taxpayer_type               VARCHAR(80),
    filing_frequency            VARCHAR(40),
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version              VARCHAR(40) NOT NULL,
    normalizer_version          VARCHAR(40) NOT NULL,
    idempotency_key             VARCHAR(300),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_gst_reg_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_gst_reg_app ON ci_gst_registration (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_gst_reg_gstin ON ci_gst_registration (gstin);
CREATE INDEX IF NOT EXISTS idx_ci_gst_reg_source ON ci_gst_registration (source_record_id);

CREATE TABLE IF NOT EXISTS ci_gst_return_period (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    gst_registration_id         UUID NOT NULL REFERENCES ci_gst_registration (id) ON DELETE CASCADE,
    return_type                 VARCHAR(40) NOT NULL,
    financial_year              VARCHAR(9) NOT NULL,
    period_yyyy_mm              VARCHAR(7) NOT NULL,
    due_date                    DATE,
    filed_date                  DATE,
    filing_status               VARCHAR(40) NOT NULL,
    filing_delay_days           INT,
    arn_reference               VARCHAR(120),
    is_effective                BOOLEAN NOT NULL DEFAULT TRUE,
    superseded_by_period_id     UUID,
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_gst_period_reg ON ci_gst_return_period (gst_registration_id, return_type, period_yyyy_mm);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_gst_period_effective
    ON ci_gst_return_period (gst_registration_id, return_type, period_yyyy_mm)
    WHERE is_effective = TRUE;

CREATE TABLE IF NOT EXISTS ci_gst_period_financials (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    return_period_id            UUID NOT NULL REFERENCES ci_gst_return_period (id) ON DELETE CASCADE,
    return_type                 VARCHAR(40) NOT NULL,
    taxable_turnover            NUMERIC(18, 2),
    zero_rated_turnover         NUMERIC(18, 2),
    exempt_turnover             NUMERIC(18, 2),
    non_gst_turnover            NUMERIC(18, 2),
    outward_taxable_supplies    NUMERIC(18, 2),
    reverse_charge_supplies     NUMERIC(18, 2),
    tax_liability               NUMERIC(18, 2),
    igst                        NUMERIC(18, 2),
    cgst                        NUMERIC(18, 2),
    sgst                        NUMERIC(18, 2),
    cess                        NUMERIC(18, 2),
    tax_paid_cash               NUMERIC(18, 2),
    tax_paid_itc                NUMERIC(18, 2),
    itc_available               NUMERIC(18, 2),
    itc_claimed                 NUMERIC(18, 2),
    credit_note_value           NUMERIC(18, 2),
    debit_note_value            NUMERIC(18, 2),
    b2b_invoice_count           INT,
    b2c_invoice_count           INT,
    einvoice_value              NUMERIC(18, 2),
    eway_bill_value             NUMERIC(18, 2),
    extraction_quality          VARCHAR(40) NOT NULL DEFAULT 'OK',
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_gst_fin_period UNIQUE (return_period_id)
);

CREATE INDEX IF NOT EXISTS idx_ci_gst_fin_period ON ci_gst_period_financials (return_period_id);

CREATE TABLE IF NOT EXISTS ci_gst_return_revision (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    gst_registration_id         UUID NOT NULL REFERENCES ci_gst_registration (id) ON DELETE CASCADE,
    return_type                 VARCHAR(40) NOT NULL,
    period_yyyy_mm              VARCHAR(7) NOT NULL,
    original_period_id          UUID REFERENCES ci_gst_return_period (id),
    effective_period_id         UUID REFERENCES ci_gst_return_period (id),
    selection_basis             VARCHAR(120) NOT NULL,
    source_references           JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_gst_rev_reg ON ci_gst_return_revision (gst_registration_id, period_yyyy_mm);

COMMENT ON TABLE ci_gst_registration IS 'Phase C2 provider-neutral GST registration';
COMMENT ON TABLE ci_gst_return_period IS 'Phase C2 GST return periods (GSTR1/GSTR3B)';
COMMENT ON TABLE ci_gst_period_financials IS 'Phase C2 period-level GST financial amounts';
COMMENT ON TABLE ci_gst_return_revision IS 'Phase C2 amended/duplicate return selection provenance';
