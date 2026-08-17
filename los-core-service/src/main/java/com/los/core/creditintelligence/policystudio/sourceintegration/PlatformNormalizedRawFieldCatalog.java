package com.los.core.creditintelligence.policystudio.sourceintegration;

import java.util.Set;

/**
 * Platform fields known to be produced by real connector normalization paths.
 * Not inferred from GACAT catalogue membership alone.
 *
 * <p>Evidence: EquifaxBureauAccountExtractor + BureauNormalizationService persist
 * tradelines / payment history / inquiries / provider summary / scoring elements
 * with these semantic fields.
 */
public final class PlatformNormalizedRawFieldCatalog {

    private PlatformNormalizedRawFieldCatalog() {}

    /**
     * Exact canonical IDs for which Equifax retail normalization has a durable field path.
     * Structural readiness may be READY while a given applicant still has valueAvailable=false.
     */
    public static final Set<String> EQUIFAX_RETAIL_NORMALIZED_FIELDS = EquifaxRetailRawIds.ALL;

    public static boolean isStructurallyMapped(String canonicalParameterId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return false;
        }
        return EQUIFAX_RETAIL_NORMALIZED_FIELDS.contains(canonicalParameterId.trim());
    }

    /** Merge into RawFactProducer registration — CPES capability tracks structural mapping. */
    public static Set<String> mergeIntoRawFactIds(Set<String> existing) {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        if (existing != null) out.addAll(existing);
        out.addAll(EQUIFAX_RETAIL_NORMALIZED_FIELDS);
        return Set.copyOf(out);
    }
}
