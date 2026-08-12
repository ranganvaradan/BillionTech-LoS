package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item 38 — comparison "or equal" must not be treated as clause-level OR.
 */
class ClauseOrEqualFalsePositiveTest {

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
    void comparisonOrEqualPhrasesCompleteAsSingleLeaf() {
        assertLte("FOIR must be less than or equal to 50%", 50);
        assertLte("FOIR less than or equal to 50%", 50);
        Map<String, Object> gte = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "FOIR must be greater than or equal to 10%"));
        assertThat(gte.get("complete")).as("greater than or equal").isEqualTo(true);
        assertThat(gte.get("parameterId")).isEqualTo("obligation.ratio");
        assertThat(gte.get("operator")).isEqualTo(">=");
        assertThat(Number.class.cast(gte.get("value")).intValue()).isEqualTo(10);

        // interest rate may or may not be in registry — at minimum must not be clause-OR false positive
        Map<String, Object> rate = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "interest rate more than or equal to 10%"));
        assertThat(String.valueOf(rate.get("status"))).as("more than or equal")
                .isNotEqualTo("NEEDS_CLARIFICATION");
        if (Boolean.TRUE.equals(rate.get("complete"))) {
            assertThat(String.valueOf(rate.get("operator"))).isIn(">=", ">");
        }
    }

    @Test
    void realClauseOrStillRoutes() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score > 700 OR bureau score = -1"));
        assertThat(p.get("complete")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> expr = (Map<String, Object>) p.get("expression");
        assertThat(String.valueOf(expr.get("op"))).isEqualToIgnoringCase("OR");
        @SuppressWarnings("unchecked")
        List<?> args = (List<?>) expr.get("args");
        assertThat(args).hasSize(2);
    }

    @Test
    void clauseOrDetectionExcludesEqual() {
        assertThat(CompoundPlainEnglishParser.hasClauseLevelOr(
                "FOIR must be less than or equal to 50%")).isFalse();
        assertThat(CompoundPlainEnglishParser.hasClauseLevelOr(
                "interest rate more than or equal to 10%")).isFalse();
        assertThat(CompoundPlainEnglishParser.hasClauseLevelOr(
                "FOIR must be greater than or equal to 10%")).isFalse();
        assertThat(CompoundPlainEnglishParser.hasClauseLevelOr(
                "bureau score > 700 OR bureau score = -1")).isTrue();
    }

    @Test
    void residualGuardDoesNotTripOnOrEqualWhenConsumed() {
        assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR must be less than or equal to 50%",
                "obligation.ratio", "<=", 50, "Obligation ratio (FOIR)")).isFalse();
    }

    private void assertLte(String text, int value) {
        Map<String, Object> p = authoring.preview(Map.of("mode", "DESCRIBE", "text", text));
        assertThat(p.get("complete")).as(text).isEqualTo(true);
        assertThat(p.get("parameterId")).as(text).isEqualTo("obligation.ratio");
        assertThat(p.get("operator")).as(text).isEqualTo("<=");
        assertThat(Number.class.cast(p.get("value")).intValue()).as(text).isEqualTo(value);
    }
}
