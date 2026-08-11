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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UnderwritingEvaluationService {

    private final UnderwritingEvaluationRepository repository;
    private final UnderwritingScorecardRepository scorecardRepository;
    private final DecisionConfigurationSnapshotBuilder snapshotBuilder;
    private final HistoricalDecisionExplanationService historicalDecisionExplanationService;

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
        return record(applicationId, multi, ctx, evaluatedBy, scorecardId, parameterResults, null);
    }

    /**
     * Persist evaluation with optional application binding metadata (no DB migration —
     * stored in selected_source_json for audit/reproducibility).
     */
    @Transactional
    public UnderwritingEvaluation record(
            UUID applicationId,
            MultiRuleEvalResult multi,
            EffectiveUnderwritingContext ctx,
            String evaluatedBy,
            UUID scorecardId,
            List<Map<String, Object>> parameterResults,
            LoanApplication application) {
        List<Map<String, Object>> rules = multi.perRule().stream()
                .map(this::perRuleToMap)
                .collect(Collectors.toList());
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("bureauScoreSource", ctx.bureauSource());
        src.put("incomeSource", ctx.incomeSource());
        src.put("kycSource", ctx.kycSource());
        src.put("productionAuthority", "LIVE_UW_PATH");
        src.put("allowCanonicalAuthority", false);
        src.put("scorecardId", scorecardId == null ? null : scorecardId.toString());
        src.put("ruleSetIds", multi.perRule() == null ? List.of() : multi.perRule().stream()
                .map(MultiRuleEvalResult.PerRuleEval::ruleId)
                .distinct()
                .collect(Collectors.toList()));
        if (ctx.scorecard() != null) {
            src.put("providerGapDefaultActive",
                    ctx.scorecard().containsKey("PROVIDER_GAP_DEFAULT_ACTIVE")
                            && ctx.scorecard().get("PROVIDER_GAP_DEFAULT_ACTIVE") != null
                            && ctx.scorecard().get("PROVIDER_GAP_DEFAULT_ACTIVE")
                            .compareTo(java.math.BigDecimal.ZERO) > 0);
            src.put("demoFallbackActive", ctx.scorecard().containsKey("DEMO_FALLBACK_ACTIVE"));
        }
        if (application != null) {
            src.put("applicationId", application.getId() == null ? applicationId.toString()
                    : application.getId().toString());
            src.put("workflowId", application.getWorkflowId() == null ? null
                    : application.getWorkflowId().toString());
            src.put("borrowerType", application.getBorrowerType());
            src.put("loanProduct", application.getLoanProduct());
            src.put("applicationNumber", application.getApplicationNumber());
        }
        Map<String, Object> eff = new HashMap<>();
        eff.putAll(ctx.toMap());
        Integer scorecardVersion = null;
        Map<String, Object> evidence = extractScorecardEvidence(multi);
        if (evidence != null && evidence.get("scorecardVersion") instanceof Number n) {
            scorecardVersion = n.intValue();
        } else if (scorecardId != null) {
            scorecardVersion = scorecardRepository.findById(scorecardId)
                    .map(com.los.core.model.entity.UnderwritingScorecard::getVersion)
                    .orElse(null);
        }
        if (evidence != null) {
            src.put("scorecardEvidence", evidence);
            src.put("scorecardVersion", scorecardVersion);
        }
        Map<String, Object> decisionSnapshot = snapshotBuilder.build(
                application, multi, ctx, scorecardId, scorecardVersion,
                parameterResults, evidence, evaluatedBy);
        src.put("decisionConfigurationSnapshot", decisionSnapshot);
        src.put("snapshotImmutable", true);
        UnderwritingEvaluation e = UnderwritingEvaluation.builder()
                .applicationId(applicationId)
                .evaluatedAt(Instant.now())
                .aggregateDecision(multi.aggregateCreditDecision())
                .aggregateScore(multi.aggregateRiskScore())
                .effectiveValuesJson(eff)
                .ruleResultsJson(rules)
                .selectedSourceJson(new HashMap<>(src))
                .scorecardId(scorecardId)
                .scorecardVersion(scorecardVersion)
                .scorecardEvidenceJson(evidence)
                .parameterResultsJson(parameterResults != null ? parameterResults : List.of())
                .decisionSnapshotJson(decisionSnapshot)
                .evaluatedBy(evaluatedBy)
                .build();
        return repository.save(e);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractScorecardEvidence(MultiRuleEvalResult multi) {
        if (multi == null || multi.perRule() == null) {
            return null;
        }
        for (MultiRuleEvalResult.PerRuleEval p : multi.perRule()) {
            if (p == null || p.matchedConditions() == null || p.matchedConditions().isEmpty()) {
                continue;
            }
            if ("SCORECARD".equalsIgnoreCase(p.kind())
                    || "STRUCTURED_SCORECARD".equalsIgnoreCase(String.valueOf(p.matchedConditions().get("engine")))) {
                return new LinkedHashMap<>(p.matchedConditions());
            }
        }
        return null;
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
        m.put("scorecardVersion", e.getScorecardVersion());
        m.put("scorecardEvidence", e.getScorecardEvidenceJson());
        m.put("parameterResults", e.getParameterResultsJson());
        m.put("decisionSnapshot", e.getDecisionSnapshotJson());
        // Historical explanation must not re-resolve today's active scorecard/rules/GACAT.
        Map<String, Object> historical = historicalDecisionExplanationService.explainFromSnapshot(e);
        m.put("historicalExplanation", historical);
        m.put("requiresCurrentConfig", historical.get("requiresCurrentConfig"));
        if (e.getDecisionSnapshotJson() != null) {
            Object sc = e.getDecisionSnapshotJson().get("scorecard");
            if (sc instanceof Map<?, ?> scm && scm.get("scorecardVersion") != null && e.getScorecardVersion() == null) {
                m.put("scorecardVersion", scm.get("scorecardVersion"));
            }
            // Optional display-only enrichment — never overrides snapshot math/decision
            if (e.getScorecardId() != null) {
                scorecardRepository.findById(e.getScorecardId()).ifPresent(card ->
                        m.put("scorecardNameDisplayOnly", card.getName()));
            }
        } else if (e.getScorecardId() != null) {
            scorecardRepository.findById(e.getScorecardId()).ifPresent(sc -> {
                m.put("scorecardName", sc.getName());
                if (e.getScorecardVersion() == null) {
                    m.put("scorecardVersion", sc.getVersion());
                }
                m.put("scorecardPriority", sc.getPriority());
                m.put("scorecardBorrowerType", sc.getBorrowerType());
                m.put("scorecardLoanProduct", sc.getLoanProduct());
                m.put("scorecardMinAmount", sc.getMinAmount() != null ? sc.getMinAmount().toPlainString() : null);
                m.put("scorecardMaxAmount", sc.getMaxAmount() != null ? sc.getMaxAmount().toPlainString() : null);
                m.put("scorecardGeography", sc.getGeography());
                m.put("scorecardStatus", sc.getStatus());
            });
        }
        return m;
    }

    /** Structural immutability: no service API mutates a finalized evaluation snapshot in place. */
    public void rejectSnapshotMutation(UUID evaluationId) {
        throw new UnsupportedOperationException(
                "DECISION_SNAPSHOT_IMMUTABLE: finalized underwriting evaluations cannot be mutated; re-underwrite creates a new evaluation");
    }
}
