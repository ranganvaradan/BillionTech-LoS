-- V10__manual_bureau_override.sql
-- Phase 3: allow manual bureau upload/override

ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS manual_bureau_score INTEGER;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS manual_bureau_remarks TEXT;
ALTER TABLE loan_applications ADD COLUMN IF NOT EXISTS manual_bureau_document_id UUID;
