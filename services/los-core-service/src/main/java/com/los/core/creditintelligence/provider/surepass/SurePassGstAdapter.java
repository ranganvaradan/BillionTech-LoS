package com.los.core.creditintelligence.provider.surepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderAdapter;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.spi.ProviderObservationDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SurePassGstAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_GST";
    public static final String SCHEMA_VERSION = "SUREPASS_GST_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_GST_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_GST_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.GST;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public boolean supports(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        JsonNode data = payload.has("data") ? payload.get("data") : payload;
        return data.has("monthly_data") || data.has("gstin");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> d
                ? new LinkedHashMap<>((Map<String, Object>) d)
                : root;

        List<Map<String, Object>> facts = new ArrayList<>();
        List<Map<String, Object>> observations = new ArrayList<>();

        Map<String, Object> profile = new LinkedHashMap<>();
        if (data.get("gstin") != null) {
            profile.put("gstin", data.get("gstin"));
        }
        if (data.get("business_name") != null) {
            profile.put("businessName", data.get("business_name"));
        }
        if (data.get("profile") instanceof Map<?, ?> p) {
            profile.putAll((Map<String, Object>) p);
        }
        if (!profile.isEmpty()) {
            facts.add(Map.of(
                    "factType", "GST_REGISTRATION",
                    "sourceType", SourceType.GST.name(),
                    "data", profile));
        }

        if (data.get("monthly_data") instanceof List<?> months) {
            for (Object m : months) {
                if (m instanceof Map<?, ?>) {
                    facts.add(Map.of(
                            "factType", "GST_PERIOD",
                            "sourceType", SourceType.GST.name(),
                            "data", objectMapper.convertValue(m, Map.class)));
                }
            }
        }

        if (data.get("analysis_summary") instanceof Map<?, ?> summary) {
            observations.add(ProviderObservationDraft.nonAuthoritative(
                    PROVIDER_CODE,
                    "analysis_summary",
                    objectMapper.convertValue(summary, Map.class),
                    PARSER_VERSION).toMap());
        }
        if (data.get("dashboard") instanceof Map<?, ?> dashboard) {
            observations.add(ProviderObservationDraft.nonAuthoritative(
                    PROVIDER_CODE,
                    "dashboard",
                    objectMapper.convertValue(dashboard, Map.class),
                    PARSER_VERSION).toMap());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (req != null && req.metadata() != null) {
            meta.putAll(req.metadata());
        }
        meta.put("providerCode", PROVIDER_CODE);
        return new ProviderExtractionResult(facts, observations, parserVersion(), normalizerVersion(), meta);
    }

    @Override
    public String parserVersion() {
        return PARSER_VERSION;
    }

    @Override
    public String normalizerVersion() {
        return NORMALIZER_VERSION;
    }
}
