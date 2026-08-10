package com.los.notification.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationDiagnosticsConfig {

    private final NotificationProperties notificationProperties;
    private final JavaMailSender mailSender;

    /**
     * Adds explicit listener exception logs (including conversion/deserialization failures).
     */
    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setErrorHandler(new ConditionalRejectingErrorHandler(t -> {
            if (t instanceof ListenerExecutionFailedException le && le.getFailedMessage() != null) {
                log.error("[NOTIFICATION_CONSUMER][ERROR] listener failed payload={} cause={}",
                        new String(le.getFailedMessage().getBody()),
                        t.getMessage(), t);
            } else {
                log.error("[NOTIFICATION_CONSUMER][ERROR] listener failed cause={}", t.getMessage(), t);
            }
            return true;
        }));
        return factory;
    }

    @PostConstruct
    void logStartupDiagnostics() {
        log.info("[SMTP_INIT] starting smtp diagnostics");
        log.info("[NOTIFICATION] Startup diagnostics emailProvider={} fromAddress={} fromName={}",
                notificationProperties.getEmail().getProvider(),
                notificationProperties.getEmail().getFromAddress(),
                notificationProperties.getEmail().getFromName());
        log.info("[SMTP_CONFIG_LOADED] provider={} fromAddress={} simulationMode={}",
                notificationProperties.getEmail().getProvider(),
                notificationProperties.getEmail().getFromAddress(),
                notificationProperties.getEmail().isSimulationEnabled());
        log.info("[SMTP_INIT] simulationMode={} provider={}",
                notificationProperties.getEmail().isSimulationEnabled(),
                notificationProperties.getEmail().getProvider());

        if (mailSender instanceof JavaMailSenderImpl impl) {
            String envSmtpHost = System.getenv("SMTP_HOST");
            String envSmtpPort = System.getenv("SMTP_PORT");
            String envSmtpUser = System.getenv("SMTP_USERNAME");
            log.info("[SMTP_ENV] SMTP_HOST={} SMTP_PORT={} SMTP_USERNAME={}",
                    envSmtpHost, envSmtpPort, envSmtpUser);
            log.info("[NOTIFICATION] SMTP configured host={} port={} usernamePresent={}",
                    impl.getHost(), impl.getPort(), hasText(impl.getUsername()));
            log.info("[SMTP_INIT] Host={} Port={} usernameLoaded={}",
                    impl.getHost(), impl.getPort(), hasText(impl.getUsername()));
            String startTlsEnabled = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
            String startTlsRequired = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
            String authEnabled = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.auth"));
            String sslEnabled = String.valueOf(impl.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
            log.info("[EMAIL_STARTTLS_ENABLED] enabled={} required={} auth={} ssl.enable={} host={} port={} username={}",
                    startTlsEnabled, startTlsRequired, authEnabled, sslEnabled, impl.getHost(), impl.getPort(), impl.getUsername());
            StringBuilder missing = new StringBuilder();
            if (!hasText(impl.getHost())) missing.append("spring.mail.host ");
            if (impl.getPort() <= 0) missing.append("spring.mail.port ");
            if (!hasText(impl.getUsername())) missing.append("spring.mail.username ");
            if (!hasText(impl.getPassword())) missing.append("spring.mail.password ");
            if (!hasText(notificationProperties.getEmail().getFromAddress())) missing.append("los.notification.email.from-address ");
            if (missing.length() > 0) {
                log.warn("[NOTIFICATION][SMTP] Missing mail properties: {}", missing.toString().trim());
                log.warn("[SMTP_INIT] SMTP not fully configured missing={}", missing.toString().trim());
            } else {
                try {
                    impl.testConnection();
                    log.info("[NOTIFICATION] SMTP connection successful host={} port={}", impl.getHost(), impl.getPort());
                    log.info("[SMTP_INIT] SMTP configured successfully");
                    log.info("[SMTP_INIT] Simulation mode {}", notificationProperties.getEmail().isSimulationEnabled() ? "enabled" : "disabled");
                } catch (Exception ex) {
                    log.error("[NOTIFICATION][SMTP][ERROR] SMTP connection failed host={} port={} reason={}",
                            impl.getHost(), impl.getPort(), ex.getMessage(), ex);
                    log.error("[SMTP_INIT] SMTP init failed reason={}", ex.getMessage(), ex);
                }
            }
        } else {
            log.warn("[NOTIFICATION][SMTP] JavaMailSenderImpl not available; cannot test SMTP connection");
            log.warn("[SMTP_INIT] SMTP init skipped JavaMailSenderImpl not available");
        }
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}

