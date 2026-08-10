-- PayU collections for LOS personal/term loans (not invoice discounting — that uses PLP).

CREATE TABLE los_loan_payment_transactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pg_transaction_ref  VARCHAR(100) NOT NULL UNIQUE,
    borrower_user_id    UUID NOT NULL,
    application_id      UUID NOT NULL,
    total_amount        NUMERIC(15, 2) NOT NULL,
    gateway             VARCHAR(30) NOT NULL DEFAULT 'PAYU',
    status              VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    payu_mihpayid       VARCHAR(100),
    portal_source       VARCHAR(30) NOT NULL DEFAULT 'LOS_LOAN',
    raw_callback_json   JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_los_loan_pay_txn_status CHECK (status IN ('INITIATED', 'SUCCESS', 'FAILED', 'VERIFY_PENDING'))
);

CREATE INDEX idx_los_loan_pay_txn_borrower ON los_loan_payment_transactions (borrower_user_id, status);
CREATE INDEX idx_los_loan_pay_txn_app ON los_loan_payment_transactions (application_id, status);

CREATE TABLE los_pg_settlement_batches (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    settlement_date     DATE NOT NULL,
    settlement_utr      VARCHAR(100) NOT NULL,
    settlement_mode     VARCHAR(30) NOT NULL DEFAULT 'PG',
    status              VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_by          VARCHAR(100),
    remarks             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at          TIMESTAMPTZ,
    CONSTRAINT chk_los_pg_settlement_batch_status CHECK (status IN ('PENDING', 'APPLIED', 'FAILED'))
);

CREATE TABLE los_loan_payment_in_progress (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pg_transaction_id   UUID NOT NULL REFERENCES los_loan_payment_transactions(id),
    application_id      UUID NOT NULL,
    application_number  VARCHAR(50),
    loan_product        VARCHAR(80),
    borrower_user_id    UUID NOT NULL,
    principal_amount    NUMERIC(15, 2) NOT NULL,
    pip_status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    settlement_batch_id UUID REFERENCES los_pg_settlement_batches(id),
    settled_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_los_loan_pip_status CHECK (pip_status IN ('OPEN', 'SETTLED'))
);

CREATE INDEX idx_los_loan_pip_open ON los_loan_payment_in_progress (pip_status, borrower_user_id);
CREATE INDEX idx_los_loan_pip_app ON los_loan_payment_in_progress (application_id, pip_status);

COMMENT ON TABLE los_loan_payment_transactions IS 'PayU PG transactions for LOS non-invoice loan repayments';
COMMENT ON TABLE los_loan_payment_in_progress IS 'PRUS/PIP after successful PayU — settled via LOS admin PG settlements';
