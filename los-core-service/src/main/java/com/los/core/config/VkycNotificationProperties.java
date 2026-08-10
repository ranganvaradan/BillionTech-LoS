package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "los.vkyc.notification")
public class VkycNotificationProperties {

    private boolean enabled = true;
    private String templateCode = "VKYC_LINK";
    private int linkExpiryHours = 24;
}
