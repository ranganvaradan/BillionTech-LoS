-- V1__lms_adapter_tables.sql
-- Database persistence for LMS adapter service
-- Replaces in-memory ConcurrentHashMap storage with PostgreSQL

CREATE TABLE IF NOT EXISTS lms_loan_handover (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number VARCHAR(50) NOT NULL UNIQUE,
    borrower_name VARCHAR(255),
    product_code VARCHAR(50),
    sanctioned_amount NUMERIC(15,2) NOT NULL,
    interest_rate NUMERIC(6,4) NOT NULL,
    tenure_months INTEGER NOT NULL,
    encore_account_id VARCHAR(100),
    encore_transaction_id VARCHAR(100),
    lms_reference_id VARCHAR(100),
    handover_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    disbursement_date DATE,
    first_emi_date DATE,
    emi_amount NUMERIC(15,2),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lms_repayment_callback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number VARCHAR(50) NOT NULL,
    encore_account_id VARCHAR(100),
    transaction_id VARCHAR(100),
    repayment_type VARCHAR(50),
    amount NUMERIC(15,2) NOT NULL,
    principal_component NUMERIC(15,2),
    interest_component NUMERIC(15,2),
    penalty_component NUMERIC(15,2),
    payment_date DATE,
    payment_mode VARCHAR(50),
    utr_number VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS lms_account_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_number VARCHAR(50) NOT NULL,
    encore_account_id VARCHAR(100),
    loan_status VARCHAR(30),
    sanctioned_amount NUMERIC(15,2),
    disbursed_amount NUMERIC(15,2),
    outstanding_principal NUMERIC(15,2),
    total_paid NUMERIC(15,2),
    overdue_amount NUMERIC(15,2),
    total_emis INTEGER,
    paid_emis INTEGER,
    overdue_emis INTEGER,
    next_emi_date DATE,
    next_emi_amount NUMERIC(15,2),
    last_payment_date DATE,
    dpd INTEGER DEFAULT 0,
    last_synced_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_lms_handover_app_number ON lms_loan_handover(application_number);
CREATE INDEX IF NOT EXISTS idx_lms_handover_encore_account ON lms_loan_handover(encore_account_id);
CREATE INDEX IF NOT EXISTS idx_lms_handover_status ON lms_loan_handover(handover_status);
CREATE INDEX IF NOT EXISTS idx_lms_repayment_app_number ON lms_repayment_callback(application_number);
CREATE INDEX IF NOT EXISTS idx_lms_repayment_encore_account ON lms_repayment_callback(encore_account_id);
CREATE INDEX IF NOT EXISTS idx_lms_summary_app_number ON lms_account_summary(application_number);
