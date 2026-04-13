package com.los.notification.template;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Renders notification message body from template code + data.
 * Templates are keyed by "{eventType}.{channel}" convention.
 */
@Component
public class NotificationTemplateEngine {

    public String render(String templateCode, String channel, Map<String, Object> data) {
        String template = getTemplate(templateCode, channel);
        return substituteVariables(template, data);
    }

    public String renderSubject(String templateCode, Map<String, Object> data) {
        String subjectTemplate = getSubjectTemplate(templateCode);
        return substituteVariables(subjectTemplate, data);
    }

    private String getTemplate(String templateCode, String channel) {
        return switch (templateCode) {
            case "APPLICATION_CREATED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, your loan application {{applicationNumber}} has been submitted. Track status at our portal.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        Your loan application {{applicationNumber}} for {{loanProduct}} of ₹{{requestedAmount}} has been successfully submitted.
                        
                        Next steps:
                        1. Our team will initiate KYC verification
                        2. You will receive updates at each stage
                        
                        Track your application at: {{portalUrl}}
                        
                        Regards,
                        BillionTech LOS Team""";
                case "WHATSAPP" -> "🏦 Hi {{borrowerName}}! Your loan application {{applicationNumber}} ({{loanProduct}} - ₹{{requestedAmount}}) is submitted. We'll update you on progress.";
                default -> "Application {{applicationNumber}} created.";
            };
            case "KYC_COMPLETED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, KYC for application {{applicationNumber}} is complete. Your application moves to underwriting.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        KYC verification for your loan application {{applicationNumber}} has been completed successfully.
                        
                        All {{totalSteps}} verification steps passed. Your application is now moving to the underwriting stage.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "KYC completed for {{applicationNumber}}.";
            };
            case "KYC_FAILED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, KYC step {{failedStep}} for application {{applicationNumber}} failed. Please contact support.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        We regret to inform you that a KYC verification step has failed for your application {{applicationNumber}}.
                        
                        Failed Step: {{failedStep}}
                        Reason: {{failureReason}}
                        
                        Please contact our support team for next steps.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "KYC failed for {{applicationNumber}}: {{failedStep}}";
            };
            case "APPLICATION_APPROVED" -> switch (channel) {
                case "SMS" -> "Congratulations {{borrowerName}}! Your loan {{applicationNumber}} for ₹{{approvedAmount}} is approved. Sanction letter will follow.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        Congratulations! Your loan application {{applicationNumber}} has been approved.
                        
                        Approved Amount: ₹{{approvedAmount}}
                        Interest Rate: {{interestRate}}%
                        Tenure: {{tenure}} months
                        EMI: ₹{{emiAmount}}
                        
                        Next steps:
                        1. Sanction letter will be issued
                        2. eSign on Key Fact Statement (KFS)
                        3. Disbursement to your bank account
                        
                        Regards,
                        BillionTech LOS Team""";
                case "WHATSAPP" -> "🎉 Congratulations {{borrowerName}}! Loan {{applicationNumber}} APPROVED for ₹{{approvedAmount}}. EMI: ₹{{emiAmount}}/month for {{tenure}} months.";
                default -> "Application {{applicationNumber}} approved for ₹{{approvedAmount}}.";
            };
            case "APPLICATION_REJECTED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, your application {{applicationNumber}} could not be approved at this time. Reason: {{rejectionReason}}.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        We regret to inform you that your loan application {{applicationNumber}} could not be approved.
                        
                        Reason: {{rejectionReason}}
                        
                        You may reapply after addressing the above. Contact support for guidance.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "Application {{applicationNumber}} rejected.";
            };
            case "SANCTION_ISSUED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, sanction letter for application {{applicationNumber}} is ready. Please eSign to proceed with disbursement.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        The sanction letter for your loan application {{applicationNumber}} has been issued.
                        
                        Sanctioned Amount: ₹{{sanctionedAmount}}
                        
                        Please review and eSign the Key Fact Statement (KFS) to proceed with disbursement. You have a {{coolingOffHours}}-hour cooling-off period after signing.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "Sanction issued for {{applicationNumber}}.";
            };
            case "DISBURSEMENT_COMPLETED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, ₹{{disbursedAmount}} disbursed to your account (UTR: {{utrNumber}}) for application {{applicationNumber}}.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        Your loan has been disbursed successfully.
                        
                        Application: {{applicationNumber}}
                        Disbursed Amount: ₹{{disbursedAmount}}
                        UTR Number: {{utrNumber}}
                        Bank Account: {{bankAccount}}
                        
                        EMI of ₹{{emiAmount}} starts from {{firstEmiDate}}.
                        
                        Regards,
                        BillionTech LOS Team""";
                case "WHATSAPP" -> "💰 Hi {{borrowerName}}! ₹{{disbursedAmount}} has been credited to your account (UTR: {{utrNumber}}). EMI starts {{firstEmiDate}}.";
                default -> "Disbursement of ₹{{disbursedAmount}} completed for {{applicationNumber}}.";
            };
            case "EMI_REMINDER" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, your EMI of ₹{{emiAmount}} for loan {{applicationNumber}} is due on {{dueDate}}. Please ensure sufficient balance.";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        This is a reminder that your EMI payment is due.
                        
                        Loan: {{applicationNumber}}
                        EMI Amount: ₹{{emiAmount}}
                        Due Date: {{dueDate}}
                        Outstanding: ₹{{outstandingAmount}}
                        
                        Please ensure sufficient balance in your registered bank account.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "EMI reminder: ₹{{emiAmount}} due on {{dueDate}} for {{applicationNumber}}.";
            };
            case "ESIGN_PENDING" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, please eSign documents for application {{applicationNumber}}. Link: {{esignLink}}";
                case "EMAIL" -> """
                        Dear {{borrowerName}},
                        
                        Documents for your loan application {{applicationNumber}} are ready for eSign.
                        
                        Please click the link below to review and sign:
                        {{esignLink}}
                        
                        This link expires in {{expiryHours}} hours.
                        
                        Regards,
                        BillionTech LOS Team""";
                default -> "eSign pending for {{applicationNumber}}.";
            };
            default -> "Notification for application {{applicationNumber}}: {{eventType}}";
        };
    }

    private String getSubjectTemplate(String templateCode) {
        return switch (templateCode) {
            case "APPLICATION_CREATED" -> "Loan Application {{applicationNumber}} — Submitted Successfully";
            case "KYC_COMPLETED" -> "KYC Verification Complete — {{applicationNumber}}";
            case "KYC_FAILED" -> "KYC Verification Failed — {{applicationNumber}}";
            case "APPLICATION_APPROVED" -> "🎉 Loan Approved — {{applicationNumber}}";
            case "APPLICATION_REJECTED" -> "Loan Application Update — {{applicationNumber}}";
            case "SANCTION_ISSUED" -> "Sanction Letter Issued — {{applicationNumber}}";
            case "DISBURSEMENT_COMPLETED" -> "Loan Disbursed — {{applicationNumber}}";
            case "EMI_REMINDER" -> "EMI Payment Reminder — {{applicationNumber}}";
            case "ESIGN_PENDING" -> "eSign Required — {{applicationNumber}}";
            default -> "LOS Notification — {{applicationNumber}}";
        };
    }

    private String substituteVariables(String template, Map<String, Object> data) {
        if (data == null) return template;
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
