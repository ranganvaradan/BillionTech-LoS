package com.los.core.creditintelligence.provider.setu;

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
 * Minimal Account Aggregator (Setu AA) FI payload adapter — Account / Transactions shape.
 */
@Component
@RequiredArgsConstructor
public class SetuAaAdapter implements ProviderAdapter {

    public static final String PROVIDER_CODE = "SETU_AA";
    public static final String SCHEMA_VERSION = "SETU_AA_SPI_V1";
    public static final String PARSER_VERSION = "SETU_AA_PARSER_V1";
    public static final String NORMALIZER_VERSION = "SETU_AA_NORMALIZER_V1";

    private final ObjectMapper objectMapper;

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.ACCOUNT_AGGREGATOR;
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
        return payload.has("Account")
                || payload.has("account")
                || (payload.has("Transactions") || payload.has("transactions"))
                || payload.path("type").asText("").equalsIgnoreCase("deposit")
                || payload.path("provider").asText("").toUpperCase().contains("SETU");
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderExtractionResult extract(JsonNode payload, ExtractionRequest req) {
        Map<String, Object> root = objectMapper.convertValue(payload, Map.class);
        List<Map<String, Object>> facts = new ArrayList<>();

        Object accountObj = root.containsKey("Account") ? root.get("Account") : root.get("account");
        if (accountObj instanceof Map<?, ?> acc) {
            Map<String, Object> account = new LinkedHashMap<>((Map<String, Object>) acc);
            Object masked = account.remove("maskedAccNumber");
            Object accNum = account.remove("accountNumber");
            String raw = masked != null ? String.valueOf(masked)
                    : (accNum != null ? String.valueOf(accNum) : null);
            account.putAll(AccountNumberHasher.hashAndLast4(raw));
            facts.add(Map.of(
                    "factType", "AA_ACCOUNT",
                    "sourceType", SourceType.ACCOUNT_AGGREGATOR.name(),
                    "data", account));
        }

        Object txObj = root.containsKey("Transactions") ? root.get("Transactions") : root.get("transactions");
        if (txObj instanceof Map<?, ?> txMap && txMap.get("Transaction") instanceof List<?> list) {
            for (Object t : list) {
                if (t instanceof Map<?, ?>) {
                    facts.add(Map.of(
                            "factType", "AA_TRANSACTION",
                            "sourceType", SourceType.ACCOUNT_AGGREGATOR.name(),
                            "data", objectMapper.convertValue(t, Map.class)));
                }
            }
        } else if (txObj instanceof List<?> list) {
            for (Object t : list) {
                if (t instanceof Map<?, ?>) {
                    facts.add(Map.of(
                            "factType", "AA_TRANSACTION",
                            "sourceType", SourceType.ACCOUNT_AGGREGATOR.name(),
                            "data", objectMapper.convertValue(t, Map.class)));
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
