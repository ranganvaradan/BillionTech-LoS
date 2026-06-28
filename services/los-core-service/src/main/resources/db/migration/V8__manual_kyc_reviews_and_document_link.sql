-- V8__manual_kyc_reviews_and_document_link.sql
-- Phase 1: manual KYC review persistence + link documents to a specific KYC step

ALTER TABLE documents ADD COLUMN IF NOT EXISTS kyc_step_type VARCHAR(50);

CREATE TABLE IF NOT EXISTS manual_kyc_reviews (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    data_json JSONB,
    remarks TEXT,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_manual_kyc_reviews_app_step UNIQUE (application_id, step_type)
);

CREATE INDEX IF NOT EXISTS idx_manual_kyc_reviews_application_id ON manual_kyc_reviews(application_id);
CREATE INDEX IF NOT EXISTS idx_documents_application_id_kyc_step_type ON documents(application_id, kyc_step_type);
