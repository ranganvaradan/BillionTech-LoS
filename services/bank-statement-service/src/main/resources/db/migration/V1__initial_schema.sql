-- Bank Statement Service — Initial Schema

CREATE TABLE bank_statements (
    id                      BIGSERIAL PRIMARY KEY,
    file_name               VARCHAR(500) NOT NULL,
    file_path               VARCHAR(1000),
    file_hash               VARCHAR(128),
    file_size               BIGINT,
    content_type            VARCHAR(100),
    account_holder_name     VARCHAR(500),
    account_number_masked   VARCHAR(50),
    bank_name               VARCHAR(200),
    bank_code               VARCHAR(50),
    ifsc_code               VARCHAR(20),
    branch_name             VARCHAR(300),
    account_type            VARCHAR(50),
    statement_from_date     DATE,
    statement_to_date       DATE,
    opening_balance         NUMERIC(18,2),
    closing_balance         NUMERIC(18,2),
    total_transactions      INTEGER DEFAULT 0,
    total_credit_amount     NUMERIC(18,2) DEFAULT 0,
    total_debit_amount      NUMERIC(18,2) DEFAULT 0,
    parsing_status          VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    parsing_error           TEXT,
    tamper_check_status     VARCHAR(30),
    tamper_check_details    TEXT,
    application_id          VARCHAR(100),
    batch_id                VARCHAR(100),
    uploaded_by             VARCHAR(200),
    tenant_id               VARCHAR(100),
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_statements_status ON bank_statements(parsing_status);
CREATE INDEX idx_statements_application ON bank_statements(application_id);
CREATE INDEX idx_statements_batch ON bank_statements(batch_id);
CREATE INDEX idx_statements_bank ON bank_statements(bank_code);

CREATE TABLE bank_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    statement_id        BIGINT NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    transaction_date    DATE NOT NULL,
    value_date          DATE,
    narration           TEXT,
    reference_number    VARCHAR(200),
    debit_amount        NUMERIC(18,2) DEFAULT 0,
    credit_amount       NUMERIC(18,2) DEFAULT 0,
    running_balance     NUMERIC(18,2),
    category            VARCHAR(50),
    sub_category        VARCHAR(100),
    channel             VARCHAR(30),
    counterparty_name   VARCHAR(500),
    counterparty_account VARCHAR(50),
    is_bounce           BOOLEAN DEFAULT FALSE,
    is_reversal         BOOLEAN DEFAULT FALSE,
    is_circular         BOOLEAN DEFAULT FALSE,
    raw_description     TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_txn_statement ON bank_transactions(statement_id);
CREATE INDEX idx_txn_date ON bank_transactions(transaction_date);
CREATE INDEX idx_txn_category ON bank_transactions(category);

CREATE TABLE statement_analysis (
    id                          BIGSERIAL PRIMARY KEY,
    statement_id                BIGINT NOT NULL UNIQUE REFERENCES bank_statements(id) ON DELETE CASCADE,
    avg_bank_balance            NUMERIC(18,2),
    min_balance                 NUMERIC(18,2),
    max_balance                 NUMERIC(18,2),
    balance_volatility          NUMERIC(10,4),
    detected_salary_amount      NUMERIC(18,2),
    salary_frequency            VARCHAR(20),
    salary_day_of_month         INTEGER,
    salary_confidence           NUMERIC(5,2),
    total_income                NUMERIC(18,2),
    non_salary_income           NUMERIC(18,2),
    imputed_income              NUMERIC(18,2),
    income_stability_score      NUMERIC(5,2),
    emi_count                   INTEGER DEFAULT 0,
    total_emi_amount            NUMERIC(18,2) DEFAULT 0,
    foir                        NUMERIC(5,2),
    total_obligations           NUMERIC(18,2) DEFAULT 0,
    rent_amount                 NUMERIC(18,2) DEFAULT 0,
    insurance_amount            NUMERIC(18,2) DEFAULT 0,
    total_credits               NUMERIC(18,2) DEFAULT 0,
    total_debits                NUMERIC(18,2) DEFAULT 0,
    net_cash_flow               NUMERIC(18,2),
    credit_debit_ratio          NUMERIC(10,4),
    cash_flow_stability         NUMERIC(5,2),
    bounce_count                INTEGER DEFAULT 0,
    bounce_amount               NUMERIC(18,2) DEFAULT 0,
    circular_txn_count          INTEGER DEFAULT 0,
    cash_deposit_ratio          NUMERIC(5,2),
    cash_withdrawal_ratio       NUMERIC(5,2),
    red_flags                   JSONB DEFAULT '[]'::JSONB,
    creditworthiness_score      NUMERIC(5,2),
    income_confidence_score     NUMERIC(5,2),
    repayment_capacity_score    NUMERIC(5,2),
    top_credit_sources          JSONB DEFAULT '[]'::JSONB,
    top_debit_destinations      JSONB DEFAULT '[]'::JSONB,
    analysis_completed_at       TIMESTAMP,
    analysis_version            VARCHAR(20) DEFAULT '1.0',
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_statement ON statement_analysis(statement_id);

CREATE TABLE monthly_summaries (
    id                      BIGSERIAL PRIMARY KEY,
    statement_id            BIGINT NOT NULL REFERENCES bank_statements(id) ON DELETE CASCADE,
    year                    INTEGER NOT NULL,
    month                   INTEGER NOT NULL,
    opening_balance         NUMERIC(18,2),
    closing_balance         NUMERIC(18,2),
    avg_eod_balance         NUMERIC(18,2),
    min_eod_balance         NUMERIC(18,2),
    max_eod_balance         NUMERIC(18,2),
    total_credits           NUMERIC(18,2) DEFAULT 0,
    total_debits            NUMERIC(18,2) DEFAULT 0,
    net_cash_flow           NUMERIC(18,2),
    credit_count            INTEGER DEFAULT 0,
    debit_count             INTEGER DEFAULT 0,
    salary_amount           NUMERIC(18,2),
    emi_amount              NUMERIC(18,2),
    bounce_count            INTEGER DEFAULT 0,
    category_summary        JSONB DEFAULT '{}'::JSONB,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(statement_id, year, month)
);

CREATE INDEX idx_monthly_statement ON monthly_summaries(statement_id);

CREATE TABLE bank_parser_configs (
    id                  BIGSERIAL PRIMARY KEY,
    bank_code           VARCHAR(50) NOT NULL UNIQUE,
    bank_name           VARCHAR(200) NOT NULL,
    header_patterns     JSONB NOT NULL DEFAULT '[]'::JSONB,
    date_formats        JSONB NOT NULL DEFAULT '[]'::JSONB,
    column_mappings     JSONB NOT NULL DEFAULT '{}'::JSONB,
    narration_patterns  JSONB DEFAULT '{}'::JSONB,
    detection_keywords  JSONB DEFAULT '[]'::JSONB,
    supported_formats   JSONB DEFAULT '["PDF"]'::JSONB,
    is_active           BOOLEAN DEFAULT TRUE,
    version             INTEGER DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE api_keys (
    id              BIGSERIAL PRIMARY KEY,
    key_hash        VARCHAR(256) NOT NULL UNIQUE,
    key_prefix      VARCHAR(10) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    is_active       BOOLEAN DEFAULT TRUE,
    expires_at      TIMESTAMP,
    last_used_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_api_keys_hash ON api_keys(key_hash);
CREATE INDEX idx_api_keys_active ON api_keys(is_active);
