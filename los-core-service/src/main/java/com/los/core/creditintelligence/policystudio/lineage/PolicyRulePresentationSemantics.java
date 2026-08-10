package com.los.core.creditintelligence.policystudio.lineage;

import com.los.core.creditintelligence.policystudio.catalogue.IngestionMatchClassification;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GACAT-POLICY-LINEAGE-FIX-1 — business-facing rule status + pass/fail presentation.
 * Does not change engine DSL; normalizes Credit Manager card fields only.
 */
public final class PolicyRulePresentationSemantics {

    private PolicyRulePresentationSemantics() {}

    /** CM-facing review status. IGNORED only when disposition is CM Ignore. */
    public static String ruleStatus(CiPolicyRuleCandidate r, String blockedReason) {
        Map<String, Object> meta = r == null || r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (Boolean.TRUE.equals(meta.get("deleted"))
                || "DELETED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
            return "Deleted";
        }
        // P0: Ignored = explicit Credit Manager action only
        if ("IGNORED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
            return "Ignored";
        }

        String classification = String.valueOf(meta.getOrDefault("classification", ""));
        if (isDataRequirement(classification) || Boolean.TRUE.equals(meta.get("dataRequirementOnly"))) {
            return "Data requirement";
        }
        if (isMetricAdjustment(classification) || Boolean.TRUE.equals(meta.get("metricAdjustment"))) {
            return "Metric adjustment";
        }
        if (isNonUnderwriting(classification)) {
            return "Non-underwriting";
        }
        if ("MANUAL_INPUT".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))
                || "MANUAL".equalsIgnoreCase(String.valueOf(meta.getOrDefault("verificationMode", "")))
                || IngestionMatchClassification.MANUAL_INPUT.name().equals(classification)) {
            return "Manual Input";
        }
        if (IngestionMatchClassification.MANUAL_REVIEW.name().equals(classification)) {
            return "Manual Review";
        }
        if (Boolean.TRUE.equals(meta.get("capabilityConflict"))) {
            return "Conflict";
        }
        if (Boolean.TRUE.equals(meta.get("potentialDuplicate"))) {
            return "Possible duplicate";
        }
        if (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))
                || "LOW".equalsIgnoreCase(String.valueOf(meta.getOrDefault("matchConfidence", "")))
                || (blockedReason != null && !blockedReason.isBlank())) {
            return "Needs your input";
        }
        if ("UNAVAILABLE".equalsIgnoreCase(String.valueOf(meta.getOrDefault("dataAvailability", "")))) {
            return "Unavailable";
        }
        if ("NEEDS_CONFIGURATION".equalsIgnoreCase(String.valueOf(meta.getOrDefault("dataAvailability", "")))) {
            return "Needs configuration";
        }
        if ("EDITED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
            return "Edited";
        }
        if ("ACCEPTED".equalsIgnoreCase(String.valueOf(meta.getOrDefault("disposition", "")))) {
            return "Accepted";
        }
        if (Boolean.TRUE.equals(meta.get("catalogueBacked"))
                || "GOLDEN_FALLBACK".equals(String.valueOf(meta.getOrDefault("source", "")))
                || IngestionMatchClassification.NEW_AUTOMATABLE_RULE.name().equals(classification)
                || IngestionMatchClassification.EXACT_EXISTING_CAPABILITY.name().equals(classification)
                || IngestionMatchClassification.EXISTING_CAPABILITY_PARAMETER_CHANGE.name().equals(classification)) {
            if (Boolean.TRUE.equals(meta.get("excludedFromActivation"))
                    && Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))) {
                return "Needs your input";
            }
            return "Ready";
        }
        if (Boolean.TRUE.equals(meta.get("excludedFromActivation"))
                && Boolean.TRUE.equals(meta.get("classificationOnly"))) {
            return "Needs your input";
        }
        return "Needs your input";
    }

    /**
     * Normalize pass/fail for cards. Engine may use failure-oriented DSL (onTrue=FAIL)
     * or pass-oriented DSL (onTrue=PASS). Surface business outcomes only.
     */
    public static Map<String, Object> passFailPresentation(CiPolicyRuleCandidate r) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (r == null) {
            return out;
        }
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String treatment = meta.get("failureTreatment") == null
                ? null : String.valueOf(meta.get("failureTreatment"));
        String onTrue = r.getOnTrue() == null ? "" : r.getOnTrue().toUpperCase(Locale.ROOT);
        String onFalse = r.getOnFalse() == null ? "" : r.getOnFalse().toUpperCase(Locale.ROOT);

        boolean failureOriented = isFailOutcome(onTrue) && isPassOutcome(onFalse);
        boolean passOriented = isPassOutcome(onTrue) && isFailOutcome(onFalse);

        String resultOnPass;
        String resultOnFailure;
        if (failureOriented) {
            // Catalogue style: expression true ⇒ rule fails
            resultOnPass = friendlyOutcome(onFalse);
            resultOnFailure = treatment != null ? friendlyTreatment(treatment) : friendlyOutcome(onTrue);
        } else if (passOriented) {
            resultOnPass = friendlyOutcome(onTrue);
            resultOnFailure = treatment != null ? friendlyTreatment(treatment) : friendlyOutcome(onFalse);
        } else {
            // Ambiguous — prefer treatment for failure; never show Fail→Pass contradiction
            resultOnPass = "Pass";
            resultOnFailure = treatment != null ? friendlyTreatment(treatment)
                    : (isFailOutcome(onTrue) ? friendlyOutcome(onTrue) : friendlyOutcome(onFalse));
            if ("Pass".equalsIgnoreCase(resultOnFailure)) {
                resultOnFailure = "Fail";
                out.put("configurationAnomaly",
                        "Source outcomes did not distinguish pass vs fail — defaulted failure display to Fail");
            }
        }

        out.put("resultOnPass", resultOnPass);
        out.put("resultOnFailure", resultOnFailure);
        out.put("failureTreatment", treatment != null ? friendlyTreatment(treatment) : resultOnFailure);
        out.put("passConditionLabel", "Pass when business condition is met");
        out.put("failConditionLabel", "Fails when business condition is not met");
        out.put("dslOrientation", failureOriented ? "FAILURE_CONDITION" : passOriented ? "PASS_CONDITION" : "MIXED");
        return out;
    }

    public static boolean acceptAllReadyEligible(Map<String, Object> card) {
        if (card == null) return false;
        String status = String.valueOf(card.getOrDefault("status", ""));
        if (!Set.of("Ready").contains(status)) return false;
        if (Boolean.TRUE.equals(card.get("excludedFromActivation"))
                && Boolean.TRUE.equals(card.get("NEEDS_INPUT"))) {
            return false;
        }
        String avail = String.valueOf(card.getOrDefault("dataAvailability", ""));
        if (Set.of(UNAVAILABLE, NEEDS_CONFIGURATION, MANUAL_INPUT_AVAILABLE).contains(avail)
                && !"Ready".equals(status)) {
            return false;
        }
        if (Boolean.TRUE.equals(card.get("capabilityConflict"))
                || Boolean.TRUE.equals(card.get("potentialDuplicate"))) {
            return false;
        }
        if (Set.of("Unavailable", "Needs configuration", "Manual Input", "Conflict",
                "Possible duplicate", "Data requirement", "Metric adjustment",
                "Non-underwriting", "Ignored", "Deleted").contains(status)) {
            return false;
        }
        return true;
    }

    private static final String UNAVAILABLE = PolicyMetricLineageService.UNAVAILABLE;
    private static final String NEEDS_CONFIGURATION = PolicyMetricLineageService.NEEDS_CONFIGURATION;
    private static final String MANUAL_INPUT_AVAILABLE = PolicyMetricLineageService.MANUAL_INPUT_AVAILABLE;

    private static boolean isDataRequirement(String c) {
        return IngestionMatchClassification.DATA_REQUIREMENT.name().equals(c)
                || IngestionMatchClassification.REPORT_FIELD.name().equals(c)
                || IngestionMatchClassification.DOCUMENT_REQUIREMENT.name().equals(c);
    }

    private static boolean isMetricAdjustment(String c) {
        return IngestionMatchClassification.METRIC_ADJUSTMENT.name().equals(c);
    }

    private static boolean isNonUnderwriting(String c) {
        return IngestionMatchClassification.PRODUCT_CONFIG.name().equals(c)
                || IngestionMatchClassification.PORTFOLIO_CONTROL.name().equals(c)
                || IngestionMatchClassification.SERVICING_RULE.name().equals(c)
                || IngestionMatchClassification.NARRATIVE.name().equals(c);
    }

    private static boolean isFailOutcome(String o) {
        return o.contains("FAIL") || o.equals("REJECT") || o.equals("DECLINE")
                || o.equals("REFER") || o.equals("MANUAL_REVIEW");
    }

    private static boolean isPassOutcome(String o) {
        return o.equals("PASS") || o.equals("APPROVE") || o.equals("OK");
    }

    private static String friendlyOutcome(String raw) {
        if (raw == null || raw.isBlank()) return "—";
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "PASS", "APPROVE", "OK" -> "Approve";
            case "FAIL", "REJECT", "DECLINE" -> "Reject";
            case "REFER", "MANUAL_REVIEW" -> "Manual Review";
            case "DATA_INSUFFICIENT", "MISSING_INFORMATION" -> "Data insufficient";
            default -> raw.charAt(0) + raw.substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        };
    }

    private static String friendlyTreatment(String t) {
        return switch (t.toUpperCase(Locale.ROOT)) {
            case "REJECT", "FAIL" -> "Reject";
            case "MANUAL_REVIEW", "REFER" -> "Manual Review";
            case "SCORE_IMPACT" -> "Score impact";
            case "LIMIT_ADJUSTMENT" -> "Limit adjustment";
            case "PASS", "APPROVE" -> "Approve";
            default -> friendlyOutcome(t);
        };
    }
}
