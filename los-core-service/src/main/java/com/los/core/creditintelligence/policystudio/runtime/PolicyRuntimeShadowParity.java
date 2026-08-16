package com.los.core.creditintelligence.policystudio.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wave-5 observational shadow parity. NEVER changes approval/rejection/score/limit/workflow.
 */
public final class PolicyRuntimeShadowParity {

    public enum ParityClass {
        EXACT_MATCH,
        EXPECTED_SEMANTIC_FIX,
        DATA_PATH_DIFFERENCE,
        ASOF_DIFFERENCE,
        MISSING_DATA_SEMANTICS_DIFFERENCE,
        OPERATOR_SEMANTICS_DIFFERENCE,
        CANONICAL_ID_DIFFERENCE,
        RULE_STRUCTURE_DIFFERENCE,
        NOT_TRANSLATABLE,
        BUG,
        UNKNOWN
    }

    public record ShadowCase(
            String caseId,
            String ruleId,
            String frozenOutcome,
            String canonicalOutcome,
            ParityClass parity,
            String note,
            boolean changesDecision
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("caseId", caseId);
            m.put("ruleId", ruleId);
            m.put("frozenOutcome", frozenOutcome);
            m.put("canonicalOutcome", canonicalOutcome);
            m.put("parity", parity.name());
            m.put("note", note);
            m.put("changesDecision", false); // hard invariant
            return m;
        }
    }

    private PolicyRuntimeShadowParity() {}

    /**
     * Compare Frozen-style outcome (APPROVE/REJECT/MANUAL/DATA_INSUFFICIENT)
     * to Canonical RuleOutcome (PASS/FAIL/DATA_INSUFFICIENT/ERROR).
     */
    public static ParityClass classify(String frozenOutcome, CanonicalRuleResult.RuleOutcome canonical) {
        String f = normalizeFrozen(frozenOutcome);
        String c = canonical == null ? "DATA_INSUFFICIENT" : canonical.name();
        if (equivalent(f, c)) {
            return ParityClass.EXACT_MATCH;
        }
        if ("DATA_INSUFFICIENT".equals(c) && ("APPROVE".equals(f) || "PASS".equals(f) || "REJECT".equals(f))) {
            return ParityClass.MISSING_DATA_SEMANTICS_DIFFERENCE;
        }
        if ("DATA_INSUFFICIENT".equals(f) && ("PASS".equals(c) || "FAIL".equals(c))) {
            return ParityClass.MISSING_DATA_SEMANTICS_DIFFERENCE;
        }
        if (("REJECT".equals(f) && "FAIL".equals(c)) || ("APPROVE".equals(f) && "PASS".equals(c))) {
            return ParityClass.EXACT_MATCH;
        }
        if (("REJECT".equals(f) && "PASS".equals(c)) || ("APPROVE".equals(f) && "FAIL".equals(c))) {
            return ParityClass.OPERATOR_SEMANTICS_DIFFERENCE;
        }
        if ("MANUAL".equals(f) || "MANUAL_REVIEW".equals(f)) {
            return ParityClass.RULE_STRUCTURE_DIFFERENCE;
        }
        if ("NOT_TRANSLATABLE".equals(f)) {
            return ParityClass.NOT_TRANSLATABLE;
        }
        return ParityClass.UNKNOWN;
    }

    public static ShadowCase compare(
            String caseId,
            String ruleId,
            String frozenOutcome,
            CanonicalRuleResult canonical,
            String note) {
        CanonicalRuleResult.RuleOutcome out = canonical == null
                ? CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT : canonical.result();
        ParityClass pc = classify(frozenOutcome, out);
        return new ShadowCase(
                caseId,
                ruleId,
                normalizeFrozen(frozenOutcome),
                out.name(),
                pc,
                note,
                false);
    }

    public static Map<String, Object> summarize(List<ShadowCase> cases) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("shadowEvaluationImplemented", true);
        m.put("shadowChangesDecision", false);
        m.put("shadowCaseCount", cases == null ? 0 : cases.size());
        long exact = 0, mismatch = 0, unknown = 0;
        Map<String, Long> byClass = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        if (cases != null) {
            for (ShadowCase c : cases) {
                rows.add(c.toMap());
                byClass.merge(c.parity().name(), 1L, Long::sum);
                if (c.parity() == ParityClass.EXACT_MATCH) exact++;
                else if (c.parity() == ParityClass.UNKNOWN) unknown++;
                else mismatch++;
            }
        }
        m.put("exactMatchCount", exact);
        m.put("mismatchCount", mismatch);
        m.put("unknownCount", unknown);
        m.put("byParityClass", byClass);
        m.put("cases", rows);
        return m;
    }

    private static String normalizeFrozen(String frozenOutcome) {
        if (frozenOutcome == null) return "UNKNOWN";
        String f = frozenOutcome.trim().toUpperCase();
        return switch (f) {
            case "APPROVED" -> "APPROVE";
            case "REJECTED" -> "REJECT";
            case "PASS" -> "APPROVE";
            case "FAIL" -> "REJECT";
            default -> f;
        };
    }

    private static boolean equivalent(String frozen, String canonical) {
        if (frozen.equals(canonical)) return true;
        if ("APPROVE".equals(frozen) && "PASS".equals(canonical)) return true;
        if ("REJECT".equals(frozen) && "FAIL".equals(canonical)) return true;
        if ("DATA_INSUFFICIENT".equals(frozen) && "DATA_INSUFFICIENT".equals(canonical)) return true;
        return false;
    }
}
