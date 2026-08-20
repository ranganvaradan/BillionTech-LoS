package com.los.core.creditintelligence.policystudio.parameters.manualoverride;

import java.util.Set;

/**
 * Allow-list of canonical GACAT parameters that may ever be manually overridden on an
 * application, plus the shared "value required" validation. Deliberately narrow — a manual
 * override is a scoped exception for one confirmed-unavailable source, never a blanket bypass.
 * Extend this set only for a specific, confirmed-unintegrated source, not speculatively.
 */
public final class ManualParameterOverridePolicy {

    public static final Set<String> OVERRIDABLE_PARAMETER_IDS = Set.of(
            "bureau.recent_inquiries_90d"
    );

    private ManualParameterOverridePolicy() {}

    public static boolean isOverridable(String canonicalParameterId) {
        return canonicalParameterId != null && OVERRIDABLE_PARAMETER_IDS.contains(canonicalParameterId.trim());
    }
}
