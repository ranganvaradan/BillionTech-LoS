package com.los.notification.service;

import com.los.notification.config.NotificationProperties;
import com.los.notification.dto.NotificationEvent;
import com.los.notification.entity.NotificationLog;
import com.los.notification.repository.NotificationLogRepository;
import com.los.notification.template.NotificationTemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * Properly escape a string for embedding in JSON.
     * Handles backslash, double-quote, newline, carriage return, tab, and other control characters.
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationTemplateEngine templateEngine;
    private final RabbitTemplate rabbitTemplate;
    private final JavaMailSender mailSender;
    private final NotificationProperties notificationProperties;

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
        switch (channel.toUpperCase()) {
            case "SMS" -> deliverSms(recipient, body);
            case "EMAIL" -> deliverEmail(recipient, subject, body);
            case "WHATSAPP" -> deliverWhatsApp(recipient, body);
            default -> log.warn("Unknown channel: {}", channel);
        }
    }

    /**
     * SMS delivery via MSG91 or Twilio.
     * Falls back to console logging when credentials not configured.
     */
    private void deliverSms(String recipient, String body) {
        NotificationProperties.SmsProperties smsConfig = notificationProperties.getSms();
        String provider = smsConfig.getProvider();

        if ("TWILIO".equalsIgnoreCase(provider)) {
            deliverSmsTwilio(recipient, body, smsConfig.getTwilio());
        } else {
            deliverSmsMsg91(recipient, body, smsConfig.getMsg91());
        }
    }

    /**
     * MSG91 SMS delivery — adapted from legacy SmsServiceFacadeImpl.
     * POST https://api.msg91.com/api/v5/flow/
     * Headers: authkey={authKey}, Content-Type: application/json
     */
    private void deliverSmsMsg91(String recipient, String body, NotificationProperties.Msg91Properties config) {
        if (config.getAuthKey() == null || config.getAuthKey().isBlank()) {
            log.info("[SMS-SIM] MSG91 credentials not configured — simulated SMS to {}: {}",
                    recipient, body.substring(0, Math.min(body.length(), 80)));
            return;
        }

        try {
            String payload = String.format(
                    "{\"sender\":\"%s\",\"route\":\"%s\",\"country\":\"91\"," +
                    "\"sms\":[{\"message\":\"%s\",\"to\":[\"%s\"]}]}",
                    escapeJson(config.getSenderId()), escapeJson(config.getRoute()),
                    escapeJson(body), escapeJson(recipient));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/flow/"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("authkey", config.getAuthKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[SMS] MSG91 delivered to {}", recipient);
            } else {
                log.error("[SMS] MSG91 failed: HTTP {} — {}", response.statusCode(), response.body());
                throw new RuntimeException("MSG91 SMS failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SMS delivery interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("MSG91 SMS delivery error: " + e.getMessage(), e);
        }
    }

    /**
     * Twilio SMS delivery.
     * POST https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json
     */
    private void deliverSmsTwilio(String recipient, String body, NotificationProperties.TwilioProperties config) {
        if (config.getAccountSid() == null || config.getAccountSid().isBlank()) {
            log.info("[SMS-SIM] Twilio credentials not configured — simulated SMS to {}: {}",
                    recipient, body.substring(0, Math.min(body.length(), 80)));
            return;
        }

        try {
            String payload = String.format("To=%s&From=%s&Body=%s",
                    java.net.URLEncoder.encode(recipient, "UTF-8"),
                    java.net.URLEncoder.encode(config.getFromNumber(), "UTF-8"),
                    java.net.URLEncoder.encode(body, "UTF-8"));

            String authString = config.getAccountSid() + ":" + config.getAuthToken();
            String authHeader = "Basic " + java.util.Base64.getEncoder().encodeToString(authString.getBytes());

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.twilio.com/2010-04-01/Accounts/"
                            + config.getAccountSid() + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", authHeader)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                log.info("[SMS] Twilio delivered to {}", recipient);
            } else {
                log.error("[SMS] Twilio failed: HTTP {} — {}", response.statusCode(), response.body());
                throw new RuntimeException("Twilio SMS failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Twilio delivery interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Twilio SMS delivery error: " + e.getMessage(), e);
        }
    }

    /**
     * Email delivery via Spring JavaMailSender (SMTP) or SendGrid API.
     * Falls back to console logging when credentials not configured.
     */
    private void deliverEmail(String recipient, String subject, String body) {
        NotificationProperties.EmailProperties emailConfig = notificationProperties.getEmail();

        if ("SENDGRID".equalsIgnoreCase(emailConfig.getProvider())) {
            deliverEmailSendGrid(recipient, subject, body, emailConfig);
        } else {
            deliverEmailSmtp(recipient, subject, body, emailConfig);
        }
    }

    /**
     * SMTP Email delivery via Spring JavaMailSender.
     */
    private void deliverEmailSmtp(String recipient, String subject, String body,
                                    NotificationProperties.EmailProperties config) {
        // Fall back to simulation if SMTP credentials are not configured
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            String user = impl.getUsername();
            if (user == null || user.isBlank()) {
                log.info("[EMAIL-SIM] SMTP credentials not configured — simulated email to {} — Subject: {}",
                        recipient, subject);
                return;
            }
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(recipient);
            helper.setSubject(subject != null ? subject : "LOS Platform Notification");
            helper.setText(body, true); // true = HTML

            mailSender.send(message);
            log.info("[EMAIL] SMTP delivered to {} — Subject: {}", recipient, subject);
        } catch (Exception e) {
            log.error("[EMAIL] SMTP delivery failed to {}: {}", recipient, e.getMessage());
            throw new RuntimeException("SMTP email delivery failed: " + e.getMessage(), e);
        }
    }

    /**
     * SendGrid Email delivery via REST API.
     * POST https://api.sendgrid.com/v3/mail/send
     */
    private void deliverEmailSendGrid(String recipient, String subject, String body,
                                        NotificationProperties.EmailProperties config) {
        String apiKey = config.getSendgrid().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.info("[EMAIL-SIM] SendGrid credentials not configured — simulated email to {} — Subject: {}",
                    recipient, subject);
            return;
        }

        try {
            String payload = String.format(
                    "{\"personalizations\":[{\"to\":[{\"email\":\"%s\"}]}]," +
                    "\"from\":{\"email\":\"%s\",\"name\":\"%s\"}," +
                    "\"subject\":\"%s\"," +
                    "\"content\":[{\"type\":\"text/html\",\"value\":\"%s\"}]}",
                    escapeJson(recipient),
                    escapeJson(config.getFromAddress()), escapeJson(config.getFromName()),
                    subject != null ? escapeJson(subject) : "LOS Notification",
                    escapeJson(body));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 202) {
                log.info("[EMAIL] SendGrid delivered to {} — Subject: {}", recipient, subject);
            } else {
                log.error("[EMAIL] SendGrid failed: HTTP {} — {}", response.statusCode(), response.body());
                throw new RuntimeException("SendGrid email failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SendGrid delivery interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SendGrid email delivery error: " + e.getMessage(), e);
        }
    }

    /**
     * WhatsApp delivery via Meta WhatsApp Business API.
     * Falls back to console logging when credentials not configured.
     */
    private void deliverWhatsApp(String recipient, String body) {
        NotificationProperties.WhatsAppProperties waConfig = notificationProperties.getWhatsapp();
        NotificationProperties.MetaWhatsAppProperties metaConfig = waConfig.getMeta();

        if (metaConfig.getAccessToken() == null || metaConfig.getAccessToken().isBlank()) {
            log.info("[WHATSAPP-SIM] WhatsApp credentials not configured — simulated message to {}: {}",
                    recipient, body.substring(0, Math.min(body.length(), 80)));
            return;
        }

        try {
            String payload = String.format(
                    "{\"messaging_product\":\"whatsapp\",\"to\":\"%s\"," +
                    "\"type\":\"text\",\"text\":{\"body\":\"%s\"}}",
                    escapeJson(recipient), escapeJson(body));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metaConfig.getBaseUrl() + "/" + metaConfig.getPhoneNumberId() + "/messages"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + metaConfig.getAccessToken())
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                log.info("[WHATSAPP] Delivered to {}", recipient);
            } else {
                log.error("[WHATSAPP] Failed: HTTP {} — {}", response.statusCode(), response.body());
                throw new RuntimeException("WhatsApp delivery failed: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("WhatsApp delivery interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("WhatsApp delivery error: " + e.getMessage(), e);
        }
    }
}
