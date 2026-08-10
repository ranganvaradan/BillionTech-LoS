package com.los.notification.template;

/**
 * Table-based HTML email layouts for workflow notifications.
 * Inline styles only — compatible with common email clients.
 * <p>
 * Uses string concatenation (not {@link String#formatted}) so literal {@code %} in HTML attributes
 * (e.g. {@code width="100%"}) never triggers {@link java.util.UnknownFormatConversionException}.
 */
public final class StyledWorkflowEmailTemplates {

    private StyledWorkflowEmailTemplates() {
    }

    public static String esignLinkEmail() {
        return actionRequiredEmail(
                "BillionTech LOS",
                "Secure eSign Workflow",
                "Your loan documents are ready for digital signing. Please review the documents carefully and complete eSign to continue processing your application.",
                """
                        <p style="margin:0 0 8px 0;font-size:13px;color:#64748b;">Application Reference</p>
                        <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Application Number:</strong> {{applicationNumber}}</p>
                        <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Expiry Duration:</strong> {{expiryHours}} hours</p>
                        """,
                "Review &amp; Sign Documents",
                "{{esignLink}}",
                "{{esignLink}}",
                "This signing link expires in {{expiryHours}} hours.");
    }

    public static String vkycLinkEmail() {
        return actionRequiredEmail(
                "{{lenderName}}",
                "Video KYC Verification",
                "Your Video KYC step is now ready for your loan application. Please complete VKYC to keep your application moving.",
                """
                        <p style="margin:0 0 8px 0;font-size:13px;color:#64748b;">Application Reference</p>
                        <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Application Number:</strong> {{applicationNumber}}</p>
                        <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Link Expiry:</strong> {{expiryAt}}</p>
                        """,
                "Start Video KYC",
                "{{vkycLink}}",
                "{{vkycLink}}",
                "Please complete VKYC before the link expiry time shown above.");
    }

    public static String vkycApprovedEmail() {
        return statusUpdateEmail(
                "Video KYC Approved",
                "Video KYC for application {{applicationNumber}} has been approved. Processing continues toward sanction and disbursement.");
    }

    public static String vkycRejectedEmail() {
        return statusUpdateEmail(
                "Video KYC Update",
                """
                        Video KYC for application {{applicationNumber}} could not be approved.
                        <br><br><strong>Reason:</strong> {{rejectionReason}}
                        <br><br>Please contact support for next steps.
                        """);
    }

    public static String vkycCompletedViaPkyEmail() {
        return statusUpdateEmail(
                "Video KYC Completed",
                "Physical KYC (PKYC) has been recorded for application {{applicationNumber}}. No further Video KYC action is required.");
    }

    public static String vkycExpiryReminderEmail() {
        return actionRequiredEmail(
                "BillionTech LOS",
                "Video KYC Reminder",
                "This is a reminder to complete your pending Video KYC for your loan application.",
                """
                        <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Application Number:</strong> {{applicationNumber}}</p>
                        <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Link Expiry:</strong> {{expiryAt}}</p>
                        """,
                "Complete Video KYC",
                "{{vkycLink}}",
                "{{vkycLink}}",
                "Complete VKYC before the link expires.");
    }

    public static String esignCompletedEmail() {
        return statusUpdateEmail(
                "eSign Completed",
                "Electronic signing for application {{applicationNumber}} is complete. Next steps will follow shortly.");
    }

    public static String esignReminderEmail() {
        return actionRequiredEmail(
                "BillionTech LOS",
                "eSign Reminder",
                "This is a reminder to complete pending eSign for your loan application.",
                """
                        <p style="margin:0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Application Number:</strong> {{applicationNumber}}</p>
                        <p style="margin:8px 0 0 0;font-size:14px;line-height:22px;color:#0f172a;"><strong>Expiry Duration:</strong> {{expiryHours}} hours</p>
                        """,
                "Review &amp; Sign Documents",
                "{{esignLink}}",
                "{{esignLink}}",
                "This signing link expires in {{expiryHours}} hours.");
    }

    public static String kycSuccessEmail() {
        return statusUpdateEmail(
                "KYC Verification Complete",
                "Identity verification for application {{applicationNumber}} is complete. We will notify you about the next steps.");
    }

    public static String sanctionApprovedEmail() {
        return statusUpdateEmail(
                "Sanction Approved",
                """
                        Sanction for application {{applicationNumber}} has been issued.
                        <br><br>Please review the sanction letter in your borrower portal.
                        """);
    }

    public static String anchorSanctionApprovedEmail() {
        return statusUpdateEmail(
                "Anchor Program Sanction Approved",
                """
                        Your anchor onboarding application {{applicationNumber}} has been sanctioned.
                        <br><br><strong>Sanctioned limit:</strong> {{sanctionedAmount}}
                        <br><strong>Interest rate:</strong> {{interestRate}}% p.a.
                        <br><strong>Tenure:</strong> {{tenureMonths}} month(s)
                        <br><br><strong>Anchor portal access</strong>
                        <br><strong>Sign-in URL:</strong> <a href="{{anchorPortalUrl}}" target="_blank" style="color:#1d4ed8;text-decoration:underline;">{{anchorPortalUrl}}</a>
                        <br><strong>Username:</strong> {{loginEmail}}
                        <br><strong>Temporary password:</strong> {{temporaryPassword}}
                        <br><br>For security, you must set a new password when you sign in for the first time.
                        <br><br>Your program is now active on the PLP platform. Use the anchor portal to view program details and begin onboarding borrowers.
                        """);
    }

    public static String idBorrowerSanctionApprovedEmail() {
        return statusUpdateEmail(
                "Sanction Approved",
                """
                        Sanction for your invoice discounting application {{applicationNumber}} has been issued.
                        <br><br><strong>Sanctioned amount:</strong> {{sanctionedAmount}}
                        <br><strong>Interest rate:</strong> {{interestRate}}% p.a.
                        <br><strong>Tenure:</strong> {{tenureMonths}} month(s)
                        <br><br>Your sanction terms document is ready. Please sign in to the borrower portal to review the sanction details and complete eSign when prompted.
                        """);
    }

    public static String termLoanSanctionApprovedEmail() {
        return statusUpdateEmail(
                "Sanction Approved — KFS &amp; Agreement Ready",
                """
                        Congratulations! Your loan application {{applicationNumber}} has been sanctioned.
                        <br><br><strong>Sanction details</strong>
                        <br><strong>Sanctioned amount:</strong> {{sanctionedAmount}}
                        <br><strong>Interest rate:</strong> {{interestRate}}% p.a.
                        <br><strong>Tenure:</strong> {{tenureMonths}} month(s)
                        <br><strong>Processing fee:</strong> {{processingFee}}
                        <br><br><strong>Key Fact Statement (KFS)</strong> and the <strong>loan agreement</strong> have been generated and are available in your borrower portal.
                        <br><br>Next steps:
                        <br>1. Review the KFS and loan agreement in your portal.
                        <br>2. Complete eSign when you receive the signing link.
                        <br>3. Disbursement will proceed after eSign is complete.
                        """);
    }

    public static String disbursementSuccessEmail() {
        return statusUpdateEmail(
                "Loan Disbursed",
                """
                        ₹{{disbursedAmount}} for application {{applicationNumber}} has been credited to your account.
                        <br><br><strong>UTR:</strong> {{utrNumber}}
                        """);
    }

    public static String applicationRejectedEmail() {
        return statusUpdateEmail(
                "Application Update",
                """
                        Loan application {{applicationNumber}} cannot be approved at this time.
                        <br><br><strong>Reason:</strong> {{rejectionReason}}
                        """);
    }

    public static String paymentReminderEmail() {
        return statusUpdateEmail(
                "Payment Reminder",
                """
                        EMI of ₹{{emiAmount}} for loan {{applicationNumber}} is due on {{dueDate}}.
                        <br><br>Please ensure sufficient balance in your registered bank account.
                        """);
    }

    private static String statusUpdateEmail(String badge, String bodyHtml) {
        return actionRequiredEmail("BillionTech LOS", badge,
                bodyHtml,
                "",
                null,
                null,
                null,
                null);
    }

    private static String actionRequiredEmail(
            String headerLabel,
            String headerBadge,
            String intro,
            String detailsHtml,
            String ctaLabel,
            String ctaUrl,
            String plainLink,
            String warningText) {
        boolean hasCta = ctaLabel != null && ctaUrl != null;
        String detailsBlock = wrapDetailsBlock(detailsHtml);
        String ctaBlock = hasCta ? wrapCtaBlock(ctaLabel, ctaUrl, plainLink) : "";
        String warningBlock = wrapWarningBlock(warningText);

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>""" + headerBadge + """
                  </title>
                </head>
                <body style="margin:0;padding:0;background-color:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f4f7fb;margin:0;padding:24px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="max-width:640px;background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;">
                        <tr>
                          <td style="padding:20px 24px;border-bottom:1px solid #e2e8f0;background-color:#ffffff;">
                            <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                              <tr>
                                <td align="left" style="font-size:18px;font-weight:700;color:#1d4ed8;">""" + headerLabel + """
                                </td>
                                <td align="right" style="font-size:12px;color:#64748b;">""" + headerBadge + """
                                </td>
                              </tr>
                            </table>
                          </td>
                        </tr>
                        <tr><td style="padding:24px;">
                          <p style="margin:0 0 14px 0;font-size:16px;line-height:24px;color:#0f172a;">Dear {{borrowerName}},</p>
                          <p style="margin:0 0 18px 0;font-size:14px;line-height:22px;color:#334155;">""" + intro + """
                          </p>
                          """ + detailsBlock + """
                          """ + ctaBlock + """
                          """ + warningBlock + """
                          <p style="margin:0;font-size:13px;line-height:20px;color:#475569;">Need help? Please contact your relationship manager or support desk.</p>
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
                </html>
                """;
    }

    private static String wrapDetailsBlock(String detailsHtml) {
        if (detailsHtml == null || detailsHtml.isBlank()) {
            return "";
        }
        return """
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#f8fafc;border:1px solid #dbe7ff;border-radius:10px;">
                    <tr><td style="padding:14px 16px;">""" + detailsHtml + """
                    </td></tr>
                  </table>
                  """;
    }

    private static String wrapCtaBlock(String ctaLabel, String ctaUrl, String plainLink) {
        String linkText = plainLink != null ? plainLink : ctaUrl;
        return """
                  <table role="presentation" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 14px 0;">
                    <tr><td align="center" style="border-radius:8px;background-color:#2563eb;">
                      <a href=\"""" + ctaUrl + """
                      " target="_blank" style="display:inline-block;padding:12px 22px;font-size:14px;line-height:20px;font-weight:700;color:#ffffff;text-decoration:none;">""" + ctaLabel + """
                      </a>
                    </td></tr>
                  </table>
                  <p style="margin:0 0 8px 0;font-size:12px;line-height:18px;color:#64748b;">If the button above does not work, copy and paste this link into your browser:</p>
                  <p style="margin:0 0 18px 0;font-size:12px;line-height:18px;word-break:break-all;overflow-wrap:anywhere;">
                    <a href=\"""" + ctaUrl + """
                      " target="_blank" style="color:#1d4ed8;text-decoration:underline;">""" + linkText + """
                      </a>
                  </p>
                  """;
    }

    private static String wrapWarningBlock(String warningText) {
        if (warningText == null || warningText.isBlank()) {
            return "";
        }
        return """
                  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 18px 0;background:#fff7ed;border:1px solid #fdba74;border-radius:8px;">
                    <tr><td style="padding:10px 12px;font-size:13px;line-height:20px;color:#9a3412;">""" + warningText + """
                    </td></tr>
                  </table>
                  """;
    }
}
