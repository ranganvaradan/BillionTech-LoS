package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY-RULE-PARTICIPATION-READINESS-INVARIANT-1
 *
 * Single authority for whether a rule participates in current policy execution/readiness.
 * IGNORED does not make a parameter READY; it only removes that rule's operands from
 * the effective policy blocker set.
 */
public final class PolicyRuleParticipation {

    public static final String AUTHORITY = "PolicyRuleParticipation";

    public enum Kind {
        PARTICIPATING,
        NON_PARTICIPATING,
        CONDITIONAL_CHILD,
        DELETED_DEAD
    }

    private PolicyRuleParticipation() {}

    public static boolean participatesInPolicyReadiness(CiPolicyRuleCandidate r) {
        return classify(r) == Kind.PARTICIPATING;
    }

    public static Kind classify(CiPolicyRuleCandidate r) {
        if (r == null) {
            return Kind.NON_PARTICIPATING;
        }
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String disposition = String.valueOf(meta.getOrDefault("disposition", ""));

        if (Boolean.TRUE.equals(meta.get("deleted"))
                || "DELETED".equalsIgnoreCase(disposition)) {
            return Kind.DELETED_DEAD;
        }
        if (PolicyStudioConvergencePresenter.isCompoundChild(r.getSystemRuleId())
                || Boolean.TRUE.equals(meta.get("compoundChild"))) {
            return Kind.CONDITIONAL_CHILD;
        }
        if (isGenuineNotApplicable(meta)) {
            return Kind.NON_PARTICIPATING;
        }
        // Ignore family: retained historically, does not participate in current readiness.
        if ("IGNORED".equalsIgnoreCase(disposition)
                || "IGNORE_FOR_AUTOMATION".equalsIgnoreCase(disposition)
                || "DEFERRED_SOURCE_NOT_PROVEN".equalsIgnoreCase(disposition)) {
            return Kind.NON_PARTICIPATING;
        }
        if ("KEEP_AS_POLICY_REQUIREMENT".equalsIgnoreCase(disposition)) {
            return Kind.PARTICIPATING;
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("systemRuleId", r.getSystemRuleId());
        card.put("disposition", disposition);
        card.put("authoringComplete", true);
        card.put("operands", List.of());
        PolicyRuleLenderState state = PolicyRuleLifecycleProjection.deriveState(
                PolicyRuleLifecycleProjection.factsFromCard(card, meta));
        return participates(state) ? Kind.PARTICIPATING : Kind.NON_PARTICIPATING;
    }

    public static boolean participates(PolicyRuleLenderState state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case IGNORED, NOT_APPLICABLE -> false;
            case NEEDS_INPUT,
                 READY_FOR_CONFIRMATION,
                 READY_TO_TEST,
                 ACCEPTED_READY_TO_TEST,
                 DATA_NOT_AVAILABLE,
                 PRODUCTION_BLOCKED,
                 PRODUCTION_READY,
                 POLICY_REQUIREMENT -> true;
        };
    }

    public static boolean isIgnoredDisposition(CiPolicyRuleCandidate r) {
        if (r == null || r.getMetadata() == null) {
            return false;
        }
        String disposition = String.valueOf(r.getMetadata().getOrDefault("disposition", ""));
        return "IGNORED".equalsIgnoreCase(disposition)
                || "IGNORE_FOR_AUTOMATION".equalsIgnoreCase(disposition);
    }

    private static boolean isGenuineNotApplicable(Map<String, Object> meta) {
        if (Boolean.TRUE.equals(meta.get("classificationOnly"))
                && !Boolean.TRUE.equals(meta.get("cmAuthored"))) {
            return true;
        }
        return Boolean.TRUE.equals(meta.get("dataRequirementOnly"))
                || Boolean.TRUE.equals(meta.get("metricAdjustment"));
    }

    static String dispositionOf(CiPolicyRuleCandidate r) {
        if (r == null || r.getMetadata() == null) {
            return "";
        }
        return String.valueOf(r.getMetadata().getOrDefault("disposition", "")).toUpperCase(Locale.ROOT);
    }
}
