package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.domain.CiPolicyVersion;
import com.los.core.creditintelligence.evaluation.domain.CiConfigFreeze;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import com.los.core.creditintelligence.evaluation.repository.CiConfigFreezeRepository;
import com.los.core.creditintelligence.evaluation.repository.CiEvaluationContextRepository;
import com.los.core.creditintelligence.repository.CiPolicyVersionRepository;
import com.los.core.creditintelligence.service.LegacyUnderwritingContextAdapter;
import com.los.core.model.entity.LoanApplication;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.underwriting.MultiRuleEvalResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure canonical evaluation driven solely by an immutable {@link CiEvaluationContext}.
 */
@Service
@RequiredArgsConstructor
public class PureCanonicalEvaluationService {

    private final CiEvaluationContextRepository evaluationContextRepository;
    private final CiConfigFreezeRepository configFreezeRepository;
    private final CiPolicyVersionRepository policyVersionRepository;
    private final LegacyUnderwritingContextAdapter adapter;
    private final FrozenPolicyExecutionAdapter frozenPolicyExecutionAdapter;
    private final DeterministicEvaluationHasher deterministicEvaluationHasher;

    public record PureEvalResult(Map<String, Object> outcomes, String deterministicEvaluationHash) {
    }

    @Transactional
    public PureEvalResult evaluate(UUID evaluationContextId) {
        CiEvaluationContext ctx = evaluationContextRepository.findById(evaluationContextId)
                .orElseThrow(() -> new IllegalArgumentException("EvaluationContext not found: " + evaluationContextId));
        return evaluate(ctx);
    }

    @Transactional
    public PureEvalResult evaluate(CiEvaluationContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("EvaluationContext is required");
        }

        LegacyUnderwritingContextAdapter.AdapterResult adapted = adapter.adapt(ctx.getFactSnapshotId());
        EffectiveUnderwritingContext underwritingCtx = adapted.context();
        LoanApplication stub = adapter.toLoanApplicationStub(ctx.getApplicationId(), adapted);
        String kycOutcome = adapted.kycOutcome();

        CiConfigFreeze freeze = configFreezeRepository.findById(ctx.getConfigFreezeId())
                .orElseThrow(() -> new IllegalStateException("Config freeze not found: " + ctx.getConfigFreezeId()));

        Map<String, Object> policyContent = Map.of();
        CiPolicyVersion policyVersion = null;
        if (ctx.getPolicyVersionId() != null) {
            policyVersion = policyVersionRepository.findById(ctx.getPolicyVersionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Policy version not found: " + ctx.getPolicyVersionId()));
            policyContent = policyVersion.getPolicyContent() != null
                    ? policyVersion.getPolicyContent()
                    : Map.of();
        }

        MultiRuleEvalResult multi = frozenPolicyExecutionAdapter.evaluateHardRules(
                stub, underwritingCtx, kycOutcome, policyContent);

        Map<String, Object> outcomes = new LinkedHashMap<>();
        outcomes.put("aggregatePolicyRecommendation", multi.aggregatePolicyRecommendation());
        outcomes.put("aggregateCreditDecision", multi.aggregateCreditDecision());
        outcomes.put("aggregateRiskScore", multi.aggregateRiskScore());
        outcomes.put("aggregateReasons", multi.aggregateReasons());
        outcomes.put("perRule", multi.perRule() != null
                ? multi.perRule().stream().map(this::perRuleMap).toList()
                : List.of());
        outcomes.put("kycOutcome", kycOutcome);
        outcomes.put("defaultedPaths", adapted.defaultedPaths());
        outcomes.put("configFreezeId", freeze.getId().toString());
        outcomes.put("configFreezeHash", freeze.getContentHash());
        if (policyVersion != null) {
            outcomes.put("policyVersionId", policyVersion.getId().toString());
            outcomes.put("policyContentHash", policyVersion.getContentHash());
        }
        outcomes.put("factSnapshotId", ctx.getFactSnapshotId().toString());
        outcomes.put("metricResultSetId",
                ctx.getMetricResultSetId() != null ? ctx.getMetricResultSetId().toString() : null);
        outcomes.put("reconciliationResultSetId",
                ctx.getReconciliationResultSetId() != null
                        ? ctx.getReconciliationResultSetId().toString()
                        : null);
        outcomes.put("evaluationAsOf", ctx.getEvaluationAsOf() != null ? ctx.getEvaluationAsOf().toString() : null);
        outcomes.put("clockInstant", ctx.getClockInstant() != null ? ctx.getClockInstant().toString() : null);
        outcomes.put("clockZone", ctx.getClockZone());

        String hash = deterministicEvaluationHasher.hashOutcomes(outcomes);
        ctx.setDeterministicEvaluationHash(hash);
        evaluationContextRepository.save(ctx);

        return new PureEvalResult(outcomes, hash);
    }

    private Map<String, Object> perRuleMap(MultiRuleEvalResult.PerRuleEval p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ruleId", p.ruleId());
        m.put("ruleName", p.ruleName());
        m.put("policyDecision", p.policyDecision());
        m.put("creditDecision", p.creditDecision());
        m.put("riskScore", p.riskScore());
        m.put("reasons", p.reasons());
        m.put("kind", p.kind());
        m.put("matchedConditions", p.matchedConditions());
        m.put("sourceValuesUsed", p.sourceValuesUsed());
        return m;
    }
}
