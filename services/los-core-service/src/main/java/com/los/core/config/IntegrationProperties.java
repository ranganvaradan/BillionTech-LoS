package com.los.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "los.integration")
public class IntegrationProperties {

    private KarzaProperties karza = new KarzaProperties();
    private VahanProperties vahan = new VahanProperties();
    private PropertyEcProperties propertyEc = new PropertyEcProperties();
    private GoogleMapsProperties googleMaps = new GoogleMapsProperties();
    private GoldRateProperties goldRate = new GoldRateProperties();
    private CkycProperties ckyc = new CkycProperties();
    private SetuAaProperties setuAa = new SetuAaProperties();
    private EquifaxProperties equifax = new EquifaxProperties();
    private EmsignerProperties emsigner = new EmsignerProperties();
    private HypervergeProperties hyperverge = new HypervergeProperties();
    private AiLosProperties aiLos = new AiLosProperties();

    /** HTTP integration with notification-service (templates & workflow-notification catalog). */
    private NotificationServiceProperties notification = new NotificationServiceProperties();

    @Data
    public static class KarzaProperties {
        private String baseUrl = "https://api.karza.in";
        private String apiKey = "xbAUwrvXGNGN2ea";
        private String gstnUrl = "https://api.karza.in/gst/prod/v2/gst-verification";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class VahanProperties {
        private String baseUrl = "https://api.karza.in/v2";
        private String apiKey = "";
        /** When true, returns realistic dummy RC data without calling Karza/Vahan. */
        private boolean simulation = true;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class PropertyEcProperties {
        /** KARZA or AUTHBRIDGE — only Karza HTTP path is wired in Phase 2. */
        private String provider = "KARZA";
        private String baseUrl = "https://api.karza.in/v3";
        private String apiKey = "";
        /** When true, returns dummy encumbrance data without calling external APIs. */
        private boolean simulation = true;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class GoogleMapsProperties {
        private String apiKey = "";
        /** When true, returns dummy geo coordinates without calling Google Maps. */
        private boolean simulation = true;
        private String geocodeUrl = "https://maps.googleapis.com/maps/api/geocode/json";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class GoldRateProperties {
        /** MCX, IBJA, or MANUAL. */
        private String provider = "MCX";
        private String apiKey = "";
        /** When true, uses {@link #simulationRatePerGram} without calling external APIs. */
        private boolean simulation = true;
        private String rateUrl = "https://api.mcxindia.com/gold-rate";
        /** Simulated rate per gram (INR) used in dev/demo. */
        private BigDecimal simulationRatePerGram = new BigDecimal("6500");
        /** Fixed rate per gram when provider is MANUAL. */
        private BigDecimal manualRatePerGram = new BigDecimal("6500");
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class CkycProperties {
        /** KARZA or AUTHBRIDGE — determines default upload URL and auth header when not overridden. */
        private String provider = "KARZA";
        /** Full CKYC upload endpoint; when blank, derived from {@link #provider}. */
        private String uploadUrl = "";
        private String apiKey = "";
        /** When true, returns a simulated KIN without calling the registry API. */
        private boolean simulation = true;
        private String fiCode = "";
        private String branchCode = "001";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 60000;
    }

    @Data
    public static class SetuAaProperties {
        private String baseUrl = "https://fiu-uat.setu.co";
        private String authUrl = "https://orgs.setu.co/api/v1/users/login";
        private String clientId = "";
        private String clientSecret = "";
        private String productInstanceId = "";
        private String redirectUrl = "https://los.billiontech.ai/aa/callback";
        /** When true, routes AA calls through {@link com.los.core.service.aa.providers.impl.SimulatedAaProvider}. */
        private boolean simulation = true;
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 60000;
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
        /**
         * When {@code aggregator_configs} does not set {@code embeddedSigningBaseUrl} in {@code extra_config},
         * this value is used to build {@code ...?WorkflowID=...} for local demos.
         */
        private String embeddedSigningBaseUrl = "";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 60000;
    }

    @Data
    public static class NotificationServiceProperties {
        /**
         * Base URL for notification-service (no trailing slash), e.g. {@code http://notification-service:8084}.
         * Used read-only by los-core admin UIs proxied via this service.
         */
        private String baseUrl = "http://localhost:8084";
    }

    @Data
    public static class HypervergeProperties {
        private String generateLinkUrl = "https://ind.idv.hyperverge.co/v1/link-kyc/start";
        private String generateLinkApiUrl = "https://ind.idv.hyperverge.co/v1/link-kyc/start";
        private String resultApiUrl = "https://ind.idv.hyperverge.co/v1/link-kyc/results";
        private String appId = "zje07z";
        private String appKey = "efvnk7kpewjtroma151y";
        private String workflowId = "billion_loans_vkyc";
        private int connectTimeoutMs = 10000;
        private int readTimeoutMs = 30000;
    }

    @Data
    public static class AiLosProperties {
        private String ingestUrl = "https://ai-los.billiontech.ai/api/los/ingest";
        private String uiBaseUrl = "https://ai-los.billiontech.ai";
        private String jwtSecret = "dev_secret";
        private String jwtSubject = "java-los";
        private String jwtRole = "integration";
        private String sourceLos = "java-los";
        private String reviewPath = "";
        private String whatIfPath = "/what-if";
        private int connectTimeoutMs = 8000;
        private int readTimeoutMs = 15000;
    }
}
