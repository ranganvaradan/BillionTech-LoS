package com.los.core.creditintelligence.policystudio.parameters.lifecycle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * POLICY-STUDIO-RULE-LIFECYCLE-AND-STATE-MODEL-CLOSURE-1 — state-machine matrix.
 */
class PolicyRuleLifecycleProjectionTest {

    @ParameterizedTest
    @CsvSource({
            // accepted, paramResolved, calcRequired, calcDefined, clarification, dataAvail, policyTest, runtime, prod, authoring, included, expected
            "false, true, false, true, false, true, true, false, false, true, true, READY_FOR_CONFIRMATION",
            "true, true, true, false, false, true, false, false, false, true, true, NEEDS_INPUT",
            "true, true, false, true, false, true, true, false, false, true, true, ACCEPTED_READY_TO_TEST",
            "true, true, false, true, false, true, true, false, true, true, true, PRODUCTION_READY",
            "false, false, false, true, false, true, false, false, false, true, true, NEEDS_INPUT",
            "false, true, false, true, true, true, false, false, false, true, true, NEEDS_INPUT",
            "false, true, false, true, false, false, false, false, false, true, true, DATA_NOT_AVAILABLE",
            "true, true, false, true, false, true, true, true, false, true, true, ACCEPTED_READY_TO_TEST",
    })
    void matrix(
            boolean accepted,
            boolean paramResolved,
            boolean calcRequired,
            boolean calcDefined,
            boolean clarification,
            boolean dataAvail,
            boolean policyTest,
            boolean runtime,
            boolean prod,
            boolean authoring,
            boolean included,
            String expected) {
        var facts = new PolicyRuleLifecycleProjection.Facts(
                accepted, paramResolved, calcRequired, calcDefined, calcDefined,
                clarification, dataAvail, policyTest, runtime, prod, included, authoring,
                false, false);
        assertEquals(expected, PolicyRuleLifecycleProjection.deriveState(facts).name());
    }

    @Test
    void noAcceptedPlusNeedsInputContradictionOnProjection() {
        var ready = PolicyRuleLifecycleProjection.project("r1", new PolicyRuleLifecycleProjection.Facts(
                true, true, false, true, true, false, true, true, false, false, true, true, false, false));
        assertEquals("ACCEPTED_READY_TO_TEST", ready.get("lenderState"));
        assertEquals("Accepted", ready.get("statusChip"));
        assertTrue(Boolean.TRUE.equals(ready.get("forbidNeedsInputWhenAcceptedReady")));
        assertFalse(Boolean.TRUE.equals(ready.get("forbidAcceptedBadgeWhenNeedsInput")));
        assertFalse(Boolean.TRUE.equals(ready.get("showAcceptRule")));

        var needs = PolicyRuleLifecycleProjection.project("r2", new PolicyRuleLifecycleProjection.Facts(
                true, true, true, false, false, false, true, false, false, false, true, true, false, false));
        assertEquals("NEEDS_INPUT", needs.get("lenderState"));
        assertEquals("Needs your input", needs.get("statusChip"));
        assertTrue(Boolean.TRUE.equals(needs.get("forbidAcceptedBadgeWhenNeedsInput")));
        assertFalse(Boolean.TRUE.equals(needs.get("showAcceptRule")));
    }

    @Test
    void factsFromCardRespectsCalculationDefinedOverlay() {
        Map<String, Object> card = Map.of(
                "includedForActivation", true,
                "executionReady", true,
                "authoringComplete", true,
                "operands", java.util.List.of(Map.of(
                        "parameterId", "bureau.credit_after_overdue.clean_history_months",
                        "calculationRequired", false,
                        "calculationDefined", true,
                        "calculationDefinitionStatus", "DEFINED",
                        "howCalculated", "Months since last overdue"
                )));
        Map<String, Object> meta = Map.of("disposition", "ACCEPTED");
        var facts = PolicyRuleLifecycleProjection.factsFromCard(card, meta);
        assertTrue(facts.ruleAccepted());
        assertFalse(facts.calculationRequired());
        assertTrue(facts.calculationDefined());
        assertTrue(facts.policyTestReady());
        assertEquals(PolicyRuleLenderState.ACCEPTED_READY_TO_TEST,
                PolicyRuleLifecycleProjection.deriveState(facts));
    }
}
