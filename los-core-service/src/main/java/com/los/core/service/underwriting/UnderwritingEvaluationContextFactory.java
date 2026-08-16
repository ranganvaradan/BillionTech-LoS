package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Adapts Scorecard / Underwriting application context into the shared spine
 * {@link EvaluationContext}. Does not invent a parallel fact model — maps known
 * UW values onto exact canonical parameter IDs as facts/inputs.
 */
public final class UnderwritingEvaluationContextFactory {

    private UnderwritingEvaluationContextFactory() {}

    public static EvaluationContext forUnderwriting(LoanApplication app, EffectiveUnderwritingContext uw) {
        return build(EvaluationMode.UNDERWRITING, app, uw);
    }

    public static EvaluationContext forPolicyTestParity(LoanApplication app, EffectiveUnderwritingContext uw) {
        return build(EvaluationMode.POLICY_TEST, app, uw);
    }

    private static EvaluationContext build(
            EvaluationMode mode, LoanApplication app, EffectiveUnderwritingContext uw) {
        EvaluationContext.Builder b = EvaluationContext.builder().mode(mode);
        if (uw != null) {
            b.fact("bureau.score", uw.effectiveBureauScore());
            b.fact("kyc.quality", uw.kycPassEffective() ? BigDecimal.ONE : BigDecimal.ZERO);
            if (uw.effectiveObligation() != null) {
                b.fact("bureau.total_monthly_obligation", uw.effectiveObligation());
            }
            if (uw.scorecard() != null) {
                for (Map.Entry<String, BigDecimal> e : uw.scorecard().entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) {
                        continue;
                    }
                    String key = e.getKey().trim();
                    if (key.contains(".")) {
                        b.fact(key, e.getValue());
                        continue;
                    }
                    ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(key);
                    if (bind.canonicalParameterId() != null
                            && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                            || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
                        b.fact(bind.canonicalParameterId(), e.getValue());
                    }
                }
            }
        }
        if (app != null) {
            if (app.getRequestedAmount() != null) {
                b.fact("application.requested_amount", app.getRequestedAmount());
            }
            if (app.getTenureMonths() != null) {
                b.input("application.tenure_months", app.getTenureMonths());
            }
            if (app.getBureauScore() != null && (uw == null || uw.effectiveBureauScore() == 0)) {
                b.fact("bureau.score", app.getBureauScore());
            }
        }
        return b.build();
    }
}
