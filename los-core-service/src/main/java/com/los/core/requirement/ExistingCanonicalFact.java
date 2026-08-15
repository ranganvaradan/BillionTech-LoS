package com.los.core.requirement;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only view of an existing canonical fact acceptable for Policy planning.
 */
public record ExistingCanonicalFact(
        String canonicalParameterId,
        boolean readyForPolicy,
        String qualityStatus,
        String classification,
        String sourceRef,
        Map<String, Object> provenance
) {
    public ExistingCanonicalFact {
        if (provenance == null) {
            provenance = Map.of();
        } else {
            provenance = Map.copyOf(new LinkedHashMap<>(provenance));
        }
    }

    public static ExistingCanonicalFact ready(String id, Map<String, Object> provenance) {
        return new ExistingCanonicalFact(id, true, "OK", "VERIFIED", null, provenance);
    }
}
