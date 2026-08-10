package com.los.core.service.vkyc;

import com.los.core.config.RabbitMQConfig;
import com.los.core.model.enums.VkycCompletionMode;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.workflow.WorkflowNotificationResolverService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkycLinkNotifier {

    private final RabbitTemplate rabbitTemplate;
    private final WorkflowNotificationResolverService workflowNotificationResolverService;
    private final LoanApplicationRepository loanApplicationRepository;
    private final AuditService auditService;

    public void publishVkycLinkEmail(
            UUID applicationId,
            String applicationNumber,
            String borrowerName,
            List<String> recipientEmails,
            String vkycUrl,
            String templateCode,
            Instant expiryAt,
            boolean resent) {
        if (applicationId != null && shouldSuppressVkycLinkNotifications(applicationId)) {
            log.info("VKYC notification skipped because PKYC completion detected for applicationId={}", applicationId);
            auditService.logEvent(applicationId, "VKYC", "NOTIFICATION_SKIPPED_DUE_TO_PKYC", null, null,
                    Map.of("resent", resent ? "true" : "false", "event", "VKYC_LINK", "source", "VkycLinkNotifier.publishVkycLinkEmail"),
                    "VKYC link notification suppressed at publisher — PKYC already completed");
            return;
        }
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            return;
        }
        List<String> to = recipientEmails.stream().filter(e -> e != null && !e.isBlank()).map(String::trim).distinct().toList();
        if (to.isEmpty()) {
            return;
        }
        Map<String, Object> templateData = new LinkedHashMap<>();
        templateData.put("borrowerName", borrowerName != null && !borrowerName.isBlank() ? borrowerName : "Borrower");
        templateData.put("applicationNumber", applicationNumber != null ? applicationNumber : "");
        templateData.put("vkycLink", vkycUrl != null ? vkycUrl : "");
        templateData.put("expiryAt", expiryAt != null ? expiryAt.toString() : "");
        templateData.put("lenderName", "BillionTech LOS");
        templateData.put("eventType", "VKYC_LINK");
        templateData.put("resent", resent ? "true" : "false");

        String code = templateCode != null && !templateCode.isBlank() ? templateCode : "VKYC_LINK";
        var actions = workflowNotificationResolverService.resolveForApplication(
                applicationId,
                "VIDEO_KYC",
                "VKYC_LINK",
                to,
                code,
                "EMAIL");
        publishWorkflowEmailActions(applicationId, actions, templateData);
    }

    /**
     * Sends resolved notification actions (from {@link WorkflowNotificationResolverService})
     * to the notification exchange. Used for VKYC link, approval, and optional PKYC events.
     */
    public void publishWorkflowEmailActions(
            UUID applicationId,
            List<WorkflowNotificationResolverService.ResolvedNotificationAction> actions,
            Map<String, Object> templateData) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        if (applicationId != null && actions.stream().anyMatch(VkycLinkNotifier::isVkycLinkFamilyEvent)) {
            if (shouldSuppressVkycLinkNotifications(applicationId)) {
                log.info("VKYC notification skipped because PKYC completion detected for applicationId={} (queued or multi-channel VKYC_LINK family)",
                        applicationId);
                auditService.logEvent(applicationId, "VKYC", "NOTIFICATION_SKIPPED_DUE_TO_PKYC", null, null,
                        Map.of("event", "VKYC_LINK_FAMILY", "source", "VkycLinkNotifier.publishWorkflowEmailActions"),
                        "VKYC link/reminder notifications suppressed at publisher — PKYC already completed");
                return;
            }
        }
        for (var action : actions) {
            for (String recipient : action.getRecipients()) {
                RoutingEmailEvent ev = RoutingEmailEvent.builder()
                        .channel(action.getChannel())
                        .recipient(recipient)
                        .templateCode(action.getTemplateCode())
                        .eventType(action.getEventType())
                        .applicationId(applicationId)
                        .templateData(templateData)
                        .build();
                String routingKey = "notification." + action.getChannel().toLowerCase() + "."
                        + action.getEventType().toLowerCase();
                log.info("[VKYC_NOTIFICATION_PUBLISH] templateCode={} eventType={} channel={} recipient={} applicationId={} routingKey={} templateData={}",
                        ev.getTemplateCode(),
                        ev.getEventType(),
                        ev.getChannel(),
                        recipient,
                        ev.getApplicationId(),
                        routingKey,
                        templateData);
                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, ev);
                log.info("[VKYC_NOTIFICATION] queued applicationId={} recipient={} channel={}", applicationId, recipient, action.getChannel());
            }
        }
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

    private static boolean isVkycLinkFamilyEvent(WorkflowNotificationResolverService.ResolvedNotificationAction action) {
        if (action == null || action.getEventType() == null) {
            return false;
        }
        String u = action.getEventType().trim().toUpperCase(Locale.ROOT);
        return "VKYC_LINK".equals(u) || "VKYC_URL".equals(u) || "VKYC_EXPIRY_REMINDER".equals(u);
    }

    private boolean shouldSuppressVkycLinkNotifications(UUID applicationId) {
        return loanApplicationRepository.findById(applicationId)
                .map(app -> app.getVkycCompletionMode() == VkycCompletionMode.PKYC)
                .orElse(false);
    }
}
