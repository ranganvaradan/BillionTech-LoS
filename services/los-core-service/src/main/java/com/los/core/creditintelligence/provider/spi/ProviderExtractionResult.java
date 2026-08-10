package com.los.core.creditintelligence.provider.spi;

import java.util.List;
import java.util.Map;

public record ProviderExtractionResult(
        List<Map<String, Object>> facts,
        List<Map<String, Object>> observations,
        String parserVersion,
        String normalizerVersion,
        Map<String, Object> metadata) {
}
