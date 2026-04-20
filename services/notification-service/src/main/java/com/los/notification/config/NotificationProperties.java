package com.los.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.notification")
public class NotificationProperties {

    private SmsProperties sms = new SmsProperties();
    private EmailProperties email = new EmailProperties();
    private WhatsAppProperties whatsapp = new WhatsAppProperties();

    @Data
    public static class SmsProperties {
        private String provider = "MSG91";
        private Msg91Properties msg91 = new Msg91Properties();
        private TwilioProperties twilio = new TwilioProperties();
    }

    @Data
    public static class Msg91Properties {
        private String authKey = "";
        private String senderId = "LOSAPP";
        private String route = "4";
        private String baseUrl = "https://api.msg91.com/api/v5";
    }

    @Data
    public static class TwilioProperties {
        private String accountSid = "";
        private String authToken = "";
        private String fromNumber = "";
    }

    @Data
    public static class EmailProperties {
        private String provider = "SMTP";
        private String fromAddress = "noreply@los-platform.com";
        private String fromName = "LOS Platform";
        private SendGridProperties sendgrid = new SendGridProperties();
    }

    @Data
    public static class SendGridProperties {
        private String apiKey = "";
    }

    @Data
    public static class WhatsAppProperties {
        private String provider = "META";
        private MetaWhatsAppProperties meta = new MetaWhatsAppProperties();
    }

    @Data
    public static class MetaWhatsAppProperties {
        private String phoneNumberId = "";
        private String accessToken = "";
        private String baseUrl = "https://graph.facebook.com/v18.0";
    }
}
