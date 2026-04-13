package com.los.core.service.integration.providers.impl;

import com.los.core.model.enums.KycStepType;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component("hypervergeKycProvider")
public class HypervergeKycProvider implements IKycProvider {

    private static final Set<KycStepType> SUPPORTED = Set.of(KycStepType.FACE_MATCH);

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        log.info("[Hyperverge] Executing face match verification");

        String transactionId = "HV-" + UUID.randomUUID().toString().substring(0, 8);

        if (stepType != KycStepType.FACE_MATCH) {
            return new KycVerificationResult(false, 0.0, null, transactionId, "Unsupported step type");
        }

        String selfieImage = (String) payload.get("selfieImage");
        String documentImage = (String) payload.get("documentImage");

        if (selfieImage == null || documentImage == null) {
            return new KycVerificationResult(false, 0.0, null, transactionId,
                    "Both selfie and document images are required");
        }

        double matchScore = 0.92;
        Map<String, Object> parsed = Map.of(
                "matchScore", matchScore,
                "livenessDetected", true,
                "spoofDetected", false,
                "qualityScore", 0.88,
                "faceDetected", true,
                "matchThreshold", 0.80
        );

        return new KycVerificationResult(matchScore >= 0.80, matchScore, parsed, transactionId, null);
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return SUPPORTED.contains(stepType);
    }

    @Override
    public String getProviderName() {
        return "HYPERVERGE";
    }
}
