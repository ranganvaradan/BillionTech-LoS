package com.los.core.service.aa.providers;

import java.util.Map;

public record AaConsentResponse(
        String consentHandle,
        String providerConsentId,
        String redirectUrl,
        String status,
        Map<String, Object> rawResponse
) {
}
