package com.los.core.service.kyc;

import com.los.core.model.dto.response.KycStepResultResponse;
import com.los.core.model.enums.KycStepType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IKycOrchestrationService {

    KycStepResultResponse executeStep(UUID applicationId, KycStepType stepType, Map<String, Object> payload);

    List<KycStepResultResponse> getStepResults(UUID applicationId);

    KycStepResultResponse overrideStep(UUID stepResultId, String reason, UUID overrideBy);

    List<KycStepResultResponse> executeWorkflow(UUID applicationId, Map<String, Object> payload);

    Map<String, Object> computeKycOutcome(UUID applicationId);
}
