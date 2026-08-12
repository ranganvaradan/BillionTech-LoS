package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * POLICY-STUDIO-AUTHORING-COMPLETENESS-GATE-1 — C1–C7, nested, IN/NOT IN,
 * amendments, adversarial fail-closed, round-trip.
 */
class PolicyStudioAuthoringCompletenessGate1Test {

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
    void c1_gte650() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score should be 650 or above"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertReadyOpValue(p, "bureau.score", ">=", 650);
    }

    @Test
    void c2_lt650() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score should be below 650"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertReadyOpValue(p, "bureau.score", "<", 650);
    }

    @Test
    void c3_bureauOrAlternatives() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau Score of -1, NTC and 650 & above only will be allowed"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("combinator")).isEqualTo("ANY");
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("OR");
        assertThat(((List<?>) expr.get("args"))).hasSize(3);
    }

    @Test
    void c4_andScoreAndFoir() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 700 and FOIR must not exceed 50%"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("combinator")).isEqualTo("ALL");
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("AND");
        assertThat(((List<?>) expr.get("args"))).hasSize(2);
    }

    @Test
    void c5_nestedAndOr() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below"));
        assertThat(p.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("AND");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> args = (List<Map<String, Object>>) expr.get("args");
        assertThat(args).hasSize(2);
        assertThat(args.stream().anyMatch(a -> "OR".equalsIgnoreCase(String.valueOf(a.get("op"))))).isTrue();
    }

    @Test
    void c6_llpUnresolvedFailsClosed() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Borrower constitution must be Proprietorship, Partnership or LLP"));
        assertThat(p.get("complete")).isEqualTo(false);
        assertThat(String.valueOf(p.get("status"))).isEqualTo("NEEDS_CLARIFICATION");
        @SuppressWarnings("unchecked")
        List<String> clarify = (List<String>) p.get("clarify");
        assertThat(clarify.stream().anyMatch(s -> s.toLowerCase().contains("llp"))).isTrue();
    }

    @Test
    void c6_inCanonicalBorrowerTypes() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Borrower constitution must be Proprietorship, Partnership or Company"));
        assertThat(p.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("IN");
    }

    @Test
    void c7_industryNotIn() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Industry must not be Real Estate, Gambling or Crypto"));
        assertThat(p.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("NOT_IN");
    }

    @Test
    void incrementalAmendmentsOnStructuredAst() {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("kind", "COMPOUND_GROUP");
        proposed.put("combinator", "ANY");
        proposed.put("children", List.of(Map.of(
                "kind", "CONDITION",
                "parameterId", "bureau.score",
                "operator", ">=",
                "value", 650,
                "leftKind", "METRIC")));

        Map<String, Object> a1 = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Also allow NTC",
                "proposedModel", proposed));
        assertThat(a1.get("complete")).isEqualTo(true);
        proposed.put("children", a1.get("children"));
        proposed.put("conditions", a1.get("conditions"));

        Map<String, Object> a2 = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Also allow score -1",
                "proposedModel", proposed));
        assertThat(a2.get("complete")).isEqualTo(true);
        proposed.put("children", a2.get("children"));

        Map<String, Object> a3 = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Change 650 to 675",
                "proposedModel", proposed));
        assertThat(a3.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c3 = (List<Map<String, Object>>) a3.get("conditions");
        assertThat(c3).anyMatch(c -> "bureau.score".equals(c.get("parameterId"))
                && ">=".equals(c.get("operator"))
                && Number.class.cast(c.get("value")).intValue() == 675);
        assertThat(c3).hasSize(3);

        proposed.put("children", a3.get("children"));
        Map<String, Object> a4 = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Remove NTC",
                "proposedModel", proposed));
        assertThat(a4.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> c4 = (List<Map<String, Object>>) a4.get("conditions");
        assertThat(c4).hasSize(2);
        assertThat(c4).noneMatch(c -> "bureau.status_ntc".equals(c.get("parameterId")));
    }

    @Test
    void adversarialFailsClosed() {
        for (String text : List.of(
                "good bureau score",
                "high income",
                "low FOIR",
                "650 around",
                "NTC maybe allowed",
                "score above 650 or something")) {
            Map<String, Object> p = authoring.preview(Map.of("mode", "DESCRIBE", "text", text));
            assertThat(p.get("complete")).as(text).isEqualTo(false);
            assertThat(String.valueOf(p.get("status"))).as(text)
                    .isIn("NEEDS_CLARIFICATION", "INCOMPLETE", "NEEDS_USER_CONFIRMATION");
        }
    }

    @Test
    void nestedRoundTripBuildDescribe() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below"));
        @SuppressWarnings("unchecked")
        Map<String, Object> expr1 = (Map<String, Object>) preview.get("expression");
        Map<String, Object> model = CompoundExpressionAuthoringSupport.toEditableModel(expr1, Map.of());
        Map<String, Object> expr2 = CompoundExpressionAuthoringSupport.buildExpression(model);
        assertThat(CompoundExpressionAuthoringSupport.sameTree(expr1, expr2)).isTrue();

        Map<String, Object> buildPreview = authoring.preview(Map.of(
                "mode", "COMPOUND_GROUP",
                "combinator", model.get("combinator"),
                "children", model.get("children")));
        assertThat(buildPreview.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr3 = (Map<String, Object>) buildPreview.get("expression");
        assertThat(CompoundExpressionAuthoringSupport.sameTree(expr1, expr3)).isTrue();
    }

    @Test
    void nestedExecutionGoldens() {
        Map<String, Object> preview = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below"));
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) preview.get("expression");
        // score 720 / FOIR 45 / LTV 70 -> ALLOW
        assertDecision(expr, Map.of("bureau.score", 720, "obligation.ratio", 45, "collateral.ltv", 70), Map.of(), true);
        // score 720 / FOIR 55 / LTV 55 -> ALLOW
        assertDecision(expr, Map.of("bureau.score", 720, "obligation.ratio", 55, "collateral.ltv", 55), Map.of(), true);
        // score 720 / FOIR 55 / LTV 70 -> REJECT
        assertDecision(expr, Map.of("bureau.score", 720, "obligation.ratio", 55, "collateral.ltv", 70), Map.of(), false);
        // score 680 / FOIR 40 / LTV 50 -> REJECT
        assertDecision(expr, Map.of("bureau.score", 680, "obligation.ratio", 40, "collateral.ltv", 50), Map.of(), false);
    }

    @Test
    void typeSafeRejectsNonsensicalOperator() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "COMPOUND_GROUP",
                "combinator", "ALL",
                "children", List.of(Map.of(
                        "kind", "CONDITION",
                        "parameterId", "application.borrower_type",
                        "operator", ">",
                        "value", "PARTNERSHIP"))));
        assertThat(p.get("complete")).isEqualTo(false);
        assertThat(String.valueOf(p.get("status"))).isEqualTo("NEEDS_CLARIFICATION");
    }

    @Test
    void persistNestedAndReloadEditableModel() {
        PolicyStudioSession session = scratchSession();
        Map<String, Object> confirmed = authoring.confirm(session, Map.of(
                "confirm", true,
                "mode", "DESCRIBE",
                "text", "Bureau score must be at least 700 and either FOIR must be 50% or below or LTV must be 60% or below",
                "treatment", "Reject"));
        assertThat(confirmed.get("confirmed")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) confirmed.get("expression");
        Map<String, Object> model = CompoundExpressionAuthoringSupport.toEditableModel(expr, Map.of());
        assertThat(model.get("kind")).isEqualTo("COMPOUND_GROUP");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) model.get("children");
        assertThat(children).hasSize(2);
        assertThat(children.stream().anyMatch(c -> "GROUP".equalsIgnoreCase(String.valueOf(c.get("kind")))
                || c.get("children") instanceof List<?>)).isTrue();
    }

    @Test
    void confirmBlockedWhenClarificationNeeded() {
        PolicyStudioSession session = scratchSession();
        assertThatThrownBy(() -> authoring.confirm(session, Map.of(
                "confirm", true,
                "mode", "DESCRIBE",
                "text", "good bureau score")))
                .hasMessageContaining("mapped");
    }

    private static void assertReadyOpValue(Map<String, Object> p, String param, String op, int value) {
        if (Boolean.TRUE.equals(p.get("compoundGroup"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conds = (List<Map<String, Object>>) p.get("conditions");
            assertThat(conds).anyMatch(c -> param.equals(c.get("parameterId"))
                    && op.equals(String.valueOf(c.get("operator")))
                    && Number.class.cast(c.get("value")).intValue() == value);
        } else {
            assertThat(p.get("parameterId")).isEqualTo(param);
            assertThat(p.get("operator")).isEqualTo(op);
            assertThat(Number.class.cast(p.get("value")).intValue()).isEqualTo(value);
        }
    }

    private static void assertDecision(
            Map<String, Object> expr,
            Map<String, Object> metrics,
            Map<String, Object> facts,
            boolean allow) {
        PolicyDslInterpreterV1 interp = new PolicyDslInterpreterV1();
        var ctx = PolicyDslInterpreterV1.EvaluationContext.of(metrics, facts, null);
        String outcome = interp.evaluate(expr, ctx);
        assertThat(outcome).isEqualTo(allow ? PolicyDslInterpreterV1.PASS : PolicyDslInterpreterV1.FAIL);
    }

    private static PolicyStudioSession scratchSession() {
        UUID docId = UUID.randomUUID();
        CiPolicyDocument doc = CiPolicyDocument.builder()
                .id(docId)
                .name("Authoring Completeness Gate")
                .status("DRAFT")
                .build();
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        return session;
    }
}
