package com.los.core.creditintelligence.decisionpolicy.kyc.shadow;

import com.los.core.creditintelligence.decisionpolicy.kyc.KycBusinessOutcome;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Classifies production computeKycOutcome vs Decision Policy shadow KYC outcome.
 * Does not invent business meaning beyond structural comparison.
 */
public final class KycShadowComparisonClassifier {

    public static final String MATCH = "MATCH";
    public static final String PRODUCTION_FAIL_SHADOW_MISSING_INFORMATION = "PRODUCTION_FAIL_SHADOW_MISSING_INFORMATION";
    public static final String PRODUCTION_FAIL_SHADOW_REFER = "PRODUCTION_FAIL_SHADOW_REFER";
    public static final String PRODUCTION_PASS_SHADOW_REFER = "PRODUCTION_PASS_SHADOW_REFER";
    public static final String PRODUCTION_PASS_SHADOW_FAIL = "PRODUCTION_PASS_SHADOW_FAIL";
    public static final String PRODUCTION_PASS_SHADOW_MISSING_INFORMATION = "PRODUCTION_PASS_SHADOW_MISSING_INFORMATION";
    public static final String SHADOW_MORE_STRICT = "SHADOW_MORE_STRICT";
    public static final String SHADOW_MORE_PERMISSIVE = "SHADOW_MORE_PERMISSIVE";
    public static final String PROVIDER_FAILURE_SEMANTIC_DIFFERENCE = "PROVIDER_FAILURE_SEMANTIC_DIFFERENCE";
    public static final String POLICY_DIFFERENCE = "POLICY_DIFFERENCE";
    public static final String MAPPING_DEFECT = "MAPPING_DEFECT";
    public static final String DATA_GAP = "DATA_GAP";
    public static final String UNKNOWN = "UNKNOWN";

    private KycShadowComparisonClassifier() {}

    public record Comparison(
            String classification,
            boolean reviewRequired,
            String reviewReason,
            String rootCauseCategory,
            Map<String, Object> detail
    ) {}

    public static Comparison compare(
            String productionOutcome,
            KycBusinessOutcome shadowOutcome,
            boolean providerTechnicalUnavailable
    ) {
        KycBusinessOutcome prod = normalizeProduction(productionOutcome);
        KycBusinessOutcome shadow = shadowOutcome == null
                ? KycBusinessOutcome.MISSING_INFORMATION
                : shadowOutcome;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("production", prod.name());
        detail.put("shadow", shadow.name());
        detail.put("providerTechnicalUnavailable", providerTechnicalUnavailable);

        if (prod == shadow
                || (prod == KycBusinessOutcome.MISSING_INFORMATION
                && shadow == KycBusinessOutcome.MISSING_INFORMATION)) {
            return new Comparison(MATCH, false, null, null, detail);
        }

        // Key KYC-5 insight: production FAIL on provider outage vs shadow MISSING/REFER
        if (providerTechnicalUnavailable
                && prod == KycBusinessOutcome.FAIL
                && (shadow == KycBusinessOutcome.MISSING_INFORMATION
                || shadow == KycBusinessOutcome.REFER)) {
            return new Comparison(
                    PROVIDER_FAILURE_SEMANTIC_DIFFERENCE,
                    false,
                    null,
                    "provider_semantics",
                    detail);
        }

        if (prod == KycBusinessOutcome.FAIL && shadow == KycBusinessOutcome.MISSING_INFORMATION) {
            return new Comparison(PRODUCTION_FAIL_SHADOW_MISSING_INFORMATION, false, null,
                    providerTechnicalUnavailable ? "provider_semantics" : "policy_difference", detail);
        }
        if (prod == KycBusinessOutcome.FAIL && shadow == KycBusinessOutcome.REFER) {
            return new Comparison(PRODUCTION_FAIL_SHADOW_REFER, false, null, "policy_difference", detail);
        }
        if (prod == KycBusinessOutcome.PASS && shadow == KycBusinessOutcome.REFER) {
            return new Comparison(PRODUCTION_PASS_SHADOW_REFER, false, null, "policy_difference", detail);
        }
        if (prod == KycBusinessOutcome.PASS && shadow == KycBusinessOutcome.FAIL) {
            return new Comparison(PRODUCTION_PASS_SHADOW_FAIL, false, null, "policy_difference", detail);
        }
        if (prod == KycBusinessOutcome.PASS && shadow == KycBusinessOutcome.MISSING_INFORMATION) {
            return new Comparison(PRODUCTION_PASS_SHADOW_MISSING_INFORMATION, false, null, "data_gap", detail);
        }

        // Shadow more permissive: production blocks/fails but shadow PASS — HIGH RISK
        if (shadow == KycBusinessOutcome.PASS
                && (prod == KycBusinessOutcome.FAIL || prod == KycBusinessOutcome.REFER
                || prod == KycBusinessOutcome.MISSING_INFORMATION)) {
            return new Comparison(
                    SHADOW_MORE_PERMISSIVE,
                    true,
                    "REVIEW_REQUIRED",
                    "unknown",
                    detail);
        }

        // Shadow more strict: production PASS, shadow not PASS (covered above) or production MI shadow FAIL
        if (prod == KycBusinessOutcome.PASS && shadow != KycBusinessOutcome.PASS) {
            return new Comparison(SHADOW_MORE_STRICT, false, null, "policy_difference", detail);
        }
        if (rank(shadow) > rank(prod)) {
            return new Comparison(SHADOW_MORE_STRICT, false, null, "policy_difference", detail);
        }
        if (rank(shadow) < rank(prod)) {
            return new Comparison(
                    SHADOW_MORE_PERMISSIVE,
                    shadow == KycBusinessOutcome.PASS,
                    shadow == KycBusinessOutcome.PASS ? "REVIEW_REQUIRED" : null,
                    "unknown",
                    detail);
        }

        return new Comparison(UNKNOWN, false, null, "unknown", detail);
    }

    private static int rank(KycBusinessOutcome o) {
        return switch (o) {
            case PASS -> 0;
            case MISSING_INFORMATION -> 1;
            case REFER -> 2;
            case FAIL -> 3;
        };
    }

    public static KycBusinessOutcome normalizeProduction(String production) {
        if (production == null || production.isBlank()) {
            return KycBusinessOutcome.MISSING_INFORMATION;
        }
        String p = production.trim().toUpperCase(Locale.ROOT);
        // Production vocabulary: PASS / FAIL / INCOMPLETE
        return switch (p) {
            case "PASS" -> KycBusinessOutcome.PASS;
            case "FAIL", "FAILED", "KYC_FAILED" -> KycBusinessOutcome.FAIL;
            case "INCOMPLETE", "PENDING" -> KycBusinessOutcome.MISSING_INFORMATION;
            case "REFER" -> KycBusinessOutcome.REFER;
            default -> KycBusinessOutcome.fromLegacyAggregate(p);
        };
    }
}
