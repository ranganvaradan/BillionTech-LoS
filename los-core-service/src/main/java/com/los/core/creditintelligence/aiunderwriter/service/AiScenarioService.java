package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AiOutputType;
import com.los.core.creditintelligence.aiunderwriter.domain.AiScenarioRequest;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiScenario;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.CiDecisionStrategy;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Scenario what-if: Decision Engine computes; AI explains. Stored separate from canonical.
 */
@Service
public class AiScenarioService {

    private final CreditIntelligenceProperties properties;
    private final AiUnderwritingStore store;
    private final ShadowDecisionEngine decisionEngine;

    public AiScenarioService() {
        this(new CreditIntelligenceProperties(), new AiUnderwritingStore(), new ShadowDecisionEngine());
    }

    public AiScenarioService(
            CreditIntelligenceProperties properties,
            AiUnderwritingStore store,
            ShadowDecisionEngine decisionEngine) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.store = store != null ? store : new AiUnderwritingStore();
        this.decisionEngine = decisionEngine != null ? decisionEngine : new ShadowDecisionEngine();
    }

    public Map<String, Object> runScenario(
            AiScenarioRequest request,
            DecisionRuntimeInput baseInput,
            CiDecisionStrategy strategy,
            Map<String, Object> canonicalRecommendationSnapshot) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authoritative", false);
        out.put("outputMarker", "AI_SUGGESTION");
        out.put("demoUrlUsed", false);

        if (!properties.getAiUnderwriter().isEnabled()
                || !properties.getAiUnderwriter().isScenarioEnabled()) {
            out.put("status", "AI_ASSISTANCE_UNAVAILABLE");
            out.put("failureCode", "AI_ASSISTANCE_UNAVAILABLE");
            return out;
        }

        UUID tenantId = request.tenantId();
        DecisionRuntimeInput mutated = mutateInput(baseInput, request);
        CiDecisionStrategy strat = strategy != null
                ? strategy
                : DecisionStrategyFactory.p2ValidationStrategyV1(tenantId);

        CiCreditRecommendation scenarioRec = decisionEngine.recommend(strat, mutated);

        Map<String, Object> deterministic = new LinkedHashMap<>();
        deterministic.put("outcome", scenarioRec.getRecommendationOutcome());
        deterministic.put("amount", scenarioRec.getRecommendedAmount());
        deterministic.put("tenureMonths", scenarioRec.getRecommendedTenureMonths());
        deterministic.put("finalRate", scenarioRec.getRecommendedFinalRate());
        deterministic.put("deterministicDecisionHash", scenarioRec.getDeterministicDecisionHash());
        deterministic.put("authoritative", false);
        deterministic.put("shadowOnly", true);
        deterministic.put("scenarioOnly", true);

        String explanation = "AI-generated — non-authoritative scenario explanation. "
                + "Deterministic Decision Engine produced outcome="
                + scenarioRec.getRecommendationOutcome()
                + " amount=" + scenarioRec.getRecommendedAmount()
                + " tenure=" + scenarioRec.getRecommendedTenureMonths()
                + ". Canonical recommendation was not overwritten.";

        CiAiUnderwritingSuggestion suggestion = CiAiUnderwritingSuggestion.builder()
                .tenantId(tenantId)
                .applicationId(request.applicationId())
                .analysisRequestId(request.analysisRequestId())
                .type(AiOutputType.SCENARIO.name())
                .title("Scenario analysis")
                .content(explanation)
                .structuredPayload(new LinkedHashMap<>(Map.of(
                        "deterministicResult", deterministic,
                        "canonicalSnapshot", canonicalRecommendationSnapshot == null
                                ? Map.of() : canonicalRecommendationSnapshot,
                        "overwritesCanonical", false
                )))
                .limitations(new ArrayList<>(List.of("AI_SUGGESTION", "scenario separate from canonical")))
                .evidenceRefs(new ArrayList<>(List.of(Map.of("kind", "DECISION_ENGINE_SCENARIO",
                        "reference", scenarioRec.getDeterministicDecisionHash() == null
                                ? "" : scenarioRec.getDeterministicDecisionHash()))))
                .outputMarker("AI_SUGGESTION")
                .authoritative(false)
                .humanReviewRequired(true)
                .status(SuggestionStatus.PENDING_REVIEW.name())
                .groundingStatus("GROUNDED")
                .promptVersion("ALTERNATE_STRUCTURE_V1@1")
                .build();
        store.saveSuggestion(suggestion);

        Map<String, Object> reqPayload = new LinkedHashMap<>();
        reqPayload.put("loanAmount", request.loanAmount());
        reqPayload.put("tenureMonths", request.tenureMonths());
        reqPayload.put("collateral", request.collateral());

        CiAiScenario scenario = CiAiScenario.builder()
                .tenantId(tenantId)
                .applicationId(request.applicationId())
                .analysisRequestId(request.analysisRequestId())
                .suggestionId(suggestion.getId())
                .requestPayload(reqPayload)
                .deterministicResult(deterministic)
                .aiExplanation(explanation)
                .status("COMPUTED")
                .build();
        store.saveScenario(scenario);

        out.put("status", "COMPUTED");
        out.put("scenarioId", scenario.getId());
        out.put("suggestionId", suggestion.getId());
        out.put("deterministicResult", deterministic);
        out.put("aiExplanation", explanation);
        out.put("canonicalUnchanged", true);
        out.put("engine", "ShadowDecisionEngine");
        return out;
    }

    private DecisionRuntimeInput mutateInput(DecisionRuntimeInput base, AiScenarioRequest request) {
        DecisionRuntimeInput.Builder b = DecisionRuntimeInput.builder()
                .tenantId(base.tenantId())
                .lenderId(base.lenderId())
                .productCode(base.productCode())
                .applicationId(base.applicationId())
                .evaluationContextId(base.evaluationContextId())
                .policyEvaluationId(base.policyEvaluationId())
                .policyPackageId(base.policyPackageId())
                .factSnapshotId(base.factSnapshotId())
                .metricResultSetId(base.metricResultSetId())
                .reconciliationResultSetId(base.reconciliationResultSetId())
                .scoreResultIds(base.scoreResultIds())
                .creditEvidenceSummaryId(base.creditEvidenceSummaryId())
                .facts(base.facts())
                .metrics(base.metrics())
                .reconciliations(base.reconciliations())
                .policyParameters(base.policyParameters())
                .applicationFields(base.applicationFields())
                .policyOverallOutcome(base.policyOverallOutcome())
                .policyRuleResults(base.policyRuleResults())
                .scoreResult(base.scoreResult())
                .requestedAmount(request.loanAmount() != null ? request.loanAmount() : base.requestedAmount())
                .requestedTenureMonths(request.tenureMonths() != null
                        ? request.tenureMonths() : base.requestedTenureMonths())
                .clock(base.clock())
                .metadata(base.metadata());

        if (request.collateral() != null && !request.collateral().isEmpty()) {
            Map<String, Object> metrics = new LinkedHashMap<>(base.metrics());
            Object value = request.collateral().get("value");
            if (value != null) {
                metrics.put("collateral.value", Map.of("value", value, "dataStatus", "AVAILABLE"));
            }
            b.metrics(metrics);
        }
        return b.build();
    }
}
