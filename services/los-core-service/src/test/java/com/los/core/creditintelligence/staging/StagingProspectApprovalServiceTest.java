package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagingProspectApprovalServiceTest {

    private StagingProspectApprovalService approvals;

    @BeforeEach
    void setUp() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        CreditIntelligenceProperties props = new CreditIntelligenceProperties();
        props.setDefaultTenantId(tenantId);
        props.getPolicyStudio().setRequireMakerChecker(true);
        props.getStagingDemo().setEnabled(true);
        props.getValidation().setEnabled(true);
        PolicyStudioOrchestrator orch = new PolicyStudioOrchestrator();
        StagingPolicyStudioDemoService demo = new StagingPolicyStudioDemoService(
                props, orch, new PolicyTextExtractionService());
        StagingProspectSimulationService sim = new StagingProspectSimulationService(props, orch);
        approvals = new StagingProspectApprovalService(props, orch, sim, demo);
    }

    @Test
    void blockedPathPreventsDraftBuild() {
        Map<String, Object> blocked = approvals.runDemoBlockedPath(null);
        assertThat(blocked.get("demoPath")).isEqualTo("BLOCKED");
        assertThat(blocked.get("blocked")).isEqualTo(true);
        assertThat(blocked.get("canBuildDraft")).isEqualTo(false);
        UUID docId = UUID.fromString(String.valueOf(blocked.get("documentId")));
        assertThatThrownBy(() -> approvals.buildDraftPackage(docId, Map.of(), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Draft blocked");
    }

    @Test
    void happyPathBuildsDraftWithSeparateChecker() {
        Map<String, Object> happy = approvals.runDemoHappyPath(null);
        assertThat(happy.get("demoPath")).isEqualTo("HAPPY");
        assertThat(happy.get("draftSummary")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) happy.get("draftSummary");
        assertThat(summary.get("packageStatus")).isEqualTo("DRAFT_ONLY");
        assertThat(summary.get("productionActive")).isEqualTo(false);
        assertThat(summary.get("creditManagerApproved")).isEqualTo(true);
        assertThat(summary.get("checkerApproved")).isEqualTo(true);
        assertThat(String.valueOf(happy.get("draftBanner"))).contains("NOT ACTIVE");
        assertThat(String.valueOf(happy.get("demoResolutionBanner"))).contains("CUSTOMER CONFIRMATION");
    }

    @Test
    void materialEditInvalidatesApproval() {
        Map<String, Object> happy = approvals.runDemoHappyPath(null);
        UUID docId = UUID.fromString(String.valueOf(happy.get("documentId")));
        Map<String, Object> invalidated = approvals.invalidateAfterMaterialEdit(
                docId, Map.of("reviewer", "credit_manager"), null);
        assertThat(invalidated.get("approvalInvalidated")).isEqualTo(true);
        assertThat(String.valueOf(invalidated.get("invalidationMessage")))
                .containsIgnoringCase("invalidated");
    }

    @Test
    void makerCheckerBlocksSameReviewerAsChecker() {
        Map<String, Object> happy = approvals.runDemoHappyPath(null);
        UUID docId = UUID.fromString(String.valueOf(happy.get("documentId")));
        assertThatThrownBy(() -> approvals.submitCheckerApproval(docId, Map.of(
                "reviewer", "credit_manager",
                "comments", "same user attempt"), null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Maker-checker");
    }
}
