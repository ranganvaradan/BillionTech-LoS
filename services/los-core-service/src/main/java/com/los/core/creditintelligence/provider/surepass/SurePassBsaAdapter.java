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
public class SurePassBsaAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SUREPASS_BSA";
    public static final String SCHEMA_VERSION = "SUREPASS_BSA_SPI_V1";
    public static final String PARSER_VERSION = "SUREPASS_BSA_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SUREPASS_BSA_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.BANK_STATEMENT;
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
        return data.has("account_info") || data.has("transactions");
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

        if (data.get("account_info") instanceof Map<?, ?> info) {
            Map<String, Object> account = new LinkedHashMap<>((Map<String, Object>) info);
            Object accNum = account.remove("account_number");
            account.putAll(AccountNumberHasher.hashAndLast4(accNum != null ? String.valueOf(accNum) : null));
            facts.add(Map.of(
                    "factType", "BANK_ACCOUNT",
                    "sourceType", SourceType.BANK_STATEMENT.name(),
                    "data", account));
        }

        if (data.get("transactions") instanceof List<?> txns) {
            for (Object t : txns) {
                if (t instanceof Map<?, ?>) {
                    facts.add(Map.of(
                            "factType", "BANK_TRANSACTION",
                            "sourceType", SourceType.BANK_STATEMENT.name(),
                            "data", objectMapper.convertValue(t, Map.class)));
                }
            }
        }

        putListFact(facts, data, "mismatched_sequence_date", "MISMATCHED_SEQUENCE");
        putListFact(facts, data, "negative_balance", "NEGATIVE_BALANCE");
        if (data.get("discrepancies") instanceof Map<?, ?> disc) {
            facts.add(Map.of(
                    "factType", "DISCREPANCIES",
                    "sourceType", SourceType.BANK_STATEMENT.name(),
                    "data", objectMapper.convertValue(disc, Map.class)));
        }

        if (data.get("analysis_result") instanceof Map<?, ?> analysis) {
            observations.add(ProviderObservationDraft.nonAuthoritative(
                    PROVIDER_CODE,
                    "analysis_result",
                    objectMapper.convertValue(analysis, Map.class),
                    PARSER_VERSION).toMap());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        if (req != null && req.metadata() != null) {
            meta.putAll(req.metadata());
        }
        meta.put("providerCode", PROVIDER_CODE);
        meta.put("analysisCompleted", data.get("analysis_completed"));
        return new ProviderExtractionResult(facts, observations, parserVersion(), normalizerVersion(), meta);
    }

    private void putListFact(List<Map<String, Object>> facts, Map<String, Object> data, String key, String factType) {
        if (data.get(key) instanceof List<?> list) {
            facts.add(Map.of(
                    "factType", factType,
                    "sourceType", SourceType.BANK_STATEMENT.name(),
                    "data", Map.of("items", list)));
        }
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
