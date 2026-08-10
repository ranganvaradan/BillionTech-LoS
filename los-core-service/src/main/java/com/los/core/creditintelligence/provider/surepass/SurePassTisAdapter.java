package com.los.core.creditintelligence.provider.surepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * SurePass TIS adapter — keeps reported_by_source / processed_by_system / accepted_by_taxpayer distinct.
 */
@Component
@RequiredArgsConstructor
public class SurePassTisAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_TIS";
    public static final String SCHEMA_VERSION = "SUREPASS_TIS_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_TIS_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_TIS_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.TIS;
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
        return data.has("tis_data") || data.has("pan_no");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> d
                ? new LinkedHashMap<>((Map<String, Object>) d)
                : root;

        List<Map<String, Object>> facts = new ArrayList<>();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("pan", data.get("pan_no"));
        header.put("clientId", data.get("client_id"));
        facts.add(Map.of(
                "factType", "TIS_HEADER",
                "sourceType", SourceType.TIS.name(),
                "data", header));

        if (data.get("tis_data") instanceof Map<?, ?> tis) {
            Map<String, Object> tisMap = (Map<String, Object>) tis;
            facts.add(Map.of(
                    "factType", "TIS_DOCUMENT",
                    "sourceType", SourceType.TIS.name(),
                    "data", Map.of(
                            "documentName", tisMap.get("document_name"),
                            "financialYear", tisMap.get("financial_year"))));

            if (tisMap.get("sections") instanceof List<?> sections) {
                for (Object s : sections) {
                    if (!(s instanceof Map<?, ?> section)) {
                        continue;
                    }
                    Map<String, Object> sectionMap = (Map<String, Object>) section;
                    if (sectionMap.get("elements") instanceof List<?> elements) {
                        for (Object el : elements) {
                            if (!(el instanceof Map<?, ?> element)) {
                                continue;
                            }
                            Map<String, Object> elementMap = (Map<String, Object>) element;
                            if (elementMap.get("summary") instanceof List<?> summary) {
                                for (Object row : summary) {
                                    if (!(row instanceof Map<?, ?> rowMap)) {
                                        continue;
                                    }
                                    Map<String, Object> amounts = new LinkedHashMap<>();
                                    // Keep three amount heads distinct — never collapse
                                    amounts.put("reportedBySource", rowMap.get("reported_by_source"));
                                    amounts.put("processedBySystem", rowMap.get("processed_by_system"));
                                    amounts.put("acceptedByTaxpayer", rowMap.get("accepted_by_taxpayer"));
                                    amounts.put("part", rowMap.get("part"));
                                    amounts.put("informationDescription", rowMap.get("information_description"));
                                    amounts.put("informationSource", rowMap.get("information_source"));
                                    amounts.put("sectionTitle", sectionMap.get("title"));
                                    amounts.put("elementTitle", elementMap.get("title"));
                                    facts.add(Map.of(
                                            "factType", "TIS_AMOUNT_ROW",
                                            "sourceType", SourceType.TIS.name(),
                                            "data", amounts));
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (req != null && req.metadata() != null) {
            meta.putAll(req.metadata());
        }
        meta.put("providerCode", PROVIDER_CODE);
        return new ProviderExtractionResult(facts, List.of(), parserVersion(), normalizerVersion(), meta);
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
