package com.los.notification.consumer;

import com.los.notification.dto.NotificationEvent;
import com.los.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    @jakarta.annotation.PostConstruct
    void onStart() {
        log.info("[NOTIFICATION] Rabbit consumer registered queues=[notification.sms, notification.email, notification.whatsapp, notification.dlq]");
    }

    @RabbitListener(queues = "notification.sms")
    public void handleSms(
            @Payload NotificationEvent event,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        log.info("[NOTIFICATION_CONSUMER] queue=notification.sms routingKey={} recipient={}",
                routingKey, maskRecipient(event.getRecipient()));
        event.setChannel("SMS");
        notificationService.sendNotification(event);
    }

    @RabbitListener(queues = "notification.email")
    public void handleEmail(
            @Payload NotificationEvent event,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        log.info("[EMAIL_CONSUMER_PAYLOAD] routingKey={} channel={} recipient={} templateCode={} eventType={} applicationId={} templateData={}",
                routingKey,
                event.getChannel(),
                maskRecipient(event.getRecipient()),
                event.getTemplateCode(),
                event.getEventType(),
                event.getApplicationId(),
                event.getTemplateData());
        log.info("[EMAIL_CONSUMER] message received queue=notification.email routingKey={} applicationId={} recipient={}",
                routingKey, event.getApplicationId(), maskRecipient(event.getRecipient()));
        log.info("[NOTIFICATION_EMAIL] received eventType={} templateCode={} applicationId={} recipient={}",
                event.getEventType(),
                event.getTemplateCode(),
                event.getApplicationId(),
                maskRecipient(event.getRecipient()));
        log.info("[NOTIFICATION_CONSUMER] queue=notification.email routingKey={} payloadTemplateDataKeys={}",
                routingKey, event.getTemplateData() != null ? event.getTemplateData().keySet() : java.util.Set.of());
        boolean signingUrlPresent = event.getTemplateData() != null
                && event.getTemplateData().get("esignLink") != null
                && !String.valueOf(event.getTemplateData().get("esignLink")).isBlank();
        Object workflowId = event.getTemplateData() != null ? event.getTemplateData().get("workflowId") : null;
        log.info("[EMAIL_CONSUMER] signingUrlAvailable={} workflowId={} templateCode={} eventType={}",
                signingUrlPresent, workflowId != null ? workflowId : "", event.getTemplateCode(), event.getEventType());
        log.info("[ESIGN_EMAIL] Message consumed successfully");
        event.setChannel("EMAIL");
        notificationService.sendNotification(event);
    }

    private static String maskRecipient(String recipient) {
        if (recipient == null || recipient.isBlank()) {
            return "";
        }
        if (recipient.contains("@")) {
            int at = recipient.indexOf('@');
            if (at <= 1) {
                return "*@" + recipient.substring(at + 1);
            }
            return recipient.charAt(0) + "***@" + recipient.substring(at + 1);
        }
        if (recipient.length() <= 4) {
            return "****";
        }
        return recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 2);
    }

    @RabbitListener(queues = "notification.whatsapp")
    public void handleWhatsApp(
            @Payload NotificationEvent event,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        log.info("[NOTIFICATION_CONSUMER] queue=notification.whatsapp routingKey={} recipient={}",
                routingKey, maskRecipient(event.getRecipient()));
        event.setChannel("WHATSAPP");
        notificationService.sendNotification(event);
    }

    @RabbitListener(queues = "notification.dlq")
    public void handleDlq(
            @Payload String payload,
            @Header(name = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey,
            @Header(name = AmqpHeaders.DELIVERY_TAG, required = false) Long deliveryTag) {
        log.error("[NOTIFICATION_DLQ][ERROR] queue=notification.dlq routingKey={} deliveryTag={} payload={}",
                routingKey, deliveryTag, payload);
    }
}
