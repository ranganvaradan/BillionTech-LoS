-- V2__add_notification_templates_and_preferences.sql

CREATE TABLE notification_templates (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code   VARCHAR(100) NOT NULL,
    channel         VARCHAR(20) NOT NULL,
    subject         VARCHAR(255) NOT NULL,
    body_template   TEXT NOT NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (template_code, channel)
);

CREATE TABLE notification_preferences (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id      UUID NOT NULL UNIQUE,
    enabled_channels JSONB,
    sms_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    whatsapp_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    in_app_enabled   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_pref_customer ON notification_preferences(customer_id);

-- Seed default templates
INSERT INTO notification_templates (template_code, channel, subject, body_template) VALUES
('APPLICATION_CREATED', 'SMS', 'Application Submitted', 'Dear {{borrowerName}}, your loan application {{applicationNumber}} has been submitted.'),
('APPLICATION_CREATED', 'EMAIL', 'Loan Application Submitted', 'Dear {{borrowerName}}, your loan application {{applicationNumber}} for {{loanProduct}} of Rs.{{requestedAmount}} has been successfully submitted.'),
('APPLICATION_APPROVED', 'SMS', 'Loan Approved', 'Congratulations {{borrowerName}}! Your loan {{applicationNumber}} for Rs.{{approvedAmount}} is approved.'),
('APPLICATION_APPROVED', 'EMAIL', 'Loan Approved', 'Congratulations {{borrowerName}}! Your loan application {{applicationNumber}} has been approved for Rs.{{approvedAmount}}.'),
('KYC_COMPLETED', 'SMS', 'KYC Complete', 'Dear {{borrowerName}}, KYC for application {{applicationNumber}} is complete.'),
('KYC_COMPLETED', 'EMAIL', 'KYC Verification Complete', 'Dear {{borrowerName}}, KYC verification for your loan application {{applicationNumber}} has been completed successfully.'),
('DISBURSEMENT_COMPLETED', 'SMS', 'Loan Disbursed', 'Dear {{borrowerName}}, Rs.{{disbursedAmount}} disbursed to your account (UTR: {{utrNumber}}).'),
('DISBURSEMENT_COMPLETED', 'EMAIL', 'Loan Disbursed', 'Dear {{borrowerName}}, your loan of Rs.{{disbursedAmount}} has been disbursed. UTR: {{utrNumber}}.');
