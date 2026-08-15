package com.los.core.service.workflow;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceVersionSemanticsTest {

    @Mock
    private WorkflowConfigRepository workflowRepository;

    @Mock
    private AdminConfigAuditSupport adminConfigAuditSupport;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void createWorkflow_startsAtVersion1() {
        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Vikasam Business Loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.save(any())).thenAnswer(inv -> {
            WorkflowConfig c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });

        WorkflowConfigResponse created = workflowEngineService.createWorkflow(request);

        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.isActive()).isFalse();
    }

    @Test
    void updateWorkflow_draftDoesNotInflateVersion() {
        UUID workflowId = UUID.randomUUID();
        WorkflowConfig existing = WorkflowConfig.builder()
                .id(workflowId)
                .name("Vikasam Business Loan")
                .borrowerType(BorrowerType.INDIVIDUAL.name())
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(false)
                .version(1)
                .build();

        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Vikasam Business Loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.updateWorkflow(workflowId, request);
        workflowEngineService.updateWorkflow(workflowId, request);
        workflowEngineService.updateWorkflow(workflowId, request);

        ArgumentCaptor<WorkflowConfig> captor = ArgumentCaptor.forClass(WorkflowConfig.class);
        verify(workflowRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().isActive()).isFalse();
    }

    @Test
    void updateWorkflow_activeIncrementsVersion() {
        UUID workflowId = UUID.randomUUID();
        WorkflowConfig existing = WorkflowConfig.builder()
                .id(workflowId)
                .name("Vikasam Business Loan")
                .borrowerType(BorrowerType.INDIVIDUAL.name())
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(true)
                .version(1)
                .build();

        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Vikasam Business Loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.updateWorkflow(workflowId, request);

        ArgumentCaptor<WorkflowConfig> captor = ArgumentCaptor.forClass(WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(2);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void updateWorkflow_preservesWorkflowIdWhenActiveVersionIncrements() {
        UUID workflowId = UUID.randomUUID();
        WorkflowConfig existing = WorkflowConfig.builder()
                .id(workflowId)
                .name("Bound journey")
                .borrowerType(BorrowerType.INDIVIDUAL.name())
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(true)
                .version(5)
                .build();

        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Bound journey");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.updateWorkflow(workflowId, request);

        ArgumentCaptor<WorkflowConfig> captor = ArgumentCaptor.forClass(WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(6);
        assertThat(captor.getValue().getId()).isEqualTo(workflowId);
    }

    @Test
    void updateWorkflow_healsInflatedDraftVersionBackTo1() {
        UUID workflowId = UUID.randomUUID();
        WorkflowConfig existing = WorkflowConfig.builder()
                .id(workflowId)
                .name("Vikasam Business Loan")
                .borrowerType(BorrowerType.INDIVIDUAL.name())
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of())
                .active(false)
                .version(8)
                .build();

        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Vikasam Business Loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.findById(workflowId)).thenReturn(Optional.of(existing));
        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.updateWorkflow(workflowId, request);

        ArgumentCaptor<WorkflowConfig> captor = ArgumentCaptor.forClass(WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getId()).isEqualTo(workflowId);
    }
}
