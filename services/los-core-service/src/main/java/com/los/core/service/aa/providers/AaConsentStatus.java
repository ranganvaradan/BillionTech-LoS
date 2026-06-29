package com.los.core.service.aa.providers;

import java.util.Map;

public record AaConsentStatus(
        String providerConsentId,
        String status,
        Map<String, Object> rawResponse
) {
}
