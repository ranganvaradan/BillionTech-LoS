package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.integration")
public class IntegrationProperties {

    private KarzaProperties karza = new KarzaProperties();
    private EquifaxProperties equifax = new EquifaxProperties();
    private EmsignerProperties emsigner = new EmsignerProperties();
    private HypervergeProperties hyperverge = new HypervergeProperties();

    @Data
    public static class KarzaProperties {
        private String baseUrl = "https://api.karza.in";
        private String apiKey = "";
        private String gstnUrl = "https://api.karza.in/v3/gstdetailed";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class EquifaxProperties {
        private String url = "https://cais.equifax.co.in/cais/equifax/enquiry";
        private String customerId = "";
        private String userId = "";
        private String password = "";
        private String memberNumber = "";
        private String securityCode = "";
        private String productVersion = "2.2";
        private String reportFormat = "XML";
        private String productCode = "CCR";
        private int connectTimeoutMs = 15000;
        private int readTimeoutMs = 60000;
    }

    @Data
    public static class EmsignerProperties {
        private String url = "https://emsigner.com/v3/gateway";
        private String authToken = "";
        private String publicKeyPath = "classpath:keys/emsigner-public.pem";
        private String successCallbackUrl = "";
        private String failureCallbackUrl = "";
        private String cancelCallbackUrl = "";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 60000;
    }

    @Data
    public static class HypervergeProperties {
        private String generateLinkUrl = "https://ind.hyperverge.co/v1/link/generate";
        private String appId = "";
        private String appKey = "";
        private String workflowId = "";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }
}
