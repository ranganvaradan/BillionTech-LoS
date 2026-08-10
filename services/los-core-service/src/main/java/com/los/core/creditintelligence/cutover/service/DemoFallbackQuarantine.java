package com.los.core.creditintelligence.cutover.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Assistive/cutover paths must not use DEMO_AI_LOS_URL.
 * Legacy AiLosIntegrationService deep-link remains untouched for now.
 */
@Component
public class DemoFallbackQuarantine {

    public static final String DEMO_AI_LOS_URL =
            "https://demo.ai-los.local/borrower/TEST001";

    /**
     * Documented known demo markers that must not appear as real underwriting success redirects.
     */
    public static final String[] DEMO_MARKERS = {
            "DEMO_AI_LOS_URL",
            "TEST001",
            "DEMO_FALLBACK",
            "demo.ai-los"
    };

    public void assertNoDemoRedirect(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String lower = url.toLowerCase();
        for (String marker : DEMO_MARKERS) {
            if (lower.contains(marker.toLowerCase())
                    || url.contains(DEMO_AI_LOS_URL)
                    || lower.contains("demo.ai-los")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cutover/assistive path must not redirect to DEMO_AI_LOS_URL or demo borrower");
            }
        }
    }

    public boolean isDemoUrl(String url) {
        if (url == null) return false;
        try {
            assertNoDemoRedirect(url);
            return false;
        } catch (ResponseStatusException e) {
            return true;
        }
    }
}
