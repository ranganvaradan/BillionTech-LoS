-- V1__init_enrollment_schema.sql

CREATE TABLE customers (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(100) NOT NULL,
    mobile              VARCHAR(15) NOT NULL UNIQUE,
    email               VARCHAR(255) UNIQUE,
    mobile_verified     BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    consent_given       BOOLEAN NOT NULL DEFAULT FALSE,
    consent_timestamp   TIMESTAMPTZ,
    consent_ip_address  VARCHAR(45),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_customers_mobile ON customers(mobile);
CREATE INDEX idx_customers_email ON customers(email);
