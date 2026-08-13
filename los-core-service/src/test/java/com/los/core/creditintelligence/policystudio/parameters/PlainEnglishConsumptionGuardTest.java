package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generic residual-content guard — unconsumed clause-bearing content fail-closes;
 * comparison-operator phrases must not false-positive.
 */
class PlainEnglishConsumptionGuardTest {

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
    void unless_except_besides_provided_apart_failClosed() {
        for (String text : new String[]{
                "Reject if FOIR exceeds 50% unless bureau score is at least 750",
                "FOIR must not exceed 50% except when bureau score is above 800",
                "FOIR must be 50% or below besides LTV must be 60% or below",
                "Approve when FOIR is below 50% provided that bureau score is at least 700",
                "Reject FOIR above 50% apart from NTC applicants"
        }) {
            Map<String, Object> p = authoring.preview(Map.of("mode", "DESCRIBE", "text", text));
            assertThat(p.get("complete")).as(text).isNotEqualTo(true);
            assertThat(String.valueOf(p.get("status"))).as(text)
                    .isIn("NEEDS_CLARIFICATION", "NEEDS_PARAMETER_SELECTION", "INCOMPLETE",
                            "NEEDS_USER_CONFIRMATION");
        }
    }

    @Test
    void writeOffHyphenAndSpaceFormsConsumeCleanly() {
        for (String text : new String[]{
                "no write-offs except credit cards",
                "no write-off except credit cards",
                "no write offs except credit cards"
        }) {
            Map<String, Object> p = authoring.preview(Map.of("mode", "DESCRIBE", "text", text));
            assertThat(p.get("complete")).as(text).isEqualTo(true);
            assertThat(p.get("parameterId")).as(text).isEqualTo("bureau.accounts.writeoff_non_cc");
            assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                    text,
                    "bureau.accounts.writeoff_non_cc",
                    "<=",
                    0,
                    "Non-credit-card write-off count")).as(text).isFalse();
        }
    }

    @Test
    void hyphenatedCompoundsNormalizeLikeSpaceForms() {
        // pay-off ≡ pay off — hyphen fold must not change residual outcome vs an unrelated binding
        boolean payHyphen = PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "bureau score > 700 with unpaid pay-off balance",
                "bureau.score", ">", 700, "Bureau score");
        boolean paySpace = PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "bureau score > 700 with unpaid pay off balance",
                "bureau.score", ">", 700, "Bureau score");
        assertThat(payHyphen).as("hyphen vs space pay-off").isEqualTo(paySpace);
        assertThat(payHyphen).as("pay-off residual").isTrue();

        // co-borrower ≡ co borrower — residual marker must survive (not only the stopword "borrower")
        boolean coHyphen = PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR must be <= 50% for co-borrower applicants",
                "obligation.ratio", "<=", 50, "Obligation ratio (FOIR)");
        boolean coSpace = PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR must be <= 50% for co borrower applicants",
                "obligation.ratio", "<=", 50, "Obligation ratio (FOIR)");
        assertThat(coHyphen).as("hyphen vs space co-borrower").isEqualTo(coSpace);
        assertThat(coHyphen).as("co-borrower residual").isTrue();
    }

    @Test
    void originalOrAndCasesRemainGreen() {
        Map<String, Object> or = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "bureau score > 700 OR bureau score IS EQUAL TO -1"));
        assertThat(or.get("complete")).isEqualTo(true);
        assertThat(String.valueOf(((Map<?, ?>) or.get("expression")).get("op")))
                .isEqualToIgnoringCase("OR");

        Map<String, Object> and = authoring.preview(Map.of(
                "mode", "DESCRIBE",
                "text", "Both bureau score must be at least 700 and FOIR must not exceed 50%"));
        assertThat(and.get("complete")).isEqualTo(true);
    }

    @Test
    void comparisonPhrasesDoNotTripGuardWhenFullyConsumed() {
        // Direct guard: after operator-phrase consumption, no residual
        assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR must be less than or equal to 50%",
                "obligation.ratio", "<=", 50, "Obligation ratio (FOIR)")).isFalse();
        assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "interest rate more than or equal to 10%",
                "application.interest_rate", ">=", 10, "Interest rate")).isFalse();
        assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR must be greater than or equal to 10%",
                "obligation.ratio", ">=", 10, "Obligation ratio (FOIR)")).isFalse();
        assertThat(PlainEnglishConsumptionGuard.hasUnconsumedSubstantiveContent(
                "FOIR less than or equal to 50%",
                "obligation.ratio", "<=", 50, "Obligation ratio (FOIR)")).isFalse();
    }

    @Test
    void simpleScoreRuleStillReady() {
        Map<String, Object> p = authoring.preview(Map.of(
                "mode", "DESCRIBE", "text", "Bureau score should be 650 or above"));
        assertThat(p.get("complete")).isEqualTo(true);
        assertThat(p.get("parameterId")).isEqualTo("bureau.score");
    }
}
