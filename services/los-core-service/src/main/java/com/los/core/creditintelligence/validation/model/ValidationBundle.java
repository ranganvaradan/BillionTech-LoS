package com.los.core.creditintelligence.validation.model;

import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory representation of a multi-source validation bundle (bundle.json + fixture refs).
 */
public record ValidationBundle(
        ValidationCaseCode caseCode,
        DataOrigin dataOrigin,
        Map<String, String> sources,
        Map<String, BigDecimal> metricStubs,
        Map<String, Object> legacyScorecardStub,
        BigDecimal declaredEmi,
        Map<String, Object> metadata
) {
    public ValidationBundle {
        sources = sources != null ? Map.copyOf(sources) : Map.of();
        metricStubs = metricStubs != null ? Map.copyOf(metricStubs) : Map.of();
        legacyScorecardStub = legacyScorecardStub != null
                ? Map.copyOf(new LinkedHashMap<>(legacyScorecardStub))
                : Map.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}
