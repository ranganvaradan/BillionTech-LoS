-- Phase C3: Canonical bank accounts, transactions, obligations, quality, duplicates

CREATE TABLE IF NOT EXISTS ci_bank_account (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    entity_id                   UUID,
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    provider_code               VARCHAR(64) NOT NULL,
    institution_name            VARCHAR(200),
    institution_code            VARCHAR(64),
    account_number_hash         VARCHAR(128),
    account_number_last4        VARCHAR(8),
    ifsc                        VARCHAR(20),
    account_type                VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    ownership_type              VARCHAR(40),
    holder_name                 VARCHAR(300),
    holder_name_match_status    VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    holder_name_match_score     NUMERIC(8, 4),
    currency                    VARCHAR(8) NOT NULL DEFAULT 'INR',
    branch                      VARCHAR(200),
    statement_from              DATE,
    statement_to                DATE,
    opening_balance             NUMERIC(18, 2),
    closing_balance             NUMERIC(18, 2),
    sanctioned_limit            NUMERIC(18, 2),
    drawing_power               NUMERIC(18, 2),
    overdraft_limit             NUMERIC(18, 2),
    account_status              VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
    operating_relevance         VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    aggregation_eligible        BOOLEAN NOT NULL DEFAULT TRUE,
    source_quality_status       VARCHAR(40) NOT NULL DEFAULT 'OK',
    parser_version              VARCHAR(40) NOT NULL,
    normalizer_version          VARCHAR(40) NOT NULL,
    idempotency_key             VARCHAR(300),
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ci_bank_account_idem UNIQUE (tenant_id, application_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_acct_app ON ci_bank_account (application_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ci_bank_acct_source ON ci_bank_account (source_record_id);
CREATE INDEX IF NOT EXISTS idx_ci_bank_acct_hash ON ci_bank_account (account_number_hash);

CREATE TABLE IF NOT EXISTS ci_bank_transaction (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    bank_account_id             UUID NOT NULL REFERENCES ci_bank_account (id) ON DELETE CASCADE,
    source_record_id            UUID NOT NULL REFERENCES ci_source_record (id),
    provider_transaction_id     VARCHAR(200),
    transaction_date            DATE NOT NULL,
    value_date                  DATE,
    posting_date                DATE,
    description_raw             TEXT,
    description_normalized      TEXT,
    direction                   VARCHAR(10) NOT NULL,
    amount                      NUMERIC(18, 2) NOT NULL,
    balance_after               NUMERIC(18, 2),
    mode                        VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    utr_reference               VARCHAR(120),
    cheque_number               VARCHAR(40),
    counterparty_raw            VARCHAR(300),
    counterparty_normalized     VARCHAR(300),
    counterparty_account_hash   VARCHAR(128),
    merchant                    VARCHAR(200),
    category                    VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN',
    subcategory                 VARCHAR(64),
    cash_flow_class             VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    purpose_class               VARCHAR(64),
    recurring_flag              BOOLEAN NOT NULL DEFAULT FALSE,
    emi_flag                    BOOLEAN NOT NULL DEFAULT FALSE,
    bounce_flag                 BOOLEAN NOT NULL DEFAULT FALSE,
    return_flag                 BOOLEAN NOT NULL DEFAULT FALSE,
    cash_flag                   BOOLEAN NOT NULL DEFAULT FALSE,
    related_party_flag          BOOLEAN NOT NULL DEFAULT FALSE,
    self_transfer_flag          BOOLEAN NOT NULL DEFAULT FALSE,
    lender_flag                 BOOLEAN NOT NULL DEFAULT FALSE,
    tax_payment_flag            BOOLEAN NOT NULL DEFAULT FALSE,
    salary_flag                 BOOLEAN NOT NULL DEFAULT FALSE,
    business_receipt_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    business_payment_flag       BOOLEAN NOT NULL DEFAULT FALSE,
    confidence                  NUMERIC(8, 4),
    classification_method       VARCHAR(80),
    classifier_version          VARCHAR(40),
    duplicate_status            VARCHAR(40) NOT NULL DEFAULT 'UNIQUE',
    duplicate_of_transaction_id UUID,
    duplicate_group_id          UUID,
    evidence_group_id           UUID,
    source_reference            VARCHAR(300),
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_acct_date ON ci_bank_transaction (bank_account_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_source ON ci_bank_transaction (source_record_id);
CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_category ON ci_bank_transaction (bank_account_id, category);
CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_utr ON ci_bank_transaction (utr_reference) WHERE utr_reference IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ci_bank_txn_provider
    ON ci_bank_transaction (bank_account_id, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ci_bank_transaction_classification (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id              UUID NOT NULL REFERENCES ci_bank_transaction (id) ON DELETE CASCADE,
    category                    VARCHAR(64) NOT NULL,
    subcategory                 VARCHAR(64),
    cash_flow_class             VARCHAR(40),
    method                      VARCHAR(80) NOT NULL,
    classifier_version          VARCHAR(40) NOT NULL,
    confidence                  NUMERIC(8, 4),
    evidence                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_txn_cls ON ci_bank_transaction_classification (transaction_id);

CREATE TABLE IF NOT EXISTS ci_bank_recurring_obligation (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    bank_account_id             UUID REFERENCES ci_bank_account (id),
    lender_name                 VARCHAR(200),
    detected_amount             NUMERIC(18, 2) NOT NULL,
    frequency                   VARCHAR(40) NOT NULL DEFAULT 'MONTHLY',
    first_observed              DATE,
    last_observed               DATE,
    occurrence_count            INT NOT NULL DEFAULT 0,
    regularity_score            NUMERIC(8, 4),
    source_transaction_ids      JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence                  NUMERIC(8, 4),
    method                      VARCHAR(80) NOT NULL,
    method_version              VARCHAR(40) NOT NULL,
    estimated                   BOOLEAN NOT NULL DEFAULT FALSE,
    quality_status              VARCHAR(40) NOT NULL DEFAULT 'OK',
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_obl_app ON ci_bank_recurring_obligation (application_id);

CREATE TABLE IF NOT EXISTS ci_bank_duplicate_group (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    bank_account_id             UUID NOT NULL REFERENCES ci_bank_account (id) ON DELETE CASCADE,
    canonical_transaction_id    UUID,
    matching_basis              VARCHAR(200) NOT NULL,
    confidence                  NUMERIC(8, 4),
    member_transaction_ids      JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_dup_acct ON ci_bank_duplicate_group (bank_account_id);

CREATE TABLE IF NOT EXISTS ci_bank_statement_quality (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    bank_account_id             UUID REFERENCES ci_bank_account (id),
    source_record_id            UUID REFERENCES ci_source_record (id),
    statement_from              DATE,
    statement_to                DATE,
    periods_expected            INT,
    periods_available           INT,
    calendar_days_expected      INT,
    calendar_days_covered       INT,
    completeness_ratio          NUMERIC(8, 4),
    integrity_status            VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN',
    balance_breaks              INT NOT NULL DEFAULT 0,
    duplicate_count             INT NOT NULL DEFAULT 0,
    classification_coverage     NUMERIC(8, 4),
    quality_score               NUMERIC(8, 4),
    findings                    JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata                    JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_bank_quality_app ON ci_bank_statement_quality (application_id);

CREATE TABLE IF NOT EXISTS ci_evidence_group (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    application_id              UUID NOT NULL REFERENCES loan_applications (id) ON DELETE CASCADE,
    group_type                  VARCHAR(80) NOT NULL,
    member_count                INT NOT NULL DEFAULT 0,
    member_ids                  JSONB NOT NULL DEFAULT '[]'::jsonb,
    summary                     JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ci_evidence_group_app ON ci_evidence_group (application_id, group_type);

COMMENT ON TABLE ci_bank_account IS 'Phase C3 provider-neutral bank accounts';
COMMENT ON TABLE ci_bank_transaction IS 'Phase C3 canonical bank transactions';
COMMENT ON TABLE ci_bank_recurring_obligation IS 'Phase C3 EMI/recurring lender obligations';
COMMENT ON TABLE ci_evidence_group IS 'Compact evidence references for large transaction sets';
