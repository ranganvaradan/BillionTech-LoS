package com.los.core.service.vkyc;

import com.los.core.config.RabbitMQConfig;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VkycLinkNotifier {

    private final RabbitTemplate rabbitTemplate;

    public void publishVkycLinkEmail(
            UUID applicationId,
            String applicationNumber,
            String borrowerName,
            List<String> recipientEmails,
            String vkycUrl,
            String templateCode,
            Instant expiryAt,
            boolean resent) {
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
        for (String recipient : to) {
            RoutingEmailEvent ev = RoutingEmailEvent.builder()
                    .channel("EMAIL")
                    .recipient(recipient)
                    .templateCode(code)
                    .eventType("VKYC_LINK")
                    .applicationId(applicationId)
                    .templateData(templateData)
                    .build();
            log.info("[VKYC_EMAIL_PUBLISH_PAYLOAD] templateCode={} eventType={} channel={} recipient={} applicationId={} templateData={}",
                    ev.getTemplateCode(),
                    ev.getEventType(),
                    ev.getChannel(),
                    recipient,
                    ev.getApplicationId(),
                    templateData);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "notification.email.vkyc_link", ev);
            log.info("[VKYC_EMAIL] queued applicationId={} recipient={}", applicationId, recipient);
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
}
