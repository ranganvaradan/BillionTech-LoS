package com.los.core.creditintelligence.aiunderwriter;

import com.los.core.creditintelligence.aiunderwriter.domain.AiScenarioRequest;
import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import com.los.core.creditintelligence.aiunderwriter.domain.FactCandidateDesign;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import com.los.core.creditintelligence.aiunderwriter.service.AiGroundingValidator;
import com.los.core.creditintelligence.aiunderwriter.service.AiOutputComparisonService;
import com.los.core.creditintelligence.aiunderwriter.service.AiPromptTemplateService;
import com.los.core.creditintelligence.aiunderwriter.service.AiReviewService;
import com.los.core.creditintelligence.aiunderwriter.service.AiScenarioService;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwriterViewBuilder;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwritingContextBuilder;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwritingProvider;
import com.los.core.creditintelligence.aiunderwriter.service.AiUnderwritingService;
import com.los.core.creditintelligence.aiunderwriter.service.IdempotencyKeyFactory;
import com.los.core.creditintelligence.aiunderwriter.service.PiiMinimizer;
import com.los.core.creditintelligence.aiunderwriter.service.StubAiUnderwritingProvider;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.decision.domain.CiCreditRecommendation;
import com.los.core.creditintelligence.decision.domain.DecisionRuntimeInput;
import com.los.core.creditintelligence.decision.fixture.DecisionStrategyFactory;
import com.los.core.creditintelligence.decision.service.ShadowDecisionEngine;
import com.los.core.creditintelligence.validation.service.CreditEvidenceViewBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A1 Assistive AI Underwriter — §32 validation suite.
 */
class AiUnderwriterA1Test {

    private UUID tenant;
    private UUID otherTenant;
    private UUID applicationId;
    private UUID evalCtx;
    private UUID policyEval;
    private UUID recommendationId;
    private CreditIntelligenceProperties props;
    private AiUnderwritingStore store;
    private AiUnderwritingService service;
    private AiReviewService reviewService;
    private AiScenarioService scenarioService;
    private AiGroundingValidator groundingValidator;
    private AiUnderwritingContextBuilder contextBuilder;
    private PiiMinimizer piiMinimizer;
    private Map<String, Object> evidence;
    private Map<String, Object> recommendation;

    @BeforeEach
    void setUp() {
        tenant = UUID.fromString("00000000-0000-0000-0000-000000000001");
        otherTenant = UUID.fromString("00000000-0000-0000-0000-000000000099");
        applicationId = UUID.randomUUID();
        evalCtx = UUID.randomUUID();
        policyEval = UUID.randomUUID();
        recommendationId = UUID.randomUUID();

        props = new CreditIntelligenceProperties();
        props.getAiUnderwriter().setEnabled(true);
        props.getAiUnderwriter().setCamDraftEnabled(true);
        props.getAiUnderwriter().setScenarioEnabled(true);
        props.getAiUnderwriter().setProvider("stub");

        store = new AiUnderwritingStore();
        groundingValidator = new AiGroundingValidator();
        contextBuilder = new AiUnderwritingContextBuilder();
        piiMinimizer = new PiiMinimizer();
        service = new AiUnderwritingService(
                props, store, contextBuilder, new AiPromptTemplateService(store),
                new IdempotencyKeyFactory(), groundingValidator,
                new StubAiUnderwritingProvider(), null);
        reviewService = new AiReviewService(store);
        scenarioService = new AiScenarioService(props, store, new ShadowDecisionEngine());

        evidence = sampleEvidence();
        recommendation = new LinkedHashMap<>();
        recommendation.put("outcome", "APPROVE_WITH_CONDITIONS");
        recommendation.put("amount", new BigDecimal("750000"));
        recommendation.put("tenureMonths", 18);
        recommendation.put("reasonCodes", List.of("XSRC_GST_BANK_TURNOVER"));
    }

    private Map<String, Object> sampleEvidence() {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("Identity", Map.of("caseCode", "CASE_A_STRONG", "dataOrigin", "CANONICAL"));
        view.put("GST", Map.of("available", true, "turnover", 84000000));
        view.put("Banking", Map.of("available", true, "turnover", 65000000, "emi", 20000, "abb", 300000));
        view.put("Bureau", Map.of("available", true, "emi", 18000));
        view.put("ITR", Map.of("available", true, "turnover", 80000000));
        view.put("TurnoverTriangulation", Map.of("gst", 84000000, "bank", 65000000, "itr", 80000000));
        view.put("MaterialReconciliations", List.of(Map.of(
                "code", "XSRC_GST_BANK_TURNOVER",
                "outcome", "REFER",
                "detail", Map.of("variancePct", 24)
        )));
        view.put("OpenInvestigationQuestions", List.of(Map.of(
                "question", "GST turnover exceeds adjusted bank credits by 24%. Are collections routed through another business account?",
                "evidenceRefs", List.of("XSRC_GST_BANK_TURNOVER")
        )));
        view.put("EvidenceStrength", Map.of("grade", "STRONG", "score", 85));
        return view;
    }

    private Map<String, Object> analyze(List<String> types) {
        Map<String, Object> snapshot = new LinkedHashMap<>(recommendation);
        return service.analyze(
                tenant, applicationId, evalCtx, policyEval, recommendationId,
                types, evidence, Map.of("overallOutcome", "REFER",
                        "ruleResults", List.of(Map.of("ruleId", "XSRC_GST_BANK_TURNOVER", "outcome", "REFER"))),
                Map.of("Limit", Map.of("amount", 750000)),
                recommendation,
                null,
                "tester",
                snapshot
        );
    }

    @Test
    void groundedSummary() {
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        assertThat(result.get("status")).isEqualTo("COMPLETED");
        assertThat(result.get("authoritative")).isEqualTo(false);
        assertThat(result.get("demoUrlUsed")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        assertThat(suggestions).isNotEmpty();
        Map<String, Object> s = suggestions.get(0);
        assertThat(s.get("outputMarker")).isEqualTo("AI_SUGGESTION");
        assertThat(s.get("authoritative")).isEqualTo(false);
        assertThat(s.get("humanReviewRequired")).isEqualTo(true);
        assertThat(s.get("status")).isEqualTo(SuggestionStatus.PENDING_REVIEW.name());
        assertThat(String.valueOf(s.get("content"))).contains("750000");
        assertThat(s.get("modelConfidence")).isNotNull();
        assertThat(s.get("groundingCoverage")).isNotNull();
        assertThat(s.get("evidenceCompleteness")).isNotNull();
    }

    @Test
    void inventedNumericRejected() {
        AiUnderwritingContext ctx = contextBuilder.build(
                tenant, applicationId, evalCtx, policyEval, recommendationId,
                evidence, Map.of(), Map.of(), recommendation, null);
        AiUnderwritingProvider.RawSuggestion raw = new AiUnderwritingProvider.RawSuggestion(
                "NARRATIVE", "bad",
                "Turnover is 999999999 which is invented.",
                Map.of(), List.of(), List.of(), List.of(), "UNDERWRITING_SUMMARY_V1@1", 0.9);
        AiGroundingValidator.GroundingResult g = groundingValidator.validate(raw, ctx);
        assertThat(g.grounded()).isFalse();
        assertThat(g.status()).isEqualTo(SuggestionStatus.REJECTED_GROUNDING_FAILURE.name());
        assertThat(g.failures().stream().anyMatch(f -> f.contains("Invented numeric"))).isTrue();
    }

    @Test
    void inventedRuleIdRejected() {
        AiUnderwritingContext ctx = contextBuilder.build(
                tenant, applicationId, evalCtx, policyEval, recommendationId,
                evidence, Map.of(), Map.of(), recommendation, null);
        AiUnderwritingProvider.RawSuggestion raw = new AiUnderwritingProvider.RawSuggestion(
                "EXPLANATION", "bad",
                "Rule FAKE_RULE_XYZ_99 caused decline.",
                Map.of("inventedRuleId", "FAKE_RULE_XYZ_99"),
                List.of(), List.of(), List.of(), "POLICY_EXPLANATION_V1@1", 0.9);
        AiGroundingValidator.GroundingResult g = groundingValidator.validate(raw, ctx);
        assertThat(g.grounded()).isFalse();
        assertThat(g.failures().stream().anyMatch(f -> f.toLowerCase().contains("rule"))).isTrue();
    }

    @Test
    void inventedNumericRejectedThroughService() {
        service.setProviderOverride((context, types, promptVersions) ->
                new AiUnderwritingProvider.ProviderResponse(true, null, "stub", "adv", "1",
                        List.of(new AiUnderwritingProvider.RawSuggestion(
                                "NARRATIVE", "bad",
                                "Revenue suddenly became 111111111.",
                                Map.of(), List.of(), List.of(), List.of(),
                                "UNDERWRITING_SUMMARY_V1@1", 0.9))));
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        assertThat(suggestions.get(0).get("status"))
                .isEqualTo(SuggestionStatus.REJECTED_GROUNDING_FAILURE.name());
    }

    @Test
    void aiUnavailable() {
        service.setProviderOverride((context, types, promptVersions) ->
                AiUnderwritingProvider.ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE"));
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        assertThat(result.get("status")).isEqualTo("AI_ASSISTANCE_UNAVAILABLE");
        assertThat(result.get("failureCode")).isEqualTo("AI_ASSISTANCE_UNAVAILABLE");
        assertThat(result.get("demoUrlUsed")).isEqualTo(false);
    }

    @Test
    void disabledFlagReturnsUnavailableWithoutBlocking() {
        props.getAiUnderwriter().setEnabled(false);
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        assertThat(result.get("status")).isEqualTo("AI_ASSISTANCE_UNAVAILABLE");
        assertThat(result.get("demoUrlUsed")).isEqualTo(false);
    }

    @Test
    void noProductionFactOrRecommendationMutation() {
        Map<String, Object> snapshot = new LinkedHashMap<>(recommendation);
        BigDecimal beforeAmount = new BigDecimal("750000");
        Map<String, Object> result = service.analyze(
                tenant, applicationId, evalCtx, policyEval, recommendationId,
                List.of("NARRATIVE", "ALTERNATE_STRUCTURE_SUGGESTION"),
                evidence, Map.of(), Map.of(), recommendation, null, "tester", snapshot);
        assertThat(result.get("recommendationUnchanged")).isEqualTo(true);
        assertThat(recommendation.get("amount")).isEqualTo(beforeAmount);
        assertThat(snapshot.get("amount")).isEqualTo(beforeAmount);
        assertThat(result.get("factCandidateActivated")).isEqualTo(false);
        assertThat(FactCandidateDesign.isPromotionAllowed()).isFalse();
        assertThat(evidence.get("GST")).isEqualTo(Map.of("available", true, "turnover", 84000000));
    }

    @Test
    void scenarioUsesDeterministicDecisionEngine() {
        DecisionRuntimeInput base = DecisionRuntimeInput.builder()
                .tenantId(tenant)
                .applicationId(applicationId)
                .evaluationContextId(evalCtx)
                .policyEvaluationId(policyEval)
                .policyOverallOutcome("PASS")
                .scoreResult(Map.of("grade", "B", "score", 720))
                .requestedAmount(new BigDecimal("1000000"))
                .requestedTenureMonths(24)
                .metrics(Map.of(
                        "income.eligible_monthly", Map.of("value", 200000, "dataStatus", "AVAILABLE"),
                        "obligation.total_emi", Map.of("value", 20000, "dataStatus", "AVAILABLE"),
                        "banking.avg_daily_balance_3m", Map.of("value", 300000, "dataStatus", "AVAILABLE"),
                        "turnover.triangulated", Map.of("value", 6000000, "dataStatus", "AVAILABLE"),
                        "cashflow.eligible_annual", Map.of("value", 2400000, "dataStatus", "AVAILABLE"),
                        "collateral.value", Map.of("value", 2000000, "dataStatus", "AVAILABLE")))
                .build();
        Map<String, Object> canonical = Map.of("amount", 1000000, "tenureMonths", 24);
        AiScenarioRequest req = new AiScenarioRequest(
                tenant, applicationId, null,
                new BigDecimal("600000"), 24,
                Map.of("value", 2000000), canonical, Map.of());
        Map<String, Object> out = scenarioService.runScenario(
                req, base, DecisionStrategyFactory.p2ValidationStrategyV1(tenant), canonical);
        assertThat(out.get("status")).isEqualTo("COMPUTED");
        assertThat(out.get("engine")).isEqualTo("ShadowDecisionEngine");
        assertThat(out.get("canonicalUnchanged")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> det = (Map<String, Object>) out.get("deterministicResult");
        assertThat(det.get("authoritative")).isEqualTo(false);
        assertThat(det.get("scenarioOnly")).isEqualTo(true);
        assertThat(det.get("amount")).isNotNull();
        // Canonical snapshot untouched
        assertThat(canonical.get("amount")).isEqualTo(1000000);
    }

    @Test
    void piiMasking() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        dirty.put("pan", "ABCDE1234F");
        dirty.put("aadhaar", "1234 5678 9012");
        dirty.put("email", "borrower@example.com");
        dirty.put("phone", "9876543210");
        dirty.put("accountNumber", "123456789012");
        dirty.put("address", "12 MG Road Bangalore");
        dirty.put("narration", "NEFT transfer from ABCDE1234F to account 123456789012 for salary credit processing details extra text");
        Map<String, Object> clean = piiMinimizer.minimize(dirty);
        assertThat(String.valueOf(clean.get("pan"))).doesNotContain("ABCDE1234F");
        assertThat(String.valueOf(clean.get("email"))).contains("***");
        assertThat(String.valueOf(clean.get("phone"))).doesNotContain("9876543210");
        assertThat(String.valueOf(clean.get("narration")).length())
                .isLessThanOrEqualTo(81);
    }

    @Test
    void idempotency() {
        Map<String, Object> first = analyze(List.of("NARRATIVE"));
        Map<String, Object> second = analyze(List.of("NARRATIVE"));
        assertThat(second.get("idempotentReplay")).isEqualTo(true);
        assertThat(second.get("analysisRequestId")).isEqualTo(first.get("analysisRequestId"));
        assertThat(second.get("idempotencyKey")).isEqualTo(first.get("idempotencyKey"));
    }

    @Test
    void promptVersioning() {
        Map<String, Object> result = analyze(List.of("NARRATIVE", "EXPLANATION", "CAM_DRAFT"));
        @SuppressWarnings("unchecked")
        Map<String, Object> versions = (Map<String, Object>) result.get("promptVersions");
        assertThat(versions).containsKeys("NARRATIVE", "EXPLANATION", "CAM_DRAFT");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        assertThat(suggestions).allSatisfy(s ->
                assertThat(String.valueOf(s.get("promptVersion"))).contains("@"));
        assertThat(new AiPromptTemplateService(store).require("UNDERWRITING_SUMMARY_V1", "1").getVersion())
                .isEqualTo("1");
    }

    @Test
    void reviewWorkflowAcceptedAsNoteDoesNotPromoteFacts() {
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        UUID suggestionId = UUID.fromString(String.valueOf(suggestions.get(0).get("id")));
        Map<String, Object> review = reviewService.review(
                tenant, suggestionId, "ACCEPT_AS_NOTE", "USEFUL", null, "uw1", "ok as note");
        assertThat(review.get("status")).isEqualTo(SuggestionStatus.ACCEPTED_AS_NOTE.name());
        assertThat(review.get("factPromoted")).isEqualTo(false);
        assertThat(review.get("acceptedAsNoteDoesNotPromoteFacts")).isEqualTo(true);
        assertThat(review.get("factCandidateActivated")).isEqualTo(false);
    }

    @Test
    void reviewRejectsAcceptAsFact() {
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        UUID suggestionId = UUID.fromString(String.valueOf(suggestions.get(0).get("id")));
        assertThatThrownBy(() -> reviewService.review(
                tenant, suggestionId, "ACCEPT_AS_FACT", null, null, "uw1", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void tenantIsolation() {
        Map<String, Object> result = analyze(List.of("NARRATIVE"));
        UUID analysisId = UUID.fromString(String.valueOf(result.get("analysisRequestId")));
        Map<String, Object> other = service.getAnalysis(otherTenant, analysisId);
        assertThat(other.get("status")).isEqualTo("TENANT_ISOLATION");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> suggestions = (List<Map<String, Object>>) result.get("suggestions");
        UUID suggestionId = UUID.fromString(String.valueOf(suggestions.get(0).get("id")));
        assertThatThrownBy(() -> reviewService.review(
                otherTenant, suggestionId, "REJECT", "NOT_USEFUL", null, "x", "no"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void noDemoUrlInNewFlow() {
        Map<String, Object> result = analyze(List.of("NARRATIVE", "QUESTION", "EXPLANATION"));
        assertThat(result.get("demoUrlUsed")).isEqualTo(false);
        String json = result.toString().toLowerCase();
        assertThat(json).doesNotContain("demo_ai_los_url");
        assertThat(json).doesNotContain("demoloan");
        Map<String, Object> view = new AiUnderwriterViewBuilder(store).build(tenant, applicationId);
        assertThat(view.get("banner")).isEqualTo(AiUnderwriterViewBuilder.BANNER);
        assertThat(view.get("demoUrlUsed")).isEqualTo(false);
        Map<String, Object> evidenceView = new CreditEvidenceViewBuilder()
                .withAiUnderwriterView(Map.of("x", 1), view);
        assertThat(evidenceView.get("AiUnderwriterView")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> aiSec = (Map<String, Object>) evidenceView.get("AiUnderwriterView");
        assertThat(aiSec.get("demoUrlUsed")).isEqualTo(false);
        assertThat(aiSec.get("authoritative")).isEqualTo(false);
    }

    @Test
    void outputComparisonDoesNotOverwrite() {
        Map<String, Object> a = analyze(List.of("NARRATIVE"));
        Map<String, Object> b = analyze(List.of("EXPLANATION"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sa = (List<Map<String, Object>>) a.get("suggestions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sb = (List<Map<String, Object>>) b.get("suggestions");
        UUID oldId = UUID.fromString(String.valueOf(sa.get(0).get("id")));
        UUID newId = UUID.fromString(String.valueOf(sb.get(0).get("id")));
        Map<String, Object> cmp = new AiOutputComparisonService(store).compare(tenant, oldId, newId);
        assertThat(cmp.get("overwroteHistorical")).isEqualTo(false);
        assertThat(cmp.get("status")).isEqualTo("COMPARED");
        assertThat(store.findSuggestion(oldId)).isPresent();
        assertThat(store.findSuggestion(newId)).isPresent();
    }

    @Test
    void shadowDecisionStillIndependentOfAi() {
        CiCreditRecommendation rec = new ShadowDecisionEngine().recommend(
                DecisionStrategyFactory.p2ValidationStrategyV1(tenant),
                DecisionRuntimeInput.builder()
                        .tenantId(tenant)
                        .applicationId(applicationId)
                        .policyOverallOutcome("PASS")
                        .scoreResult(Map.of("grade", "B", "score", 720))
                        .requestedAmount(new BigDecimal("1000000"))
                        .requestedTenureMonths(24)
                        .metrics(Map.of(
                                "income.eligible_monthly", Map.of("value", 200000, "dataStatus", "AVAILABLE"),
                                "obligation.total_emi", Map.of("value", 20000, "dataStatus", "AVAILABLE"),
                                "banking.avg_daily_balance_3m", Map.of("value", 300000, "dataStatus", "AVAILABLE"),
                                "turnover.triangulated", Map.of("value", 6000000, "dataStatus", "AVAILABLE"),
                                "cashflow.eligible_annual", Map.of("value", 2400000, "dataStatus", "AVAILABLE"),
                                "collateral.value", Map.of("value", 2000000, "dataStatus", "AVAILABLE")))
                        .build());
        assertThat(rec.getAuthoritative()).isFalse();
        analyze(List.of("NARRATIVE"));
        assertThat(rec.getRecommendedAmount()).isNotNull();
        assertThat(rec.getAuthoritative()).isFalse();
    }
}
