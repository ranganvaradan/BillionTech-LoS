package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphNode;
import com.los.core.creditintelligence.policystudio.graph.PolicyGraphParticipation;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphNodeRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationEntity;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationRepository;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalResolutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared observational canonical evaluation of an exact frozen package.
 * Used by Policy Studio Application Test (presentation) and canonical shadow persist.
 * Does not write live decisions. Does not look up latest artifacts.
 */
@Service
@RequiredArgsConstructor
public class CanonicalObservationalEvaluationService {

    public static final String SERVICE = "CanonicalObservationalEvaluationService";

    private final CanonicalApplicationConfigurationRepository freezeRepository;
    private final CanonicalShadowContextFactory contextFactory;
    private final CanonicalShadowScorecardExecutor scorecardExecutor;
    private final CanonicalParameterExecutionService cpes;
    private final CiPolicyRuleGraphRepository graphRepository;
    private final CiPolicyRuleGraphNodeRepository nodeRepository;
    private final CreditIntelligenceProperties properties;

    public CanonicalObservationalEvaluation evaluate(UUID applicationId) {
        if (applicationId == null) {
            return CanonicalObservationalEvaluation.notExecutable(
                    null, "unresolved", null,
                    List.of(CanonicalShadowFailureCode.FROZEN_PACKAGE_NOT_RESOLVED.name()),
                    List.of(), List.of(), Map.of(), null);
        }
        Optional<CanonicalApplicationConfigurationEntity> freezeRow =
                freezeRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(
                        applicationId, CanonicalResolutionStatus.RESOLVED.name());
        if (freezeRow.isEmpty()) {
            return CanonicalObservationalEvaluation.notExecutable(
                    null, "unresolved", null,
                    List.of(CanonicalShadowFailureCode.FROZEN_PACKAGE_NOT_RESOLVED.name()),
                    List.of(), List.of(), Map.of(), null);
        }
        return evaluateFreeze(freezeRow.get());
    }

    public CanonicalObservationalEvaluation evaluateFreeze(CanonicalApplicationConfigurationEntity freezeRow) {
        CanonicalApplicationConfiguration freeze =
                CanonicalApplicationConfiguration.fromMap(freezeRow.getPackageJson());
        String hash = freezeRow.getIdentityHash();
        UUID rowId = freezeRow.getId();
        if (freeze == null || freeze.evaluationAsOf() == null) {
            return CanonicalObservationalEvaluation.notExecutable(
                    freeze, hash, rowId,
                    List.of(CanonicalShadowFailureCode.EVALUATION_AS_OF_MISSING.name()),
                    List.of(), List.of(), Map.of(), null);
        }
        if (freeze.policyDocumentId() == null || freeze.policyDocumentVersion() == null) {
            return CanonicalObservationalEvaluation.notExecutable(
                    freeze, hash, rowId,
                    List.of(CanonicalShadowFailureCode.POLICY_GRAPH_NOT_PINNED.name()),
                    List.of(), List.of(), Map.of(), null);
        }
        Optional<CiPolicyRuleGraph> graph = graphRepository.findByPolicyDocumentIdAndDocumentVersion(
                freeze.policyDocumentId(), freeze.policyDocumentVersion());
        if (graph.isEmpty()) {
            return CanonicalObservationalEvaluation.notExecutable(
                    freeze, hash, rowId,
                    List.of(CanonicalShadowFailureCode.POLICY_NOT_EXECUTABLE.name()),
                    List.of(), List.of(), Map.of(), null);
        }

        EvaluationContext spine = contextFactory.build(freeze, properties.getDefaultTenantId());
        List<CiPolicyRuleGraphNode> nodes = nodeRepository.findByGraphIdOrderBySortOrderAsc(graph.get().getId());
        List<CanonicalPolicyRuntime.RuleSpec> specs = new ArrayList<>();
        List<Map<String, Object>> ruleEvidence = new ArrayList<>();
        CanonicalPolicyRuntime runtime = new CanonicalPolicyRuntime(cpes);
        for (CiPolicyRuleGraphNode node : nodes) {
            boolean participates = PolicyGraphParticipation.participates(node);
            Map<String, Object> expr = node.getExpression() == null ? Map.of() : node.getExpression();
            CanonicalPolicyRuntime.RuleSpec spec = new CanonicalPolicyRuntime.RuleSpec(
                    node.getRuleKey() == null ? node.getId().toString() : node.getRuleKey(),
                    node.getContentHash(),
                    expr,
                    node.getOnMissing());
            CanonicalRuleResult rr = participates
                    ? runtime.evaluateRule(spec, spine, freeze.evaluationAsOf())
                    : skipped(node, freeze);
            if (participates) {
                specs.add(spec);
            }
            ruleEvidence.add(ruleRow(node, participates, rr, expr));
        }

        CanonicalPolicyResult policy = runtime.evaluate(new CanonicalPolicyRuntime.PolicyRequest(
                freeze.policyDocumentId().toString(),
                String.valueOf(freeze.policyDocumentVersion()),
                specs,
                spine,
                freeze.evaluationAsOf()));

        List<Map<String, Object>> parameterEvidence =
                CanonicalShadowParameterEvidence.rows(policy, freeze, spine, cpes);
        Map<String, Object> scorecard = scorecardExecutor.execute(freeze, spine);
        boolean scorecardBroken = Boolean.FALSE.equals(scorecard.get("executable"))
                && freeze.scorecardId() != null
                && !Boolean.TRUE.equals(scorecard.get("scorecardExplicitlyAbsent"));
        if (scorecardBroken) {
            return CanonicalObservationalEvaluation.notExecutable(
                    freeze, hash, rowId,
                    List.of(String.valueOf(scorecard.getOrDefault("reason",
                            CanonicalShadowFailureCode.SCORECARD_NOT_EXECUTABLE.name()))),
                    parameterEvidence, ruleEvidence, scorecard, policy);
        }

        FinalUnderwritingDecision.FinalOutcome band = bandOf(scorecard);
        boolean scorecardExplicitlyAbsent = Boolean.TRUE.equals(scorecard.get("scorecardExplicitlyAbsent"));
        var prec = PolicyScorecardPrecedence.combine(policy, band, null, scorecardExplicitlyAbsent);
        CanonicalShadowDecision decision = CanonicalShadowUnderwritingService.mapDecision(prec.outcome());
        return new CanonicalObservationalEvaluation(
                "COMPLETED",
                List.of(),
                freeze,
                hash,
                rowId,
                spine,
                policy,
                parameterEvidence,
                ruleEvidence,
                scorecard,
                prec,
                decision == null ? null : decision.name(),
                CanonicalObservationalEvaluation.zeroLookups());
    }

    static Map<String, Object> ruleRow(
            CiPolicyRuleGraphNode node, boolean participates, CanonicalRuleResult rr, Map<String, Object> expr) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ruleId", node.getRuleKey());
        row.put("ruleVersion", node.getContentHash());
        row.put("ruleState", node.getRuleType());
        row.put("participates", participates);
        row.put("operands", rr.canonicalParameterIds());
        row.put("parameterIds", rr.canonicalParameterIds());
        row.put("operator", rr.operator());
        row.put("threshold", rr.expectedOrThreshold());
        row.put("parameterValues", rr.actualExecution() == null ? null : rr.actualExecution().value());
        row.put("result", rr.result() == null ? null : rr.result().name());
        row.put("reason", rr.reason());
        row.put("missingDataStatus", rr.result() == CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT
                ? "MISSING" : "OK");
        row.put("onTrue", node.getOnTrue());
        row.put("onFalse", node.getOnFalse());
        row.put("onMissing", node.getOnMissing());
        row.put("expressionPresent", expr != null && !expr.isEmpty());
        if (!participates) {
            Object disposition = node.getMetadata() == null ? null : node.getMetadata().get("disposition");
            row.put("disposition", disposition);
            row.put("deferred", "DEFERRED_SOURCE_NOT_PROVEN".equalsIgnoreCase(
                    disposition == null ? "" : String.valueOf(disposition)));
        }
        return row;
    }

    static CanonicalRuleResult skipped(CiPolicyRuleGraphNode node, CanonicalApplicationConfiguration freeze) {
        return new CanonicalRuleResult(
                node.getRuleKey(),
                node.getContentHash(),
                List.of(),
                null,
                null,
                null,
                CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT,
                freeze.evaluationAsOf(),
                "NON_PARTICIPATING",
                Map.of("participates", false),
                List.of());
    }

    static FinalUnderwritingDecision.FinalOutcome bandOf(Map<String, Object> scorecard) {
        Object raw = scorecard.get("bandOutcome");
        if (raw == null) {
            return FinalUnderwritingDecision.FinalOutcome.REFER;
        }
        try {
            return FinalUnderwritingDecision.FinalOutcome.valueOf(String.valueOf(raw));
        } catch (Exception e) {
            return FinalUnderwritingDecision.FinalOutcome.REFER;
        }
    }
}
