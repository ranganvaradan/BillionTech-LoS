package com.los.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;
import java.util.UUID;

/**
 * Read model for the integration provider matrix (source: {@code aggregator_routing}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationProviderMatrixRow(
        UUID id,
        String integrationType,
        String kycStepType,
        String esignStepType,
        String providerName,
        int priority,
        boolean allowFallback,
        boolean active,
        Map<String, Object> metadata
) {
}
