package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of intercepting a legacy fallback for an enabled cutover cohort.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarantineResult {
    /** When true, caller may apply historical CreditControl gap default (outside cohort / flag off). */
    private boolean allowLegacy;
    private String disposition;
    private Object canonicalValue;
    private String legacyKey;
    private String message;
    private String missingDataBehavior;

    public static QuarantineResult allowLegacy(String legacyKey) {
        return QuarantineResult.builder()
                .allowLegacy(true)
                .disposition("LEGACY_UNCHANGED")
                .legacyKey(legacyKey)
                .message("Outside cutover cohort or quarantine disabled — legacy default permitted")
                .build();
    }

    public static QuarantineResult canonical(String legacyKey, Object value) {
        return QuarantineResult.builder()
                .allowLegacy(false)
                .disposition("CANONICAL_REPLACEMENT")
                .legacyKey(legacyKey)
                .canonicalValue(value)
                .message("Canonical replacement available")
                .build();
    }

    public static QuarantineResult insufficient(String legacyKey, String behavior) {
        return QuarantineResult.builder()
                .allowLegacy(false)
                .disposition(behavior != null ? behavior : "DATA_INSUFFICIENT")
                .legacyKey(legacyKey)
                .missingDataBehavior(behavior != null ? behavior : "DATA_INSUFFICIENT")
                .message("Canonical replacement missing — do not apply silent numeric default")
                .build();
    }

    public static QuarantineResult approvedPolicyDefault(String legacyKey, Object value) {
        return QuarantineResult.builder()
                .allowLegacy(true)
                .disposition("APPROVED_POLICY_DEFAULT")
                .legacyKey(legacyKey)
                .canonicalValue(value)
                .message("Auditable business policy default permitted")
                .build();
    }
}
