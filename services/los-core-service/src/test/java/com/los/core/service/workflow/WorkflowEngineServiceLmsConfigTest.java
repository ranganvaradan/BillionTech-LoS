package com.los.core.service.workflow;

import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.enums.BorrowerType;
import com.los.core.repository.WorkflowConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceLmsConfigTest {

    @Mock
    private WorkflowConfigRepository workflowRepository;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void createWorkflow_persistsLmsDefaultsWhenOmitted() {
        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Personal loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.createWorkflow(request);

        ArgumentCaptor<com.los.core.model.entity.WorkflowConfig> captor =
                ArgumentCaptor.forClass(com.los.core.model.entity.WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertEquals("IPPOPAYM01", captor.getValue().getLmsProductCode());
        assertEquals("Month", captor.getValue().getLmsTenureUnit());
    }

    @Test
    void createWorkflow_persistsExplicitLmsFields() {
        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Term loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("TERM_LOAN");
        request.setLmsProductCode("CUSTOM99");
        request.setLmsTenureUnit("Week");
        request.setSteps(List.of());

        when(workflowRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        workflowEngineService.createWorkflow(request);

        ArgumentCaptor<com.los.core.model.entity.WorkflowConfig> captor =
                ArgumentCaptor.forClass(com.los.core.model.entity.WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertEquals("CUSTOM99", captor.getValue().getLmsProductCode());
        assertEquals("Week", captor.getValue().getLmsTenureUnit());
    }
}
