package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Observational comparison of legacy live evidence vs canonical shadow.
 * Differences are expected and must be classified — they are not W11.3 failures.
 */
public final class CanonicalShadowComparator {

    private CanonicalShadowComparator() {}

    public static Map<String, Object> compare(
            Map<String, Object> legacy,
            Map<String, Object> canonical,
            String canonicalStatus,
            CanonicalShadowDecision canonicalDecision) {
        List<Map<String, Object>> mismatches = new ArrayList<>();
        int paramValue = 0;
        int paramStatus = 0;
        int rulePart = 0;
        int ruleResult = 0;
        int scInput = 0;
        int scResult = 0;
        int finalMismatch = 0;
        int unknown = 0;

        String legacyDecision = str(legacy.get("finalDecision"));
        String canonDec = canonicalDecision == null ? null : canonicalDecision.name();
        if (!sameDecision(legacyDecision, canonDec)) {
            finalMismatch = 1;
            mismatches.add(row("FINAL_DECISION", "UnderwritingRuleEngine+ScorecardPolicyEngine",
                    "CanonicalPolicyRuntime+canonical scorecard",
                    legacyDecision, canonDec, classifyFinal(legacy)));
        }

        String legacySc = str(legacy.get("scorecardId"));
        String canonSc = str(canonical.get("scorecardId"));
        if (!Objects.equals(nullToEmpty(legacySc), nullToEmpty(canonSc))) {
            scResult++;
            mismatches.add(row("SCORECARD_IDENTITY", "ScorecardPolicyEngine",
                    "frozen policyDocument.scorecardId",
                    legacySc, canonSc, CanonicalShadowRootCauseClass.SCORECARD_DIFFERENCE));
        }

        Object legacyScore = legacy.get("scorecardPercent");
        Object canonScore = canonical.get("numericalScore");
        if (!Objects.equals(stringify(legacyScore), stringify(canonScore))) {
            scResult++;
            mismatches.add(row("SCORECARD_RESULT", "ScorecardPolicyEngine",
                    "CanonicalShadowScorecardExecutor",
                    stringify(legacyScore), stringify(canonScore),
                    CanonicalShadowRootCauseClass.SCORECARD_DIFFERENCE));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legacyRules = listOfMaps(legacy.get("rules"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> canonRules = listOfMaps(canonical.get("rules"));
        if (legacyRules.size() != canonRules.size()) {
            rulePart++;
            mismatches.add(row("RULE_PARTICIPATION_COUNT", "UnderwritingRuleEngine",
                    "CanonicalPolicyRuntime",
                    String.valueOf(legacyRules.size()), String.valueOf(canonRules.size()),
                    CanonicalShadowRootCauseClass.RULE_PARTICIPATION_DIFFERENCE));
        }

        CanonicalShadowComparisonStatus status;
        if ("NOT_ELIGIBLE".equals(canonicalStatus)) {
            status = CanonicalShadowComparisonStatus.SHADOW_NOT_ELIGIBLE;
        } else if ("NOT_EXECUTABLE".equals(canonicalStatus)
                || canonicalDecision == CanonicalShadowDecision.NOT_EXECUTABLE) {
            status = CanonicalShadowComparisonStatus.CANONICAL_NOT_EXECUTABLE;
        } else if ("ERROR".equals(canonicalStatus)) {
            status = CanonicalShadowComparisonStatus.ERROR;
        } else if (mismatches.isEmpty()) {
            status = CanonicalShadowComparisonStatus.MATCH;
        } else {
            status = CanonicalShadowComparisonStatus.MISMATCH;
        }

        for (Map<String, Object> m : mismatches) {
            if (CanonicalShadowRootCauseClass.UNKNOWN.name().equals(str(m.get("rootCauseClass")))) {
                unknown++;
            }
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("PARAMETER_VALUE_MISMATCH_COUNT", paramValue);
        counts.put("PARAMETER_STATUS_MISMATCH_COUNT", paramStatus);
        counts.put("RULE_PARTICIPATION_MISMATCH_COUNT", rulePart);
        counts.put("RULE_RESULT_MISMATCH_COUNT", ruleResult);
        counts.put("SCORECARD_INPUT_MISMATCH_COUNT", scInput);
        counts.put("SCORECARD_RESULT_MISMATCH_COUNT", scResult);
        counts.put("FINAL_DECISION_MISMATCH", finalMismatch);
        counts.put("UNKNOWN_MISMATCH_COUNT", unknown);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("comparisonStatus", status.name());
        out.put("mismatchCounts", counts);
        out.put("mismatches", mismatches);
        return out;
    }

    public static Map<String, Object> policyTestEquivalence(
            List<Map<String, Object>> policyTestRules,
            List<Map<String, Object>> shadowRules,
            boolean policyTestScorecardAvailable,
            boolean shadowScorecardAvailable) {
        int paramValue = 0;
        int paramStatus = 0;
        int rulePart = 0;
        int ruleResult = 0;
        int scInput = 0;
        int scResult = 0;
        int finalMismatch = 0;
        Map<String, String> testById = new LinkedHashMap<>();
        for (Map<String, Object> r : policyTestRules == null ? List.<Map<String, Object>>of() : policyTestRules) {
            String id = str(r.get("ruleKey"));
            if (id == null) {
                id = str(r.get("ruleId"));
            }
            String outcome = str(r.get("canonicalResult"));
            if (outcome == null) {
                outcome = str(r.get("outcome"));
            }
            if (id != null) {
                testById.put(id, outcome);
            }
        }
        Map<String, String> shadowById = new LinkedHashMap<>();
        for (Map<String, Object> r : shadowRules == null ? List.<Map<String, Object>>of() : shadowRules) {
            String id = str(r.get("ruleId"));
            if (id == null) {
                id = str(r.get("ruleKey"));
            }
            String outcome = str(r.get("result"));
            if (id != null) {
                shadowById.put(id, outcome);
            }
        }
        if (testById.size() != shadowById.size()) {
            rulePart = 1;
        }
        for (Map.Entry<String, String> e : testById.entrySet()) {
            String other = shadowById.get(e.getKey());
            if (other == null) {
                rulePart++;
            } else if (!normOutcome(e.getValue()).equals(normOutcome(other))) {
                ruleResult++;
            }
        }
        if (policyTestScorecardAvailable != shadowScorecardAvailable) {
            scResult = 1;
            scInput = 1;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("PARAMETER_VALUE_MISMATCH_COUNT", paramValue);
        out.put("PARAMETER_STATUS_MISMATCH_COUNT", paramStatus);
        out.put("RULE_PARTICIPATION_MISMATCH_COUNT", rulePart);
        out.put("RULE_RESULT_MISMATCH_COUNT", ruleResult);
        out.put("SCORECARD_INPUT_MISMATCH_COUNT", scInput);
        out.put("SCORECARD_RESULT_MISMATCH_COUNT", scResult);
        out.put("FINAL_DECISION_MISMATCH_COUNT", finalMismatch);
        out.put("policyTestScorecardAvailable", policyTestScorecardAvailable);
        out.put("shadowScorecardAvailable", shadowScorecardAvailable);
        out.put("scorecardConvergenceGap", policyTestScorecardAvailable != shadowScorecardAvailable);
        return out;
    }

    static boolean sameDecision(String legacy, String canonical) {
        return normalizeDecision(legacy).equals(normalizeDecision(canonical));
    }

    static String normalizeDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "APPROVED", "APPROVE", "PASS" -> "APPROVE";
            case "REJECTED", "REJECT", "FAIL" -> "REJECT";
            case "MANUAL_REVIEW", "MANUAL", "REFER" -> "MANUAL_REVIEW";
            case "DATA_INSUFFICIENT", "DI" -> "DATA_INSUFFICIENT";
            default -> u;
        };
    }

    private static CanonicalShadowRootCauseClass classifyFinal(Map<String, Object> legacy) {
        if (Boolean.TRUE.equals(legacy.get("legacyFallbackUsed"))) {
            return CanonicalShadowRootCauseClass.LEGACY_FALLBACK;
        }
        return CanonicalShadowRootCauseClass.RULE_CONTENT_DIFFERENCE;
    }

    private static Map<String, Object> row(
            String type, String legacyAuth, String canonAuth,
            String legacyVal, String canonVal, CanonicalShadowRootCauseClass root) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mismatchType", type);
        m.put("legacyAuthority", legacyAuth);
        m.put("canonicalAuthority", canonAuth);
        m.put("legacyValue", legacyVal);
        m.put("canonicalValue", canonVal);
        m.put("rootCauseClass", root.name());
        return m;
    }

    private static String str(Object o) {
        return o == null || "null".equalsIgnoreCase(String.valueOf(o)) ? null : String.valueOf(o);
    }

    private static String stringify(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String normOutcome(String s) {
        if (s == null) {
            return "";
        }
        String u = s.trim().toUpperCase(Locale.ROOT);
        if ("PASS".equals(u) || "FAIL".equals(u) || "ERROR".equals(u) || "DATA_INSUFFICIENT".equals(u)) {
            return u;
        }
        return u;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object o) {
        if (!(o instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object e : list) {
            if (e instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }
}
