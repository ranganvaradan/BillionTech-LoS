package com.los.core.service.aa.providers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AaConsentRequest(
        UUID applicationId,
        UUID customerId,
        List<String> fiTypes,
        String aaName,
        Map<String, Object> purpose,
        String consentHandle,
        Instant consentStartDate,
        Instant consentExpiryDate,
        String fetchFrequency,
        String consentMode
) {
}
