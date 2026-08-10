package com.los.core.creditintelligence.validation.model;

import com.los.core.creditintelligence.validation.domain.DataOrigin;

import java.util.List;
import java.util.Map;

public record ProviderStackResult(
        String provider,
        String sourceType,
        DataOrigin origin,
        String parserVersion,
        String normalizerVersion,
        int entityCount,
        int factCount,
        int observationCount,
        List<String> errors,
        List<String> warnings,
        List<String> ignoredHighValueFields,
        Map<String, Object> metadata
) {
}
