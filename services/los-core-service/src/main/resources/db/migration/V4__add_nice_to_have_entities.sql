-- V4: Nice-to-Have entities — Co-Lending, Collateral, Webhooks, Application Notes, etc.

-- Application Notes (BR-2.8)
CREATE TABLE IF NOT EXISTS application_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    author_id UUID NOT NULL,
    author_name VARCHAR(200) NOT NULL,
    author_role VARCHAR(50),
    content TEXT NOT NULL,
    note_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    internal BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_notes_application ON application_notes(application_id);
CREATE INDEX idx_app_notes_author ON application_notes(author_id);

-- Co-Lending Partners (BR-17)
CREATE TABLE IF NOT EXISTS co_lending_partners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_name VARCHAR(200) NOT NULL,
    partner_code VARCHAR(50) NOT NULL UNIQUE,
    partner_type VARCHAR(30) NOT NULL,
    default_apportionment_percent DECIMAL(5,2),
    max_exposure_limit DECIMAL(15,2),
    interest_rate_spread DECIMAL(5,2),
    api_endpoint VARCHAR(500),
    contact_email VARCHAR(200),
    contact_phone VARCHAR(15),
    active BOOLEAN DEFAULT TRUE,
    config JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Co-Lending Allocations (BR-17)
CREATE TABLE IF NOT EXISTS co_lending_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    partner_id UUID REFERENCES co_lending_partners(id),
    partner_name VARCHAR(200),
    share_percent DECIMAL(5,2) NOT NULL,
    share_amount DECIMAL(15,2) NOT NULL,
    partner_interest_rate DECIMAL(5,2),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    partner_reference_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_colending_alloc_app ON co_lending_allocations(application_id);
CREATE INDEX idx_colending_alloc_partner ON co_lending_allocations(partner_id);

-- Collateral Valuations (BR-4.9)
CREATE TABLE IF NOT EXISTS collateral_valuations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    collateral_type VARCHAR(50) NOT NULL,
    description TEXT,
    address TEXT,
    market_value DECIMAL(15,2),
    forced_sale_value DECIMAL(15,2),
    valuation_amount DECIMAL(15,2),
    valuer_id VARCHAR(100),
    valuer_name VARCHAR(200),
    valuation_date TIMESTAMP WITH TIME ZONE,
    valuation_expiry TIMESTAMP WITH TIME ZONE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    details JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collateral_application ON collateral_valuations(application_id);
CREATE INDEX idx_collateral_status ON collateral_valuations(status);

-- Webhook Registrations (BR-18.4)
CREATE TABLE IF NOT EXISTS webhook_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_name VARCHAR(200) NOT NULL,
    callback_url VARCHAR(500) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    secret_key VARCHAR(200),
    active BOOLEAN DEFAULT TRUE,
    failure_count INTEGER DEFAULT 0,
    headers JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_partner ON webhook_registrations(partner_name);
CREATE INDEX idx_webhook_event ON webhook_registrations(event_type);

-- Add parallel groups to workflow configs (BR-6.2)
ALTER TABLE workflow_configs ADD COLUMN IF NOT EXISTS parallel_groups JSONB;
