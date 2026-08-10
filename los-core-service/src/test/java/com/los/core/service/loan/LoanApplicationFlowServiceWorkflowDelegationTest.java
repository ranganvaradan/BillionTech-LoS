package com.los.core.service.loan;

import com.los.core.repository.LoanApplicationRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.credit.ICreditDecisionService;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.flow.step.StepResult;
import com.los.core.service.kfs.KfsPdfGenerationService;
import com.los.core.service.kfs.KfsService;
import com.los.core.service.kyc.IKycOrchestrationService;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the four IStepExecutor-backed methods delegate to {@link WorkflowExecutionCoordinator}
 * (and thus through {@link com.los.core.service.flow.step.StepExecutionRecordingService} in production).
 */
@ExtendWith(MockitoExtension.class)
class LoanApplicationFlowServiceWorkflowDelegationTest {

    @Mock
    private LoanApplicationRepository applicationRepository;
    @Mock
    private IKycOrchestrationService kycOrchestrationService;
    @Mock
    private WorkflowExecutionCoordinator workflowExecutionCoordinator;
    @Mock
    private ICreditDecisionService creditDecisionService;
    @Mock
    private KfsService kfsService;
    @Mock
    private KfsPdfGenerationService kfsPdfGenerationService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private LoanApplicationFlowService flowService;

    @Test
    void runKyc_delegatesToCoordinator() {
        UUID id = UUID.randomUUID();
        Map<String, Object> payload = Map.of("pan", "X");
        when(workflowExecutionCoordinator.executeFlowStepForApplication(
                eq(id), eq(FlowStepType.KYC_WORKFLOW), anyMap()))
                .thenReturn(StepResult.ok(Map.of("allPassed", true)));
        assertTrue((Boolean) flowService.runKycWorkflow(id, payload).get("allPassed"));
        verify(workflowExecutionCoordinator).executeFlowStepForApplication(
                eq(id), eq(FlowStepType.KYC_WORKFLOW), argThat(m -> m.get("kycPayload").equals(payload)));
    }

    @Test
    void pullBureau_delegatesToCoordinator() {
        UUID id = UUID.randomUUID();
        when(workflowExecutionCoordinator.executeFlowStepForApplication(
                id, FlowStepType.BUREAU_PULL, Map.of()))
                .thenReturn(StepResult.ok(Map.of("success", true)));
        assertEquals(true, flowService.pullBureauReport(id).get("success"));
    }
}
