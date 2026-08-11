package com.los.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.UUID;

/**
 * LOS-PRODUCTION-P0-CLOSURE-1 — fail-closed single-tenant deployment isolation.
 * <p>
 * Product model is one customer per deployment (LOS core tables have no tenant_id).
 * Production must bind to exactly one configured deployment tenant and reject foreign tenants.
 */
@Component
public class SingleTenantDeploymentGuard {

    public static final String MODE_SINGLE = "SINGLE_TENANT_DEPLOYMENT";
    public static final String MODE_OFF = "OFF";
    public static final UUID DEFAULT_DEPLOYMENT_TENANT =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final String mode;
    private final UUID deploymentTenantId;

    public SingleTenantDeploymentGuard(
            @Value("${los.security.tenancy.mode:OFF}") String mode,
            @Value("${los.security.tenancy.deployment-tenant-id:00000000-0000-0000-0000-000000000001}")
                    String deploymentTenantId) {
        this.mode = mode == null ? MODE_OFF : mode.trim().toUpperCase(Locale.ROOT);
        UUID parsed;
        try {
            parsed = UUID.fromString(deploymentTenantId == null ? "" : deploymentTenantId.trim());
        } catch (Exception e) {
            parsed = DEFAULT_DEPLOYMENT_TENANT;
        }
        this.deploymentTenantId = parsed;
    }

    public boolean isSingleTenantFailClosed() {
        return MODE_SINGLE.equals(mode);
    }

    public String mode() {
        return mode;
    }

    public UUID deploymentTenantId() {
        return deploymentTenantId;
    }

    /** Returns deployment tenant; rejects foreign explicit tenants when fail-closed. */
    public UUID bindOrReject(UUID explicitOrNull) {
        if (!isSingleTenantFailClosed()) {
            return explicitOrNull != null ? explicitOrNull : deploymentTenantId;
        }
        if (explicitOrNull == null) {
            return deploymentTenantId;
        }
        if (!deploymentTenantId.equals(explicitOrNull)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "TENANT_FORBIDDEN: foreign tenant rejected (single-tenant deployment)");
        }
        return deploymentTenantId;
    }

    public UUID bindOrRejectHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return bindOrReject(null);
        }
        try {
            return bindOrReject(UUID.fromString(headerValue.trim()));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (isSingleTenantFailClosed()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_FORBIDDEN: invalid tenant id");
            }
            return deploymentTenantId;
        }
    }

    public void assertJwtTenantAllowed(String tenantClaim) {
        if (!isSingleTenantFailClosed()) {
            return;
        }
        if (tenantClaim == null || tenantClaim.isBlank()) {
            return; // will be stamped to deployment tenant
        }
        try {
            UUID t = UUID.fromString(tenantClaim.trim());
            if (!deploymentTenantId.equals(t)) {
                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "TENANT_FORBIDDEN: JWT tenant does not match deployment tenant");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "TENANT_FORBIDDEN: invalid JWT tenant claim");
        }
    }
}
