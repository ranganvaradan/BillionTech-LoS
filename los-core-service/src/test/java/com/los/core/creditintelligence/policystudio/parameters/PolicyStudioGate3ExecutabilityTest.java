package com.los.core.creditintelligence.policystudio.parameters;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POLICY-STUDIO-GATE3 — executability honesty + mode distinction.
 */
class PolicyStudioGate3ExecutabilityTest {

    @Test
    void bureauScore_isProductionReady() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("bureau.score");
        assertEquals(ParameterExecutabilitySupport.PRODUCTION_READY, e.get("executionState"));
        assertEquals(true, e.get("policyTestReady"));
        assertEquals(true, e.get("runtimeReady"));
        assertEquals(true, e.get("productionReady"));
        assertTrue(((java.util.List<?>) e.get("runtimeFactAliases")).contains("bureau.consumer.score"));
    }

    @Test
    void maxDpd6m_isPolicyTestNotProduction() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("bureau.max_dpd_6m");
        assertNotEquals(ParameterExecutabilitySupport.PRODUCTION_READY, e.get("executionState"));
        assertEquals(true, e.get("policyTestReady"));
        assertEquals(false, e.get("productionReady"));
    }

    @Test
    void writeoffNonCc_policyTestOnly_notProduction() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("bureau.accounts.writeoff_non_cc");
        assertEquals(true, e.get("policyTestReady"));
        assertEquals(false, e.get("runtimeReady"));
        assertEquals(false, e.get("productionReady"));
        assertEquals(ParameterExecutabilitySupport.POLICY_TEST_READY, e.get("executionState"));
    }

    @Test
    void conceptResolver_writeoff_stampsModes() {
        Map<String, Object> r = BusinessConceptResolver.resolve(
                "No Loan Write-Offs are allowed, except for Credit Cards");
        assertEquals(BusinessConceptResolver.READY_DERIVED, r.get("resolutionState"));
        assertEquals(false, r.get("mappedToProposedEdi"));
        assertEquals(true, r.get("policyTestReady"));
        assertEquals(false, r.get("productionReady"));
        assertEquals("bureau.accounts.writeoff_non_cc", r.get("canonicalParameter"));
    }

    @Test
    void derivationDefinedAlone_notPolicyStudioReady() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("bureau.thin_file_indicator");
        assertEquals(ParameterExecutabilitySupport.DERIVATION_DEFINED_NOT_IMPLEMENTED, e.get("executionState"));
        assertEquals(false, e.get("policyTestReady"));
        assertEquals(false, e.get("productionReady"));
    }

    @Test
    void proposedEdi_manualAuthorised() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("application.proposed_edi");
        assertEquals(ParameterExecutabilitySupport.MANUAL_AUTHORISED, e.get("executionState"));
        assertEquals(true, e.get("productionReady"));
    }

    @Test
    void emiBounce_productionReady_afterGate3() {
        Map<String, Object> e = ParameterExecutabilitySupport.evaluate("banking.emi_bounce_count_3m");
        assertEquals(ParameterExecutabilitySupport.PRODUCTION_READY, e.get("executionState"));
        assertTrue(((java.util.List<?>) e.get("runtimeFactAliases")).contains("banking.bounce.emi_count_3m"));
    }

    @Test
    void workflowProvides_doesNotClaimStudioMaxDpd6m() {
        var ids = com.los.core.service.readiness.WorkflowParameterProvidesCatalog
                .parametersProvidedByWorkflow(java.util.List.of(Map.of("step", "BUREAU_PULL")), true, false);
        assertFalse(ids.contains("bureau.max_dpd_6m"));
        assertFalse(ids.contains("bureau.accounts.writeoff_non_cc"));
        assertTrue(ids.contains("bureau.score") || ids.contains("bureau.max_dpd_12m"));
    }

    @Test
    void gstinVerify_doesNotProvideTurnover() {
        var ids = com.los.core.service.readiness.WorkflowParameterProvidesCatalog
                .parametersProvidedByWorkflowSteps(java.util.List.of(Map.of("step", "GSTIN_VERIFY")));
        assertFalse(ids.contains("gst.turnover.trailing_12m"));
    }

    @Test
    void pennyDrop_doesNotProvideAdb() {
        var ids = com.los.core.service.readiness.WorkflowParameterProvidesCatalog
                .parametersProvidedByWorkflowSteps(java.util.List.of(Map.of("step", "BANK_PENNY_DROP")));
        assertFalse(ids.contains("banking.avg_daily_balance_3m"));
    }
}
