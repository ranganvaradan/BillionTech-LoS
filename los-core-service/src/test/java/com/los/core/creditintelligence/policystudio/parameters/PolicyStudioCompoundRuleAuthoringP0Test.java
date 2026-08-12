package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-STUDIO-COMPOUND-RULE-AUTHORING-P0 — bureau OR(-1, NTC, &gt;=650) lossless authoring.
 */
class PolicyStudioCompoundRuleAuthoringP0Test {

    private static final String UAT =
            "Bureau Score of -1, NTC and 650 & above only will be allowed";

    private CmRuleAuthoringService authoring;

    @BeforeEach
    void setUp() {
        CanonicalParameterRegistry.clearInstalledForTests();
        CanonicalParameterRegistry.install(
                CanonicalParameterRegistry.fromSeedForTestsOnly(),
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY);
        authoring = new CmRuleAuthoringService();
    }

    @Test
    void uatSentenceParsesThreeAlternativesWithInclusiveGte() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", UAT,
                "treatment", "Reject"));

        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("compoundGroup")).isEqualTo(true);
        assertThat(preview.get("combinator")).isEqualTo("ANY");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) preview.get("conditions");
        assertThat(conditions).hasSize(3);
        assertThat(conditions).anyMatch(c ->
                "bureau.score".equals(c.get("parameterId"))
                        && "=".equals(c.get("operator"))
                        && Number.class.cast(c.get("value")).intValue() == -1);
        assertThat(conditions).anyMatch(c ->
                "bureau.status_ntc".equals(c.get("parameterId"))
                        && Boolean.TRUE.equals(c.get("value")));
        assertThat(conditions).anyMatch(c ->
                "bureau.score".equals(c.get("parameterId"))
                        && ">=".equals(c.get("operator"))
                        && Number.class.cast(c.get("value")).intValue() == 650);

        @SuppressWarnings("unchecked")
        List<String> lines = (List<String>) preview.get("previewLines");
        assertThat(String.join("\n", lines)).containsIgnoringCase("ANY");
        assertThat(String.join("\n", lines)).contains("-1");
        assertThat(String.join("\n", lines)).containsIgnoringCase("NTC");
        assertThat(String.join("\n", lines)).contains("650");
        assertThat(String.join("\n", lines)).doesNotContain("> 650");
    }

    @Test
    void inclusiveAndExclusiveBoundariesPreserved() {
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("650 & above")).isEqualTo(">=");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("650 and above")).isEqualTo(">=");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("at least 650")).isEqualTo(">=");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("650 or more")).isEqualTo(">=");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("above 650")).isEqualTo(">");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("more than 650")).isEqualTo(">");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("650 and below")).isEqualTo("<=");
        assertThat(CompoundPlainEnglishParser.detectBoundaryOperator("below 650")).isEqualTo("<");

        Map<String, Object> gte = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score 650 and above will be allowed"));
        assertThat(gte.get("complete")).isEqualTo(true);
        assertThat(gte.get("operator")).isEqualTo(">=");
        assertThat(Number.class.cast(gte.get("value")).intValue()).isEqualTo(650);

        Map<String, Object> gt = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score above 650 will be allowed"));
        assertThat(gt.get("complete")).isEqualTo(true);
        assertThat(gt.get("operator")).isEqualTo(">");
        assertThat(Number.class.cast(gt.get("value")).intValue()).isEqualTo(650);
    }

    @Test
    void incrementalAmendmentAddsMinusOneAndNtc() {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("kind", "COMPOUND_GROUP");
        proposed.put("combinator", "ANY");
        proposed.put("conditions", List.of(Map.of(
                "parameterId", "bureau.score",
                "parameterName", "Bureau score",
                "operator", ">=",
                "value", 650,
                "leftKind", "METRIC")));

        Map<String, Object> amended = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "I also want to add Bureau Score of -1 and NTC",
                "proposedModel", proposed));
        assertThat(amended.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) amended.get("conditions");
        assertThat(conditions).hasSize(3);
    }

    @Test
    void semanticLossFailsClosedWhenIncomplete() {
        Map<String, Object> incomplete = authoring.preview(Map.of(
                "mode", "COMPOUND_GROUP",
                "combinator", "ANY",
                "conditions", List.of()));
        assertThat(incomplete.get("complete")).isEqualTo(false);

        PolicyStudioSession session = scratchSession();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                authoring.confirm(session, Map.of(
                        "confirm", true,
                        "mode", "COMPOUND_GROUP",
                        "combinator", "ANY",
                        "conditions", List.of())))
                .hasMessageContaining("condition");
    }

    @Test
    void buildModeRoundTripLossless() {
        Map<String, Object> preview = authoring.preview(Map.of("mode", "DESCRIBE", "text", UAT));
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) preview.get("expression");
        Map<String, Object> model = CompoundExpressionAuthoringSupport.toEditableModel(expr, Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions = (List<Map<String, Object>>) model.get("conditions");
        Map<String, Object> rebuilt = CompoundExpressionAuthoringSupport.buildExpression(
                String.valueOf(model.get("combinator")), conditions);
        Map<String, Object> model2 = CompoundExpressionAuthoringSupport.toEditableModel(rebuilt, Map.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conditions2 = (List<Map<String, Object>>) model2.get("conditions");
        assertThat(CompoundExpressionAuthoringSupport.sameConditions(conditions, conditions2)).isTrue();
    }

    @Test
    void persistAndExecuteSameExpressionGoldens() {
        PolicyStudioSession session = scratchSession();
        Map<String, Object> confirmed = authoring.confirm(session, Map.of(
                "confirm", true,
                "mode", "DESCRIBE",
                "text", UAT,
                "treatment", "Reject"));
        assertThat(confirmed.get("confirmed")).isEqualTo(true);
        assertThat(confirmed.get("compoundGroup")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) confirmed.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("OR");

        assertEval(expr, Map.of("bureau.score", -1), Map.of(), true);
        assertEval(expr, Map.of(), Map.of("bureau.status_ntc", true), true);
        assertEval(expr, Map.of("bureau.score", 649), Map.of("bureau.status_ntc", false), false);
        assertEval(expr, Map.of("bureau.score", 650), Map.of("bureau.status_ntc", false), true);
        assertEval(expr, Map.of("bureau.score", 651), Map.of("bureau.status_ntc", false), true);
        assertEval(expr, Map.of("bureau.score", 600), Map.of("bureau.status_ntc", false), false);
        // NTC with no numeric score — no invented zero default
        assertEval(expr, Map.of(), Map.of("bureau.status_ntc", true), true);
    }

    @Test
    void simpleNumericStillWorks() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score should be > 625",
                "treatment", "Reject"));
        assertThat(preview.get("complete")).isEqualTo(true);
        assertThat(preview.get("parameterId")).isEqualTo("bureau.score");
        assertThat(preview.get("operator")).isEqualTo(">");
        assertThat(Number.class.cast(preview.get("value")).intValue()).isEqualTo(625);
    }

    private static void assertEval(
            Map<String, Object> expr,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            boolean expectedPass) {
        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(metrics, facts, null);
        String outcome = interp.evaluate(expr, ctx);
        assertThat(outcome).isEqualTo(expectedPass
                ? PolicyDslInterpreterV1.PASS
                : PolicyDslInterpreterV1.FAIL);
    }

    private static PolicyStudioSession scratchSession() {
        UUID docId = UUID.randomUUID();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(docId)
                .name("Compound Authoring P0")
                .status("DRAFT")
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        return session;
    }
}
