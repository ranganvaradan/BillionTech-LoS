package com.los.core.service.esign;

import com.los.core.config.RabbitMQConfig;
import com.los.core.service.workflow.WorkflowNotificationResolverService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes eSign signing-link emails to {@code los.notification} (queue {@code notification.email}).
 * Payload fields match notification-service {@code NotificationEvent} for JSON deserialization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsignSigningLinkNotifier {

    /** Same template code rendered in notification-service ({@link com.los.notification.template.NotificationTemplateEngine}). */
    public static final String TEMPLATE_ESIGN_PENDING = "ESIGN_PENDING";

    private final RabbitTemplate rabbitTemplate;
    private final WorkflowNotificationResolverService workflowNotificationResolverService;

    /**
     * @param recipientEmails validated non-blank borrower email(s)
     */
    public void publishSigningLinkEmail(
            UUID applicationId,
            String applicationNumber,
            String borrowerName,
            List<String> recipientEmails,
            String signingUrl,
            String templateCode,
            int expiryHours,
            boolean reusedSigningUrl) {
        if (recipientEmails == null || recipientEmails.stream().noneMatch(e -> e != null && !e.isBlank())) {
            log.warn("[ESIGN_EMAIL] skipped (no recipients) applicationId={} applicationNumber={} reusedSigningUrl={}",
                    applicationId, maskAppNumber(applicationNumber), reusedSigningUrl);
            return;
        }
        List<String> to = recipientEmails.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (to.isEmpty()) {
            log.warn("[ESIGN_EMAIL] skipped (empty after sanitize) applicationId={}", applicationId);
            return;
        }
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("borrowerName", borrowerName != null ? borrowerName : "Borrower");
        templateData.put("applicationNumber", applicationNumber != null ? applicationNumber : "");
        templateData.put("esignLink", signingUrl);
        templateData.put("expiryHours", expiryHours);
        templateData.put("eventType", "ESIGN_LINK");

        String code = templateCode != null && !templateCode.isBlank() ? templateCode : TEMPLATE_ESIGN_PENDING;
        String exchange = RabbitMQConfig.EXCHANGE;
        var actions = workflowNotificationResolverService.resolveForApplication(
                applicationId,
                "ESIGN_KFS",
                "ESIGN_LINK",
                to,
                code,
                "EMAIL");
        log.info("[ESIGN_EMAIL] trigger started applicationId={} applicationNumber={} templateCode={} recipientCount={} recipients={} reusedSigningUrl={}",
                applicationId, maskAppNumber(applicationNumber),
                code, to.size(),
                maskEmails(to),
                reusedSigningUrl);

        for (var action : actions) {
            String routingKey = "notification." + action.getChannel().toLowerCase() + "."
                    + action.getEventType().toLowerCase();
            for (String recipient : action.getRecipients()) {
                RoutingEmailEvent ev = RoutingEmailEvent.builder()
                        .channel(action.getChannel())
                        .recipient(recipient)
                        .templateCode(action.getTemplateCode())
                        .eventType(action.getEventType())
                        .applicationId(applicationId)
                        .templateData(templateData)
                        .build();
                try {
                    log.info("[ESIGN_NOTIFICATION_PUBLISH] templateCode={} eventType={} channel={} recipient={} applicationId={} routingKey={} templateData={}",
                            ev.getTemplateCode(),
                            ev.getEventType(),
                            ev.getChannel(),
                            recipient,
                            ev.getApplicationId(),
                            routingKey,
                            templateData);
                    rabbitTemplate.convertAndSend(exchange, routingKey, ev);
                    log.info("[ESIGN_NOTIFICATION] queued exchange={} routingKey={} applicationId={} recipient={}",
                            exchange, routingKey, applicationId, maskEmail(recipient));
                } catch (Exception e) {
                    log.error("[ESIGN_EMAIL][ERROR] RabbitMQ routing failed exchange={} routingKey={} applicationId={} recipient={}: {}",
                            exchange, routingKey, applicationId, maskEmail(recipient), e.getMessage(), e);
                }
            }
        }
        log.info("[ESIGN_EMAIL] publish batch finished applicationId={} configuredActionCount={} (delivery via notification-service)",
                applicationId, actions.size());
    }

    private static String maskAppNumber(String applicationNumber) {
        if (applicationNumber == null || applicationNumber.length() <= 6) {
            return applicationNumber != null ? applicationNumber : "";
        }
        return applicationNumber.substring(0, 3) + "…" + applicationNumber.substring(applicationNumber.length() - 3);
    }

    private static String maskEmails(List<String> emails) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emails.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(maskEmail(emails.get(i)));
        }
        return sb.toString();
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
