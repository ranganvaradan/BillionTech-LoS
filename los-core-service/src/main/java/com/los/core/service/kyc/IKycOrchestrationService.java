package com.los.core.service.kyc;

import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.enums.KycStepType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IKycOrchestrationService {

    default KycStepResultResponse executeStep(
            UUID applicationId, KycStepType stepType, Map<String, Object> payload) {
        return executeStep(applicationId, stepType, payload, null);
    }

    /**
     * @param workflowPreferredProvider from {@code workflow_configs.steps[].provider} (e.g. AUTHBRIDGE);
     *                               pass null for API/manual single-step calls with no workflow hint.
     */
    KycStepResultResponse executeStep(
            UUID applicationId,
            KycStepType stepType,
            Map<String, Object> payload,
            String workflowPreferredProvider);

    List<KycStepResultResponse> getStepResults(UUID applicationId);

    KycStepResultResponse overrideStep(UUID stepResultId, String reason, UUID overrideBy);

    List<KycStepResultResponse> executeWorkflow(UUID applicationId, Map<String, Object> payload);

    Map<String, Object> computeKycOutcome(UUID applicationId);
}
