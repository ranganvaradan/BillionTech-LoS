package com.los.core.creditintelligence.policystudio.lifecycle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business-facing policy lifecycle labels for a single-NBFC deployment.
 * Maps onto existing DocumentStatus / DraftPackageStatus / ExecutablePackageStatus / ReviewState
 * without renaming backend enums.
 */
public final class PolicyBusinessLifecycleStatus {

    public static final String DRAFT = "DRAFT";
    public static final String IN_REVIEW = "IN REVIEW";
    public static final String APPROVED = "APPROVED";
    public static final String SCHEDULED = "SCHEDULED";
    public static final String ACTIVE = "ACTIVE";
    public static final String SUPERSEDED = "SUPERSEDED";
    public static final String RETIRED = "RETIRED";

    private PolicyBusinessLifecycleStatus() {}

    public static List<String> businessStates() {
        return List.of(DRAFT, IN_REVIEW, APPROVED, SCHEDULED, ACTIVE, SUPERSEDED, RETIRED);
    }

    /** Canonical mapping for UI / reports. */
    public static Map<String, Object> statusMapping() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessStates", businessStates());
        m.put("documentStatus", Map.of(
                "UPLOADED/PARSED/REVIEW_REQUIRED", DRAFT,
                "DRAFT_READY", DRAFT,
                "APPROVED_FOR_POLICY_BUILD", APPROVED,
                "SUPERSEDED", SUPERSEDED,
                "REJECTED", DRAFT));
        m.put("draftPackageStatus", Map.of(
                "DRAFT_ONLY", DRAFT + " (package never production-active)"));
        m.put("executablePackageStatus", Map.of(
                "DRAFT/REVIEW", DRAFT + " / " + IN_REVIEW,
                "APPROVED", APPROVED,
                "SHADOW", "Shadow publish (not production authority)",
                "ACTIVE", ACTIVE + " (engine status — production authority still gated)",
                "SUPERSEDED", SUPERSEDED,
                "RETIRED", RETIRED));
        m.put("reviewState", Map.of(
                "AI_DRAFTED…TEST_REVIEW", IN_REVIEW,
                "CREDIT_MANAGER_APPROVED", IN_REVIEW,
                "CHECKER_APPROVED / READY_FOR_POLICY_BUILD", APPROVED));
        m.put("note", "Business ACTIVE ≠ Credit Intelligence production authority. "
                + "allowCanonicalAuthority remains false until separately enabled.");
        return m;
    }

    public static String fromStored(String stored) {
        if (stored == null || stored.isBlank()) {
            return DRAFT;
        }
        String s = stored.trim().toUpperCase().replace(' ', '_');
        return switch (s) {
            case "DRAFT", "DRAFT_ONLY", "UPLOADED", "PARSED", "PARSING", "INTERPRETING",
                    "REVIEW_REQUIRED", "DRAFT_READY", "REJECTED", "AI_DRAFTED" -> DRAFT;
            case "IN_REVIEW", "IN REVIEW", "MAPPING_REVIEW", "METRIC_REVIEW", "RULE_REVIEW",
                    "TEST_REVIEW", "CREDIT_MANAGER_APPROVED", "REVIEW" -> IN_REVIEW;
            case "APPROVED", "APPROVED_FOR_POLICY_BUILD", "CHECKER_APPROVED",
                    "READY_FOR_POLICY_BUILD", "PUBLISHED" -> APPROVED;
            case "SCHEDULED" -> SCHEDULED;
            case "ACTIVE", "SHADOW" -> ACTIVE; // SHADOW may carry business ACTIVE for demo; authority separate
            case "SUPERSEDED" -> SUPERSEDED;
            case "RETIRED" -> RETIRED;
            default -> stored;
        };
    }
}
