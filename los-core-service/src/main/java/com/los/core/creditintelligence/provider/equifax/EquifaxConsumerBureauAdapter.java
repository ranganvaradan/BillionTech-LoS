package com.los.core.creditintelligence.provider.equifax;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.bureau.provider.EquifaxBureauAccountExtractor;
import com.los.core.creditintelligence.domain.SourceType;
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
public class EquifaxConsumerBureauAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "EQUIFAX_CONSUMER";
    public static final String SCHEMA_VERSION = "EQUIFAX_CONSUMER_SPI_V1";
    public static final String NORMALIZER_VERSION = "EQUIFAX_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CONSUMER_BUREAU;
    }

    @Override
    public String schemaVersion() {
        return SCHEMA_VERSION;
    }

    @Override
    public boolean supports(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return false;
        }
        if (payload.isTextual()) {
            String xml = payload.asText();
            return xml.contains("InquiryResponse") || xml.contains("equifax");
        }
        return payload.has("xml") || payload.has("rawXml") || payload.has("InquiryResponse");
    }

    @Override
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        String xml = resolveXml(payload);
        Map<String, Object> base = new LinkedHashMap<>();
        if (payload != null && payload.isObject()) {
            base.putAll(objectMapper.convertValue(payload, Map.class));
            base.remove("xml");
            base.remove("rawXml");
        }
        Map<String, Object> enriched = EquifaxBureauAccountExtractor.enrichFromXml(xml, base);
        List<Map<String, Object>> facts = new ArrayList<>();
        facts.add(Map.of(
                "factType", "BUREAU_REPORT",
                "sourceType", SourceType.CONSUMER_BUREAU.name(),
                "data", enriched));
        if (enriched.get("accounts") instanceof List<?> accounts) {
            for (Object a : accounts) {
                if (a instanceof Map<?, ?> m) {
                    facts.add(Map.of(
                            "factType", "TRADELINE",
                            "sourceType", SourceType.CONSUMER_BUREAU.name(),
                            "data", objectMapper.convertValue(m, Map.class)));
                }
            }
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        if (req != null && req.metadata() != null) {
            meta.putAll(req.metadata());
        }
        meta.put("providerCode", PROVIDER_CODE);
        return new ProviderExtractionResult(
                facts,
                List.of(),
                parserVersion(),
                normalizerVersion(),
                meta);
    }

    @Override
    public String parserVersion() {
        return EquifaxBureauAccountExtractor.PARSER_VERSION;
    }

    @Override
    public String normalizerVersion() {
        return NORMALIZER_VERSION;
    }

    private String resolveXml(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "";
        }
        if (payload.isTextual()) {
            return payload.asText();
        }
        if (payload.has("xml") && payload.get("xml").isTextual()) {
            return payload.get("xml").asText();
        }
        if (payload.has("rawXml") && payload.get("rawXml").isTextual()) {
            return payload.get("rawXml").asText();
        }
        return payload.toString();
    }
}
