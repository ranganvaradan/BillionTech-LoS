package com.los.core.creditintelligence.aiunderwriter.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What-if scenario request — Decision Engine computes; AI only explains.
 */
public record AiScenarioRequest(
        UUID tenantId,
        UUID applicationId,
        UUID analysisRequestId,
        BigDecimal loanAmount,
        Integer tenureMonths,
        Map<String, Object> collateral,
        Map<String, Object> baseRecommendationSnapshot,
        Map<String, Object> decisionInputOverrides
) {
    public AiScenarioRequest {
        collateral = collateral == null ? Map.of() : Map.copyOf(collateral);
        baseRecommendationSnapshot = baseRecommendationSnapshot == null
                ? Map.of() : Map.copyOf(baseRecommendationSnapshot);
        decisionInputOverrides = decisionInputOverrides == null
                ? Map.of() : Map.copyOf(decisionInputOverrides);
    }
}
