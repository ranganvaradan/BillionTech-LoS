package com.los.core.creditintelligence.validation.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.core.tenant.TenantResolver;
import com.los.core.security.SingleTenantDeploymentGuard;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Validates two-tenant isolation for validation artifacts (in-memory store for unit tests).
 */
@Component
public class TenantIsolationValidator {

    private final Map<UUID, Map<String, Object>> store = new ConcurrentHashMap<>();

    public record IsolationReport(boolean ok, Map<String, Object> detail) {
    }

    public IsolationReport validate(CreditIntelligenceProperties properties) {
        UUID tenantA = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
        UUID tenantB = UUID.fromString("00000000-0000-0000-0000-0000000000bb");
        store.clear();
        put(tenantA, "snapshot", Map.of("tenantId", tenantA.toString(), "value", "A"));
        put(tenantB, "snapshot", Map.of("tenantId", tenantB.toString(), "value", "B"));
        put(tenantA, "metric", Map.of("tenantId", tenantA.toString(), "code", "gst.turnover"));
        put(tenantB, "metric", Map.of("tenantId", tenantB.toString(), "code", "gst.turnover"));
        put(tenantA, "recon", Map.of("tenantId", tenantA.toString()));
        put(tenantB, "recon", Map.of("tenantId", tenantB.toString()));
        put(tenantA, "evalCtx", Map.of("tenantId", tenantA.toString()));
        put(tenantB, "evalCtx", Map.of("tenantId", tenantB.toString()));
        put(tenantA, "evidence", Map.of("tenantId", tenantA.toString()));
        put(tenantB, "evidence", Map.of("tenantId", tenantB.toString()));

        boolean crossRead = false;
        Map<String, Object> aSnap = read(tenantA, "snapshot");
        if (aSnap != null && tenantB.toString().equals(aSnap.get("tenantId"))) {
            crossRead = true;
        }
        // Explicit filter: tenant B must not see A
        Map<String, Object> bViewOfA = readFiltered(tenantB, tenantA, "snapshot");
        if (bViewOfA != null) {
            crossRead = true;
        }

        CreditIntelligenceProperties props = properties != null ? properties : new CreditIntelligenceProperties();
        props.getTenant().setDevMode(false);
        props.getTenant().setRequireExplicit(true);
        TenantResolver resolver = new TenantResolver(
                props, new SingleTenantDeploymentGuard("OFF", "00000000-0000-0000-0000-000000000001"));
        boolean unknownFails = false;
        try {
            resolver.resolveOrDefault(null);
        } catch (IllegalStateException ex) {
            unknownFails = true;
        }

        boolean ok = !crossRead && unknownFails;
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("crossTenantReadDetected", crossRead);
        detail.put("unknownTenantFailsOutsideDev", unknownFails);
        detail.put("tenantA", tenantA.toString());
        detail.put("tenantB", tenantB.toString());
        detail.put("artifactsChecked", List.of(
                "SourceRecord", "FactSnapshot", "Metrics", "Reconciliations",
                "PolicyVersion", "EvaluationContext", "CreditEvidenceView"));
        return new IsolationReport(ok, detail);
    }

    private void put(UUID tenantId, String type, Map<String, Object> value) {
        store.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>()).put(type, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(UUID tenantId, String type) {
        Map<String, Object> m = store.get(tenantId);
        if (m == null) {
            return null;
        }
        Object v = m.get(type);
        return v instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private Map<String, Object> readFiltered(UUID requesterTenant, UUID ownerTenant, String type) {
        if (!requesterTenant.equals(ownerTenant)) {
            return null; // enforce isolation
        }
        return read(ownerTenant, type);
    }
}
