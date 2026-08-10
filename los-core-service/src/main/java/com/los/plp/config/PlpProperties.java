package com.los.plp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.plp")
public class PlpProperties {

    private boolean enabled = true;
    private String baseUrl = "https://plp.billiontech.ai/plp-api";
    private String lenderId = "";
    /**
     * PLP IAM login email for machine calls through the API gateway (JWT Bearer required on integration paths).
     */
    private String integrationEmail = "";
    private String integrationPassword = "";
    /**
     * Seconds before access-token expiry when we proactively refresh ({@link #integrationEmail} / password login).
     */
    private long tokenExpirySkewSeconds = 120;
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 30_000;
}
