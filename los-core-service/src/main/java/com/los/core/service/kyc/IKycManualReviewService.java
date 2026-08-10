package com.los.core.service.kyc;

import com.los.core.model.dto.request.ManualKycReviewRequest;
import com.los.core.model.dto.response.ManualKycReviewResponse;
import com.los.core.model.enums.KycStepType;

import java.util.List;
import java.util.UUID;

public interface IKycManualReviewService {

    ManualKycReviewResponse save(UUID applicationId, KycStepType stepType, ManualKycReviewRequest request, UUID reviewedBy);

    List<ManualKycReviewResponse> getAll(UUID applicationId);

    ManualKycReviewResponse getByStep(UUID applicationId, KycStepType stepType);
}
