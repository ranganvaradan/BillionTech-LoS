package com.los.core.service.workflow;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.WorkflowConfig;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceImplDeleteWorkflowTest {

    @Mock
    private WorkflowConfigRepository workflowRepository;

    @Mock
    private AdminConfigAuditSupport adminConfigAuditSupport;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void deleteWorkflow_removesRowWhenInactive() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000099");
        WorkflowConfig cfg = WorkflowConfig.builder()
                .name("Draft")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of(Map.of("step", "PAN_VERIFY", "mandatory", true, "order", 1)))
                .active(false)
                .version(1)
                .build();
        cfg.setId(id);
        when(workflowRepository.findById(id)).thenReturn(Optional.of(cfg));

        workflowEngineService.deleteWorkflow(id);

        verify(workflowRepository).delete(ArgumentMatchers.same(cfg));
    }

    @Test
    void deleteWorkflow_rejectsWhenActive() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000088");
        WorkflowConfig cfg = WorkflowConfig.builder()
                .name("Live")
                .borrowerType("INDIVIDUAL")
                .loanProduct("PERSONAL_LOAN")
                .steps(List.of())
                .active(true)
                .version(1)
                .build();
        cfg.setId(id);
        when(workflowRepository.findById(id)).thenReturn(Optional.of(cfg));

        assertThrows(BusinessRuleException.class, () -> workflowEngineService.deleteWorkflow(id));
        verify(workflowRepository, never()).delete(ArgumentMatchers.any());
    }
}
