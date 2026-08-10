-- V1__init_los_core_schema.sql
-- LOS Core Service — Loan Applications, KYC, Workflow, Documents, Transactions, Audit

-- Loan Applications
CREATE TABLE loan_applications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number  VARCHAR(30) NOT NULL UNIQUE,
    customer_id         UUID NOT NULL,
    borrower_type       VARCHAR(30) NOT NULL,
    loan_product        VARCHAR(50) NOT NULL,
    requested_amount    NUMERIC(15,2),
    interest_rate       NUMERIC(5,2),
    tenure_months       INTEGER,
    status              VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    personal_info       JSONB,
    business_info       JSONB,
    financial_info      JSONB,
    collateral_info     JSONB,
    remarks             VARCHAR(500),
    assigned_to         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ
);

-- KYC Step Results
CREATE TABLE kyc_step_results (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES loan_applications(id),
    step_type           VARCHAR(30) NOT NULL,
    provider            VARCHAR(30) NOT NULL,
    outcome             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    confidence_score    DOUBLE PRECISION DEFAULT 0,
    parsed_data         JSONB,
    raw_response        TEXT,
    transaction_id      VARCHAR(100),
    error_message       VARCHAR(500),
    overridden          BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason     VARCHAR(500),
    override_by         UUID,
    attempt_number      INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

-- Workflow Configurations
CREATE TABLE workflow_configs (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100) NOT NULL,
    borrower_type       VARCHAR(30) NOT NULL,
    loan_product        VARCHAR(50) NOT NULL,
    steps               JSONB NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    version             INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ
);

-- Documents
CREATE TABLE documents (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES loan_applications(id),
    document_type       VARCHAR(50) NOT NULL,
    file_name           VARCHAR(255) NOT NULL,
    storage_key         VARCHAR(512) NOT NULL,
    content_type        VARCHAR(100) NOT NULL,
    file_size           BIGINT NOT NULL,
    checksum            VARCHAR(64),
    uploaded_by         UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transactions (Disbursements, Repayments)
CREATE TABLE transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES loan_applications(id),
    transaction_type    VARCHAR(30) NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'INITIATED',
    reference_number    VARCHAR(100),
    utr_number          VARCHAR(100),
    metadata            JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

-- Audit Events
CREATE TABLE audit_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES loan_applications(id),
    event_type          VARCHAR(50) NOT NULL,
    action              VARCHAR(100) NOT NULL,
    performed_by        UUID,
    ip_address          VARCHAR(45),
    previous_state      JSONB,
    new_state           JSONB,
    description         VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Application Status History (for TAT tracking)
CREATE TABLE application_status_history (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id      UUID NOT NULL REFERENCES loan_applications(id),
    from_status         VARCHAR(30),
    to_status           VARCHAR(30) NOT NULL,
    changed_by          UUID,
    remarks             VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_app_status ON loan_applications(status);
CREATE INDEX idx_app_customer ON loan_applications(customer_id);
CREATE INDEX idx_app_number ON loan_applications(application_number);
CREATE INDEX idx_app_borrower_type ON loan_applications(borrower_type);
CREATE INDEX idx_app_created_at ON loan_applications(created_at);
CREATE INDEX idx_app_personal_info ON loan_applications USING GIN (personal_info);

CREATE INDEX idx_kyc_application ON kyc_step_results(application_id);
CREATE INDEX idx_kyc_step_type ON kyc_step_results(step_type);

CREATE INDEX idx_wf_borrower_product ON workflow_configs(borrower_type, loan_product) WHERE active = TRUE;

CREATE INDEX idx_doc_application ON documents(application_id);

CREATE INDEX idx_txn_application ON transactions(application_id);
CREATE INDEX idx_txn_type_status ON transactions(transaction_type, status);

CREATE INDEX idx_audit_application ON audit_events(application_id);
CREATE INDEX idx_audit_created ON audit_events(created_at);

CREATE INDEX idx_status_history_app ON application_status_history(application_id);
