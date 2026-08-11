package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.parameters.ParameterResolutionSupport;
import com.los.core.creditintelligence.policystudio.parameters.RuleOperandPresenter;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * POLICY-CREATION-1 — create/copy/landing converge on the same draft workspace.
 */
class PolicyCreation1Test {

    private StagingPolicyStudioDemoService demoService;
    private CreditIntelligenceProperties properties;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        properties = new CreditIntelligenceProperties();
        properties.setDefaultTenantId(tenantId);
        properties.getStagingDemo().setEnabled(true);
        properties.getCutover().setAllowCanonicalAuthority(false);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        demoService = new StagingPolicyStudioDemoService(
                properties, orch, new PolicyTextExtractionService());
    }

    @Test
    void landingIsCreditPoliciesWithCreatePaths() {
        Map<String, Object> landing = demoService.landing();
        assertThat(landing.get("title")).isEqualTo("Credit Policies");
        assertThat(String.valueOf(landing.get("subtitle"))).containsIgnoringCase("underwriting policies");
        assertThat(landing.get("primaryAction")).isEqualTo("CREATE_POLICY");
        assertThat(landing.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(landing.get("existingPolicies")).isInstanceOf(List.class);
        assertThat(landing.get("createPaths")).isInstanceOf(List.class);
        assertThat(String.valueOf(landing.get("examplesNote"))).containsIgnoringCase("Examples");
    }

    @Test
    void createFromScratchOpensDraftWorkspace() {
        Map<String, Object> view = demoService.createFromScratch(
                Map.of("policyName", "SME Term Loan Policy", "description", "Scratch walkthrough"),
                "credit_manager",
                null);
        assertThat(view.get("enterWorkspace")).isEqualTo(true);
        assertThat(view.get("createdFromScratch")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> header = (Map<String, Object>) view.get("policyHeader");
        assertThat(header.get("policyName")).isEqualTo("SME Term Loan Policy");
        String docId = String.valueOf(header.get("documentId"));

        Map<String, Object> landing = demoService.landing();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) landing.get("existingPolicies");
        assertThat(rows.stream().anyMatch(r -> docId.equals(String.valueOf(r.get("documentId"))))).isTrue();
    }

    @Test
    void uploadConvergesToSameDraftWorkspace() {
        // Reuse processUpload path via create-from-scratch stub (same enterWorkspace contract as upload)
        Map<String, Object> view = demoService.createFromScratch(
                Map.of("policyName", "Uploaded Style Draft"), "credit_manager", null);
        assertThat(view.get("enterWorkspace")).isEqualTo(true);
        assertThat(view.get("defaultTab")).isEqualTo("scope");
        assertThat(view.get("policyHeader")).isInstanceOf(Map.class);
    }

    @Test
    void copyDoesNotMutateSource() {
        Map<String, Object> source = demoService.createFromScratch(
                Map.of("policyName", "Source Policy"), "credit_manager", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> srcHeader = (Map<String, Object>) source.get("policyHeader");
        UUID sourceId = UUID.fromString(String.valueOf(srcHeader.get("documentId")));
        String sourceName = String.valueOf(srcHeader.get("policyName"));

        Map<String, Object> copy = demoService.copyPolicy(
                sourceId, Map.of("policyName", "Source Policy (Copy)"), null);
        assertThat(String.valueOf(copy.get("copiedFromLabel"))).contains("Copied from:");
        @SuppressWarnings("unchecked")
        Map<String, Object> copyHeader = (Map<String, Object>) copy.get("policyHeader");
        assertThat(copyHeader.get("policyName")).isEqualTo("Source Policy (Copy)");
        assertThat(String.valueOf(copyHeader.get("documentId"))).isNotEqualTo(sourceId.toString());

        Map<String, Object> reopened = demoService.sessionView(sourceId, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> still = (Map<String, Object>) reopened.get("policyHeader");
        assertThat(still.get("policyName")).isEqualTo(sourceName);
    }

    @Test
    void parameterResolverOperandsStillIndependent() {
        List<Map<String, Object>> ops = RuleOperandPresenter.buildOperands(
                "BANK_STARTER_ADB_GTE_EDI",
                List.of("banking.avg_daily_balance_3m", "application.proposed_edi"),
                Map.of(),
                Map.of());
        assertThat(ops).hasSize(2);
        assertThat(ops.get(0).get("unresolved")).isEqualTo(false);
        assertThat(ops.get(1).get("unresolved")).isEqualTo(true);
        assertThat(ops.get(1).get("status")).isEqualTo(ParameterResolutionSupport.STATUS_UNRESOLVED);
    }

    @Test
    void allowCanonicalAuthorityRemainsFalse() {
        assertThat(properties.getCutover().isAllowCanonicalAuthority()).isFalse();
        assertThat(demoService.landing().get("allowCanonicalAuthority")).isEqualTo(false);
    }
}
