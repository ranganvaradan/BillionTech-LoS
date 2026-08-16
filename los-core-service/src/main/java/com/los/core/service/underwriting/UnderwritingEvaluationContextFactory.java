package com.los.core.service.underwriting;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalCompatibilityRegistry;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapts Scorecard / Underwriting application context into the shared spine
 * {@link EvaluationContext}. Wave-3: projects exact GACAT IDs; optional explicit asOf
 * (never invents wall-clock).
 */
public final class UnderwritingEvaluationContextFactory {

    private UnderwritingEvaluationContextFactory() {}

    public static EvaluationContext forUnderwriting(LoanApplication app, EffectiveUnderwritingContext uw) {
        return build(EvaluationMode.UNDERWRITING, app, uw, null, null);
    }

    public static EvaluationContext forUnderwriting(
            LoanApplication app, EffectiveUnderwritingContext uw, LocalDate evaluationAsOf) {
        return build(EvaluationMode.UNDERWRITING, app, uw, evaluationAsOf, null);
    }

    public static EvaluationContext forPolicyTestParity(LoanApplication app, EffectiveUnderwritingContext uw) {
        return build(EvaluationMode.POLICY_TEST, app, uw, LocalDate.of(2026, 8, 1), null);
    }

    /**
     * @param preloadedExactFacts optional facts already keyed by exact GACAT / collections
     *                            (e.g. from snapshot materialization).
     */
    public static EvaluationContext build(
            EvaluationMode mode,
            LoanApplication app,
            EffectiveUnderwritingContext uw,
            LocalDate evaluationAsOf,
            Map<String, Object> preloadedExactFacts) {
        Map<String, Object> mixed = new LinkedHashMap<>();
        if (preloadedExactFacts != null) {
            mixed.putAll(preloadedExactFacts);
        }
        if (uw != null) {
            mixed.put("bureau.score", uw.effectiveBureauScore());
            mixed.put("kyc.quality", uw.kycPassEffective() ? BigDecimal.ONE : BigDecimal.ZERO);
            if (uw.effectiveObligation() != null) {
                mixed.put("bureau.total_monthly_obligation", uw.effectiveObligation());
            }
            if (uw.scorecard() != null) {
                for (Map.Entry<String, BigDecimal> e : uw.scorecard().entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) {
                        continue;
                    }
                    String key = e.getKey().trim();
                    if (key.contains(".")) {
                        mixed.put(key, e.getValue());
                        continue;
                    }
                    ScorecardCanonicalFactorMapper.Binding bind = ScorecardCanonicalFactorMapper.resolve(key);
                    if (bind.canonicalParameterId() != null
                            && (ScorecardCanonicalFactorMapper.EXACT.equals(bind.mappingStatus())
                            || ScorecardCanonicalFactorMapper.SAFE_ALIAS.equals(bind.mappingStatus()))) {
                        mixed.put(bind.canonicalParameterId(), e.getValue());
                    }
                }
            }
        }
        if (app != null) {
            if (app.getRequestedAmount() != null) {
                mixed.put("application.requested_amount", app.getRequestedAmount());
            }
            if (app.getBureauScore() != null && (uw == null || uw.effectiveBureauScore() == 0)) {
                mixed.put("bureau.score", app.getBureauScore());
            }
        }

        Map<String, Object> facts = CanonicalCompatibilityRegistry.projectExactCanonicalFacts(mixed);

        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(mode)
                .evaluationAsOf(evaluationAsOf);
        if (evaluationAsOf == null) {
            b.entity("evaluationAsOfMissing", true);
            b.entity("evaluationAsOfGap", "Caller did not supply evaluationAsOf");
        }
        facts.forEach(b::fact);
        if (app != null) {
            b.applicationId(app.getId());
            if (app.getTenureMonths() != null) {
                b.input("application.tenure_months", app.getTenureMonths());
            }
        }
        b.entity("acquisitionSuccessDoesNotImplyValueAvailable", true);
        return b.build();
    }
}
