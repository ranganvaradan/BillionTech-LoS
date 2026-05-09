package com.los.core.service.integration.providers.impl;

import com.los.core.model.enums.KycStepType;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * CKYC central registry: routing matrix lists {@code CKYC_REGISTRY} as the sole provider.
 * Download delegates to {@link AuthbridgeKycProvider}; upload is a demo-safe local completion until upload API is wired.
 */
@Component("ckycRegistryKycProvider")
@RequiredArgsConstructor
public class CkycRegistryKycProvider implements IKycProvider {

    private final AuthbridgeKycProvider authbridgeKycProvider;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        if (stepType == KycStepType.CKYC_DOWNLOAD && authbridgeKycProvider.supports(KycStepType.CKYC_DOWNLOAD)) {
            return authbridgeKycProvider.verify(KycStepType.CKYC_DOWNLOAD, payload);
        }
        if (stepType == KycStepType.CKYC_UPLOAD) {
            return new KycVerificationResult(
                    true,
                    1.0,
                    Map.of("recordStatus", "ACCEPTED"),
                    "CKYC-UP-" + UUID.randomUUID().toString().substring(0, 8),
                    null);
        }
        return new KycVerificationResult(false, 0.0, null, null, "CKYC step not available");
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return stepType == KycStepType.CKYC_DOWNLOAD || stepType == KycStepType.CKYC_UPLOAD;
    }

    @Override
    public String getProviderName() {
        return "CKYC_REGISTRY";
    }

    @Override
    public int getPriority() {
        return 5;
    }
}
