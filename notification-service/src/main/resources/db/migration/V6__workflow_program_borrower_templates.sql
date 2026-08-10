INSERT INTO notification_templates (template_code, channel, subject, body_template)
VALUES
    ('PROGRAM_PENDING_L2', 'EMAIL', 'Program pending approval — {{programName}}',
     $body$A program requires your L2 approval.

Program: {{programName}} ({{programCode}})
Anchor: {{anchorName}}

Review in LOS: {{approvalLink}}$body$),
    ('PROGRAM_SENT_BACK', 'EMAIL', 'Program sent back — {{programName}}',
     $body$Program {{programName}} was sent back for revision.

Notes: {{notes}}

Review in LOS: {{approvalLink}}$body$),
    ('PROGRAM_APPROVED', 'EMAIL', 'Program approved — {{programName}}',
     $body$Program {{programName}} ({{programCode}}) has been approved.

Anchor: {{anchorName}}
Next steps: generate program terms and complete eSign.

View: {{approvalLink}}$body$),
    ('BORROWER_INTAKE_INVITE', 'EMAIL', 'Complete your loan application — {{applicationNumber}}',
     $body$Dear {{borrowerName}},

Your lender has started a loan application on your behalf. Please sign in to the borrower portal and complete the remaining steps.

Application: {{applicationNumber}}
Portal: {{portalUrl}}
Login email: {{loginEmail}}
Temporary password: {{temporaryPassword}}

You will be asked to change your password on first login.$body$),
    ('BORROWER_APPLICATION_SENT_BACK', 'EMAIL', 'More details needed — {{applicationNumber}}',
     $body$Dear {{borrowerName}},

We need additional information for application {{applicationNumber}}.

Notes from reviewer:
{{notes}}

Please sign in to update and resubmit: {{portalUrl}}$body$),
    ('ANCHOR_KFS_SIGNED_COPY', 'EMAIL', 'Signed copy received — {{applicationNumber}}',
     $body$Dear {{anchorName}},

This is to confirm that we have received the signed Key Fact Statement / terms document for borrower application {{applicationNumber}}.

Borrower: {{borrowerName}}

A copy is attached for your records.$body$)
ON CONFLICT (template_code, channel) DO NOTHING;
