package com.los.core.creditintelligence.provider.spi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Draft observation fields prior to persistence as {@code CiProviderObservation}.
 */
public record ProviderObservationDraft(
        String provider,
        String observationCode,
        Map<String, Object> value,
        String valueType,
        String providerAnalysisVersion,
        BigDecimal confidence,
        LocalDate periodFrom,
        LocalDate periodTo,
        List<Object> evidenceRefs,
        boolean authoritative,
        Map<String, Object> metadata) {

    public static ProviderObservationDraft nonAuthoritative(
            String provider,
            String observationCode,
            Map<String, Object> value,
            String analysisVersion) {
        return new ProviderObservationDraft(
                provider,
                observationCode,
                value,
                "JSON",
                analysisVersion,
                null,
                null,
                null,
                List.of(),
                false,
                Map.of());
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "provider", provider != null ? provider : "",
                "observationCode", observationCode != null ? observationCode : "",
                "value", value != null ? value : Map.of(),
                "valueType", valueType != null ? valueType : "JSON",
                "providerAnalysisVersion", providerAnalysisVersion != null ? providerAnalysisVersion : "",
                "authoritative", authoritative,
                "metadata", metadata != null ? metadata : Map.of());
    }
}
