package com.los.notification.consumer;

import com.los.notification.dto.NotificationEvent;
import com.los.notification.entity.NotificationLog;
import com.los.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "notification.sms")
    public void handleSms(NotificationEvent event) {
        log.info("SMS notification received for: {}", event.getRecipient());
        event.setChannel("SMS");
        notificationService.sendNotification(event);
    }

    @RabbitListener(queues = "notification.email")
    public void handleEmail(NotificationEvent event) {
        log.info("Email notification received for: {}", event.getRecipient());
        event.setChannel("EMAIL");
        notificationService.sendNotification(event);
    }

    @RabbitListener(queues = "notification.whatsapp")
    public void handleWhatsApp(NotificationEvent event) {
        log.info("WhatsApp notification received for: {}", event.getRecipient());
        event.setChannel("WHATSAPP");
        notificationService.sendNotification(event);
    }
}
