package com.los.core.creditintelligence.provider.surepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderAdapter;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.support.AccountNumberHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Commercial bureau adapter — keeps commercial facts separate from consumer parsers.
 */
@Component
@RequiredArgsConstructor
public class SurePassCommercialBureauAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_COMMERCIAL_BUREAU";
    public static final String SCHEMA_VERSION = "SUREPASS_COMMERCIAL_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_COMMERCIAL_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_COMMERCIAL_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.COMMERCIAL_BUREAU;
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
        JsonNode report = data.path("credit_report");
        return report.isObject()
                && (report.has("CommercialBureauResponseDetails")
                || report.path("CommercialBureauResponseDetails").isObject());
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
        header.put("pan", data.get("pan"));
        header.put("businessName", data.get("business_name"));
        header.put("creditScore", data.get("credit_score"));
        header.put("clientId", data.get("client_id"));
        facts.add(Map.of(
                "factType", "COMMERCIAL_BUREAU_HEADER",
                "sourceType", SourceType.COMMERCIAL_BUREAU.name(),
                "data", header));

        if (data.get("credit_report") instanceof Map<?, ?> report) {
            Map<String, Object> reportMap = (Map<String, Object>) report;
            Object details = reportMap.get("CommercialBureauResponseDetails");
            if (details instanceof Map<?, ?> detailsMap) {
                Map<String, Object> d = (Map<String, Object>) detailsMap;
                if (d.get("IDAndContactInfo") instanceof Map<?, ?> idInfo) {
                    facts.add(Map.of(
                            "factType", "COMMERCIAL_IDENTITY",
                            "sourceType", SourceType.COMMERCIAL_BUREAU.name(),
                            "data", objectMapper.convertValue(idInfo, Map.class)));
                }
                if (d.get("CreditFacilityDetails") instanceof List<?> facilities) {
                    for (Object f : facilities) {
                        if (f instanceof Map<?, ?> fac) {
                            Map<String, Object> facility = new LinkedHashMap<>((Map<String, Object>) fac);
                            Object accNum = facility.remove("AccountNumber");
                            facility.putAll(AccountNumberHasher.hashAndLast4(
                                    accNum != null ? String.valueOf(accNum) : null));
                            facts.add(Map.of(
                                    "factType", "COMMERCIAL_CREDIT_FACILITY",
                                    "sourceType", SourceType.COMMERCIAL_BUREAU.name(),
                                    "data", facility));
                        }
                    }
                }
                if (d.get("RelationshipDetails") instanceof List<?> rels) {
                    for (Object r : rels) {
                        if (r instanceof Map<?, ?>) {
                            facts.add(Map.of(
                                    "factType", "COMMERCIAL_RELATIONSHIP",
                                    "sourceType", SourceType.COMMERCIAL_BUREAU.name(),
                                    "data", objectMapper.convertValue(r, Map.class)));
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
