package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Distinguishes POLICY/AUTHORING values (thresholds that define the rule)
 * from RUNTIME/APPLICATION values (borrower facts evaluated later).
 *
 * Authoring readiness must not require a runtime applicant value.
 */
public final class PolicyAuthoringCompleteness {

    public static final String MSG_THRESHOLD_MISSING =
            "Policy threshold missing — set the value before Accept";
    /** Legacy message that incorrectly implied a runtime/application value was required. */
    public static final String LEGACY_VALUE_MISSING =
            "Parameter value missing — confirm before Accept";

    private PolicyAuthoringCompleteness() {}

    public static boolean isAuthoringComplete(CiPolicyRuleCandidate r) {
        if (r == null) return false;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (Boolean.TRUE.equals(meta.get("classificationOnly"))
                && !Boolean.TRUE.equals(meta.get("cmAuthored"))) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.get("deleted"))) return false;
        if (hasUnresolvedAuthoringOperand(meta)) return false;
        Object threshold = authoringThreshold(r);
        if (threshold == null || isBlank(threshold)) {
            // Boolean / flag rules with no numeric threshold can still be complete
            return isNonThresholdComplete(r, meta);
        }
        return true;
    }

    /**
     * Authoring threshold/configuration only — never an applicant/runtime fact.
     */
    public static Object authoringThreshold(CiPolicyRuleCandidate r) {
        if (r == null) return null;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (meta.get("threshold") != null && !isBlank(meta.get("threshold"))) {
            return unwrapConst(meta.get("threshold"));
        }
        Object fromParams = firstThresholdParam(meta.get("parameters"));
        if (fromParams != null) return fromParams;
        Map<String, Object> expr = r.getExpression() == null ? Map.of() : r.getExpression();
        Object right = expr.get("right");
        Object unwrapped = unwrapConst(right);
        if (unwrapped != null) return unwrapped;
        if (expr.get("threshold") != null) return unwrapConst(expr.get("threshold"));
        if (expr.get("value") != null) return unwrapConst(expr.get("value"));
        return null;
    }

    public static String authoringGapMessage(CiPolicyRuleCandidate r) {
        if (r == null) return MSG_THRESHOLD_MISSING;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        if (hasUnresolvedAuthoringOperand(meta)) {
            return null; // operand UI carries the message
        }
        if (isAuthoringComplete(r)) return null;
        return MSG_THRESHOLD_MISSING;
    }

    /**
     * Reconcile card status/blockedReason so runtime-missing never blocks authoring Ready.
     * POLICY-READINESS-CONVERGENCE-1 — Ready/Accepted only when execution-ready
     * (authoring complete AND required operands resolved). Disposition ACCEPTED stays distinct.
     */
    public static void reconcileCard(Map<String, Object> card, CiPolicyRuleCandidate r) {
        if (card == null || r == null) return;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String blocked = card.get("blockedReason") == null ? null : String.valueOf(card.get("blockedReason"));
        if (LEGACY_VALUE_MISSING.equals(blocked) || MSG_THRESHOLD_MISSING.equals(blocked)) {
            if (isAuthoringComplete(r)) {
                card.put("blockedReason", null);
                blocked = null;
            } else {
                card.put("blockedReason", MSG_THRESHOLD_MISSING);
                blocked = MSG_THRESHOLD_MISSING;
            }
        }
        boolean executionReady = PolicyExecutionReadiness.isExecutionReady(r)
                || (!PolicyExecutionReadiness.isIncludedExecutableRule(r)
                && isAuthoringComplete(r)
                && !hasUnresolvedAuthoringOperand(meta)
                && PolicyExecutionReadiness.unresolvedOperands(r).isEmpty());
        if (executionReady
                && isAuthoringComplete(r)
                && !hasUnresolvedAuthoringOperand(meta)
                && Set.of("Needs your input", "Needs Review", "Blocked").contains(String.valueOf(card.get("status")))
                && (blocked == null || blocked.isBlank()
                || LEGACY_VALUE_MISSING.equals(blocked)
                || MSG_THRESHOLD_MISSING.equals(blocked))) {
            // Do not override Ignored/Deleted/Manual
            if (!Set.of("Ignored", "Deleted", "Manual Input", "Manual Review",
                    "Data requirement", "Metric adjustment", "Non-underwriting")
                    .contains(String.valueOf(card.get("status")))) {
                String disposition = String.valueOf(meta.getOrDefault("disposition", ""));
                if ("ACCEPTED".equalsIgnoreCase(disposition)) {
                    card.put("status", "Accepted");
                } else if ("EDITED".equalsIgnoreCase(disposition)) {
                    card.put("status", "Edited");
                } else {
                    card.put("status", "Ready");
                }
            }
        }
        applyCmWording(card, r);
        card.put("authoringComplete", isAuthoringComplete(r));
        card.put("authoringThreshold", authoringThreshold(r));
        card.put("runtimeValueRequired", false); // never required for policy authoring readiness
        // Canonical execution readiness — may demote Ready/Accepted → Needs your input
        PolicyExecutionReadiness.applyToCard(card, r);
        // POLICY-STUDIO-RULE-LIFECYCLE-AND-STATE-MODEL-CLOSURE-1 — single lender projection
        attachLifecycle(card, r);
    }

    private static void attachLifecycle(Map<String, Object> card, CiPolicyRuleCandidate r) {
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        // Prefer card operands; if absent (unit tests / partial cards), rebuild from rule
        if (!(card.get("operands") instanceof List<?> ops) || ops.isEmpty()) {
            card.put("operands", PolicyExecutionReadiness.operandsOf(r));
        }
        // Spine-backed lifecycle facts — policyTestReady from CanonicalParameterExecutionService only
        var facts = com.los.core.creditintelligence.policystudio.parameters.lifecycle
                .PolicyRuleLifecycleProjection.factsFromCard(card, meta);
        card.put("executionReady", facts.policyTestReady());
        card.put("policyTestReady", facts.policyTestReady());
        card.put("runtimeReady", facts.runtimeReady());
        card.put("productionReady", false);
        String ruleId = r.getId() == null ? r.getSystemRuleId() : r.getId().toString();
        Map<String, Object> life = com.los.core.creditintelligence.policystudio.parameters.lifecycle
                .PolicyRuleLifecycleProjection.project(ruleId, facts);
        // Primary parameter id for Advanced
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operands = card.get("operands") instanceof List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
        operands.stream()
                .map(o -> o.get("parameterId") != null ? o.get("parameterId") : o.get("canonicalParameterId"))
                .filter(p -> p != null && !String.valueOf(p).isBlank())
                .findFirst()
                .ifPresent(p -> life.put("parameterId", String.valueOf(p)));
        card.put("lifecycle", life);
        // Converge visible status chip to lifecycle (single authority)
        String chip = String.valueOf(life.getOrDefault("statusChip", card.get("status")));
        if (!Set.of("Ignored", "Deleted", "Data requirement", "Metric adjustment", "Non-underwriting",
                "Policy requirement", "Manual Input", "Manual Review").contains(String.valueOf(card.get("status")))) {
            card.put("status", chip);
        }
        if (Boolean.TRUE.equals(life.get("forbidAcceptedBadgeWhenNeedsInput"))) {
            card.remove("reviewBadge");
        }
        card.put("lenderState", life.get("lenderState"));
        card.put("outstandingAction", life.get("outstandingAction"));
        card.put("lenderPrimaryAction", life.get("lenderPrimaryAction"));
        // Policy-level aggregation helpers
        card.put("lifecycleNeedsInput",
                "NEEDS_INPUT".equals(life.get("lenderState"))
                        || "DATA_NOT_AVAILABLE".equals(life.get("lenderState")));
        card.put("lifecycleReadyToTest",
                "READY_TO_TEST".equals(life.get("lenderState"))
                        || "ACCEPTED_READY_TO_TEST".equals(life.get("lenderState"))
                        || "PRODUCTION_BLOCKED".equals(life.get("lenderState"))
                        || "PRODUCTION_READY".equals(life.get("lenderState")));
    }

    /**
     * Preferred CM display without changing DSL execution semantics.
     * Failure-oriented LT(score, 650) → "Bureau Score must be >= 650".
     */
    public static void applyCmWording(Map<String, Object> card, CiPolicyRuleCandidate r) {
        if (card == null || r == null) return;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String cap = String.valueOf(meta.getOrDefault("businessCapabilityId", ""));
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
        Object thr = authoringThreshold(r);
        // Never flatten OR/AND compound bureau eligibility into a single >= threshold label
        Map<String, Object> expr = r.getExpression();
        boolean compoundGroup = Boolean.TRUE.equals(meta.get("compoundGroup"))
                || CompoundExpressionAuthoringSupport.isGroupExpression(expr);
        if (compoundGroup) {
            String summary = String.valueOf(meta.getOrDefault("businessSummary",
                    card.getOrDefault("businessRule", "")));
            if (summary != null && !summary.isBlank() && !"null".equals(summary)) {
                card.put("businessRule", summary);
            }
            card.put("compoundGroup", true);
            return;
        }
        boolean bureauMin = "BUREAU.MIN_SCORE".equals(cap)
                || "ELIG.MIN_BUREAU_SCORE".equals(cap)
                || sys.contains("BUREAU_SCORE")
                || sys.contains("BUREAU_MIN_SCORE")
                || "bureau.score".equals(String.valueOf(meta.get("parameterId")));
        if (bureauMin && thr != null) {
            card.put("ruleName", "Minimum Bureau Score");
            card.put("parameterName", "Bureau Score");
            card.put("businessRule", "Bureau Score must be >= " + formatThr(thr));
            card.put("operator", ">=");
            card.put("thresholdValue", thr);
            card.put("operatorValueLabel", ">= " + formatThr(thr));
            String treatment = String.valueOf(card.getOrDefault("treatment",
                    card.getOrDefault("failureTreatmentDisplay", "Reject")));
            card.put("failureConditionLabel", "If below threshold: " + treatment);
            card.put("resultOnFailure", treatment);
            card.put("treatment", treatment);
        } else if (thr != null && card.get("businessRule") != null) {
            // Generic pass-oriented label when we have operator + threshold on the card
            String op = String.valueOf(card.getOrDefault("operator", ""));
            String param = String.valueOf(card.getOrDefault("parameterName", "Parameter"));
            if ((" < ".equals(" " + op + " ") || "<".equals(op) || "LT".equalsIgnoreCase(op))
                    && looksLikeMinimumGate(meta, sys)) {
                card.put("businessRule", param + " must be >= " + formatThr(thr));
                card.put("operatorValueLabel", ">= " + formatThr(thr));
                card.put("operator", ">=");
            }
        }
    }

    private static boolean looksLikeMinimumGate(Map<String, Object> meta, String sys) {
        String title = String.valueOf(meta.getOrDefault("businessTitle", "")).toLowerCase(Locale.ROOT);
        return title.contains("minimum") || title.contains("min ")
                || sys.contains("MIN_SCORE") || sys.contains("MIN_");
    }

    private static boolean hasUnresolvedAuthoringOperand(Map<String, Object> meta) {
        // CLEAN / EDI style resolutions stored on the rule — authoring mapping gaps
        Map<String, Object> resolutions = ParameterResolutionSupport.resolutionsOf(meta);
        for (Object v : resolutions.values()) {
            if (v instanceof Map<?, ?> m) {
                Object stObj = m.get("status");
                String st = stObj == null ? "" : String.valueOf(stObj);
                if (ParameterResolutionSupport.STATUS_UNRESOLVED.equals(st)) {
                    return true;
                }
            }
        }
        Object clean = meta.get(CleanHistoryDefinitionSupport.META_KEY);
        if (clean instanceof Map<?, ?> c) {
            Object stObj = c.get("status");
            String st = stObj == null
                    ? CleanHistoryDefinitionSupport.STATUS_UNRESOLVED
                    : String.valueOf(stObj);
            if (CleanHistoryDefinitionSupport.STATUS_UNRESOLVED.equals(st)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNonThresholdComplete(CiPolicyRuleCandidate r, Map<String, Object> meta) {
        Map<String, Object> expr = r.getExpression() == null ? Map.of() : r.getExpression();
        String op = String.valueOf(expr.getOrDefault("op", ""));
        if (Set.of("EXISTS", "NOT", "AND", "OR", "IF", "TRUE", "FALSE").contains(op.toUpperCase(Locale.ROOT))) {
            return true;
        }
        // Lifecycle/scratch fixtures sometimes use op:"true" without a numeric threshold
        if ("true".equalsIgnoreCase(op) || "false".equalsIgnoreCase(op)) {
            return true;
        }
        if (Boolean.TRUE.equals(meta.get("catalogueBacked"))
                && meta.get("businessCapabilityId") != null
                && (meta.get("parameters") instanceof Map<?, ?> p && !p.isEmpty()
                || "ELIG.REQUIRE_KYC_PASS".equals(meta.get("businessCapabilityId")))) {
            return meta.get("parameters") instanceof Map<?, ?> params
                    && (params.isEmpty() || params.values().stream().allMatch(v -> v != null)
                    || authoringThreshold(r) != null);
        }
        return Boolean.TRUE.equals(meta.get("cmAuthored")) && meta.get("parameterId") != null;
    }

    @SuppressWarnings("unchecked")
    private static Object firstThresholdParam(Object parameters) {
        if (!(parameters instanceof Map<?, ?> raw)) return null;
        Map<String, Object> params = (Map<String, Object>) raw;
        for (String key : List.of(
                "minimumScore", "maximumCount", "maximumPercentage", "minimumPercentage",
                "minimumValue", "maximumAmount", "minimumAmount", "minimumRatio",
                "maximumDays", "threshold", "value")) {
            if (params.get(key) != null && !isBlank(params.get(key))) {
                return unwrapConst(params.get(key));
            }
        }
        for (Object v : params.values()) {
            if (v instanceof Number) return v;
            if (v instanceof String s && !s.isBlank()) {
                try {
                    if (s.contains(".")) return Double.parseDouble(s);
                    return Long.parseLong(s.replace(",", ""));
                } catch (Exception ignored) {
                    // continue
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Object unwrapConst(Object v) {
        if (v == null) return null;
        if (v instanceof Map<?, ?> m) {
            if (m.get("const") != null) return m.get("const");
            if (m.get("value") != null) return m.get("value");
        }
        return v;
    }

    private static boolean isBlank(Object v) {
        return v == null || String.valueOf(v).isBlank() || "null".equalsIgnoreCase(String.valueOf(v));
    }

    private static String formatThr(Object thr) {
        if (thr instanceof Number n) {
            if (n.doubleValue() == Math.rint(n.doubleValue())) {
                return String.valueOf(n.longValue());
            }
        }
        return String.valueOf(thr);
    }

    /** Clear legacy NEEDS_INPUT when authoring threshold is present (session heal). */
    public static void healMetadata(CiPolicyRuleCandidate r) {
        if (r == null || r.getMetadata() == null) return;
        if (!isAuthoringComplete(r)) return;
        // Do not heal away NEEDS_INPUT when required runtime operands are still unresolved
        if (!PolicyExecutionReadiness.unresolvedOperands(r).isEmpty()
                || !PolicyExecutionReadiness.unavailableOperands(r).isEmpty()) {
            return;
        }
        Map<String, Object> meta = new LinkedHashMap<>(r.getMetadata());
        if (Boolean.TRUE.equals(meta.get("NEEDS_INPUT"))
                && !hasUnresolvedAuthoringOperand(meta)) {
            meta.put("NEEDS_INPUT", false);
            meta.put("excludedFromActivation", false);
            meta.put("activationIncluded", true);
            if (LEGACY_VALUE_MISSING.equals(String.valueOf(meta.get("blockedReason")))
                    || MSG_THRESHOLD_MISSING.equals(String.valueOf(meta.get("blockedReason")))) {
                meta.remove("blockedReason");
            }
            meta.put("authoringThresholdSource",
                    meta.get("authoringThresholdSource") != null
                            ? meta.get("authoringThresholdSource") : "PRESENT");
            r.setMetadata(meta);
        }
    }
}
