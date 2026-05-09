package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingEvaluation;
import com.los.core.repository.UnderwritingEvaluationRepository;
import com.los.core.repository.UnderwritingScorecardRepository;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnderwritingEvaluationService {

    private final UnderwritingEvaluationRepository repository;
    private final UnderwritingScorecardRepository scorecardRepository;

    @Transactional
    public UnderwritingEvaluation record(
            UUID applicationId,
            MultiRuleEvalResult multi,
            EffectiveUnderwritingContext ctx,
            String evaluatedBy) {
        return record(applicationId, multi, ctx, evaluatedBy, null, null);
    }

    @Transactional
    public UnderwritingEvaluation record(
            UUID applicationId,
            MultiRuleEvalResult multi,
            EffectiveUnderwritingContext ctx,
            String evaluatedBy,
            UUID scorecardId,
            List<Map<String, Object>> parameterResults) {
        List<Map<String, Object>> rules = multi.perRule().stream()
                .map(this::perRuleToMap)
                .collect(Collectors.toList());
        Map<String, Object> src = new HashMap<>();
        src.put("bureauScoreSource", ctx.bureauSource());
        src.put("incomeSource", ctx.incomeSource());
        src.put("kycSource", ctx.kycSource());
        Map<String, Object> eff = new HashMap<>();
        eff.putAll(ctx.toMap());
        UnderwritingEvaluation e = UnderwritingEvaluation.builder()
                .applicationId(applicationId)
                .evaluatedAt(Instant.now())
                .aggregateDecision(multi.aggregateCreditDecision())
                .aggregateScore(multi.aggregateRiskScore())
                .effectiveValuesJson(eff)
                .ruleResultsJson(rules)
                .selectedSourceJson(new HashMap<>(src))
                .scorecardId(scorecardId)
                .parameterResultsJson(parameterResults != null ? parameterResults : List.of())
                .evaluatedBy(evaluatedBy)
                .build();
        return repository.save(e);
    }

    private Map<String, Object> perRuleToMap(MultiRuleEvalResult.PerRuleEval p) {
        Map<String, Object> m = new HashMap<>();
        m.put("ruleId", p.ruleId());
        m.put("ruleName", p.ruleName());
        m.put("policyDecision", p.policyDecision());
        m.put("creditDecision", p.creditDecision());
        m.put("riskScore", p.riskScore());
        m.put("reasons", p.reasons() != null ? p.reasons() : List.of());
        m.put("kind", p.kind());
        m.put("matchedConditions", p.matchedConditions() != null ? p.matchedConditions() : Map.of());
        m.put("sourceValuesUsed", p.sourceValuesUsed() != null ? p.sourceValuesUsed() : Map.of());
        return m;
    }

    public java.util.Optional<UnderwritingEvaluation> findLatest(LoanApplication app) {
        return repository.findTopByApplicationIdOrderByEvaluatedAtDesc(app.getId());
    }

    /**
     * API shape for {@link com.los.core.model.dto.response.ApplicationResponse#getLatestUnderwritingEvaluation()}.
     * Enriches with scorecard name/version from DB when {@code scorecardId} is set.
     */
    public Map<String, Object> toApiMap(UnderwritingEvaluation e) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", e.getId().toString());
        m.put("evaluatedAt", e.getEvaluatedAt() != null ? e.getEvaluatedAt().toString() : null);
        m.put("aggregateDecision", e.getAggregateDecision());
        m.put("aggregateScore", e.getAggregateScore());
        m.put("effectiveValues", e.getEffectiveValuesJson());
        m.put("ruleResults", e.getRuleResultsJson());
        m.put("selectedSources", e.getSelectedSourceJson());
        m.put("evaluatedBy", e.getEvaluatedBy());
        m.put("scorecardId", e.getScorecardId() != null ? e.getScorecardId().toString() : null);
        m.put("parameterResults", e.getParameterResultsJson());
        if (e.getScorecardId() != null) {
            scorecardRepository.findById(e.getScorecardId()).ifPresent(sc -> {
                m.put("scorecardName", sc.getName());
                m.put("scorecardVersion", sc.getVersion());
                m.put("scorecardPriority", sc.getPriority());
                m.put("scorecardBorrowerType", sc.getBorrowerType());
                m.put("scorecardLoanProduct", sc.getLoanProduct());
                m.put("scorecardMinAmount", sc.getMinAmount() != null ? sc.getMinAmount().toPlainString() : null);
                m.put("scorecardMaxAmount", sc.getMaxAmount() != null ? sc.getMaxAmount().toPlainString() : null);
                m.put("scorecardGeography", sc.getGeography());
            });
        }
        return m;
    }
}
