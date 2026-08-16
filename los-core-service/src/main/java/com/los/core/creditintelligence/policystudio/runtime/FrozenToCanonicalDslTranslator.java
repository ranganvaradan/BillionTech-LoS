package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.service.underwriting.ScorecardCanonicalFactorMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic Frozen hard-rule → Policy DSL AST translator (compatibility only).
 * Not a second policy engine. Does not approximate NOT_TRANSLATABLE rules.
 *
 * <p>Frozen hard-rule polarity: condition MATCH → trigger REJECT/MANUAL.
 * Canonical DSL polarity: expression TRUE → PASS (loan OK).
 * Therefore REJECT hard-rules translate to the <em>negation</em> of the hard condition.
 */
public final class FrozenToCanonicalDslTranslator {

    public enum TranslationClass {
        TRANSLATABLE_EXACT,
        TRANSLATABLE_WITH_EXPLICIT_COMPATIBILITY,
        NOT_TRANSLATABLE,
        NOT_POLICY_SEMANTICS,
        DEAD_UNUSED
    }

    public record TranslationResult(
            TranslationClass classification,
            String legacyParameter,
            String canonicalParameterId,
            String legacyCondition,
            String decision,
            Map<String, Object> dslExpression,
            String note,
            boolean polarityInverted
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classification", classification.name());
            m.put("legacyParameter", legacyParameter);
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("legacyCondition", legacyCondition);
            m.put("decision", decision);
            m.put("dslExpression", dslExpression);
            m.put("note", note);
            m.put("polarityInverted", polarityInverted);
            return m;
        }
    }

    private FrozenToCanonicalDslTranslator() {}

    public static TranslationResult translateHardRule(Map<String, Object> hardRule) {
        if (hardRule == null || hardRule.isEmpty()) {
            return new TranslationResult(TranslationClass.DEAD_UNUSED, null, null, null, null, null,
                    "Empty hard rule", false);
        }
        String parameter = str(hardRule.get("parameter"));
        String condition = str(hardRule.get("condition"));
        String decision = str(hardRule.get("decision"));
        if (parameter == null || condition == null) {
            return new TranslationResult(TranslationClass.NOT_TRANSLATABLE, parameter, null, condition, decision,
                    null, "Missing parameter or condition", false);
        }
        if (condition.toUpperCase(Locale.ROOT).startsWith("PARAM_REF:")) {
            return new TranslationResult(TranslationClass.NOT_TRANSLATABLE, parameter, null, condition, decision,
                    null, "PARAM_REF conditions require explicit compatibility design", false);
        }
        if (hardRule.get("dependsOn") instanceof Map<?, ?> dep && !dep.isEmpty()) {
            return new TranslationResult(TranslationClass.TRANSLATABLE_WITH_EXPLICIT_COMPATIBILITY,
                    parameter, null, condition, decision, null,
                    "dependsOn present — structural dependency not auto-translated", false);
        }

        ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(parameter);
        String canonicalId = bind.canonicalParameterId();
        boolean exactMap = canonicalId != null
                && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()));
        if (!exactMap) {
            // Allow dotted GACAT ids already
            if (parameter.contains(".")) {
                canonicalId = parameter.trim();
                exactMap = true;
            } else {
                return new TranslationResult(TranslationClass.NOT_TRANSLATABLE, parameter, null, condition, decision,
                        null, "No EXACT/SAFE_ALIAS GACAT mapping for legacy parameter", false);
            }
        }

        ParsedCond parsed = parseCondition(condition);
        if (parsed == null) {
            return new TranslationResult(TranslationClass.NOT_TRANSLATABLE, parameter, canonicalId, condition, decision,
                    null, "Unsupported condition syntax", false);
        }

        boolean rejectish = decision != null && (decision.equalsIgnoreCase("REJECT")
                || decision.equalsIgnoreCase("REJECTED"));
        boolean manualish = decision != null && (decision.equalsIgnoreCase("MANUAL")
                || decision.equalsIgnoreCase("MANUAL_REVIEW"));
        if (!rejectish && !manualish) {
            return new TranslationResult(TranslationClass.NOT_POLICY_SEMANTICS, parameter, canonicalId, condition,
                    decision, null, "Decision is not REJECT/MANUAL hard gate", false);
        }

        // Invert for PASS-when-OK DSL
        Map<String, Object> dsl = invertToPassExpression(canonicalId, parsed);
        TranslationClass cls = exactMap && parsed.simple
                ? TranslationClass.TRANSLATABLE_EXACT
                : TranslationClass.TRANSLATABLE_WITH_EXPLICIT_COMPATIBILITY;
        String note = rejectish
                ? "Frozen MATCH→REJECT inverted to DSL PASS-when-OK"
                : "Frozen MATCH→MANUAL inverted; MANUAL mapped as FAIL gate for shadow PASS/FAIL only";
        return new TranslationResult(cls, parameter, canonicalId, condition, decision, dsl, note, true);
    }

    public static List<TranslationResult> translateHardRules(List<Map<String, Object>> hardRules) {
        List<TranslationResult> out = new ArrayList<>();
        if (hardRules == null) return out;
        for (Map<String, Object> hr : hardRules) {
            out.add(translateHardRule(hr));
        }
        return out;
    }

    /** Translate constraint minBureauScore into DSL GTE. */
    public static TranslationResult translateMinBureauScore(Integer minBureau) {
        if (minBureau == null) {
            return new TranslationResult(TranslationClass.DEAD_UNUSED, "minBureauScore", null, null, null, null,
                    "Absent", false);
        }
        Map<String, Object> dsl = PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", minBureau));
        return new TranslationResult(TranslationClass.TRANSLATABLE_EXACT, "minBureauScore", "bureau.score",
                "GTE:" + minBureau, "CONSTRAINT", dsl,
                "Constraint → DSL GTE(bureau.score, min)", false);
    }

    private static Map<String, Object> invertToPassExpression(String canonicalId, ParsedCond parsed) {
        Object left = PolicyDsl.metric(canonicalId);
        Object right = parsed.rightConst;
        // Hard MATCH means bad. PASS expression = NOT(hardCondition) ≈ inverted op
        return switch (parsed.op) {
            case "GTE" -> PolicyDsl.lt(left, right);   // NOT(v>=x) => v<x ... wait
            // If hard is GTE:x REJECT when v>=x → PASS when v<x → LT
            case "GT" -> PolicyDsl.lte(left, right);
            case "LTE" -> PolicyDsl.gt(left, right);
            case "LT" -> PolicyDsl.gte(left, right);  // REJECT when v<x → PASS when v>=x
            case "EQ" -> PolicyDsl.ne(left, right);
            case "NE" -> PolicyDsl.eq(left, right);
            case "BETWEEN" -> PolicyDsl.not(PolicyDsl.between(left, parsed.betweenA, parsed.betweenB));
            default -> PolicyDsl.not(PolicyDsl.op(parsed.op, left, right));
        };
    }

    private record ParsedCond(String op, Object rightConst, Object betweenA, Object betweenB, boolean simple) {}

    private static ParsedCond parseCondition(String condition) {
        String c = condition.trim();
        int first = c.indexOf(':');
        if (first < 0) return null;
        String op = c.substring(0, first).trim().toUpperCase(Locale.ROOT);
        String rest = c.substring(first + 1).trim();
        if ("BETWEEN".equals(op)) {
            int mid = rest.indexOf(':');
            if (mid < 0) return null;
            Object a = constValue(rest.substring(0, mid).trim());
            Object b = constValue(rest.substring(mid + 1).trim());
            return new ParsedCond(op, null, a, b, true);
        }
        if (!List.of("GTE", "GT", "LTE", "LT", "EQ", "NE", "NEQ").contains(op)) {
            return null;
        }
        if ("NEQ".equals(op)) op = "NE";
        return new ParsedCond(op, constValue(rest), null, null, true);
    }

    private static Object constValue(String rest) {
        try {
            return Map.of("const", new BigDecimal(rest));
        } catch (Exception e) {
            return Map.of("const", rest);
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
