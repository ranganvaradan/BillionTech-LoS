package com.los.notification.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Renders notification message body from template code + data.
 * Templates are keyed by "{eventType}.{channel}" convention.
 */
@Component
@Slf4j
public class NotificationTemplateEngine {

    public String render(String templateCode, String channel, Map<String, Object> data) {
        String resolvedTemplateCode = resolveTemplateCode(templateCode, data);
        String resolvedChannel = normalizeChannel(channel);
        String template = getTemplate(resolvedTemplateCode, resolvedChannel);
        boolean defaultTemplateFallback = isDefaultTemplate(template);
        if (defaultTemplateFallback) {
            log.warn("[TEMPLATE_ENGINE] fallback template used templateCodeRaw={} templateCodeResolved={} channelRaw={} channelResolved={} dataKeys={}",
                    templateCode,
                    resolvedTemplateCode,
                    channel,
                    resolvedChannel,
                    data != null ? data.keySet() : java.util.Set.of());
            log.warn("[TEMPLATE_ENGINE] fallback diagnostics templateCodeChars={} channelChars={}",
                    printableChars(templateCode),
                    printableChars(channel));
        } else {
            log.info("[TEMPLATE_ENGINE] template resolved templateCodeRaw={} templateCodeResolved={} channelRaw={} channelResolved={} isHtml={}",
                    templateCode,
                    resolvedTemplateCode,
                    channel,
                    resolvedChannel,
                    looksLikeHtml(template));
        }
        String rendered = substituteVariables(template, data);
        log.info("[EMAIL_TEMPLATE_RENDERED] templateCodeResolved={} channelResolved={} isHtml={} renderedLength={} preview=\"{}\"",
                resolvedTemplateCode,
                resolvedChannel,
                looksLikeHtml(rendered),
                rendered != null ? rendered.length() : 0,
                preview(rendered, 200));
        return rendered;
    }

    public String renderSubject(String templateCode, Map<String, Object> data) {
        String resolvedTemplateCode = resolveTemplateCode(templateCode, data);
        String subjectTemplate = getSubjectTemplate(resolvedTemplateCode);
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
            case "ESIGN_PENDING", "ESIGN_LINK" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, please eSign documents for application {{applicationNumber}}. Link: {{esignLink}}";
                case "EMAIL" -> """
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>Action Required: eSign Documents</title>
                        </head>
                        <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f4f7fb;margin:0;padding:24px 12px;">
                            <tr>
                              <td align="center">
                                <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;">
                                  <tr>
                                    <td style="padding:20px 24px;border-bottom:1px solid #e2e8f0;background-color:#ffffff;">
                                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                                        <tr>
                                          <td align="left" style="font-size:18px;font-weight:700;color:#1d4ed8;">BillionTech LOS</td>
                                          <td align="right" style="font-size:12px;color:#64748b;">Secure eSign Workflow</td>
                                        </tr>
                                      </table>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:24px;">
                                      <p style="margin:0 0 14px 0;font-size:16px;line-height:24px;color:#0f172a;">Dear {{borrowerName}},</p>
                                      <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;color:#334155;">
                                        Your loan documents are ready for digital signing. Please review the documents carefully and complete eSign to continue processing your application.
                                      </p>

                                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#f8fafc;border:1px solid #dbe7ff;border-radius:10px;">
                                        <tr>
                                          <td style="padding:14px 16px;">
                                            <p style="margin:0 0 8px 0;font-size:13px;color:#64748b;">Application Reference</p>
                                            <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;">
                                              <strong>Application Number:</strong> {{applicationNumber}}
                                            </p>
                                            <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;">
                                              <strong>Expiry Duration:</strong> {{expiryHours}} hours
                                            </p>
                                          </td>
                                        </tr>
                                      </table>

                                      <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 14px 0;">
                                        <tr>
                                          <td align="center" style="border-radius:8px;background-color:#2563eb;">
                                            <a href="{{esignLink}}" target="_blank" style="display:inline-block;padding:12px 22px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">
                                              Review &amp; Sign Documents
                                            </a>
                                          </td>
                                        </tr>
                                      </table>

                                      <p style="margin:0 0 8px 0;font-size:12px;line-height:18px;color:#64748b;">
                                        If the button above does not work, copy and paste this link into your browser:
                                      </p>
                                      <p style="margin:0 0 18px 0;font-size:12px;line-height:18px;word-break:break-all;overflow-wrap:anywhere;">
                                        <a href="{{esignLink}}" target="_blank" style="color:#1d4ed8;text-decoration:underline;">{{esignLink}}</a>
                                      </p>

                                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#fff7ed;border:1px solid #fdba74;border-radius:8px;">
                                        <tr>
                                          <td style="padding:10px 12px;font-size:13px;line-height:20px;color:#9a3412;">
                                            This signing link expires in {{expiryHours}} hours.
                                          </td>
                                        </tr>
                                      </table>

                                      <p style="margin:0;font-size:13px;line-height:20px;color:#475569;">
                                        Need help? Please contact your relationship manager or support desk.
                                      </p>
                                      <p style="margin:12px 0 0 0;font-size:13px;line-height:20px;color:#475569;">
                                        Regards,<br>
                                        BillionTech LOS Team
                                      </p>
                                    </td>
                                  </tr>
                                  <tr>
                                    <td style="padding:14px 24px;border-top:1px solid #e2e8f0;background:#f8fafc;">
                                      <p style="margin:0;font-size:12px;line-height:18px;color:#6b7280;">
                                        If you did not request this action, please ignore this email.
                                      </p>
                                    </td>
                                  </tr>
                                </table>
                              </td>
                            </tr>
                          </table>
                        </body>
                        </html>""";
                default -> "eSign pending for {{applicationNumber}}.";
            };
            case "VKYC_LINK", "VKYC_URL" -> switch (channel) {
                case "SMS" -> "Dear {{borrowerName}}, complete your Video KYC for application {{applicationNumber}}. Link: {{vkycLink}}";
                case "EMAIL" -> """
                        <!doctype html>
                        <html lang="en">
                        <head>
                          <meta charset="UTF-8">
                          <meta name="viewport" content="width=device-width, initial-scale=1.0">
                          <title>Action Required: Complete Video KYC</title>
                        </head>
                        <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f4f7fb;margin:0;padding:24px 12px;">
                            <tr><td align="center">
                              <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;">
                                <tr>
                                  <td style="padding:20px 24px;border-bottom:1px solid #e2e8f0;background-color:#ffffff;">
                                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                                      <tr>
                                        <td align="left" style="font-size:18px;font-weight:700;color:#1d4ed8;">{{lenderName}}</td>
                                        <td align="right" style="font-size:12px;color:#64748b;">Video KYC Verification</td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                                <tr><td style="padding:24px;">
                                  <p style="margin:0 0 14px 0;font-size:16px;line-height:24px;color:#0f172a;">Dear {{borrowerName}},</p>
                                  <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;color:#334155;">
                                    Your Video KYC step is now ready for your loan application. Please complete VKYC to keep your application moving.
                                  </p>
                                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#f8fafc;border:1px solid #dbe7ff;border-radius:10px;">
                                    <tr>
                                      <td style="padding:14px 16px;">
                                        <p style="margin:0 0 8px 0;font-size:13px;color:#64748b;">Application Reference</p>
                                        <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Application Number:</strong> {{applicationNumber}}</p>
                                        <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Link Expiry:</strong> {{expiryAt}}</p>
                                      </td>
                                    </tr>
                                  </table>
                                  <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 14px 0;"><tr><td align="center" style="border-radius:8px;background-color:#2563eb;">
                                    <a href="{{vkycLink}}" target="_blank" style="display:inline-block;padding:12px 22px;font-size:14px;font-weight:700;color:#ffffff;text-decoration:none;">Start Video KYC</a>
                                  </td></tr></table>
                                  <p style="margin:0 0 8px 0;font-size:12px;line-height:18px;color:#64748b;">If the button above does not work, copy and paste this link into your browser:</p>
                                  <p style="margin:0 0 18px 0;font-size:12px;line-height:18px;word-break:break-all;overflow-wrap:anywhere;"><a href="{{vkycLink}}" target="_blank" style="color:#1d4ed8;text-decoration:underline;">{{vkycLink}}</a></p>
                                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#fff7ed;border:1px solid #fdba74;border-radius:8px;">
                                    <tr>
                                      <td style="padding:10px 12px;font-size:13px;line-height:20px;color:#9a3412;">
                                        Please complete VKYC before the link expiry time shown above.
                                      </td>
                                    </tr>
                                  </table>
                                  <p style="margin:0;font-size:13px;line-height:20px;color:#475569;">For assistance, contact BillionTech LOS support.</p>
                                  <p style="margin:12px 0 0 0;font-size:13px;line-height:20px;color:#475569;">Regards,<br>BillionTech LOS Team</p>
                                </td></tr>
                                <tr>
                                  <td style="padding:14px 24px;border-top:1px solid #e2e8f0;background:#f8fafc;">
                                    <p style="margin:0;font-size:12px;line-height:18px;color:#6b7280;">If you did not request this action, please ignore this email.</p>
                                  </td>
                                </tr>
                              </table>
                            </td></tr>
                          </table>
                        </body>
                        </html>""";
                default -> "VKYC link for {{applicationNumber}}: {{vkycLink}}";
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
            case "ESIGN_PENDING", "ESIGN_LINK" -> "eSign Required — {{applicationNumber}}";
            case "VKYC_LINK", "VKYC_URL" -> "Video KYC Link — {{applicationNumber}}";
            default -> "LOS Notification — {{applicationNumber}}";
        };
    }

    private String substituteVariables(String template, Map<String, Object> data) {
        if (data == null) return template;
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
        String lower = template.toLowerCase();
        return lower.contains("<html")
                || lower.contains("<body")
                || lower.contains("<table")
                || lower.contains("<a ");
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

    private static String resolveTemplateCode(String templateCode, Map<String, Object> data) {
        String raw = templateCode;
        if (raw == null || raw.isBlank()) {
            Object eventType = data != null ? data.get("eventType") : null;
            raw = eventType != null ? String.valueOf(eventType) : "";
        }
        String normalized = raw == null ? "" : raw
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Z0-9_]", "");
        String canonical = switch (normalized) {
            case "ESIGN", "ESIGN_REQUIRED", "ESIGN_EMAIL", "ESIGN_NOTIFICATION" -> "ESIGN_PENDING";
            case "VKYC", "VIDEO_KYC", "VKYC_EMAIL", "VKYC_NOTIFICATION" -> "VKYC_LINK";
            default -> normalized;
        };
        // Keep template selection resilient to producer-side variants and hidden separators.
        String normalizedWithoutUnderscores = canonical.replace("_", "");
        if (normalizedWithoutUnderscores.contains("VKYC") || normalizedWithoutUnderscores.contains("VIDEOKYC")) {
            if (!"VKYC_LINK".equals(canonical)) {
                log.warn("[TEMPLATE_ENGINE] canonicalized templateCode raw={} normalized={} canonical={}",
                        raw, normalized, "VKYC_LINK");
            }
            return "VKYC_LINK";
        }
        if (normalizedWithoutUnderscores.contains("ESIGN")) {
            if (!"ESIGN_PENDING".equals(canonical)) {
                log.warn("[TEMPLATE_ENGINE] canonicalized templateCode raw={} normalized={} canonical={}",
                        raw, normalized, "ESIGN_PENDING");
            }
            return "ESIGN_PENDING";
        }
        return canonical;
    }

    private static String normalizeChannel(String channel) {
        if (channel == null) {
            return "";
        }
        return channel.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isDefaultTemplate(String template) {
        return "Notification for application {{applicationNumber}}: {{eventType}}".equals(template);
    }

    private static String printableChars(String value) {
        if (value == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                sb.append(String.format("U+%04X", (int) c));
            } else {
                sb.append(c).append("(U+").append(String.format("%04X", (int) c)).append(')');
            }
        }
        return sb.toString();
    }

    private static String preview(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(maxLen, normalized.length()));
    }
}
