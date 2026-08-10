package com.los.core.service.sanction;

import com.los.core.config.RabbitMQConfig;
import com.los.core.config.SanctionNotificationProperties;
import com.los.core.model.entity.KfsDocument;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.SanctionRecord;
import com.los.core.service.loan.ApplicationPartyResolver;
import com.los.core.service.workflow.WorkflowNotificationResolverService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes post-sanction emails to {@code los.notification} (queue {@code notification.email}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SanctionApprovedNotifier {

    public static final String EVENT_SANCTION_APPROVED = "SANCTION_APPROVED";

    private final RabbitTemplate rabbitTemplate;
    private final WorkflowNotificationResolverService workflowNotificationResolverService;
    private final SanctionNotificationProperties sanctionNotificationProperties;

    public void publishSanctionApprovedEmail(
            LoanApplication app,
            SanctionRecord sanctionRecord,
            boolean anchorFlow,
            boolean idBorrowerFlow,
            KfsDocument kfs) {
        if (!sanctionNotificationProperties.isEnabled()) {
            log.info("[SANCTION_EMAIL] skipped (disabled) applicationId={}", app != null ? app.getId() : null);
            return;
        }
        if (app == null) {
            return;
        }

        String email = ApplicationPartyResolver.resolveEmail(app);
        if (email == null || email.isBlank()) {
            log.warn("[SANCTION_EMAIL] skipped (no applicant email) applicationId={} applicationNumber={}",
                    app.getId(), app.getApplicationNumber());
            return;
        }

        String templateCode = resolveTemplateCode(anchorFlow, idBorrowerFlow);
        String displayName = ApplicationPartyResolver.resolveDisplayName(app);
        Map<String, Object> templateData = buildTemplateData(
                app, sanctionRecord, anchorFlow, idBorrowerFlow, kfs, displayName, email.trim());

        List<String> recipients = List.of(email.trim());
        var actions = workflowNotificationResolverService.resolveForApplication(
                app.getId(),
                "SANCTION",
                EVENT_SANCTION_APPROVED,
                recipients,
                templateCode,
                "EMAIL");

        log.info("[SANCTION_EMAIL] publishing applicationId={} applicationNumber={} anchorFlow={} idBorrowerFlow={} templateCode={} recipient={}",
                app.getId(),
                app.getApplicationNumber(),
                anchorFlow,
                idBorrowerFlow,
                templateCode,
                maskEmail(email));

        String exchange = RabbitMQConfig.EXCHANGE;
        for (var action : actions) {
            String routingKey = "notification." + action.getChannel().toLowerCase() + "."
                    + action.getEventType().toLowerCase();
            for (String recipient : action.getRecipients()) {
                RoutingEmailEvent event = RoutingEmailEvent.builder()
                        .channel(action.getChannel())
                        .recipient(recipient)
                        .templateCode(action.getTemplateCode())
                        .eventType(action.getEventType())
                        .applicationId(app.getId())
                        .templateData(templateData)
                        .build();
                try {
                    rabbitTemplate.convertAndSend(exchange, routingKey, event);
                    log.info("[SANCTION_NOTIFICATION] queued exchange={} routingKey={} applicationId={} templateCode={}",
                            exchange, routingKey, app.getId(), action.getTemplateCode());
                } catch (Exception e) {
                    log.error("[SANCTION_EMAIL][ERROR] RabbitMQ publish failed applicationId={} recipient={}: {}",
                            app.getId(), maskEmail(recipient), e.getMessage(), e);
                }
            }
        }
    }

    private String resolveTemplateCode(boolean anchorFlow, boolean idBorrowerFlow) {
        if (anchorFlow) {
            return sanctionNotificationProperties.getAnchorTemplateCode();
        }
        if (idBorrowerFlow) {
            return sanctionNotificationProperties.getIdBorrowerTemplateCode();
        }
        return sanctionNotificationProperties.getTermLoanTemplateCode();
    }

    private Map<String, Object> buildTemplateData(
            LoanApplication app,
            SanctionRecord sanctionRecord,
            boolean anchorFlow,
            boolean idBorrowerFlow,
            KfsDocument kfs,
            String displayName,
            String loginEmail) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("borrowerName", displayName != null && !displayName.isBlank() ? displayName : "Customer");
        data.put("applicationNumber", app.getApplicationNumber() != null ? app.getApplicationNumber() : "");
        data.put("eventType", EVENT_SANCTION_APPROVED);

        BigDecimal amount = sanctionRecord != null && sanctionRecord.getApprovedAmount() != null
                ? sanctionRecord.getApprovedAmount()
                : app.getSanctionedAmount();
        BigDecimal rate = sanctionRecord != null && sanctionRecord.getInterestRate() != null
                ? sanctionRecord.getInterestRate()
                : (app.getApprovedRate() != null ? app.getApprovedRate() : app.getInterestRate());
        Integer tenureMonths = sanctionRecord != null && sanctionRecord.getApprovedTenure() != null
                ? sanctionRecord.getApprovedTenure()
                : app.getTenureMonths();

        data.put("sanctionedAmount", formatAmount(amount));
        data.put("interestRate", rate != null ? rate.toPlainString() : "");
        data.put("tenureMonths", tenureMonths != null ? tenureMonths : "");
        data.put("tenureDays", tenureMonths != null ? tenureMonths * 30 : "");
        data.put("processingFee", sanctionRecord != null && sanctionRecord.getProcessingFee() != null
                ? formatAmount(sanctionRecord.getProcessingFee()) : "");
        data.put("conditions", sanctionRecord != null && sanctionRecord.getConditionsText() != null
                ? sanctionRecord.getConditionsText() : "");
        data.put("sanctionFlow", anchorFlow ? "ANCHOR" : (idBorrowerFlow ? "ID_BORROWER" : "TERM_LOAN"));
        data.put("kfsGenerated", kfs != null);
        data.put("kfsVersion", kfs != null && kfs.getVersion() != null ? kfs.getVersion() : "");
        if (anchorFlow) {
            data.put("loginEmail", loginEmail != null ? loginEmail : "");
            data.put("temporaryPassword", sanctionNotificationProperties.getDefaultTemporaryPassword());
            data.put("anchorPortalUrl", sanctionNotificationProperties.getAnchorPortalUrl());
        }
        return data;
    }

    private static String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "₹" + amount.stripTrailingZeros().toPlainString();
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "*@" + (at < 0 ? "" : email.substring(at + 1));
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
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
