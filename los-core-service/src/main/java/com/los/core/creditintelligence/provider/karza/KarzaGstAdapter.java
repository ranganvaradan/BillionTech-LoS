package com.los.core.creditintelligence.provider.karza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.gst.provider.KarzaGstCanonicalExtractor;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderAdapter;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KarzaGstAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "KARZA_GST";
    public static final String SCHEMA_VERSION = "KARZA_GST_SPI_V1";
    public static final String NORMALIZER_VERSION = "KARZA_GST_NORMALIZER_V1";

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
        return payload.has("result")
                || (payload.has("fullResponse") && payload.get("fullResponse").has("result"))
                || payload.path("provider").asText("").equalsIgnoreCase("KARZA");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        KarzaGstCanonicalExtractor.ExtractionResult extracted = KarzaGstCanonicalExtractor.extract(root);
        List<Map<String, Object>> facts = new ArrayList<>();
        for (var reg : extracted.registrations()) {
            facts.add(Map.of(
                    "factType", "GST_REGISTRATION",
                    "sourceType", SourceType.GST.name(),
                    "data", objectMapper.convertValue(reg, Map.class)));
        }
        for (var period : extracted.periods()) {
            facts.add(Map.of(
                    "factType", "GST_PERIOD",
                    "sourceType", SourceType.GST.name(),
                    "data", objectMapper.convertValue(period, Map.class)));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        if (req != null && req.metadata() != null) {
            meta.putAll(req.metadata());
        }
        meta.put("providerCode", PROVIDER_CODE);
        return new ProviderExtractionResult(
                facts, List.of(), parserVersion(), normalizerVersion(), meta);
    }

    @Override
    public String parserVersion() {
        return KarzaGstCanonicalExtractor.PARSER_VERSION;
    }

    @Override
    public String normalizerVersion() {
        return NORMALIZER_VERSION;
    }
}
