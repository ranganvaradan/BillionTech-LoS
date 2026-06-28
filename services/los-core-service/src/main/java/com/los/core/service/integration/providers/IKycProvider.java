package com.los.core.service.integration.providers;

import com.los.core.model.enums.KycStepType;

import java.util.Map;

public interface IKycProvider {

    KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload);

    boolean supports(KycStepType stepType);

    default int getPriority() {
        return 0;
    }

    String getProviderName();

    record KycVerificationResult(
            boolean success,
            double confidenceScore,
            Map<String, Object> parsedData,
            String transactionId,
            String errorMessage
    ) {}
}
