package com.los.notification.service;

import com.los.notification.dto.NotificationEvent;
import com.los.notification.entity.NotificationLog;
import com.los.notification.repository.NotificationLogRepository;
import com.los.notification.template.NotificationTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRY_COUNT = 3;

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationTemplateEngine templateEngine;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Send a notification through a specific channel.
     */
    @Transactional
    public NotificationLog sendNotification(NotificationEvent event) {
        String renderedBody = templateEngine.render(event.getTemplateCode(), event.getChannel(), event.getTemplateData());
        String renderedSubject = templateEngine.renderSubject(event.getTemplateCode(), event.getTemplateData());

        NotificationLog notifLog = NotificationLog.builder()
                .channel(event.getChannel())
                .recipient(event.getRecipient())
                .templateCode(event.getTemplateCode())
                .eventType(event.getEventType())
                .templateData(event.getTemplateData())
                .applicationId(event.getApplicationId())
                .build();

        try {
            deliverNotification(event.getChannel(), event.getRecipient(), renderedSubject, renderedBody);
            notifLog.setStatus("SENT");
            notifLog.setSentAt(Instant.now());
            log.info("Notification sent: channel={}, recipient={}, template={}", event.getChannel(), event.getRecipient(), event.getTemplateCode());
        } catch (Exception e) {
            log.error("Failed to send {} notification to {}: {}", event.getChannel(), event.getRecipient(), e.getMessage());
            notifLog.setStatus("FAILED");
            notifLog.setErrorMessage(e.getMessage());
            notifLog.setRetryCount(1);
        }

        return notificationLogRepository.save(notifLog);
    }

    /**
     * Send notification to all channels (SMS + Email + WhatsApp).
     */
    @Transactional
    public void sendMultiChannel(String templateCode, String eventType,
                                  String mobile, String email,
                                  UUID applicationId, Map<String, Object> data) {
        if (mobile != null) {
            publishToQueue("SMS", mobile, templateCode, eventType, applicationId, data);
        }
        if (email != null) {
            publishToQueue("EMAIL", email, templateCode, eventType, applicationId, data);
        }
        if (mobile != null) {
            publishToQueue("WHATSAPP", mobile, templateCode, eventType, applicationId, data);
        }
    }

    /**
     * Retry failed notifications — runs every 5 minutes.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    @Transactional
    public void retryFailedNotifications() {
        List<NotificationLog> failedLogs = notificationLogRepository.findByStatusAndRetryCountLessThan("FAILED", MAX_RETRY_COUNT);

        if (failedLogs.isEmpty()) return;

        log.info("Retrying {} failed notifications", failedLogs.size());

        for (NotificationLog notifLog : failedLogs) {
            try {
                String body = templateEngine.render(notifLog.getTemplateCode(), notifLog.getChannel(), notifLog.getTemplateData());
                String subject = templateEngine.renderSubject(notifLog.getTemplateCode(), notifLog.getTemplateData());

                deliverNotification(notifLog.getChannel(), notifLog.getRecipient(), subject, body);

                notifLog.setStatus("SENT");
                notifLog.setSentAt(Instant.now());
                notifLog.setErrorMessage(null);
                log.info("Retry succeeded for notification {}", notifLog.getId());
            } catch (Exception e) {
                notifLog.setRetryCount(notifLog.getRetryCount() + 1);
                notifLog.setErrorMessage("Retry #" + notifLog.getRetryCount() + ": " + e.getMessage());

                if (notifLog.getRetryCount() >= MAX_RETRY_COUNT) {
                    notifLog.setStatus("PERMANENTLY_FAILED");
                    log.warn("Notification {} permanently failed after {} retries", notifLog.getId(), MAX_RETRY_COUNT);
                }
            }
            notificationLogRepository.save(notifLog);
        }
    }

    /**
     * Get notification history for an application.
     */
    public Page<NotificationLog> getByApplication(UUID applicationId, Pageable pageable) {
        return notificationLogRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable);
    }

    /**
     * Get notification history for a recipient.
     */
    public Page<NotificationLog> getByRecipient(String recipient, Pageable pageable) {
        return notificationLogRepository.findByRecipientOrderByCreatedAtDesc(recipient, pageable);
    }

    /**
     * Get all notifications with pagination.
     */
    public Page<NotificationLog> getAll(Pageable pageable) {
        return notificationLogRepository.findAll(pageable);
    }

    /**
     * Get notification summary stats.
     */
    public Map<String, Object> getSummary() {
        long total = notificationLogRepository.count();
        long sent = notificationLogRepository.countByStatus("SENT");
        long failed = notificationLogRepository.countByStatus("FAILED");
        long pending = notificationLogRepository.countByStatus("PENDING");
        long permFailed = notificationLogRepository.countByStatus("PERMANENTLY_FAILED");

        return Map.of(
                "total", total,
                "sent", sent,
                "failed", failed,
                "pending", pending,
                "permanentlyFailed", permFailed,
                "successRate", total > 0 ? String.format("%.1f%%", (sent * 100.0) / total) : "N/A"
        );
    }

    /**
     * Resend a specific notification.
     */
    @Transactional
    public NotificationLog resend(UUID notificationId) {
        NotificationLog notifLog = notificationLogRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));

        try {
            String body = templateEngine.render(notifLog.getTemplateCode(), notifLog.getChannel(), notifLog.getTemplateData());
            String subject = templateEngine.renderSubject(notifLog.getTemplateCode(), notifLog.getTemplateData());

            deliverNotification(notifLog.getChannel(), notifLog.getRecipient(), subject, body);

            notifLog.setStatus("SENT");
            notifLog.setSentAt(Instant.now());
            notifLog.setErrorMessage(null);
        } catch (Exception e) {
            notifLog.setRetryCount(notifLog.getRetryCount() + 1);
            notifLog.setErrorMessage("Manual resend failed: " + e.getMessage());
        }

        return notificationLogRepository.save(notifLog);
    }

    /**
     * BR-10.7: Bulk SMS for overdue reminders.
     */
    @Transactional
    public Map<String, Object> sendBulkOverdueReminders(List<Map<String, Object>> overdueAccounts) {
        int sent = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Map<String, Object> account : overdueAccounts) {
            String mobile = (String) account.get("mobile");
            String customerName = (String) account.getOrDefault("customerName", "Customer");
            String applicationNumber = (String) account.getOrDefault("applicationNumber", "N/A");
            Object overdueAmountObj = account.getOrDefault("overdueAmount", "0");
            Object dpdObj = account.getOrDefault("dpd", 0);

            if (mobile == null || mobile.isBlank()) {
                errors.add(applicationNumber + ": no mobile number");
                failed++;
                continue;
            }

            try {
                Map<String, Object> data = Map.of(
                        "customerName", customerName,
                        "applicationNumber", applicationNumber,
                        "overdueAmount", overdueAmountObj.toString(),
                        "dpd", dpdObj.toString()
                );

                NotificationEvent event = new NotificationEvent();
                event.setChannel("SMS");
                event.setRecipient(mobile);
                event.setTemplateCode("OVERDUE_REMINDER");
                event.setEventType("OVERDUE_REMINDER");
                event.setTemplateData(data);

                sendNotification(event);
                sent++;
            } catch (Exception e) {
                errors.add(applicationNumber + ": " + e.getMessage());
                failed++;
            }
        }

        log.info("Bulk overdue reminders: {} sent, {} failed out of {}", sent, failed, overdueAccounts.size());

        return Map.of(
                "totalRequested", overdueAccounts.size(),
                "sent", sent,
                "failed", failed,
                "errors", errors
        );
    }

    /**
     * BR-10.4: In-app notification — store for dashboard polling.
     * In production, would use WebSocket/SSE push. Here we store and expose via REST.
     */
    @Transactional
    public NotificationLog createInAppNotification(UUID applicationId, String userId,
                                                     String title, String message) {
        NotificationLog notifLog = NotificationLog.builder()
                .channel("IN_APP")
                .recipient(userId)
                .templateCode("IN_APP_ALERT")
                .eventType("IN_APP")
                .applicationId(applicationId)
                .templateData(Map.of("title", title, "message", message))
                .status("DELIVERED")
                .sentAt(Instant.now())
                .build();

        return notificationLogRepository.save(notifLog);
    }

    /**
     * BR-10.4: Get unread in-app notifications for a user.
     */
    public List<NotificationLog> getInAppNotifications(String userId) {
        return notificationLogRepository.findByChannelAndRecipientOrderByCreatedAtDesc("IN_APP", userId);
    }

    private void publishToQueue(String channel, String recipient, String templateCode,
                                 String eventType, UUID applicationId, Map<String, Object> data) {
        NotificationEvent event = new NotificationEvent();
        event.setChannel(channel);
        event.setRecipient(recipient);
        event.setTemplateCode(templateCode);
        event.setEventType(eventType);
        event.setApplicationId(applicationId);
        event.setTemplateData(data);

        String routingKey = "notification." + channel.toLowerCase() + "." + eventType.toLowerCase();
        rabbitTemplate.convertAndSend("los.notification", routingKey, event);
    }

    private void deliverNotification(String channel, String recipient, String subject, String body) {
        // Simulated delivery — in production, integrate with actual providers
        switch (channel.toUpperCase()) {
            case "SMS" -> {
                log.info("📱 SMS to {}: {}", recipient, body.substring(0, Math.min(body.length(), 80)));
                // Integration point: Twilio, MSG91, etc.
            }
            case "EMAIL" -> {
                log.info("📧 Email to {} — Subject: {}", recipient, subject);
                // Integration point: JavaMailSender, SendGrid, etc.
            }
            case "WHATSAPP" -> {
                log.info("💬 WhatsApp to {}: {}", recipient, body.substring(0, Math.min(body.length(), 80)));
                // Integration point: WhatsApp Business API
            }
            default -> log.warn("Unknown channel: {}", channel);
        }
    }
}
