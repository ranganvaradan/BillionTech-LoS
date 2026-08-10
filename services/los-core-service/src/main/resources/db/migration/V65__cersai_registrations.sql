-- CERSAI security interest registration and search records (COLLATERAL_AA Phase 2).

CREATE TABLE IF NOT EXISTS cersai_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES loan_applications(id),
    collateral_valuation_id UUID REFERENCES collateral_valuations(id),
    cersai_id VARCHAR(50),
    asset_type VARCHAR(30) NOT NULL,
    asset_description TEXT,
    registration_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    security_interest_type VARCHAR(30) NOT NULL,
    secured_amount DECIMAL(15, 2),
    borrower_name VARCHAR(200),
    borrower_pan VARCHAR(10),
    lender_name VARCHAR(200),
    lender_cin VARCHAR(21),
    registration_date TIMESTAMP WITH TIME ZONE,
    expiry_date TIMESTAMP WITH TIME ZONE,
    response_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cersai_application ON cersai_registrations(application_id);
CREATE INDEX IF NOT EXISTS idx_cersai_id ON cersai_registrations(cersai_id);

COMMENT ON TABLE cersai_registrations IS 'CERSAI security interest search and registration audit trail';
COMMENT ON COLUMN cersai_registrations.registration_status IS 'PENDING, SEARCHED, REGISTERED, FAILED';
