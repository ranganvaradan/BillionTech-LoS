-- Seed missing workflow notification templates and catalog mappings.
-- Plain DB rows remain for SMS; styled HTML is supplied by NotificationTemplateEngine when DB body is not fully styled.

INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('VKYC_COMPLETED_VIA_PKY_EMAIL', 'EMAIL', 'Video KYC completed — {{applicationNumber}}',
     'Dear {{borrowerName}}, physical KYC (PKYC) has been recorded for application {{applicationNumber}}. No further Video KYC action is required.'),
    ('VKYC_COMPLETED_VIA_PKY_SMS', 'SMS', 'PKYC recorded',
     'Dear {{borrowerName}}, PKYC recorded for {{applicationNumber}}. No Video KYC action is required.'),

    ('ESIGN_REMINDER_EMAIL', 'EMAIL', 'eSign reminder — {{applicationNumber}}',
     'Dear {{borrowerName}}, reminder to eSign documents for {{applicationNumber}} here: {{esignLink}} (valid {{expiryHours}} hours).'),
    ('ESIGN_REMINDER_SMS', 'SMS', 'eSign reminder',
     'Reminder: please eSign for {{applicationNumber}}: {{esignLink}}'),

    ('VKYC_EXPIRY_REMINDER_EMAIL', 'EMAIL', 'Video KYC reminder — {{applicationNumber}}',
     'Dear {{borrowerName}}, reminder to complete Video KYC for {{applicationNumber}} using {{vkycLink}} before {{expiryAt}}.'),
    ('VKYC_EXPIRY_REMINDER_SMS', 'SMS', 'VKYC reminder',
     'Reminder: complete Video KYC for {{applicationNumber}} before {{expiryAt}}: {{vkycLink}}'),

    ('ESIGN_EXPIRED_EMAIL', 'EMAIL', 'eSign link expired — {{applicationNumber}}',
     'Dear {{borrowerName}}, the eSign link for {{applicationNumber}} has expired. Please contact support to request a new link.'),
    ('ESIGN_EXPIRED_SMS', 'SMS', 'eSign expired',
     'Dear {{borrowerName}}, eSign link for {{applicationNumber}} expired. Contact support for a new link.'),

    ('SANCTION_REJECTED_EMAIL', 'EMAIL', 'Sanction update — {{applicationNumber}}',
     'Dear {{borrowerName}}, sanction for application {{applicationNumber}} could not be issued. Reason: {{rejectionReason}}.'),
    ('SANCTION_REJECTED_SMS', 'SMS', 'Sanction update',
     'Dear {{borrowerName}}, sanction update for {{applicationNumber}}: {{rejectionReason}}'),

    ('DISBURSEMENT_FAILED_EMAIL', 'EMAIL', 'Disbursement update — {{applicationNumber}}',
     'Dear {{borrowerName}}, disbursement for application {{applicationNumber}} could not be completed. Reason: {{failureReason}}.'),
    ('DISBURSEMENT_FAILED_SMS', 'SMS', 'Disbursement update',
     'Dear {{borrowerName}}, disbursement update for {{applicationNumber}}: {{failureReason}}')
ON CONFLICT (template_code, channel) DO NOTHING;

INSERT INTO workflow_event_template_mapping (workflow_event, channel, template_code, is_default, sort_order)
VALUES
    ('VKYC_COMPLETED_VIA_PKY', 'EMAIL', 'VKYC_COMPLETED_VIA_PKY_EMAIL', TRUE, 0),
    ('VKYC_COMPLETED_VIA_PKY', 'SMS', 'VKYC_COMPLETED_VIA_PKY_SMS', TRUE, 0),
    ('VKYC_COMPLETED_VIA_PKY', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),
    ('VKYC_COMPLETED_VIA_PKY', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),
    ('VKYC_COMPLETED_VIA_PKY', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('ESIGN_REMINDER', 'EMAIL', 'ESIGN_REMINDER_EMAIL', TRUE, 0),
    ('ESIGN_REMINDER', 'SMS', 'ESIGN_REMINDER_SMS', TRUE, 0),
    ('ESIGN_REMINDER', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),
    ('ESIGN_REMINDER', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),
    ('ESIGN_REMINDER', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('VKYC_EXPIRY_REMINDER', 'EMAIL', 'VKYC_EXPIRY_REMINDER_EMAIL', TRUE, 0),
    ('VKYC_EXPIRY_REMINDER', 'SMS', 'VKYC_EXPIRY_REMINDER_SMS', TRUE, 0),
    ('VKYC_EXPIRY_REMINDER', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),
    ('VKYC_EXPIRY_REMINDER', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),
    ('VKYC_EXPIRY_REMINDER', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('ESIGN_COMPLETED', 'EMAIL', 'ESIGN_COMPLETED_EMAIL', TRUE, 0),
    ('ESIGN_COMPLETED', 'SMS', 'ESIGN_COMPLETED_SMS', TRUE, 0),
    ('ESIGN_COMPLETED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),
    ('ESIGN_COMPLETED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),
    ('ESIGN_COMPLETED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0),

    ('ESIGN_EXPIRED', 'EMAIL', 'ESIGN_EXPIRED_EMAIL', TRUE, 0),
    ('ESIGN_EXPIRED', 'SMS', 'ESIGN_EXPIRED_SMS', TRUE, 0),
    ('ESIGN_EXPIRED', 'WHATSAPP', 'WORKFLOW_STANDARD_WHATSAPP', TRUE, 0),
    ('ESIGN_EXPIRED', 'PUSH', 'WORKFLOW_STANDARD_PUSH', TRUE, 0),
    ('ESIGN_EXPIRED', 'WEBHOOK', 'WORKFLOW_STANDARD_WEBHOOK', TRUE, 0)
ON CONFLICT (workflow_event, channel, template_code) DO NOTHING;
