-- Phase C1: Canonical bureau reports, tradelines, payment history, inquiries, taxonomy, metrics

CREATE TABLE IF NOT EXISTS ci_bureau_report (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    source_record_id        UUID NOT NULL REFERENCES ci_source_record (id),
    subject_entity_id       UUID,
    subject_type            VARCHAR(40) NOT NULL,
    provider_code           VARCHAR(64) NOT NULL,
    provider_report_ref     VARCHAR(200),
    report_date             DATE,
    score                   INT,
    score_type              VARCHAR(80),
    risk_grade              VARCHAR(40),
    name_match_score        NUMERIC(8, 4),
    identifier_match_score  NUMERIC(8, 4),
    address_match_score     NUMERIC(8, 4),
    quality_status          VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version          VARCHAR(40) NOT NULL,
    normalizer_version      VARCHAR(40) NOT NULL,
    tradelines_present      BOOLEAN NOT NULL DEFAULT FALSE,
    tradeline_extraction_status VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    idempotency_key         VARCHAR(300),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_bureau_report_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_report_app ON ci_bureau_report (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_bureau_report_source ON ci_bureau_report (source_record_id);
CREATE INDEX IF NOT EXISTS idx_ci_bureau_report_provider ON ci_bureau_report (provider_code, provider_report_ref);

CREATE TABLE IF NOT EXISTS ci_bureau_tradeline (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    bureau_report_id        UUID NOT NULL REFERENCES ci_bureau_report (id) ON DELETE CASCADE,
    provider_tradeline_ref  VARCHAR(200),
    lender_name             VARCHAR(200),
    account_type_raw        VARCHAR(120),
    product_category        VARCHAR(64) NOT NULL,
    ownership_type          VARCHAR(40),
    secured                 BOOLEAN,
    revolving               BOOLEAN,
    opened_date             DATE,
    closed_date             DATE,
    last_reported_date      DATE,
    sanctioned_amount       NUMERIC(18, 2),
    high_credit             NUMERIC(18, 2),
    current_balance         NUMERIC(18, 2),
    overdue_amount          NUMERIC(18, 2),
    emi_amount              NUMERIC(18, 2),
    interest_rate           NUMERIC(10, 4),
    tenure_months           INT,
    asset_classification    VARCHAR(80),
    suit_filed              BOOLEAN NOT NULL DEFAULT FALSE,
    wilful_default          BOOLEAN NOT NULL DEFAULT FALSE,
    written_off_amount      NUMERIC(18, 2),
    settlement_amount       NUMERIC(18, 2),
    restructured            BOOLEAN NOT NULL DEFAULT FALSE,
    settled                 BOOLEAN NOT NULL DEFAULT FALSE,
    written_off             BOOLEAN NOT NULL DEFAULT FALSE,
    collateral_type         VARCHAR(80),
    collateral_value        NUMERIC(18, 2),
    account_status          VARCHAR(80),
    data_quality_status     VARCHAR(40) NOT NULL DEFAULT 'OK',
    is_live                 BOOLEAN,
    live_definition_version VARCHAR(80),
    duplicate_of_tradeline_id UUID,
    duplicate_group_id      UUID,
    source_reference        VARCHAR(300),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_tl_report ON ci_bureau_tradeline (bureau_report_id);
CREATE INDEX IF NOT EXISTS idx_ci_bureau_tl_category ON ci_bureau_tradeline (bureau_report_id, product_category);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_bureau_tl_provider_ref
    ON ci_bureau_tradeline (bureau_report_id, provider_tradeline_ref)
    WHERE provider_tradeline_ref IS NOT NULL;

CREATE TABLE IF NOT EXISTS ci_bureau_payment_history (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tradeline_id            UUID NOT NULL REFERENCES ci_bureau_tradeline (id) ON DELETE CASCADE,
    month                   DATE NOT NULL,
    dpd                     INT,
    status                  VARCHAR(40) NOT NULL,
    provider_raw_status     VARCHAR(80),
    estimated               BOOLEAN NOT NULL DEFAULT FALSE,
    source_reference        VARCHAR(300),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_bureau_ph_month UNIQUE (tradeline_id, month)
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_ph_tl ON ci_bureau_payment_history (tradeline_id);

CREATE TABLE IF NOT EXISTS ci_bureau_inquiry (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bureau_report_id        UUID NOT NULL REFERENCES ci_bureau_report (id) ON DELETE CASCADE,
    inquiry_date            DATE,
    member_name             VARCHAR(200),
    purpose                 VARCHAR(120),
    amount                  NUMERIC(18, 2),
    source_reference        VARCHAR(300),
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_inq_report ON ci_bureau_inquiry (bureau_report_id);

CREATE TABLE IF NOT EXISTS ci_bureau_product_mapping (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    provider_code           VARCHAR(64) NOT NULL,
    provider_product_code   VARCHAR(120),
    provider_product_desc   VARCHAR(200),
    canonical_category      VARCHAR(64) NOT NULL,
    secured                 BOOLEAN,
    revolving               BOOLEAN,
    applicability           VARCHAR(40) NOT NULL DEFAULT 'BOTH',
    mapping_version         VARCHAR(40) NOT NULL,
    effective_from          DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to            DATE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_product_map
    ON ci_bureau_product_mapping (provider_code, provider_product_code, mapping_version);

CREATE TABLE IF NOT EXISTS ci_bureau_duplicate_group (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    bureau_report_id        UUID NOT NULL REFERENCES ci_bureau_report (id) ON DELETE CASCADE,
    selected_tradeline_id   UUID,
    matching_basis          VARCHAR(200) NOT NULL,
    confidence              NUMERIC(8, 4),
    human_review_required   BOOLEAN NOT NULL DEFAULT FALSE,
    member_tradeline_ids    JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bureau_dup_report ON ci_bureau_duplicate_group (bureau_report_id);

CREATE TABLE IF NOT EXISTS ci_metric_definition (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_code             VARCHAR(120) NOT NULL,
    version                 VARCHAR(40) NOT NULL,
    description             TEXT,
    domain                  VARCHAR(64) NOT NULL DEFAULT 'BUREAU',
    status                  VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    definition_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_metric_def UNIQUE (metric_code, version)
);

CREATE TABLE IF NOT EXISTS ci_metric_result (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    application_id          UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    bureau_report_id        UUID REFERENCES ci_bureau_report (id),
    fact_snapshot_id        UUID REFERENCES ci_fact_snapshot (id),
    evaluation_id           UUID REFERENCES ci_credit_evaluation (id),
    metric_code             VARCHAR(120) NOT NULL,
    metric_version          VARCHAR(40) NOT NULL,
    outcome                 VARCHAR(40) NOT NULL,
    value                   JSONB,
    data_quality_status     VARCHAR(40) NOT NULL DEFAULT 'OK',
    included_references     JSONB NOT NULL DEFAULT '[]'::jsonb,
    excluded_references     JSONB NOT NULL DEFAULT '[]'::jsonb,
    unknown_count           INT NOT NULL DEFAULT 0,
    source_record_ids       JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence                JSONB NOT NULL DEFAULT '{}'::jsonb,
    metadata                JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_metric_result_app ON ci_metric_result (application_id, metric_code, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_metric_result_report ON ci_metric_result (bureau_report_id);

COMMENT ON TABLE ci_bureau_report IS 'Phase C1 provider-neutral bureau report';
COMMENT ON TABLE ci_bureau_tradeline IS 'Phase C1 normalized tradelines';
COMMENT ON TABLE ci_metric_result IS 'Phase C1 deterministic bureau metrics with evidence';
