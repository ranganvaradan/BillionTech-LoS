package com.los.core.service.workflow.coordinator;

import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.LoanApplicationRepository;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.flow.step.StepExecutionRecordingService;
import com.los.core.service.flow.step.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowExecutionCoordinatorTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private WorkflowConfigRepository workflowConfigRepository;
    @Mock
    private StepExecutionRecordingService stepExecutionRecordingService;

    private LoanApplication app;
    private UUID id;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        app = new LoanApplication();
        app.setId(id);
        app.setBorrowerType(BorrowerType.INDIVIDUAL);
        app.setLoanProduct("PERSONAL_LOAN");
        when(loanApplicationRepository.findById(id)).thenReturn(Optional.of(app));
    }

    @Test
    void getResolvedOrder_matchesResolverForActiveConfig() {
        WorkflowConfig cfg = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("w")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(Map.of("step", "PAN_VERIFY", "order", 1)))
                .active(true)
                .version(1)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndActiveTrue("INDIVIDUAL", "PERSONAL_LOAN"))
                .thenReturn(Optional.of(cfg));

        WorkflowExecutionCoordinator c = newWorkflowCoordinator(false, WorkflowResolutionOptions.defaults());
        assertEquals(
                List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL),
                c.getResolvedOrderedFlowStepTypes(id));
    }

    @Test
    void executeFlowStep_usesRecordingService() {
        when(stepExecutionRecordingService.executeWithRecording(
                eq(FlowStepType.KYC_WORKFLOW), eq(id), anyMap()))
                .thenReturn(StepResult.ok(Map.of("allPassed", true)));
        WorkflowExecutionCoordinator c = newWorkflowCoordinator(false, WorkflowResolutionOptions.defaults());
        StepResult r = c.executeFlowStepForApplication(
                id, FlowStepType.KYC_WORKFLOW, Map.of("kycPayload", Map.of()));
        assertEquals(true, r.output().get("allPassed"));
        verify(stepExecutionRecordingService).executeWithRecording(
                eq(FlowStepType.KYC_WORKFLOW), eq(id), anyMap());
    }

    @Test
    void strictMode_bureauNotInResolvedOrder_fails() {
        WorkflowConfig cfg = WorkflowConfig.builder()
                .id(UUID.randomUUID())
                .name("w")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(Map.of("step", "PAN_VERIFY", "order", 1)))
                .active(true)
                .version(1)
                .build();
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndActiveTrue("INDIVIDUAL", "PERSONAL_LOAN"))
                .thenReturn(Optional.of(cfg));
        // No BUREAU in JSON + addBureauWhenMissingFromConfig = false  -> resolved [KYC_WORKFLOW] only
        var noBureau = new WorkflowResolutionOptions(false, List.of(FlowStepType.KYC_WORKFLOW, FlowStepType.BUREAU_PULL));
        WorkflowExecutionCoordinator c = newWorkflowCoordinator(true, noBureau);
        assertThrows(BusinessRuleException.class, () -> c.executeFlowStepForApplication(id, FlowStepType.BUREAU_PULL, Map.of()));
    }

    @Test
    void strictMode_postSanctionSteps_alwaysAllowed() {
        when(stepExecutionRecordingService.executeWithRecording(
                eq(FlowStepType.DISBURSE), eq(id), anyMap()))
                .thenReturn(StepResult.ok(Map.of("d", 1)));
        WorkflowExecutionCoordinator c = newWorkflowCoordinator(true, WorkflowResolutionOptions.defaults());
        assertEquals(1, c.executeFlowStepForApplication(id, FlowStepType.DISBURSE, Map.of()).output().get("d"));
    }

    @Test
    void strictMode_noConfigRow_allowsKyc() {
        when(workflowConfigRepository.findByBorrowerTypeAndLoanProductAndActiveTrue("INDIVIDUAL", "PERSONAL_LOAN"))
                .thenReturn(Optional.empty());
        when(stepExecutionRecordingService.executeWithRecording(
                eq(FlowStepType.KYC_WORKFLOW), eq(id), anyMap()))
                .thenReturn(StepResult.ok(Map.of("k", 1)));
        WorkflowExecutionCoordinator c = newWorkflowCoordinator(true, WorkflowResolutionOptions.defaults());
        assertEquals(1, c.executeFlowStepForApplication(id, FlowStepType.KYC_WORKFLOW, Map.of("kycPayload", Map.of())).output().get("k"));
    }

    private WorkflowExecutionCoordinator newWorkflowCoordinator(boolean strict, WorkflowResolutionOptions options) {
        WorkflowExecutionCoordinator c = new WorkflowExecutionCoordinator(
                loanApplicationRepository, workflowConfigRepository, stepExecutionRecordingService, options);
        ReflectionTestUtils.setField(c, "stepValidationStrict", strict);
        return c;
    }
}
