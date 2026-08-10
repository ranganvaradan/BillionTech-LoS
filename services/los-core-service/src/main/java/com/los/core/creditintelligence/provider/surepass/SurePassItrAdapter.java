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
 * SurePass ITR adapter — keeps GrossTotalIncome / TotalIncome / total_revenue_from_operations distinct;
 * extracts {@code tds_26as} as FORM_26AS facts.
 */
@Component
@RequiredArgsConstructor
public class SurePassItrAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_ITR";
    public static final String SCHEMA_VERSION = "SUREPASS_ITR_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_ITR_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_ITR_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.ITR;
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
        return data.has("filings") || data.has("tds_26as");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        Map<String, Object> data = root.get("data") instanceof Map<?, ?> d
                ? new LinkedHashMap<>((Map<String, Object>) d)
                : root;

        List<Map<String, Object>> facts = new ArrayList<>();

        if (data.get("profile") instanceof Map<?, ?> profile) {
            facts.add(Map.of(
                    "factType", "ITR_PROFILE",
                    "sourceType", SourceType.ITR.name(),
                    "data", objectMapper.convertValue(profile, Map.class)));
        }

        if (data.get("filings") instanceof List<?> filings) {
            for (Object f : filings) {
                if (!(f instanceof Map<?, ?> filingMap)) {
                    continue;
                }
                Map<String, Object> filing = objectMapper.convertValue(filingMap, Map.class);
                Map<String, Object> incomeHeads = extractDistinctIncomeHeads(filing);
                Map<String, Object> filingFact = new LinkedHashMap<>(filing);
                filingFact.put("incomeHeads", incomeHeads);
                facts.add(Map.of(
                        "factType", "ITR_FILING",
                        "sourceType", SourceType.ITR.name(),
                        "data", filingFact));
            }
        }

        if (data.get("tds_26as") instanceof List<?> tdsList) {
            for (Object t : tdsList) {
                if (t instanceof Map<?, ?>) {
                    Map<String, Object> tdsFact = new LinkedHashMap<>();
                    tdsFact.put("sourceType", SourceType.FORM_26AS.name());
                    tdsFact.putAll(objectMapper.convertValue(t, Map.class));
                    facts.add(Map.of(
                            "factType", "FORM_26AS",
                            "sourceType", SourceType.FORM_26AS.name(),
                            "data", tdsFact));
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractDistinctIncomeHeads(Map<String, Object> filing) {
        Map<String, Object> heads = new LinkedHashMap<>();
        Object parsed = filing.get("parsed_data");
        if (!(parsed instanceof Map<?, ?> parsedMap)) {
            return heads;
        }
        Object itr = ((Map<String, Object>) parsedMap).get("ITR");
        if (!(itr instanceof Map<?, ?> itrMap)) {
            return heads;
        }
        // Prefer first form node under ITR (ITR6, ITR3, ...)
        for (Object formObj : ((Map<String, Object>) itrMap).values()) {
            if (!(formObj instanceof Map<?, ?> form)) {
                continue;
            }
            Map<String, Object> formMap = (Map<String, Object>) form;
            Object partB = formMap.get("PartB-TI");
            if (partB instanceof Map<?, ?> ti) {
                Map<String, Object> tiMap = (Map<String, Object>) ti;
                // Keep distinct — never copy one into another
                if (tiMap.containsKey("GrossTotalIncome")) {
                    heads.put("grossTotalIncome", tiMap.get("GrossTotalIncome"));
                }
                if (tiMap.containsKey("TotalIncome")) {
                    heads.put("totalIncome", tiMap.get("TotalIncome"));
                }
            }
            Object trading = formMap.get("TradingAccount");
            if (trading instanceof Map<?, ?> ta) {
                Map<String, Object> taMap = (Map<String, Object>) ta;
                if (taMap.containsKey("total_revenue_from_operations")) {
                    heads.put("totalRevenueFromOperations", taMap.get("total_revenue_from_operations"));
                    heads.put("salesTurnover", taMap.get("total_revenue_from_operations"));
                }
            }
            break;
        }
        return heads;
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
