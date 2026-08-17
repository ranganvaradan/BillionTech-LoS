package com.los.core.creditintelligence.policystudio.sourceintegration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * GOLDEN-PARAMETER-TRUTH — sole Source Integration authority.
 *
 * <p>Distinguishes platform connector existence from lender configuration.
 * Does NOT invent integration from GACAT catalogue parameter counts.
 * Does NOT change when an applicant has DATA_NOT_AVAILABLE.
 * Does NOT change when W6 marks SOURCE_ACQUIRED.
 */
public final class CanonicalSourceIntegrationAuthority {

    public static final String AUTHORITY = "CanonicalSourceIntegrationAuthority";

    public static final String PLATFORM_INTEGRATED = "PLATFORM_INTEGRATED";
    public static final String PLATFORM_NOT_INTEGRATED = "NOT_INTEGRATED";
    public static final String PLATFORM_NOT_APPLICABLE = "NOT_APPLICABLE";

    public static final String LENDER_CONFIGURED = "LENDER_CONFIGURED";
    public static final String LENDER_NOT_CONFIGURED = "NOT_CONFIGURED";
    public static final String LENDER_NOT_APPLICABLE = "NOT_APPLICABLE";

    /** Optional lender configuration probe — Equifax YAML / aggregator_configs, etc. */
    @FunctionalInterface
    public interface LenderConfigurationProbe {
        boolean isConfigured(PlatformSourceConnectorCatalog.SourceKey key, String familyLabel);
    }

    private static final AtomicReference<LenderConfigurationProbe> LENDER_PROBE =
            new AtomicReference<>();

    private CanonicalSourceIntegrationAuthority() {}

    public static void installLenderProbe(LenderConfigurationProbe probe) {
        LENDER_PROBE.set(probe);
    }

    public static void clearLenderProbe() {
        LENDER_PROBE.set(null);
    }

    public static Map<String, Object> forFamily(String family, String sourceType) {
        PlatformSourceConnectorCatalog.SourceKey key =
                PlatformSourceConnectorCatalog.resolveKey(family, sourceType);
        return forKey(key, family);
    }

    public static Map<String, Object> forKey(PlatformSourceConnectorCatalog.SourceKey key, String familyLabel) {
        PlatformSourceConnectorCatalog.PlatformEntry entry = PlatformSourceConnectorCatalog.entry(key);
        boolean platformIntegrated = entry.platformIntegrated();
        boolean notApplicable = entry.notApplicable();

        String platformStatus;
        if (notApplicable) {
            platformStatus = PLATFORM_NOT_APPLICABLE;
        } else if (platformIntegrated) {
            platformStatus = PLATFORM_INTEGRATED;
        } else {
            platformStatus = PLATFORM_NOT_INTEGRATED;
        }

        boolean lenderConfigured;
        String lenderStatus;
        if (notApplicable) {
            lenderConfigured = true;
            lenderStatus = LENDER_NOT_APPLICABLE;
        } else if (!platformIntegrated) {
            lenderConfigured = false;
            lenderStatus = LENDER_NOT_CONFIGURED;
        } else {
            LenderConfigurationProbe probe = LENDER_PROBE.get();
            if (probe != null) {
                lenderConfigured = probe.isConfigured(key, familyLabel);
            } else {
                // Platform/admin projection without tenant probe: connector present ⇒ treat configured
                // for structural readiness (lender-specific probe overrides when installed).
                lenderConfigured = true;
            }
            lenderStatus = lenderConfigured ? LENDER_CONFIGURED : LENDER_NOT_CONFIGURED;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authority", AUTHORITY);
        out.put("sourceKey", key.name());
        out.put("displayName", entry.displayName());
        out.put("familyLabel", familyLabel);
        out.put("platformIntegrated", platformIntegrated && !notApplicable);
        out.put("platformStatus", platformStatus);
        out.put("lenderConfigured", lenderConfigured);
        out.put("lenderStatus", lenderStatus);
        out.put("notApplicable", notApplicable);
        out.put("evidence", List.copyOf(entry.evidence()));
        out.put("catalogueExistenceIsNotIntegration", true);
        out.put("applicantDataDoesNotChangeIntegration", true);
        out.put("w6AcquisitionDoesNotChangeIntegration", true);
        return out;
    }

    public static Map<String, Object> forParameter(
            String evaluatedFrom,
            String sourceFamily,
            String sourceType) {
        String family = firstNonBlank(sourceFamily, evaluatedFrom);
        return forFamily(family, sourceType);
    }

    /** Summaries for all known connector keys (not GACAT families). */
    public static List<Map<String, Object>> allConnectorStates() {
        return java.util.Arrays.stream(PlatformSourceConnectorCatalog.SourceKey.values())
                .filter(k -> k != PlatformSourceConnectorCatalog.SourceKey.UNKNOWN)
                .map(k -> forKey(k, k.name()))
                .toList();
    }

    public static boolean isStructurallyAvailable(Map<String, Object> sourceState) {
        if (sourceState == null) return false;
        if (Boolean.TRUE.equals(sourceState.get("notApplicable"))) return true;
        return Boolean.TRUE.equals(sourceState.get("platformIntegrated"))
                && Boolean.TRUE.equals(sourceState.get("lenderConfigured"));
    }

    public static String structuralBlockReason(Map<String, Object> sourceState) {
        if (sourceState == null) return BusinessReadinessBridge.SOURCE_NOT_INTEGRATED;
        if (Boolean.TRUE.equals(sourceState.get("notApplicable"))) return null;
        if (!Boolean.TRUE.equals(sourceState.get("platformIntegrated"))) {
            return BusinessReadinessBridge.SOURCE_NOT_INTEGRATED;
        }
        if (!Boolean.TRUE.equals(sourceState.get("lenderConfigured"))) {
            return BusinessReadinessBridge.SOURCE_NOT_CONFIGURED;
        }
        return null;
    }

    /** Tiny bridge so source package does not depend on truth package enums at class-init. */
    static final class BusinessReadinessBridge {
        static final String SOURCE_NOT_INTEGRATED = "SOURCE_NOT_INTEGRATED";
        static final String SOURCE_NOT_CONFIGURED = "SOURCE_NOT_CONFIGURED";
        private BusinessReadinessBridge() {}
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return "";
    }

    /** Helper for Spring wiring — adapt family-string probe. */
    public static LenderConfigurationProbe fromFamilyPredicate(Function<String, Boolean> familyConfigured) {
        Objects.requireNonNull(familyConfigured);
        return (key, familyLabel) -> Boolean.TRUE.equals(familyConfigured.apply(
                familyLabel != null && !familyLabel.isBlank() ? familyLabel : key.name()));
    }
}
