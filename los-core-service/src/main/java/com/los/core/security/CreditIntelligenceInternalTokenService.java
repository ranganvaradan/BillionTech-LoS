package com.los.core.security;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * LOS-LIVE-P0-CLOSURE-1 — CI/internal token enforcement.
 * Production sets {@code credit-intelligence.internal-token-required=true} and a non-blank token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditIntelligenceInternalTokenService {

    private final CreditIntelligenceProperties properties;

    public void assertToken(String presented) {
        String configured = properties.getInternalToken() == null ? "" : properties.getInternalToken().trim();
        boolean required = properties.isInternalTokenRequired();

        if (configured.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Internal token not configured");
            }
            // Staging/local soft-open only when not required
            log.warn("credit-intelligence.internal-token blank — allowing internal API (token not required)");
            return;
        }
        if (presented == null || presented.isBlank() || !configured.equals(presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid or missing X-Internal-Token");
        }
    }

    public void assertConfiguredWhenRequired() {
        if (!properties.isInternalTokenRequired()) {
            return;
        }
        String configured = properties.getInternalToken() == null ? "" : properties.getInternalToken().trim();
        if (configured.isBlank()) {
            throw new IllegalStateException(
                    "CREDIT_INTELLIGENCE_INTERNAL_TOKEN is required when credit-intelligence.internal-token-required=true");
        }
    }
}
