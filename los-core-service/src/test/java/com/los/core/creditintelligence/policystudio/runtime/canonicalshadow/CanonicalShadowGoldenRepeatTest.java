package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraph;
import com.los.core.creditintelligence.policystudio.graph.CiPolicyRuleGraphNode;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionSpineProducerBootstrap;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphNodeRepository;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyRuleGraphRepository;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyRuntime;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.SharedCanonicalEvaluationSupport;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationEntity;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfigurationRepository;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalResolutionStatus;
import com.los.core.creditintelligence.policystudio.runtime.ownership.FinalUnderwritingDecision;
import com.los.core.creditintelligence.policystudio.runtime.ownership.PolicyScorecardPrecedence;
import com.los.core.creditintelligence.staging.PolicyStudioTestExperienceService;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.UnderwritingScorecardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * W11.3 golden repeat + Policy Test vs canonical shadow equivalence on controlled fixtures.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CanonicalShadowGoldenRepeatTest {

    static final UUID APP_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID DOC_ID = UUID.fromString("4543e643-c3a0-4a57-a92c-370dff8b2fa9");
    static final UUID SCORECARD_ID = UUID.fromString("cc38f5a0-fab7-4001-8167-5a8f47d2487a");
    static final UUID BUREAU_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    static final LocalDate ASOF = SharedCanonicalEvaluationSupport.CANONICAL_POLICY_TEST_AS_OF;

    @Mock CanonicalApplicationConfigurationRepository freezeRepository;
    @Mock CanonicalShadowEvaluationRepository evaluationRepository;
    @Mock CanonicalShadowComparisonRepository comparisonRepository;
    @Mock CanonicalShadowContextFactory contextFactory;
    @Mock CiPolicyRuleGraphRepository graphRepository;
    @Mock CiPolicyRuleGraphNodeRepository nodeRepository;
    @Mock UnderwritingScorecardRepository scorecardRepository;

    CanonicalParameterExecutionService cpes;
    CreditIntelligenceProperties properties;
    CanonicalShadowUnderwritingService service;

    @BeforeEach
    void setUp() {
        cpes = ExecutionSpineProducerBootstrap.standalone((id, t) -> Optional.empty());
        properties = new CreditIntelligenceProperties();
        properties.getCanonicalShadow().setMode(CanonicalShadowMode.LEGACY_WITH_CANONICAL_SHADOW);
        CanonicalShadowScorecardExecutor scorecardExecutor = new CanonicalShadowScorecardExecutor(
                scorecardRepository, cpes);
        service = new CanonicalShadowUnderwritingService(
                properties,
                freezeRepository,
                evaluationRepository,
                comparisonRepository,
                contextFactory,
                scorecardExecutor,
                cpes,
                graphRepository,
                nodeRepository);
        lenient().when(evaluationRepository.findFirstByApplicationIdAndIdentityHashAndUnderwritingEvaluationIdIsNull(any(), any()))
                .thenReturn(Optional.empty());
        Map<UUID, CanonicalShadowEvaluationEntity> saved = new ConcurrentHashMap<>();
        lenient().when(evaluationRepository.save(any())).thenAnswer(inv -> {
            CanonicalShadowEvaluationEntity e = inv.getArgument(0);
            saved.putIfAbsent(e.getId(), e);
            return e;
        });
        lenient().when(evaluationRepository.findFirstByApplicationIdAndIdentityHashAndUnderwritingEvaluationIdIsNull(
                eq(APP_ID), any())).thenAnswer(inv -> {
            String h = inv.getArgument(1);
            return saved.values().stream()
                    .filter(e -> h.equals(e.getIdentityHash()))
                    .findFirst();
        });
        lenient().when(comparisonRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void goldenRepeatCompletedShadowIsDeterministic() {
        CanonicalApplicationConfiguration freeze = frozenPackage();
        String hash = freeze.identityHash();
        stubFrozen(freeze, hash);
        stubPolicyGraph();
        stubContext();
        stubScorecard();
        LoanApplication app = LoanApplication.builder()
                .id(APP_ID)
                .borrowerType(BorrowerType.INDIVIDUAL)
                .loanProduct("PL")
                .creditDecision("APPROVED")
                .build();

        CanonicalShadowEvaluationEntity first = service.runControlled(app);
        CanonicalShadowEvaluationEntity second = service.runControlled(app);

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getCanonicalDecision()).isEqualTo(second.getCanonicalDecision());
        assertThat(first.getParameterEvidence()).isEqualTo(second.getParameterEvidence());
        assertThat(first.getRuleEvidence()).isEqualTo(second.getRuleEvidence());
        assertThat(first.getScorecardEvidence()).isEqualTo(second.getScorecardEvidence());
        assertThat(first.getPolicyResult()).isEqualTo(second.getPolicyResult());
    }

    @Test
    void policyTestAndShadowRuleOutcomesMatchOnControlledFixture() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.POLICY_TEST)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .build();
        CanonicalPolicyRuntime runtime = new CanonicalPolicyRuntime(cpes);
        Map<String, Object> expr = PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650));
        CanonicalRuleResult rr = runtime.evaluateRule(
                new CanonicalPolicyRuntime.RuleSpec("score-gte", "v1", expr, "DATA_INSUFFICIENT"), ctx, ASOF);
        assertThat(rr.result()).isEqualTo(CanonicalRuleResult.RuleOutcome.PASS);

        List<Map<String, Object>> policyTestRules = List.of(Map.of(
                "ruleKey", "score-gte",
                "canonicalResult", "PASS"));
        List<Map<String, Object>> shadowRules = List.of(Map.of(
                "ruleId", "score-gte",
                "result", "PASS"));
        Map<String, Object> eq = CanonicalShadowComparator.policyTestEquivalence(
                policyTestRules, shadowRules, false, true);
        assertThat(eq.get("RULE_PARTICIPATION_MISMATCH_COUNT")).isEqualTo(0);
        assertThat(eq.get("RULE_RESULT_MISMATCH_COUNT")).isEqualTo(0);
        assertThat(eq.get("PARAMETER_VALUE_MISMATCH_COUNT")).isEqualTo(0);
        assertThat(eq.get("FINAL_DECISION_MISMATCH_COUNT")).isEqualTo(0);
        assertThat(eq.get("scorecardConvergenceGap")).isEqualTo(true);
        assertThat(PolicyStudioTestExperienceService.ENGINE).contains("CanonicalPolicyRuntime");
    }

    @Test
    void scorecardConvergenceGapIsExplicitWhenPolicyTestLacksScorecard() {
        Map<String, Object> eq = CanonicalShadowComparator.policyTestEquivalence(
                List.of(), List.of(), false, true);
        assertThat(eq.get("SCORECARD_RESULT_MISMATCH_COUNT")).isEqualTo(1);
        assertThat(eq.get("SCORECARD_INPUT_MISMATCH_COUNT")).isEqualTo(1);
        assertThat(eq.get("scorecardConvergenceGap")).isEqualTo(true);
        assertThat(eq.get("policyTestScorecardAvailable")).isEqualTo(false);
        assertThat(eq.get("shadowScorecardAvailable")).isEqualTo(true);
    }

    @Test
    void precedenceAggregationMapsToCanonicalShadowDecision() {
        CanonicalPolicyRuntime runtime = new CanonicalPolicyRuntime(cpes);
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .build();
        var policy = runtime.evaluate(new CanonicalPolicyRuntime.PolicyRequest(
                DOC_ID.toString(), "1",
                List.of(new CanonicalPolicyRuntime.RuleSpec("score-gte",
                        PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650)))),
                ctx, ASOF));
        var prec = PolicyScorecardPrecedence.combine(
                policy, FinalUnderwritingDecision.FinalOutcome.APPROVE, null);
        assertThat(CanonicalShadowUnderwritingService.mapDecision(prec.outcome()))
                .isEqualTo(CanonicalShadowDecision.APPROVE);
    }

    private void stubFrozen(CanonicalApplicationConfiguration freeze, String hash) {
        CanonicalApplicationConfigurationEntity row = CanonicalApplicationConfigurationEntity.builder()
                .id(UUID.randomUUID())
                .applicationId(APP_ID)
                .status(CanonicalResolutionStatus.RESOLVED.name())
                .identityHash(hash)
                .packageJson(freeze.toMap())
                .build();
        when(freezeRepository.findFirstByApplicationIdAndStatusOrderByCreatedAtAsc(
                APP_ID, CanonicalResolutionStatus.RESOLVED.name())).thenReturn(Optional.of(row));
    }

    private void stubPolicyGraph() {
        UUID graphId = UUID.randomUUID();
        CiPolicyRuleGraph graph = CiPolicyRuleGraph.builder()
                .id(graphId)
                .policyDocumentId(DOC_ID)
                .documentVersion(1)
                .build();
        when(graphRepository.findByPolicyDocumentIdAndDocumentVersion(DOC_ID, 1))
                .thenReturn(Optional.of(graph));
        Map<String, Object> expr = PolicyDsl.gte(PolicyDsl.metric("bureau.score"), Map.of("const", 650));
        CiPolicyRuleGraphNode node = CiPolicyRuleGraphNode.builder()
                .id(UUID.randomUUID())
                .graphId(graphId)
                .ruleKey("score-gte")
                .contentHash("v1")
                .expression(expr)
                .onMissing("DATA_INSUFFICIENT")
                .metadata(Map.of())
                .build();
        when(nodeRepository.findByGraphIdOrderBySortOrderAsc(graphId)).thenReturn(List.of(node));
    }

    private void stubContext() {
        EvaluationContext ctx = EvaluationContext.builder()
                .mode(EvaluationMode.UNDERWRITING)
                .applicationId(APP_ID)
                .documentId(DOC_ID)
                .evaluationAsOf(ASOF)
                .fact("bureau.score", 720)
                .entity("forbidLatestFor", true)
                .entity("pinnedCalculationDefinitions", Map.of())
                .entity("pinnedCalculationDefinitionIds", Map.of())
                .build();
        when(contextFactory.build(any(), any())).thenReturn(ctx);
    }

    private void stubScorecard() {
        UnderwritingScorecard card = UnderwritingScorecard.builder()
                .id(SCORECARD_ID)
                .name("Golden")
                .version(1)
                .status("ACTIVE")
                .scorecardJson(Map.of("rows", List.of(
                        Map.of("id", "d1", "parameter", "bureau.score", "source", "BUREAU",
                                "condition", "GTE:650", "weight", 1, "score", 35))))
                .thresholdsJson(Map.of("approveMinPercent", 70, "manualMinPercent", 40))
                .build();
        when(scorecardRepository.findById(SCORECARD_ID)).thenReturn(Optional.of(card));
    }

    private static CanonicalApplicationConfiguration frozenPackage() {
        return new CanonicalApplicationConfiguration(
                APP_ID,
                UUID.fromString("b8396924-0315-4645-9c0b-b1a487a32502"),
                1,
                "VIKASAM",
                UUID.fromString("3b1e0488-c33a-48d5-b494-5b77eec30b8c"),
                1,
                UUID.fromString("5908a11e-bf5e-4870-a1f3-2f5547f6568f"),
                DOC_ID,
                1,
                "v1",
                SCORECARD_ID,
                1,
                true,
                false,
                BUREAU_ID,
                "EQUIFAX",
                "v1",
                "v1",
                ASOF,
                List.of(),
                Instant.parse("2026-08-01T00:00:00Z"),
                Map.of("source", "golden"));
    }
}
