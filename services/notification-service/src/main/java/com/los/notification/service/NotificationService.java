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
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.MessagingException;
import java.util.regex.Pattern;
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
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<\\s*(html|body|table|div|a|p|span|h[1-6]|!doctype)\\b");

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
        if (event == null) {
            throw new RuntimeException("NotificationEvent is null");
        }
        log.info("[NOTIFICATION_SEND] start channel={} templateCode={} eventType={} recipient={} applicationId={}",
                event.getChannel(),
                event.getTemplateCode(),
                event.getEventType(),
                maskRecipient(event.getRecipient()),
                event.getApplicationId());
        log.info("[NOTIFICATION_PAYLOAD] templateCodeRaw={} templateCodeChars={} channelRaw={} channelChars={} templateDataKeys={}",
                event.getTemplateCode(),
                printableChars(event.getTemplateCode()),
                event.getChannel(),
                printableChars(event.getChannel()),
                event.getTemplateData() != null ? event.getTemplateData().keySet() : java.util.Set.of());
        log.info("[EMAIL_SEND_START] channel={} templateCode={} recipient={} applicationId={}",
                event.getChannel(), event.getTemplateCode(), maskRecipient(event.getRecipient()), event.getApplicationId());

        String effectiveChannel = resolveEffectiveChannel(event);
        log.info("[EMAIL_TEMPLATE] render requested templateCode={} channel={} effectiveChannel={} applicationId={}",
                event.getTemplateCode(), event.getChannel(), effectiveChannel, event.getApplicationId());
        String renderedBody = templateEngine.render(event.getTemplateCode(), effectiveChannel, event.getTemplateData());
        String renderedSubject = templateEngine.renderSubject(event.getTemplateCode(), event.getTemplateData());
        boolean renderedHtml = looksLikeHtml(renderedBody);
        if (!renderedHtml && shouldForceHtmlTemplate(event, effectiveChannel)) {
            String forcedHtmlBody = templateEngine.render(resolveForcedTemplateCode(event), "EMAIL", event.getTemplateData());
            boolean forcedIsHtml = looksLikeHtml(forcedHtmlBody);
            log.warn("[EMAIL_TEMPLATE_FORCE] non-html rendered for email templateCode={} eventType={} -> forcedTemplateCode={} forcedIsHtml={}",
                    event.getTemplateCode(),
                    event.getEventType(),
                    resolveForcedTemplateCode(event),
                    forcedIsHtml);
            if (forcedIsHtml) {
                renderedBody = forcedHtmlBody;
                renderedHtml = true;
            }
        }
        log.info("[EMAIL_TEMPLATE_TYPE] templateCode={} channel={} isHtml={} containsHtmlTag={} preview=\"{}\"",
                event.getTemplateCode(),
                effectiveChannel,
                renderedHtml,
                renderedBody != null && renderedBody.toLowerCase().contains("<html"),
                previewBody(renderedBody));
        log.info("[EMAIL_TEMPLATE_SNIPPET] bodyFirst300=\"{}\"", previewBody(renderedBody, 300));
        log.info("[EMAIL_TEMPLATE] rendered successfully subjectPresent={} bodyLength={}",
                renderedSubject != null && !renderedSubject.isBlank(),
                renderedBody != null ? renderedBody.length() : 0);
        log.info("[EMAIL_SEND_INPUT] templateCode={} effectiveChannel={} subjectLength={} bodyLength={} bodyPreview=\"{}\"",
                event.getTemplateCode(),
                effectiveChannel,
                renderedSubject != null ? renderedSubject.length() : 0,
                renderedBody != null ? renderedBody.length() : 0,
                previewBody(renderedBody, 200));
        log.debug("[NOTIFICATION_SEND] template rendered subjectLen={} bodyLen={}",
                renderedSubject != null ? renderedSubject.length() : 0,
                renderedBody != null ? renderedBody.length() : 0);

        NotificationLog notifLog = NotificationLog.builder()
                .channel(event.getChannel())
                .recipient(event.getRecipient())
                .templateCode(event.getTemplateCode())
                .eventType(event.getEventType())
                .templateData(event.getTemplateData())
                .applicationId(event.getApplicationId())
                .build();

        try {
            log.info("[NOTIFICATION_SEND] template rendering started templateCode={} channel={} effectiveChannel={} recipient={}",
                    event.getTemplateCode(), event.getChannel(), effectiveChannel, maskRecipient(event.getRecipient()));
            deliverNotification(effectiveChannel, event.getRecipient(), renderedSubject, renderedBody);
            notifLog.setStatus("SENT");
            notifLog.setSentAt(Instant.now());
            log.info("[NOTIFICATION_SEND] success channel={} templateCode={} recipient={}",
                    event.getChannel(),
                    event.getTemplateCode(),
                    maskRecipient(event.getRecipient()));
            log.info("[EMAIL_SEND_SUCCESS] channel={} templateCode={} recipient={}",
                    event.getChannel(), event.getTemplateCode(), maskRecipient(event.getRecipient()));
            if (isEsignEmailEvent(event)) {
                log.info("[ESIGN_EMAIL] Mail sent successfully to {}", maskRecipient(event.getRecipient()));
            }
        } catch (Exception e) {
            log.error("[NOTIFICATION_SEND] failure channel={} templateCode={} recipient={} reason={}",
                    event.getChannel(),
                    event.getTemplateCode(),
                    maskRecipient(event.getRecipient()),
                    e.getMessage());
            if (isEsignEmailEvent(event)) {
                log.error("[ESIGN_EMAIL][ERROR] Queue consumer failed while rendering/sending template: {}", e.getMessage(), e);
            }
            log.error("[EMAIL_SEND_FAILED] channel={} templateCode={} recipient={} reason={}",
                    event.getChannel(), event.getTemplateCode(), maskRecipient(event.getRecipient()), e.getMessage(), e);
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
        log.info("[EMAIL_DELIVERY_PATH] provider={} usingSender={} recipient={}",
                emailConfig.getProvider(),
                "SENDGRID".equalsIgnoreCase(emailConfig.getProvider()) ? "SendGridApi" : "JavaMailSenderMimeMessage",
                maskRecipient(recipient));

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
        log.info("[ESIGN_EMAIL] Preparing SMTP mail for borrower");
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            log.info("[EMAIL_SMTP] connecting host={} port={} recipient={} applicationFlow=esign",
                    impl.getHost(), impl.getPort(), maskRecipient(recipient));
        } else {
            log.info("[EMAIL_SMTP] connecting recipient={} applicationFlow=esign",
                    maskRecipient(recipient));
        }
        log.info("[EMAIL] SMTP connection started to={} provider={} subject={}",
                maskRecipient(recipient), config.getProvider(), subject != null ? subject : "");
        // Fall back to simulation if SMTP credentials are not configured
        if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
            String user = impl.getUsername();
            String smtpHost = impl.getHost();
            int smtpPort = impl.getPort();
            String startTlsEnabled = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
            String startTlsRequired = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
            String smtpAuth = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.auth"));
            log.info("[EMAIL_STARTTLS_ENABLED] enabled={} required={} auth={} host={} port={}",
                    startTlsEnabled, startTlsRequired, smtpAuth, smtpHost, smtpPort);
            if (user == null || user.isBlank()) {
                String missing = missingSmtpProperties(impl, config);
                if (config.isSimulationEnabled()) {
                    log.warn("[EMAIL-SIM] SMTP credentials not configured missing=[{}] — simulated email to {} — Subject: {}",
                            missing, maskRecipient(recipient), subject);
                    return;
                }
                log.error("[EMAIL_SEND_FAILED] SMTP credentials missing and simulation disabled missing=[{}]", missing);
                throw new RuntimeException("SMTP credentials missing and simulation mode is disabled. Missing: " + missing);
            }
            log.info("[EMAIL_SMTP_CONFIG] host={} port={} usernameLoaded={} simulationMode={}",
                    impl.getHost(), impl.getPort(), true, config.isSimulationEnabled());
            log.info("[EMAIL_SMTP] authenticated host={} port={} usernamePresent=true",
                    impl.getHost(), impl.getPort());
            log.info("[EMAIL] SMTP auth config usernamePresent=true host={} port={}", impl.getHost(), impl.getPort());
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    "UTF-8");
            helper.setFrom(config.getFromAddress(), config.getFromName());
            helper.setTo(recipient);
            helper.setSubject(subject != null ? subject : "LOS Platform Notification");
            boolean bodyLooksHtml = looksLikeHtml(body);
            log.info("[EMAIL_SMTP] body diagnostics isHtml={} containsHtmlTag={} preview=\"{}\"",
                    bodyLooksHtml,
                    body != null && body.toLowerCase().contains("<html"),
                    previewBody(body));
            if (bodyLooksHtml) {
                String htmlBody = body != null ? body : "";
                String plainTextFallback = toPlainText(htmlBody);
                helper.setText(plainTextFallback, htmlBody);
                log.info("[EMAIL_SMTP_SETTEXT] method=MimeMessageHelper.setText(plain,html) htmlLength={} plainLength={}",
                        htmlBody.length(),
                        plainTextFallback.length());
                log.info("[EMAIL_SMTP] html snippet first300=\"{}\"", previewBody(body, 300));
                log.info("[EMAIL_SMTP] html mode enabled contentPath=multipart-alternative");
            } else {
                helper.setText(body != null ? body : "", false);
                log.info("[EMAIL_SMTP_SETTEXT] method=MimeMessageHelper.setText(text,false) textLength={}",
                        body != null ? body.length() : 0);
                log.warn("[EMAIL_SMTP] plain text path executed mode=text fallbackReason=renderedBodyNotHtml");
            }
            message.saveChanges();
            log.info("[EMAIL_SMTP] contentType={} mimeVersion={}",
                    message.getContentType(),
                    firstHeader(message, "MIME-Version"));

            log.info("[EMAIL_SMTP] sending mail recipient={} subject={} appIdUnknownInMailer=true",
                    maskRecipient(recipient), subject != null ? subject : "");
            mailSender.send(message);
            log.info("[EMAIL] SMTP send accepted to={} subject={}",
                    maskRecipient(recipient),
                    subject != null ? subject : "");
            log.info("[EMAIL_SMTP] send success recipient={}", maskRecipient(recipient));
            log.info("[ESIGN_EMAIL] SMTP send successful");
        } catch (MailAuthenticationException e) {
            log.error("[ESIGN_EMAIL][ERROR] SMTP authentication failed", e);
            log.error("[EMAIL_SMTP_AUTH_FAILED] {}", e.getMessage(), e);
            log.error("[EMAIL_SMTP][ERROR] send failed reason={} note=For Gmail use App Password if 2FA enabled or allow less secure access is blocked",
                    e.getMessage(), e);
            throw new RuntimeException("SMTP authentication failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[EMAIL][ERROR] SMTP delivery failed to {}: {} — {}",
                    maskRecipient(recipient), e.getClass().getSimpleName(), e.getMessage(), e);
            if (mailSender instanceof org.springframework.mail.javamail.JavaMailSenderImpl impl) {
                log.error("[EMAIL_SMTP][ERROR] host={} port={} auth={} starttls.enable={} starttls.required={}",
                        impl.getHost(),
                        impl.getPort(),
                        impl.getJavaMailProperties().getProperty("mail.smtp.auth"),
                        impl.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"),
                        impl.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
            }
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.error("[EMAIL_SMTP][ERROR] rootCause={} message={}",
                    root.getClass().getSimpleName(), root.getMessage());
            if (root instanceof MessagingException me && me.getNextException() != null) {
                Exception next = me.getNextException();
                log.error("[EMAIL_SMTP][ERROR] smtpStageFailure={} message={}",
                        next.getClass().getSimpleName(), next.getMessage());
            }
            log.error("[EMAIL_SMTP][ERROR] send failed reason={}", e.getMessage(), e);
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

    private static boolean isEsignEmailEvent(NotificationEvent event) {
        if (event == null) return false;
        return "EMAIL".equalsIgnoreCase(event.getChannel())
                && event.getTemplateCode() != null
                && event.getTemplateCode().toUpperCase().contains("ESIGN");
    }

    private static String resolveEffectiveChannel(NotificationEvent event) {
        if (event == null) {
            return "";
        }
        String channel = event.getChannel();
        if (channel != null && !channel.isBlank()) {
            return channel;
        }
        String recipient = event.getRecipient();
        if (recipient != null && recipient.contains("@")) {
            return "EMAIL";
        }
        return "";
    }

    private static boolean shouldForceHtmlTemplate(NotificationEvent event, String effectiveChannel) {
        if (event == null || !"EMAIL".equalsIgnoreCase(effectiveChannel)) {
            return false;
        }
        String tc = event.getTemplateCode() != null ? event.getTemplateCode().toUpperCase() : "";
        String et = event.getEventType() != null ? event.getEventType().toUpperCase() : "";
        return tc.contains("VKYC")
                || tc.contains("ESIGN")
                || et.contains("VKYC")
                || et.contains("ESIGN");
    }

    private static String resolveForcedTemplateCode(NotificationEvent event) {
        String tc = event != null && event.getTemplateCode() != null ? event.getTemplateCode().toUpperCase() : "";
        String et = event != null && event.getEventType() != null ? event.getEventType().toUpperCase() : "";
        if (tc.contains("VKYC") || et.contains("VKYC")) {
            return "VKYC_LINK";
        }
        if (tc.contains("ESIGN") || et.contains("ESIGN")) {
            return "ESIGN_PENDING";
        }
        return event != null && event.getTemplateCode() != null ? event.getTemplateCode() : "";
    }

    private static String missingSmtpProperties(org.springframework.mail.javamail.JavaMailSenderImpl impl,
                                                NotificationProperties.EmailProperties cfg) {
        StringBuilder sb = new StringBuilder();
        if (impl.getHost() == null || impl.getHost().isBlank()) sb.append("spring.mail.host ");
        if (impl.getPort() <= 0) sb.append("spring.mail.port ");
        if (impl.getUsername() == null || impl.getUsername().isBlank()) sb.append("spring.mail.username ");
        if (impl.getPassword() == null || impl.getPassword().isBlank()) sb.append("spring.mail.password ");
        if (cfg.getFromAddress() == null || cfg.getFromAddress().isBlank()) sb.append("los.notification.email.from-address ");
        return sb.toString().trim();
    }

    private static boolean looksLikeHtml(String body) {
        return body != null && HTML_TAG_PATTERN.matcher(body).find();
    }

    private static String previewBody(String body) {
        return previewBody(body, 500);
    }

    private static String previewBody(String body, int maxLen) {
        if (body == null) {
            return "";
        }
        String normalized = body.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(maxLen, normalized.length()));
    }

    private static String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n\n")
                .replaceAll("(?is)</div>", "\n")
                .replaceAll("(?is)</tr>", "\n")
                .replaceAll("(?is)</li>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return text.replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static String firstHeader(MimeMessage message, String headerName) {
        try {
            String[] values = message.getHeader(headerName);
            if (values == null || values.length == 0) {
                return "";
            }
            return values[0];
        } catch (MessagingException ex) {
            return "";
        }
    }

    private static String printableChars(String value) {
        if (value == null) {
            return "<null>";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                sb.append(String.format("U+%04X", (int) c));
            } else {
                sb.append(c).append("(U+").append(String.format("%04X", (int) c)).append(')');
            }
        }
        return sb.toString();
    }
}
