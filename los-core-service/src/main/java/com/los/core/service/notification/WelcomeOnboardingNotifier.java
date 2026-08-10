package com.los.core.service.notification;

import com.los.core.config.RabbitMQConfig;
import com.los.core.config.SanctionNotificationProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.repository.KfsDocumentRepository;
import com.los.core.service.auth.BorrowerAccountProvisioningService;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.loan.InvoiceDiscountingApplicationRules;
import com.los.core.service.loan.InvoiceDiscountingSanctionDefaultsService;
import com.los.plp.model.entity.ProgramMaster;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sends a Welcome email when onboarding reaches its terminal signed stage
 * (anchor {@code SANCTIONED} or borrower {@code ESIGN_COMPLETED}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WelcomeOnboardingNotifier {

    public static final String TEMPLATE_ANCHOR = "WELCOME_ANCHOR";
    public static final String TEMPLATE_ID_BORROWER = "WELCOME_ID_BORROWER";
    public static final String TEMPLATE_TERM_BORROWER = "WELCOME_TERM_BORROWER";

    private final RabbitTemplate rabbitTemplate;
    private final KfsDocumentRepository kfsDocumentRepository;
    private final KfsPdfGenerationService kfsPdfGenerationService;
    private final BorrowerAccountProvisioningService borrowerAccountProvisioningService;
    private final SanctionNotificationProperties sanctionNotificationProperties;
    private final InvoiceDiscountingSanctionDefaultsService invoiceDiscountingSanctionDefaultsService;

    @Value("${los.borrower-ui-url:http://localhost:5173/los/borrower}")
    private String borrowerUiUrl;

    public void sendWelcomeAfterEsignComplete(LoanApplication app, boolean anchorFlow, boolean idBorrowerFlow) {
        if (app == null) {
            return;
        }
        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email == null || email.isBlank()) {
            log.warn("[WELCOME_EMAIL] skipped (no email) applicationId={}", app.getId());
            return;
        }

        String displayName = ApplicationPartyResolver.resolveDisplayName(app);
        String mobile = ApplicationPartyResolver.resolveMobile(app);
        String templateCode = anchorFlow
                ? TEMPLATE_ANCHOR
                : (idBorrowerFlow ? TEMPLATE_ID_BORROWER : TEMPLATE_TERM_BORROWER);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipientName", displayName != null && !displayName.isBlank() ? displayName : "Customer");
        data.put("applicationNumber", app.getApplicationNumber());
        data.put("loginEmail", email.trim());

        if (anchorFlow) {
            data.put("portalUrl", sanctionNotificationProperties.getAnchorPortalUrl());
            enrichProgramDetails(app, data);
            String temp = sanctionNotificationProperties.getDefaultTemporaryPassword();
            data.put("temporaryPassword", temp != null ? temp : "");
            data.put("passwordHint", "Use the temporary password above on first login, then change it.");
        } else {
            data.put("portalUrl", borrowerUiUrl);
            if (idBorrowerFlow || InvoiceDiscountingApplicationRules.isBorrowerFlow(app)) {
                enrichProgramDetails(app, data);
            }
            Optional<BorrowerAccountProvisioningService.BorrowerProvisionResult> provisioned =
                    borrowerAccountProvisioningService.findOrCreateBorrowerWithCredential(
                            displayName, email, mobile);
            String tempPassword = provisioned
                    .map(BorrowerAccountProvisioningService.BorrowerProvisionResult::temporaryPasswordForEmail)
                    .orElse(null);
            if (tempPassword != null && !tempPassword.isBlank()) {
                data.put("temporaryPassword", tempPassword);
                data.put("passwordHint", "You will be asked to change this temporary password on first login.");
            } else {
                data.put("temporaryPassword", "");
                data.put("passwordHint", "Use your existing password, or the forgot-password link on the portal login page.");
            }
            if (provisioned.isPresent() && app.getCustomerId() == null) {
                // customerId may already be set; linking is handled elsewhere on notify/submit
            }
        }

        attachSignedPdf(app, data);

        RoutingEmailEvent event = RoutingEmailEvent.builder()
                .channel("EMAIL")
                .recipient(email.trim())
                .templateCode(templateCode)
                .eventType("WELCOME_ONBOARDING")
                .applicationId(app.getId())
                .templateData(data)
                .build();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "notification.email.welcome", event);
            log.info("[WELCOME_EMAIL] published applicationId={} templateCode={} recipient={}",
                    app.getId(), templateCode, mask(email));
        } catch (Exception e) {
            log.error("[WELCOME_EMAIL] publish failed for {}: {}", app.getId(), e.getMessage());
        }
    }

    private void enrichProgramDetails(LoanApplication app, Map<String, Object> data) {
        Optional<ProgramMaster> program = invoiceDiscountingSanctionDefaultsService.resolveAnchorProgram(app);
        if (program.isEmpty()) {
            data.put("programName", "");
            data.put("programCode", "");
            data.put("programDetails", "See your signed documents for full program terms.");
            return;
        }
        ProgramMaster p = program.get();
        data.put("programName", nullToEmpty(p.getProgramName()));
        data.put("programCode", nullToEmpty(p.getProgramCode()));
        StringBuilder details = new StringBuilder();
        if (p.getProductType() != null) {
            details.append("Product: ").append(p.getProductType()).append('\n');
        }
        if (p.getProgramLimit() != null) {
            details.append("Program limit: ").append(p.getProgramLimit()).append('\n');
        }
        if (p.getMaxBorrowerLimit() != null) {
            details.append("Max. dealer / borrower limit: ").append(p.getMaxBorrowerLimit()).append('\n');
        }
        if (p.getInterestRate() != null) {
            details.append("Interest rate: ").append(p.getInterestRate()).append("%\n");
        }
        if (p.getTenureDays() != null) {
            details.append("Tenure (days): ").append(p.getTenureDays()).append('\n');
        }
        data.put("programDetails", details.length() > 0 ? details.toString().trim() : "See signed documents for terms.");
    }

    private void attachSignedPdf(LoanApplication app, Map<String, Object> data) {
        KfsDocument kfs = kfsDocumentRepository.findFirstByApplicationIdOrderByCreatedAtDesc(app.getId())
                .orElse(null);
        if (kfs == null) {
            return;
        }
        try {
            byte[] pdf = kfsPdfGenerationService.generateKfsPdf(kfs);
            if (pdf != null && pdf.length > 0) {
                data.put("attachmentBase64", Base64.getEncoder().encodeToString(pdf));
                data.put("attachmentFileName", "signed-terms-" + app.getApplicationNumber() + ".pdf");
            }
        } catch (Exception e) {
            log.warn("[WELCOME_EMAIL] PDF attach failed for {}: {}", app.getId(), e.getMessage());
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    @Data
    @Builder
    private static final class RoutingEmailEvent {
        private String channel;
        private String recipient;
        private String templateCode;
        private String eventType;
        private Map<String, Object> templateData;
        private UUID applicationId;
    }
}
