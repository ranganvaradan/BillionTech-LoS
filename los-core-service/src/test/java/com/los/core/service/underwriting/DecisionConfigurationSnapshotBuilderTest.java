package com.los.core.service.underwriting;

import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.security.SingleTenantDeploymentGuard;
import com.los.core.service.credit.EffectiveUnderwritingContext;
import com.los.core.service.workflow.ActiveWorkflowConfigService;
import com.los.lms.service.LmsApplicationConfigResolver;
import com.los.lms.service.LmsProductMappingResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecisionConfigurationSnapshotBuilderTest {

    @Mock ActiveWorkflowConfigService activeWorkflowConfigService;
    @Mock LmsApplicationConfigResolver lmsApplicationConfigResolver;
    DecisionConfigurationSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new DecisionConfigurationSnapshotBuilder(
                activeWorkflowConfigService,
                lmsApplicationConfigResolver,
                new SingleTenantDeploymentGuard(
                        "SINGLE_TENANT_DEPLOYMENT", "00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void build_marksImmutableAndCapturesRouting() {
        UUID appId = UUID.randomUUID();
        UUID wfId = UUID.randomUUID();
        LoanApplication app = new LoanApplication();
        app.setId(appId);
        app.setApplicationNumber("APP-SNAP-1");
        app.setLoanProduct("TERM_LOAN");
        app.setRequestedAmount(new BigDecimal("250000"));

        WorkflowConfig wf = new WorkflowConfig();
        wf.setId(wfId);
        wf.setVersion(2);
        wf.setName("Company Term Loan");
        wf.setLmsProductCode("IPPOPAYM01");
        when(activeWorkflowConfigService.findActiveForApplication(any())).thenReturn(Optional.of(wf));
        when(lmsApplicationConfigResolver.resolveEncoreProductMapping(any())).thenReturn(Optional.of(
                new LmsProductMappingResolution(
                        "IPPOPAYM01",
                        LmsProductMappingResolution.SOURCE_WORKFLOW,
                        wfId,
                        2,
                        "TERM_LOAN",
                        "COMPANY")));

        MultiRuleEvalResult multi = new MultiRuleEvalResult(
                List.of(new MultiRuleEvalResult.PerRuleEval(
                        "rule-1", "R1", "APPROVE", "APPROVE", 80, List.of(), "HARD",
                        Map.of(), Map.of())),
                "APPROVE", "APPROVE", 80, List.of());
        EffectiveUnderwritingContext ctx = new EffectiveUnderwritingContext(
                750, true, BigDecimal.ZERO, BigDecimal.ZERO, "MH", "PUNE",
                "MANUAL", "MANUAL", "MANUAL", Map.of());

        Map<String, Object> snap = builder.build(
                app, multi, ctx, UUID.randomUUID(), 3,
                List.of(Map.of("parameter", "BUREAU_SCORE", "valueUsed", 750)),
                Map.of("normalizedScore", 0.8), "tester");

        assertThat(snap.get("immutable")).isEqualTo(true);
        assertThat(snap.get("allowCanonicalAuthority")).isEqualTo(false);
        assertThat(snap.get("snapshotVersion")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> routing = (Map<String, Object>) snap.get("routing");
        assertThat(routing.get("workflowId")).isEqualTo(wfId.toString());
        assertThat(routing.get("workflowVersion")).isEqualTo(2);
        assertThat(routing.get("scorecardVersion")).isEqualTo(3);
    }
}
