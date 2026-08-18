package com.los.core.creditintelligence.policystudio.runtime.canonicalconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Explicit typed result: RESOLVED package or NOT_RESOLVABLE with reason codes.
 * Never swallows failure into a different authority.
 */
public record CanonicalApplicationConfigurationResolution(
        CanonicalResolutionStatus status,
        CanonicalApplicationConfiguration configuration,
        List<String> reasonCodes,
        Map<String, String> outcomes
) {
    public CanonicalApplicationConfigurationResolution {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
    }

    public boolean resolved() {
        return status == CanonicalResolutionStatus.RESOLVED;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status == null ? null : status.name());
        m.put("reasonCodes", new ArrayList<>(reasonCodes));
        m.put("outcomes", new LinkedHashMap<>(outcomes));
        m.put("configuration", configuration == null ? null : configuration.toMap());
        m.put("liveDecisionAuthorityUnchanged", true);
        m.put("canonicalRuntimeUsedForLiveDecision", false);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static CanonicalApplicationConfigurationResolution fromStored(
            String status,
            Map<String, Object> packageJson,
            List<String> reasonCodes,
            Map<String, String> outcomes) {
        CanonicalApplicationConfiguration cfg = packageJson == null
                ? null : CanonicalApplicationConfiguration.fromMap(packageJson);
        CanonicalResolutionStatus st;
        try {
            st = CanonicalResolutionStatus.valueOf(status);
        } catch (Exception e) {
            st = CanonicalResolutionStatus.NOT_RESOLVABLE;
        }
        return new CanonicalApplicationConfigurationResolution(st, cfg, reasonCodes, outcomes);
    }
}
