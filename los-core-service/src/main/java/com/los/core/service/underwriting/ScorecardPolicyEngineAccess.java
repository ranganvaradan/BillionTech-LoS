package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Public bridge to package-private {@link ScorecardPolicyEngine} helpers for frozen evaluation (C5.1).
 */
public final class ScorecardPolicyEngineAccess {

    private ScorecardPolicyEngineAccess() {
    }

    public static BigDecimal resolve(
            String source, String parameter, LoanApplication app, EffectiveUnderwritingContext ctx) {
        return ScorecardPolicyEngine.resolve(source, parameter, app, ctx);
    }

    public static BigDecimal resolveComputed(
            String parameter,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        return ScorecardPolicyEngine.resolveComputed(parameter, parameterDefs, app, ctx);
    }

    public static boolean dependencyGroupMatches(
            Object dependsOn,
            Map<String, Map<String, Object>> parameterDefs,
            LoanApplication app,
            EffectiveUnderwritingContext ctx) {
        return ScorecardPolicyEngine.dependencyGroupMatches(dependsOn, parameterDefs, app, ctx);
    }

    public static boolean conditionMatchesWithRef(
            String condition, BigDecimal value, LoanApplication app, EffectiveUnderwritingContext ctx) {
        return ScorecardPolicyEngine.conditionMatchesWithRef(condition, value, app, ctx);
    }

    public static String hardRuleFailureMessage(
            String parameter, String condition, BigDecimal value, String reason) {
        return ScorecardPolicyEngine.hardRuleFailureMessage(parameter, condition, value, reason);
    }
}
