package com.los.core.creditintelligence.core.tenant;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.security.SingleTenantDeploymentGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Resolves tenant id for Credit Intelligence evaluation.
 * <p>
 * Under {@code SINGLE_TENANT_DEPLOYMENT} fail-closed mode, foreign tenants are rejected and
 * blank/null resolves to the configured deployment tenant (not caller-supplied).
 */
@Service
@RequiredArgsConstructor
public class TenantResolver {

    private final CreditIntelligenceProperties properties;
    private final SingleTenantDeploymentGuard singleTenantDeploymentGuard;

    public UUID resolve(UUID applicationTenantId) {
        return resolveOrDefault(applicationTenantId);
    }

    public UUID resolveFromHeader(String headerValue) {
        if (singleTenantDeploymentGuard.isSingleTenantFailClosed()) {
            return singleTenantDeploymentGuard.bindOrRejectHeader(headerValue);
        }
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
        if (singleTenantDeploymentGuard.isSingleTenantFailClosed()) {
            return singleTenantDeploymentGuard.bindOrReject(explicit);
        }
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
