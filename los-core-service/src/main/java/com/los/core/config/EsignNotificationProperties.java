package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "los.esign.notification")
public class EsignNotificationProperties {

    private boolean enabled = true;

    /** Must exist in notification-service templates ({@code NotificationTemplateEngine}). */
    private String templateCode = "ESIGN_PENDING";

    private int linkExpiryHours = 72;
}
