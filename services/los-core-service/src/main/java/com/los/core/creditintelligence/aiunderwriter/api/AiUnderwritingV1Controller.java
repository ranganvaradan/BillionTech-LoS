package com.los.core.creditintelligence.aiunderwriter.api;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned alias for assistive AI underwriting analyses.
 * Delegates to the same internal controller mappings via composition path:
 * POST /api/v1/ai-underwriting/analyses → handled here by extending mapping.
 */
@RestController
@RequestMapping("/api/v1/ai-underwriting")
public class AiUnderwritingV1Controller {

    private final AiUnderwritingAdminController delegate;

    public AiUnderwritingV1Controller(AiUnderwritingAdminController delegate) {
        this.delegate = delegate;
    }

    @org.springframework.web.bind.annotation.PostMapping("/analyses")
    public java.util.Map<String, Object> createAnalysis(
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Internal-Token", required = false) String token,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, Object> body) {
        java.util.UUID applicationId = body.get("applicationId") == null
                ? java.util.UUID.randomUUID()
                : java.util.UUID.fromString(String.valueOf(body.get("applicationId")));
        return delegate.analyzeForApplication(applicationId, token, tenantHeader, body);
    }

    @org.springframework.web.bind.annotation.GetMapping("/analyses/{id}")
    public java.util.Map<String, Object> getAnalysis(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID id,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Internal-Token", required = false) String token,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        return delegate.getAnalysis(id, token, tenantHeader);
    }
}
