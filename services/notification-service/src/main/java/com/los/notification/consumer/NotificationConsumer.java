package com.los.notification.consumer;

import com.los.notification.dto.NotificationEvent;
import com.los.notification.entity.NotificationLog;
import com.los.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationLogRepository notificationLogRepository;

    @RabbitListener(queues = "notification.sms")
    public void handleSms(NotificationEvent event) {
        log.info("SMS notification received for: {}", event.getRecipient());
        processNotification(event, "SMS");
    }

    @RabbitListener(queues = "notification.email")
    public void handleEmail(NotificationEvent event) {
        log.info("Email notification received for: {}", event.getRecipient());
        processNotification(event, "EMAIL");
    }

    @RabbitListener(queues = "notification.whatsapp")
    public void handleWhatsApp(NotificationEvent event) {
        log.info("WhatsApp notification received for: {}", event.getRecipient());
        processNotification(event, "WHATSAPP");
    }

    private void processNotification(NotificationEvent event, String channel) {
        NotificationLog notifLog = NotificationLog.builder()
                .channel(channel)
                .recipient(event.getRecipient())
                .templateCode(event.getTemplateCode())
                .eventType(event.getEventType())
                .templateData(event.getTemplateData())
                .applicationId(event.getApplicationId())
                .build();

        try {
            // TODO: Integrate with actual SMS/Email/WhatsApp provider
            log.info("Sending {} to {} using template: {}", channel, event.getRecipient(), event.getTemplateCode());

            notifLog.setStatus("SENT");
            notifLog.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send {} notification: {}", channel, e.getMessage());
            notifLog.setStatus("FAILED");
            notifLog.setErrorMessage(e.getMessage());
            notifLog.setRetryCount(notifLog.getRetryCount() + 1);
        }

        notificationLogRepository.save(notifLog);
    }
}
