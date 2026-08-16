package com.los.core.creditintelligence.policystudio.runtime.ownership;

import com.los.core.creditintelligence.policystudio.runtime.FrozenToCanonicalDslTranslator;
import com.los.core.creditintelligence.policystudio.runtime.PolicyRuntimeShadowParity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scorecard hard-rule ownership: classify as POLICY_ELIGIBILITY, translate for CPR shadow,
 * staged disable via {@link DecisionOwnershipFlags#scorecardHardRulesShadowOnly()}.
 */
public final class ScorecardHardRuleOwnership {

    private ScorecardHardRuleOwnership() {}

    public enum HardRuleKind {
        POLICY_ELIGIBILITY,
        SCORE_BAND_BEHAVIOR,
        DUPLICATE_OF_POLICY,
        UNKNOWN
    }

    public record ClassifiedHardRule(
            HardRuleKind kind,
            Map<String, Object> hardRule,
            FrozenToCanonicalDslTranslator.TranslationResult translation,
            String note
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", kind.name());
            m.put("hardRule", hardRule);
            m.put("translation", translation == null ? null : translation.toMap());
            m.put("note", note);
            return m;
        }
    }

    public static ClassifiedHardRule classify(Map<String, Object> hardRule) {
        if (hardRule == null || hardRule.isEmpty()) {
            return new ClassifiedHardRule(HardRuleKind.UNKNOWN, hardRule, null, "Empty");
        }
        String decision = hardRule.get("decision") == null ? "" : String.valueOf(hardRule.get("decision"));
        boolean eligibility = decision.equalsIgnoreCase("REJECT")
                || decision.equalsIgnoreCase("REJECTED")
                || decision.equalsIgnoreCase("MANUAL")
                || decision.equalsIgnoreCase("MANUAL_REVIEW");
        if (!eligibility) {
            return new ClassifiedHardRule(HardRuleKind.SCORE_BAND_BEHAVIOR, hardRule, null,
                    "Non-knockout decision — not policy eligibility");
        }
        var tr = FrozenToCanonicalDslTranslator.translateHardRule(hardRule);
        HardRuleKind kind = tr.classification()
                == FrozenToCanonicalDslTranslator.TranslationClass.NOT_TRANSLATABLE
                ? HardRuleKind.POLICY_ELIGIBILITY
                : HardRuleKind.DUPLICATE_OF_POLICY;
        return new ClassifiedHardRule(kind, hardRule, tr,
                "Scorecard hard rule is POLICY_ELIGIBILITY; target owner=Policy/CPR");
    }

    public static List<ClassifiedHardRule> classifyAll(List<Map<String, Object>> rules) {
        List<ClassifiedHardRule> out = new ArrayList<>();
        if (rules == null) return out;
        for (Map<String, Object> r : rules) out.add(classify(r));
        return out;
    }

    public static Map<String, Object> shadowSummary(List<ClassifiedHardRule> classified) {
        Map<String, Object> m = new LinkedHashMap<>();
        int total = classified.size();
        long policy = classified.stream().filter(c ->
                c.kind() == HardRuleKind.POLICY_ELIGIBILITY
                        || c.kind() == HardRuleKind.DUPLICATE_OF_POLICY).count();
        long migratedShadow = classified.stream().filter(c ->
                c.translation() != null
                        && c.translation().dslExpression() != null).count();
        m.put("scorecardHardRulesFound", total);
        m.put("scorecardHardRulesPolicyEligibility", policy);
        m.put("scorecardHardRulesMigratedOrShadowed", migratedShadow);
        m.put("compatibilityFlag", "DecisionOwnershipFlags.scorecardHardRulesShadowOnly");
        m.put("flagDefault", false);
        m.put("parityVocabulary", PolicyRuntimeShadowParity.ParityClass.class.getSimpleName());
        return m;
    }
}
