package com.los.core.service.workflow;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceImplActivateWorkflowTest {

    @Mock
    private WorkflowConfigRepository workflowRepository;

    @Mock
    private AdminConfigAuditSupport adminConfigAuditSupport;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void activateWorkflow_activatesWithoutDeactivatingOthers() {
        UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        WorkflowConfig target = workflowConfig(targetId, false);
        when(workflowRepository.findById(targetId)).thenReturn(Optional.of(target));

        workflowEngineService.activateWorkflow(targetId);

        assertTrue(target.isActive());
        verify(workflowRepository).save(target);
    }

    @Test
    void activateWorkflow_throwsWhenWorkflowMissing() {
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        when(workflowRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> workflowEngineService.activateWorkflow(missingId));
        verify(workflowRepository, never()).save(any());
    }

    private static WorkflowConfig workflowConfig(UUID id, boolean active) {
        WorkflowConfig cfg = WorkflowConfig.builder()
                .name("Test workflow")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .intakeSegment("BORROWER")
                .steps(List.of(Map.of("stepType", "PAN_VERIFY", "mandatory", true, "order", 1)))
                .active(active)
                .version(1)
                .build();
        cfg.setId(id);
        return cfg;
    }
}
