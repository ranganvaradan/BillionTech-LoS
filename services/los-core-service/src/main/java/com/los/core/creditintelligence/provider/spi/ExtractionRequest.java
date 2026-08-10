package com.los.core.creditintelligence.provider.spi;

import java.util.Map;
import java.util.UUID;

public record ExtractionRequest(
        UUID tenantId,
        UUID applicationId,
        UUID sourceRecordId,
        Map<String, Object> metadata) {
}
