-- V9__manual_kyc_decision.sql
-- Phase 2: add decision/status for manual KYC reviews

ALTER TABLE manual_kyc_reviews ADD COLUMN IF NOT EXISTS decision VARCHAR(20);
