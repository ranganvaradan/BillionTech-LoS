package com.los.core.creditintelligence.provider.surepass;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.creditintelligence.domain.SourceType;
import com.los.core.creditintelligence.provider.spi.ExtractionRequest;
import com.los.core.creditintelligence.provider.spi.ProviderAdapter;
import com.los.core.creditintelligence.provider.spi.ProviderExtractionResult;
import com.los.core.creditintelligence.provider.spi.ProviderObservationDraft;
import com.los.core.creditintelligence.provider.support.AccountNumberHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SurePassCibilAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_CIBIL";
    public static final String SCHEMA_VERSION = "SUREPASS_CIBIL_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_CIBIL_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_CIBIL_NORMALIZER_V1";

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
        if (payload == null || !payload.isObject()) {
            return false;
        }
        JsonNode data = payload.has("data") ? payload.get("data") : payload;
        return data.has("credit_report") && data.get("credit_report").isArray();
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

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("pan", data.get("pan"));
        header.put("name", data.get("name"));
        header.put("creditScore", data.get("credit_score"));
        header.put("clientId", data.get("client_id"));
        facts.add(Map.of(
                "factType", "CONSUMER_BUREAU_HEADER",
                "sourceType", SourceType.CONSUMER_BUREAU.name(),
                "data", header));

        if (data.get("credit_report") instanceof List<?> reports) {
            for (Object r : reports) {
                if (!(r instanceof Map<?, ?> reportMap)) {
                    continue;
                }
                Map<String, Object> report = objectMapper.convertValue(reportMap, Map.class);
                if (report.get("accounts") instanceof List<?> accounts) {
                    for (Object a : accounts) {
                        if (a instanceof Map<?, ?> acc) {
                            Map<String, Object> tradeline = new LinkedHashMap<>((Map<String, Object>) acc);
                            Object accNum = tradeline.remove("accountNumber");
                            tradeline.putAll(AccountNumberHasher.hashAndLast4(
                                    accNum != null ? String.valueOf(accNum) : null));
                            facts.add(Map.of(
                                    "factType", "TRADELINE",
                                    "sourceType", SourceType.CONSUMER_BUREAU.name(),
                                    "data", tradeline));
                        }
                    }
                }
                if (report.get("enquiries") instanceof List<?> enquiries) {
                    for (Object e : enquiries) {
                        if (e instanceof Map<?, ?>) {
                            facts.add(Map.of(
                                    "factType", "BUREAU_INQUIRY",
                                    "sourceType", SourceType.CONSUMER_BUREAU.name(),
                                    "data", objectMapper.convertValue(e, Map.class)));
                        }
                    }
                }
                if (report.get("response") instanceof Map<?, ?> response) {
                    observations.add(ProviderObservationDraft.nonAuthoritative(
                            PROVIDER_CODE,
                            "consumer_summary",
                            objectMapper.convertValue(response, Map.class),
                            PARSER_VERSION).toMap());
                }
            }
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
