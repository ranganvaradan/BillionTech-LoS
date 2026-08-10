package com.los.core.creditintelligence.core.tenant;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves tenant id for Credit Intelligence evaluation.
 * <p>
 * Fails with {@link IllegalStateException} when tenant is unknown/null and either
 * {@code tenant.requireExplicit=true} or ({@code evaluationContext.enabled} and not {@code tenant.devMode}).
 * Default UUID is only used when {@code tenant.devMode=true} (local/tests).
 */
@Service
@RequiredArgsConstructor
public class TenantResolver {

    private final CreditIntelligenceProperties properties;

    public UUID resolve(UUID applicationTenantId) {
        return resolveOrDefault(applicationTenantId);
    }

    public UUID resolveFromHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return resolveOrDefault(null);
        }
        try {
            return resolveOrDefault(UUID.fromString(headerValue.trim()));
        } catch (IllegalArgumentException e) {
            if (mustRequireExplicit()) {
                throw new IllegalStateException("Invalid tenant header value: " + headerValue, e);
            }
            if (properties.getTenant().isDevMode()) {
                return properties.getDefaultTenantId();
            }
            throw new IllegalStateException("Invalid tenant header value: " + headerValue, e);
        }
    }

    public UUID resolveOrDefault(UUID explicit) {
        if (explicit != null) {
            return explicit;
        }
        if (mustRequireExplicit()) {
            throw new IllegalStateException(
                    "Tenant id is required (tenant.requireExplicit or evaluationContext.enabled outside devMode)");
        }
        if (properties.getTenant().isDevMode()) {
            return properties.getDefaultTenantId();
        }
        throw new IllegalStateException("Tenant id is required when tenant.devMode=false");
    }

    private boolean mustRequireExplicit() {
        boolean requireExplicit = properties.getTenant().isRequireExplicit();
        boolean evalCtxStrict = properties.getEvaluationContext().isEnabled()
                && !properties.getTenant().isDevMode();
        return requireExplicit || evalCtxStrict;
    }
}
