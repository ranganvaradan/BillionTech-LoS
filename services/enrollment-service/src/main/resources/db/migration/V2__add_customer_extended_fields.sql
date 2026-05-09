-- V2__add_customer_extended_fields.sql
-- Adds consent details, PAN/Aadhaar dedup fields

ALTER TABLE customers ADD COLUMN consent_device_id    VARCHAR(200);
ALTER TABLE customers ADD COLUMN consent_otp_session_id VARCHAR(100);
ALTER TABLE customers ADD COLUMN pan_number           VARCHAR(10) UNIQUE;
ALTER TABLE customers ADD COLUMN aadhaar_hash         VARCHAR(12) UNIQUE;
