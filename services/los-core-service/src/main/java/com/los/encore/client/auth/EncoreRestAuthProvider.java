package com.los.encore.client.auth;

import com.los.encore.client.config.EncoreClientProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code X-Auth-Token} for Encore REST paths ({@code api/loan-od-accounts}, etc.).
 * Webservices continue to use HTTP Basic auth.
 */
@Slf4j
@Component
public class EncoreRestAuthProvider {

    private final EncoreClientProperties properties;

    public EncoreRestAuthProvider(EncoreClientProperties properties) {
        this.properties = properties;
    }

    public boolean isConfigured() {
        String token = properties.getRestAuthToken();
        return token != null && !token.isBlank();
    }

    public String resolveToken() {
        String token = properties.getRestAuthToken();
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim();
    }
}
