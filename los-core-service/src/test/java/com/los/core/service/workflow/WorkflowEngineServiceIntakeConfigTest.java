package com.los.core.service.workflow;

import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.WorkflowConfigRepository;
import com.los.core.service.workflow.intake.KycStepIntakeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceIntakeConfigTest {

    @Mock
    private WorkflowConfigRepository workflowRepository;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void updateWorkflow_persistsIntakePolicyChange() {
        UUID workflowId = UUID.randomUUID();
        WorkflowConfig existing = WorkflowConfig.builder()
                .id(workflowId)
                .name("Personal loan")
                .borrowerType(BorrowerType.INDIVIDUAL.name())
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .intakeConfig(KycStepIntakeCatalog.legacyIntakeConfig())
                .steps(List.of())
                .active(false)
                .version(1)
                .build();

        Map<String, Object> workflowDriven = KycStepIntakeCatalog.defaultWorkflowDrivenIntakeConfig();
        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Personal loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());
        request.setIntakeConfig(new LinkedHashMap<>(workflowDriven));

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.updateWorkflow(workflowId, request);

        ArgumentCaptor<WorkflowConfig> captor = ArgumentCaptor.forClass(WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertThat(captor.getValue().getIntakeConfig())
                .containsEntry("policy", KycStepIntakeCatalog.POLICY_WORKFLOW_DRIVEN);
    }
}
