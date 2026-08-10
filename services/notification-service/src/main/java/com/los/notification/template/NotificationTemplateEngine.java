package com.los.notification.template;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import com.los.notification.entity.NotificationTemplate;
import com.los.notification.repository.NotificationTemplateRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Renders notification message body from template code + data.
 * DB templates win when fully styled; plain/minimal DB rows defer to built-in HTML layouts.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationTemplateEngine {

    private final NotificationTemplateRepository notificationTemplateRepository;

    public String render(String templateCode, String channel, Map<String, Object> data) {
        String lookupCode = normalizeTemplateCode(templateCode, data);
        String resolvedChannel = normalizeChannel(channel);
        String template = getTemplate(lookupCode, resolvedChannel);
        boolean defaultTemplateFallback = isDefaultTemplate(template);
        if (defaultTemplateFallback) {
            log.warn("[TEMPLATE_ENGINE] fallback template used templateCodeRaw={} templateCodeLookup={} channelRaw={} channelResolved={} dataKeys={}",
                    templateCode,
                    lookupCode,
                    channel,
                    resolvedChannel,
                    data != null ? data.keySet() : java.util.Set.of());
        } else {
            log.info("[TEMPLATE_ENGINE] template resolved templateCodeRaw={} templateCodeLookup={} channelRaw={} channelResolved={} isHtml={}",
                    templateCode,
                    lookupCode,
                    channel,
                    resolvedChannel,
                    looksLikeHtml(template));
        }
        String rendered = substituteVariables(template, data);
        log.info("[EMAIL_TEMPLATE_RENDERED] templateCodeLookup={} channelResolved={} isHtml={} renderedLength={} preview=\"{}\"",
                lookupCode,
                resolvedChannel,
                looksLikeHtml(rendered),
                rendered != null ? rendered.length() : 0,
                preview(rendered, 200));
        return rendered;
    }

    public String renderSubject(String templateCode, Map<String, Object> data) {
        String lookupCode = normalizeTemplateCode(templateCode, data);
        String subjectTemplate = getSubjectTemplate(lookupCode);
        return substituteVariables(subjectTemplate, data);
    }

    private String getTemplate(String templateCode, String channel) {
        String dbTemplate = lookupDbBody(templateCode, channel);
        if (dbTemplate != null && isFullyStyledTemplate(dbTemplate)) {
            return dbTemplate;
        }

        String familyCode = resolveFamilyCode(templateCode);
        String builtIn = getBuiltInTemplate(familyCode, channel);
        if (builtIn != null && !isDefaultTemplate(builtIn)) {
            if (dbTemplate == null || dbTemplate.isBlank() || !isFullyStyledTemplate(dbTemplate)) {
                return builtIn;
            }
        }

        if (dbTemplate != null && !dbTemplate.isBlank()) {
            return dbTemplate;
        }

        if (!familyCode.equals(templateCode)) {
            String dbFamily = lookupDbBody(familyCode, channel);
            if (dbFamily != null && isFullyStyledTemplate(dbFamily)) {
                return dbFamily;
            }
            if (dbFamily != null && !dbFamily.isBlank()) {
                return dbFamily;
            }
        }

        return builtIn != null ? builtIn : defaultBodyTemplate();
    }

    private String getSubjectTemplate(String templateCode) {
        String dbSubject = lookupDbSubject(templateCode);
        if (dbSubject != null && !dbSubject.isBlank()) {
            return dbSubject;
        }
        String familyCode = resolveFamilyCode(templateCode);
        if (!familyCode.equals(templateCode)) {
            dbSubject = lookupDbSubject(familyCode);
            if (dbSubject != null && !dbSubject.isBlank()) {
                return dbSubject;
            }
        }
        return getBuiltInSubject(familyCode);
    }

    private String lookupDbBody(String templateCode, String channel) {
        return notificationTemplateRepository
                .findByTemplateCodeAndChannelAndActiveTrue(templateCode, channel)
                .map(NotificationTemplate::getBodyTemplate)
                .orElse(null);
    }

    private String lookupDbSubject(String templateCode) {
        return notificationTemplateRepository
                .findByTemplateCodeAndActiveTrue(templateCode)
                .stream()
                .findFirst()
                .map(NotificationTemplate::getSubject)
                .orElse(null);
    }

    private String getBuiltInTemplate(String familyCode, String channel) {
        return switch (familyCode) {
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
                case "EMAIL" -> StyledWorkflowEmailTemplates.kycSuccessEmail();
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
                case "EMAIL" -> StyledWorkflowEmailTemplates.applicationRejectedEmail();
                default -> "Application {{applicationNumber}} rejected.";
            };
            case "SANCTION_ISSUED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, sanction letter for application {{applicationNumber}} is ready. Please eSign to proceed with disbursement.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.sanctionApprovedEmail();
                default -> "Sanction issued for {{applicationNumber}}.";
            };
            case "SANCTION_ANCHOR" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, anchor program sanction for {{applicationNumber}} is approved. Limit: {{sanctionedAmount}}.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.anchorSanctionApprovedEmail();
                default -> "Anchor sanction approved for {{applicationNumber}}.";
            };
            case "SANCTION_ID_BORROWER" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, sanction for {{applicationNumber}} is approved ({{sanctionedAmount}}). Review terms in your portal.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.idBorrowerSanctionApprovedEmail();
                default -> "Sanction approved for {{applicationNumber}}.";
            };
            case "SANCTION_TERM_LOAN" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, loan {{applicationNumber}} sanctioned for {{sanctionedAmount}}. KFS and agreement are ready in your portal.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.termLoanSanctionApprovedEmail();
                default -> "Sanction and KFS ready for {{applicationNumber}}.";
            };
            case "DISBURSEMENT_COMPLETED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, ₹{{disbursedAmount}} disbursed to your account (UTR: {{utrNumber}}) for application {{applicationNumber}}.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.disbursementSuccessEmail();
                case "WHATSAPP" -> "💰 Hi {{borrowerName}}! ₹{{disbursedAmount}} has been credited to your account (UTR: {{utrNumber}}). EMI starts {{firstEmiDate}}.";
                default -> "Disbursement of ₹{{disbursedAmount}} completed for {{applicationNumber}}.";
            };
            case "EMI_REMINDER" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, your EMI of ₹{{emiAmount}} for loan {{applicationNumber}} is due on {{dueDate}}. Please ensure sufficient balance.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.paymentReminderEmail();
                default -> "EMI reminder: ₹{{emiAmount}} due on {{dueDate}} for {{applicationNumber}}.";
            };
            case "ESIGN_PENDING" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, please eSign documents for application {{applicationNumber}}. Link: {{esignLink}}";
                case "EMAIL" -> StyledWorkflowEmailTemplates.esignLinkEmail();
                default -> "eSign pending for {{applicationNumber}}.";
            };
            case "ESIGN_REMINDER" -> switch (channel) {
                case "SMS" -> "Reminder: please eSign documents for {{applicationNumber}}: {{esignLink}}";
                case "EMAIL" -> StyledWorkflowEmailTemplates.esignReminderEmail();
                default -> "eSign reminder for {{applicationNumber}}.";
            };
            case "ESIGN_COMPLETED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, eSign for {{applicationNumber}} is complete.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.esignCompletedEmail();
                default -> "eSign completed for {{applicationNumber}}.";
            };
            case "VKYC_LINK" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, complete your Video KYC for application {{applicationNumber}}. Link: {{vkycLink}}";
                case "EMAIL" -> StyledWorkflowEmailTemplates.vkycLinkEmail();
                default -> "VKYC link for {{applicationNumber}}: {{vkycLink}}";
            };
            case "VKYC_EXPIRY_REMINDER" -> switch (channel) {
                case "SMS" -> "Reminder: complete Video KYC for {{applicationNumber}} before {{expiryAt}}: {{vkycLink}}";
                case "EMAIL" -> StyledWorkflowEmailTemplates.vkycExpiryReminderEmail();
                default -> "VKYC reminder for {{applicationNumber}}.";
            };
            case "VKYC_APPROVED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, Video KYC for {{applicationNumber}} is approved.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.vkycApprovedEmail();
                default -> "VKYC approved for {{applicationNumber}}.";
            };
            case "VKYC_REJECTED" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, VKYC update for {{applicationNumber}}: {{rejectionReason}}";
                case "EMAIL" -> StyledWorkflowEmailTemplates.vkycRejectedEmail();
                default -> "VKYC rejected for {{applicationNumber}}.";
            };
            case "VKYC_COMPLETED_VIA_PKY" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, PKYC recorded for {{applicationNumber}}. No Video KYC action is required.";
                case "EMAIL" -> StyledWorkflowEmailTemplates.vkycCompletedViaPkyEmail();
                default -> "PKYC completed for {{applicationNumber}}.";
            };
            default -> defaultBodyTemplate();
        };
    }

    private static String getBuiltInSubject(String familyCode) {
        return switch (familyCode) {
            case "APPLICATION_CREATED" -> "Loan Application {{applicationNumber}} — Submitted Successfully";
            case "KYC_COMPLETED" -> "KYC Verification Complete — {{applicationNumber}}";
            case "KYC_FAILED" -> "KYC Verification Failed — {{applicationNumber}}";
            case "APPLICATION_APPROVED" -> "Loan Approved — {{applicationNumber}}";
            case "APPLICATION_REJECTED" -> "Loan Application Update — {{applicationNumber}}";
            case "SANCTION_ISSUED" -> "Sanction Approved — {{applicationNumber}}";
            case "SANCTION_ANCHOR" -> "Anchor Program Sanction Approved — {{applicationNumber}}";
            case "SANCTION_ID_BORROWER" -> "Sanction Approved — {{applicationNumber}}";
            case "SANCTION_TERM_LOAN" -> "Sanction Approved — KFS & Agreement — {{applicationNumber}}";
            case "DISBURSEMENT_COMPLETED" -> "Loan Disbursed — {{applicationNumber}}";
            case "EMI_REMINDER" -> "Payment Reminder — {{applicationNumber}}";
            case "ESIGN_PENDING" -> "eSign Required — {{applicationNumber}}";
            case "ESIGN_REMINDER" -> "eSign Reminder — {{applicationNumber}}";
            case "ESIGN_COMPLETED" -> "eSign Completed — {{applicationNumber}}";
            case "VKYC_LINK" -> "Video KYC Link — {{applicationNumber}}";
            case "VKYC_EXPIRY_REMINDER" -> "Video KYC Reminder — {{applicationNumber}}";
            case "VKYC_APPROVED" -> "Video KYC Approved — {{applicationNumber}}";
            case "VKYC_REJECTED" -> "Video KYC Update — {{applicationNumber}}";
            case "VKYC_COMPLETED_VIA_PKY" -> "Video KYC Completed — {{applicationNumber}}";
            default -> "LOS Notification — {{applicationNumber}}";
        };
    }

    private String substituteVariables(String template, Map<String, Object> data) {
        if (data == null) {
            return template;
        }
        boolean htmlTemplate = looksLikeHtml(template);
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String value = String.valueOf(entry.getValue());
            if (htmlTemplate) {
                value = escapeHtml(value);
            }
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private static boolean looksLikeHtml(String template) {
        if (template == null) {
            return false;
        }
        String lower = template.toLowerCase(Locale.ROOT);
        return lower.contains("<html")
                || lower.contains("<body")
                || lower.contains("<table")
                || lower.contains("<a ");
    }

    private static boolean isFullyStyledTemplate(String template) {
        if (template == null || template.isBlank() || !looksLikeHtml(template)) {
            return false;
        }
        String lower = template.toLowerCase(Locale.ROOT);
        return lower.contains("role=\"presentation\"")
                || (lower.contains("<table") && lower.contains("style="));
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /** Preserve explicit workflow template codes such as ESIGN_PENDING_EMAIL. */
    private static String normalizeTemplateCode(String templateCode, Map<String, Object> data) {
        String raw = templateCode;
        if (raw == null || raw.isBlank()) {
            Object eventType = data != null ? data.get("eventType") : null;
            raw = eventType != null ? String.valueOf(eventType) : "";
        }
        return raw.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Z0-9_]", "");
    }

    /** Map workflow/catalog template codes to built-in template families. */
    private static String resolveFamilyCode(String templateCode) {
        String normalized = templateCode == null ? "" : templateCode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ESIGN", "ESIGN_REQUIRED", "ESIGN_EMAIL", "ESIGN_NOTIFICATION", "ESIGN_LINK", "ESIGN_PENDING_EMAIL" -> "ESIGN_PENDING";
            case "ESIGN_REMINDER_EMAIL" -> "ESIGN_REMINDER";
            case "ESIGN_COMPLETED_EMAIL" -> "ESIGN_COMPLETED";
            case "VKYC", "VIDEO_KYC", "VKYC_EMAIL", "VKYC_NOTIFICATION", "VKYC_URL", "VKYC_LINK_EMAIL" -> "VKYC_LINK";
            case "VKYC_EXPIRY_REMINDER_EMAIL" -> "VKYC_EXPIRY_REMINDER";
            case "VKYC_APPROVED_EMAIL" -> "VKYC_APPROVED";
            case "VKYC_REJECTED_EMAIL" -> "VKYC_REJECTED";
            case "VKYC_COMPLETED_VIA_PKY", "VKYC_COMPLETED_VIA_PKY_EMAIL" -> "VKYC_COMPLETED_VIA_PKY";
            case "KYC_SUCCESS", "KYC_SUCCESS_EMAIL" -> "KYC_COMPLETED";
            case "SANCTION_APPROVED", "SANCTION_APPROVED_EMAIL" -> "SANCTION_ISSUED";
            case "SANCTION_APPROVED_ANCHOR", "SANCTION_APPROVED_ANCHOR_EMAIL" -> "SANCTION_ANCHOR";
            case "SANCTION_APPROVED_ID_BORROWER", "SANCTION_APPROVED_ID_BORROWER_EMAIL" -> "SANCTION_ID_BORROWER";
            case "SANCTION_APPROVED_TERM_LOAN", "SANCTION_APPROVED_TERM_LOAN_EMAIL" -> "SANCTION_TERM_LOAN";
            case "DISBURSEMENT_SUCCESS", "DISBURSEMENT_SUCCESS_EMAIL" -> "DISBURSEMENT_COMPLETED";
            case "WORKFLOW_PAYMENT_REMINDER", "WORKFLOW_PAYMENT_REMINDER_EMAIL" -> "EMI_REMINDER";
            case "APPLICATION_REJECTED_EMAIL" -> "APPLICATION_REJECTED";
            default -> normalized;
        };
    }

    private static String normalizeChannel(String channel) {
        if (channel == null) {
            return "";
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isDefaultTemplate(String template) {
        return defaultBodyTemplate().equals(template);
    }

    private static String defaultBodyTemplate() {
        return "Notification for application {{applicationNumber}}: {{eventType}}";
    }

    private static String preview(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(maxLen, normalized.length()));
    }
}
