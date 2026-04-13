package com.los.core.service.integration.providers;

import com.los.core.model.enums.KycStepType;

import java.util.Map;

public interface IKycProvider {

    KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload);

    boolean supports(KycStepType stepType);

    String getProviderName();

    record KycVerificationResult(
            boolean verified,
            double confidenceScore,
            Map<String, Object> parsedData,
            String rawResponse,
            String transactionId,
            String errorMessage
    ) {}
}
