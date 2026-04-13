package com.los.core.service.workflow;

import com.los.core.model.dto.request.WorkflowConfigRequest;
import com.los.core.model.dto.response.WorkflowConfigResponse;
import com.los.core.model.enums.BorrowerType;

import java.util.List;
import java.util.UUID;

public interface IWorkflowEngineService {

    WorkflowConfigResponse createWorkflow(WorkflowConfigRequest request);

    WorkflowConfigResponse updateWorkflow(UUID workflowId, WorkflowConfigRequest request);

    WorkflowConfigResponse getActiveWorkflow(BorrowerType borrowerType, String loanProduct);

    List<WorkflowConfigResponse> listWorkflows();

    void activateWorkflow(UUID workflowId);

    void deactivateWorkflow(UUID workflowId);
}
