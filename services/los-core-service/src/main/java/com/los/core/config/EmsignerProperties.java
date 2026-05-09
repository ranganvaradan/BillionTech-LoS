package com.los.core.config;

import lombok.Data;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "los.esign.emsigner")
public class EmsignerProperties {
    private boolean enabled;
    private String baseUrl;
    private String appName;
    private String secretKey;
    private String accessTokenUrl;
    private String initiateApiUrl;
    private String listTemplatesApiUrl;
    private String downloadApiUrl;
    private String authType;
    private boolean fallbackToDb = false;

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(baseUrl)) missing.add("baseUrl");
        if (isBlank(appName)) missing.add("appName");
        if (isBlank(secretKey)) missing.add("secretKey");
        if (isBlank(initiateApiUrl)) missing.add("initiateApiUrl");
        if (!missing.isEmpty()) {
            throw new RuntimeException("EMSIGNER PROPERTIES NOT LOADED: missing " + missing);
        }
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}

