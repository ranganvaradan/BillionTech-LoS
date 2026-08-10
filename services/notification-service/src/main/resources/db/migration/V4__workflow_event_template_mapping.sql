-- Catalog of workflow step notification events → allowed template codes per channel.
-- Seeded defaults keep workflow JSON storing concrete template_code values unchanged in behavior.

CREATE TABLE workflow_event_template_mapping (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_event    VARCHAR(120) NOT NULL,
    channel           VARCHAR(40)  NOT NULL,
    template_code     VARCHAR(100) NOT NULL,
    is_default        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order        INTEGER      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wetm_event_channel_code UNIQUE (workflow_event, channel, template_code)
);

CREATE INDEX idx_wetm_event_channel_active ON workflow_event_template_mapping (workflow_event, channel, is_active);
CREATE UNIQUE INDEX uq_wetm_one_default_per_event_channel ON workflow_event_template_mapping (workflow_event, channel)
    WHERE is_default = TRUE AND is_active = TRUE;

-- ---------------------------------------------------------------------------
-- Seed notification_templates (skipped if already present)
-- ---------------------------------------------------------------------------

INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('KYC_SUCCESS_EMAIL', 'EMAIL', 'KYC verification complete — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, identity verification for application {{applicationNumber}} is complete. We will notify you about the next steps.$body$),
    ('KYC_SUCCESS_SMS', 'SMS', 'KYC complete',
     'Dear {{borrowerName}}, KYC for loan application {{applicationNumber}} is complete.'),

    ('VKYC_LINK_EMAIL', 'EMAIL', 'Video KYC link — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, please complete Video KYC for application {{applicationNumber}} using this link: {{vkycLink}} (expires {{expiryAt}}).$body$),
    ('VKYC_LINK_SMS', 'SMS', 'Video KYC',
     'Dear {{borrowerName}}, complete Video KYC for {{applicationNumber}}: {{vkycLink}}'),

    ('VKYC_APPROVED_EMAIL', 'EMAIL', 'Video KYC approved — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, Video KYC for application {{applicationNumber}} has been approved. Processing continues toward sanction and disbursement.$body$),
    ('VKYC_APPROVED_SMS', 'SMS', 'VKYC approved',
     'Dear {{borrowerName}}, Video KYC for {{applicationNumber}} is approved.'),

    ('VKYC_REJECTED_EMAIL', 'EMAIL', 'Video KYC update — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, Video KYC for application {{applicationNumber}} could not be approved. Reason: {{rejectionReason}}. Please contact support.$body$),
    ('VKYC_REJECTED_SMS', 'SMS', 'VKYC update',
     'Dear {{borrowerName}}, VKYC update for {{applicationNumber}}: {{rejectionReason}}'),

    ('ESIGN_PENDING_EMAIL', 'EMAIL', 'eSign required — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, please eSign documents for {{applicationNumber}} here: {{esignLink}} (valid {{expiryHours}} hours).$body$),
    ('ESIGN_PENDING_SMS', 'SMS', 'eSign pending',
     'Dear {{borrowerName}}, please eSign for {{applicationNumber}}: {{esignLink}}'),

    ('ESIGN_COMPLETED_EMAIL', 'EMAIL', 'eSign completed — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, electronic signing for application {{applicationNumber}} is complete. Next steps will follow shortly.$body$),
    ('ESIGN_COMPLETED_SMS', 'SMS', 'eSign done',
     'Dear {{borrowerName}}, eSign for {{applicationNumber}} is complete.'),

    ('SANCTION_APPROVED_EMAIL', 'EMAIL', 'Sanction approved — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, sanction for application {{applicationNumber}} has been issued. Review the sanction letter in your portal.$body$),
    ('SANCTION_APPROVED_SMS', 'SMS', 'Sanction issued',
     'Dear {{borrowerName}}, sanction for {{applicationNumber}} is issued. Review your portal.'),

    ('DISBURSEMENT_SUCCESS_EMAIL', 'EMAIL', 'Loan disbursed — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, ₹{{disbursedAmount}} for application {{applicationNumber}} has been credited. UTR: {{utrNumber}}.$body$),
    ('DISBURSEMENT_SUCCESS_SMS', 'SMS', 'Disbursement done',
     'Dear {{borrowerName}}, ₹{{disbursedAmount}} disbursed for {{applicationNumber}}. UTR {{utrNumber}}'),

    ('APPLICATION_REJECTED_EMAIL', 'EMAIL', 'Application update — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, loan application {{applicationNumber}} cannot be approved at this time. Reason: {{rejectionReason}}.$body$),
    ('APPLICATION_REJECTED_SMS', 'SMS', 'Application update',
     'Dear {{borrowerName}}, application {{applicationNumber}} could not be approved.'),

    ('WORKFLOW_PAYMENT_REMINDER_EMAIL', 'EMAIL', 'Payment reminder — {{applicationNumber}}',
     $body$Dear {{borrowerName}}, EMI of ₹{{emiAmount}} for loan {{applicationNumber}} is due on {{dueDate}}.$body$),
    ('WORKFLOW_PAYMENT_REMINDER_SMS', 'SMS', 'Payment reminder',
     'Dear {{borrowerName}}, EMI ₹{{emiAmount}} for {{applicationNumber}} due {{dueDate}}.'),

    ('WORKFLOW_STANDARD_WHATSAPP', 'WHATSAPP', 'LOS update — {{applicationNumber}}',
     'Hi {{borrowerName}}, update for application {{applicationNumber}} ({{eventType}}).'),
    ('WORKFLOW_STANDARD_PUSH', 'PUSH', 'LOS update — {{applicationNumber}}',
     'Update on application {{applicationNumber}} — {{eventType}}'),
    ('WORKFLOW_STANDARD_WEBHOOK', 'WEBHOOK', 'Workflow event webhook',
     $body${"applicationNumber":"{{applicationNumber}}","eventType":"{{eventType}}"}$body$)
ON CONFLICT (template_code, channel) DO NOTHING;

-- Alternate / legacy-aligned codes offered for parity with NotificationTemplateEngine + existing configs
INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('VKYC_LINK', 'EMAIL', 'Video KYC Link — {{applicationNumber}}',
     '<html><body><p>Dear {{borrowerName}}, complete VKYC for {{applicationNumber}}: <a href="{{vkycLink}}">{{vkycLink}}</a>. Expiry: {{expiryAt}}</p></body></html>'),
    ('VKYC_LINK', 'SMS', 'Video KYC', 'Dear {{borrowerName}}, VKYC {{applicationNumber}}: {{vkycLink}}'),

    ('ESIGN_PENDING', 'EMAIL', 'eSign Required — {{applicationNumber}}',
     '<html><body><p>Dear {{borrowerName}}, eSign documents for {{applicationNumber}} at <a href="{{esignLink}}">{{esignLink}}</a> within {{expiryHours}} hour(s).</p></body></html>'),
    ('ESIGN_PENDING', 'SMS', 'eSign', 'Dear {{borrowerName}}, please eSign {{applicationNumber}}: {{esignLink}}'),

    ('KYC_COMPLETED', 'EMAIL', 'KYC Verification Complete — {{applicationNumber}}',
     'Dear {{borrowerName}}, KYC for {{applicationNumber}} completed successfully.'),
    ('KYC_COMPLETED', 'SMS', 'KYC complete', 'Dear {{borrowerName}}, KYC {{applicationNumber}} is complete.')

ON CONFLICT (template_code, channel) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Mappings: primary default (+ optional alternates preserved for scalability)
-- ---------------------------------------------------------------------------

INSERT INTO workflow_event_template_mapping (workflow_event, channel, template_code, is_default, sort_order)
VALUES
    ('KYC_SUCCESS', 'EMAIL', 'KYC_SUCCESS_EMAIL', TRUE, 0),
    ('KYC_SUCCESS', 'EMAIL', 'KYC_COMPLETED', FALSE, 10),

    ('KYC_SUCCESS', 'SMS', 'KYC_SUCCESS_SMS', TRUE, 0),

    ('VKYC_LINK', 'EMAIL', 'VKYC_LINK_EMAIL', TRUE, 0),
    ('VKYC_LINK', 'EMAIL', 'VKYC_LINK', FALSE, 10),

    ('VKYC_LINK', 'SMS', 'VKYC_LINK_SMS', TRUE, 0),

    ('VKYC_APPROVED', 'EMAIL', 'VKYC_APPROVED_EMAIL', TRUE, 0),
    ('VKYC_APPROVED', 'SMS', 'VKYC_APPROVED_SMS', TRUE, 0),

    ('VKYC_REJECTED', 'EMAIL', 'VKYC_REJECTED_EMAIL', TRUE, 0),
    ('VKYC_REJECTED', 'SMS', 'VKYC_REJECTED_SMS', TRUE, 0),

    ('ESIGN_LINK', 'EMAIL', 'ESIGN_PENDING_EMAIL', TRUE, 0),
    ('ESIGN_LINK', 'EMAIL', 'ESIGN_PENDING', FALSE, 10),

    ('ESIGN_LINK', 'SMS', 'ESIGN_PENDING_SMS', TRUE, 0),

    ('SANCTION_APPROVED', 'EMAIL', 'SANCTION_APPROVED_EMAIL', TRUE, 0),
    ('SANCTION_APPROVED', 'SMS', 'SANCTION_APPROVED_SMS', TRUE, 0),

    ('DISBURSEMENT_COMPLETED', 'EMAIL', 'DISBURSEMENT_SUCCESS_EMAIL', TRUE, 0),

    ('DISBURSEMENT_COMPLETED', 'EMAIL', 'DISBURSEMENT_COMPLETED', FALSE, 10),

    ('DISBURSEMENT_COMPLETED', 'SMS', 'DISBURSEMENT_SUCCESS_SMS', TRUE, 0),

    ('APPLICATION_REJECTED', 'EMAIL', 'APPLICATION_REJECTED_EMAIL', TRUE, 0),
    ('APPLICATION_REJECTED', 'SMS', 'APPLICATION_REJECTED_SMS', TRUE, 0),

    ('REMINDER', 'EMAIL', 'WORKFLOW_PAYMENT_REMINDER_EMAIL', TRUE, 0),

    ('REMINDER', 'SMS', 'WORKFLOW_PAYMENT_REMINDER_SMS', TRUE, 0),

    ('KYC_SUCCESS', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('VKYC_LINK', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('VKYC_APPROVED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('VKYC_REJECTED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('ESIGN_LINK', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('SANCTION_APPROVED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('DISBURSEMENT_COMPLETED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('APPLICATION_REJECTED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('REMINDER', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),

    ('KYC_SUCCESS', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('VKYC_LINK', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('VKYC_APPROVED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('VKYC_REJECTED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('ESIGN_LINK', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('SANCTION_APPROVED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('DISBURSEMENT_COMPLETED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('APPLICATION_REJECTED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('REMINDER', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),

    ('KYC_SUCCESS', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('VKYC_LINK', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('VKYC_APPROVED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('VKYC_REJECTED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('ESIGN_LINK', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('SANCTION_APPROVED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('DISBURSEMENT_COMPLETED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('APPLICATION_REJECTED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('REMINDER', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0)

ON CONFLICT (workflow_event, channel, template_code) DO NOTHING;
