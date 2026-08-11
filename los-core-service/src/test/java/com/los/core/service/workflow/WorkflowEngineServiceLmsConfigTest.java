package com.los.core.service.workflow;

import com.los.core.audit.AdminConfigAuditSupport;
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
    @Mock
    private AdminConfigAuditSupport adminConfigAuditSupport;

    @InjectMocks
    private WorkflowEngineServiceImpl workflowEngineService;

    @Test
    void createWorkflow_leavesLmsProductCodeNullWhenOmitted_failClosedAtOpen() {
        WorkflowConfigRequest request = new WorkflowConfigRequest();
        request.setName("Personal loan");
        request.setBorrowerType(BorrowerType.INDIVIDUAL);
        request.setLoanProduct("PERSONAL_LOAN");
        request.setSteps(List.of());

        when(workflowRepository.save(any())).thenAnswer(inv -> {
            com.los.core.model.entity.WorkflowConfig c = inv.getArgument(0);
            c.setId(java.util.UUID.randomUUID());
            return c;
        });

        workflowEngineService.createWorkflow(request);

        ArgumentCaptor<com.los.core.model.entity.WorkflowConfig> captor =
                ArgumentCaptor.forClass(com.los.core.model.entity.WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        // LMS-PRODUCT-MAPPING-P0: no silent IPPOPAYM01 seed on create
        assertEquals(null, captor.getValue().getLmsProductCode());
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

        when(workflowRepository.save(any())).thenAnswer(inv -> {
            com.los.core.model.entity.WorkflowConfig c = inv.getArgument(0);
            c.setId(java.util.UUID.randomUUID());
            return c;
        });

        workflowEngineService.createWorkflow(request);

        ArgumentCaptor<com.los.core.model.entity.WorkflowConfig> captor =
                ArgumentCaptor.forClass(com.los.core.model.entity.WorkflowConfig.class);
        verify(workflowRepository).save(captor.capture());
        assertEquals("CUSTOM99", captor.getValue().getLmsProductCode());
        assertEquals("Week", captor.getValue().getLmsTenureUnit());
    }
}
