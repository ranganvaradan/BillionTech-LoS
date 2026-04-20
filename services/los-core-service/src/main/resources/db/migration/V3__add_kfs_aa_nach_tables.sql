-- V3: Add KFS, Account Aggregator, NACH, and enhanced workflow tables

-- KFS Documents
CREATE TABLE IF NOT EXISTS kfs_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    version VARCHAR(20) NOT NULL,
    sanctioned_amount DECIMAL(15,2) NOT NULL,
    interest_rate DECIMAL(5,2) NOT NULL,
    apr DECIMAL(5,2) NOT NULL,
    tenure_months INTEGER NOT NULL,
    emi_amount DECIMAL(15,2) NOT NULL,
    total_interest DECIMAL(15,2) NOT NULL,
    total_repayment DECIMAL(15,2) NOT NULL,
    processing_fee DECIMAL(15,2),
    stamp_duty DECIMAL(15,2),
    insurance_premium DECIMAL(15,2),
    other_charges DECIMAL(15,2),
    total_cost_of_credit DECIMAL(15,2),
    cooling_off_hours INTEGER NOT NULL DEFAULT 72,
    grievance_mechanism VARCHAR(500),
    lsp_disclosure VARCHAR(500),
    additional_terms JSONB,
    document_storage_path VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'GENERATED',
    acknowledged_at TIMESTAMP WITH TIME ZONE,
    acknowledged_by UUID,
    cooling_off_expires_at TIMESTAMP WITH TIME ZONE,
    cooling_off_completed BOOLEAN DEFAULT FALSE,
    esigned_at TIMESTAMP WITH TIME ZONE,
    esign_transaction_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kfs_application ON kfs_documents(application_id);
CREATE INDEX idx_kfs_status ON kfs_documents(status);

-- KFS Templates
CREATE TABLE IF NOT EXISTS kfs_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    loan_product VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    template_content TEXT NOT NULL,
    default_charges JSONB,
    grievance_officer_details VARCHAR(500),
    lsp_details VARCHAR(500),
    rbi_circular_ref VARCHAR(500),
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Account Aggregator Consents
CREATE TABLE IF NOT EXISTS aa_consents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    customer_id UUID NOT NULL,
    consent_handle VARCHAR(100) NOT NULL UNIQUE,
    consent_id VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    fi_types JSONB,
    consent_start_date TIMESTAMP WITH TIME ZONE NOT NULL,
    consent_expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    fetch_frequency VARCHAR(50),
    consent_mode VARCHAR(50),
    purpose_info JSONB,
    aa_name VARCHAR(200),
    approved_at TIMESTAMP WITH TIME ZONE,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revoke_reason VARCHAR(500),
    fetched_data_summary JSONB,
    data_fetched_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_aa_consent_application ON aa_consents(application_id);
CREATE INDEX idx_aa_consent_handle ON aa_consents(consent_handle);
CREATE INDEX idx_aa_consent_status ON aa_consents(status);

-- NACH Mandates
CREATE TABLE IF NOT EXISTS nach_mandates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    customer_id UUID NOT NULL,
    mandate_reference VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    bank_name VARCHAR(100) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    ifsc_code VARCHAR(11) NOT NULL,
    account_holder_name VARCHAR(200) NOT NULL,
    max_amount DECIMAL(15,2) NOT NULL,
    frequency VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    umrn VARCHAR(50),
    registered_at TIMESTAMP WITH TIME ZONE,
    cancelled_at TIMESTAMP WITH TIME ZONE,
    cancel_reason VARCHAR(200),
    esign_transaction_id VARCHAR(50),
    esigned BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_nach_application ON nach_mandates(application_id);
CREATE INDEX idx_nach_status ON nach_mandates(status);

-- Add SLA/TAT tracking columns to workflow_configs
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS sla_hours_per_step JSONB;
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS escalation_emails JSONB;
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS conditional_rules JSONB;

-- Add document versioning columns
ALTER TABLE documents ADD COLUMN IF NOT EXISTS version_number INTEGER DEFAULT 1;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS previous_version_id UUID;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS is_latest BOOLEAN DEFAULT TRUE;

-- Add TAT tracking columns to loan_applications
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS sla_deadline TIMESTAMP WITH TIME ZONE;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS current_step_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS escalated BOOLEAN DEFAULT FALSE;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS escalated_at TIMESTAMP WITH TIME ZONE;
