INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('WELCOME_ANCHOR', 'EMAIL', 'Welcome — {{applicationNumber}} onboarded',
     $body$Dear {{recipientName}},

Welcome! Your anchor onboarding for application {{applicationNumber}} is complete.

Program: {{programName}} ({{programCode}})
{{programDetails}}

Portal: {{portalUrl}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}
{{passwordHint}}

Your signed program terms are attached for your records.

Thank you.$body$),
    ('WELCOME_ID_BORROWER', 'EMAIL', 'Welcome — {{applicationNumber}} onboarding complete',
     $body$Dear {{recipientName}},

Welcome! Your invoice discounting application {{applicationNumber}} has been successfully onboarded.

Program: {{programName}} ({{programCode}})
{{programDetails}}

Borrower portal: {{portalUrl}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}
{{passwordHint}}

Your signed terms document is attached for your records.

Thank you.$body$),
    ('WELCOME_TERM_BORROWER', 'EMAIL', 'Welcome — {{applicationNumber}} documents signed',
     $body$Dear {{recipientName}},

Welcome! Your loan application {{applicationNumber}} has reached the signed-documents stage.

Borrower portal: {{portalUrl}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}
{{passwordHint}}

Your signed Key Fact Statement / agreement is attached for your records.

Thank you.$body$)
ON CONFLICT (template_code, channel) DO NOTHING;
