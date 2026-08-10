package com.los.core.service.collateral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.CollateralProperties;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.dto.GoldValuationResult;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Gold standard valuation using live MCX/IBJA gold rates or simulation.
 *
 * <p>Pure Gold Value = Weight(grams) × Purity(%) × Live Rate(per gram)
 * Accepted Value = Pure Gold Value × (1 - haircut)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoldValuationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Duration RATE_CACHE_TTL = Duration.ofHours(1);

    private final IntegrationProperties integrationProperties;
    private final CollateralProperties collateralProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    private volatile CachedRate cachedRate;

    public GoldValuationResult calculateGoldValue(BigDecimal weightGrams,
                                                    BigDecimal purityPercent,
                                                    String articleDescription) {
        validateInputs(weightGrams, purityPercent);

        RateSnapshot rate = resolveLiveRate();
        BigDecimal purityFactor = purityPercent.divide(HUNDRED, 6, RoundingMode.HALF_UP);
        BigDecimal pureGoldWeight = weightGrams.multiply(purityFactor).setScale(4, RoundingMode.HALF_UP);
        BigDecimal marketValue = pureGoldWeight.multiply(rate.ratePerGram()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal haircut = collateralProperties.getGold().getHaircut();
        BigDecimal acceptedValue = marketValue.multiply(BigDecimal.ONE.subtract(haircut))
                .setScale(2, RoundingMode.HALF_UP);

        log.info("[GoldValuation] weight={}g purity={}% rate={} market={} accepted={} article={}",
                weightGrams, purityPercent, rate.ratePerGram(), marketValue, acceptedValue,
                articleDescription != null ? articleDescription : "N/A");

        return new GoldValuationResult(
                weightGrams,
                purityPercent,
                rate.ratePerGram(),
                pureGoldWeight,
                marketValue,
                haircut,
                acceptedValue,
                collateralProperties.getGold().getMaxLtv(),
                rate.timestamp()
        );
    }

    public BigDecimal fetchLiveGoldRate() {
        return resolveLiveRate().ratePerGram();
    }

    private void validateInputs(BigDecimal weightGrams, BigDecimal purityPercent) {
        if (weightGrams == null || weightGrams.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gold weight in grams must be greater than zero.");
        }
        if (purityPercent == null || purityPercent.compareTo(BigDecimal.ZERO) <= 0
                || purityPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Gold purity must be between 0 and 100 percent.");
        }
    }

    private RateSnapshot resolveLiveRate() {
        IntegrationProperties.GoldRateProperties config = integrationProperties.getGoldRate();

        if (config.isSimulation() || isBlankApiKeyForLiveFetch(config)) {
            return simulatedRate(config);
        }

        if ("MANUAL".equalsIgnoreCase(config.getProvider())) {
            return new RateSnapshot(config.getManualRatePerGram(), Instant.now());
        }

        CachedRate existing = cachedRate;
        if (existing != null && existing.expiresAt.isAfter(Instant.now())) {
            return existing.snapshot;
        }

        RateSnapshot fetched = fetchRateFromProvider(config);
        cachedRate = new CachedRate(fetched, Instant.now().plus(RATE_CACHE_TTL));
        return fetched;
    }

    private static boolean isBlankApiKeyForLiveFetch(IntegrationProperties.GoldRateProperties config) {
        return !"MANUAL".equalsIgnoreCase(config.getProvider())
                && (config.getApiKey() == null || config.getApiKey().isBlank());
    }

    private RateSnapshot simulatedRate(IntegrationProperties.GoldRateProperties config) {
        log.info("[GoldValuation] Simulation mode — rate {} INR/gram", config.getSimulationRatePerGram());
        return new RateSnapshot(config.getSimulationRatePerGram(), Instant.now());
    }

    private RateSnapshot fetchRateFromProvider(IntegrationProperties.GoldRateProperties config) {
        String provider = config.getProvider() != null ? config.getProvider().toUpperCase() : "MCX";
        String transactionId = "GOLD-" + UUID.randomUUID().toString().substring(0, 8);
        Instant requestTime = Instant.now();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getRateUrl()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .GET();
            if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + config.getApiKey());
            }

            HttpResponse<String> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            saveAuditLog(provider, response.body(),
                    response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() != 200) {
                throw new IllegalStateException(provider + " gold rate API returned HTTP "
                        + response.statusCode());
            }

            BigDecimal rate = parseRateFromResponse(response.body());
            log.info("[GoldValuation] Fetched live rate {} INR/gram from {}", rate, provider);
            return new RateSnapshot(rate, responseTime);

        } catch (Exception e) {
            log.error("[GoldValuation] Live rate fetch failed ({}): {}", provider, e.getMessage(), e);
            saveAuditLog(provider, null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            log.warn("[GoldValuation] Falling back to simulation rate");
            return simulatedRate(config);
        }
    }

    private BigDecimal parseRateFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        BigDecimal rate = findRateNode(root);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Could not parse gold rate from provider response.");
        }
        return rate.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal findRateNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (node.isObject()) {
            for (String key : new String[]{
                    "ratePerGram", "rate_per_gram", "goldRate", "gold_rate",
                    "price", "rate", "value", "lastPrice", "last_price"
            }) {
                if (node.has(key)) {
                    BigDecimal found = findRateNode(node.get(key));
                    if (found != null) {
                        return found;
                    }
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                BigDecimal found = findRateNode(entry.getValue());
                if (found != null) {
                    return found;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode element : node) {
                BigDecimal found = findRateNode(element);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void saveAuditLog(String provider, String response, String status, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime, Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName(provider)
                    .apiName("GOLD_RATE_FETCH")
                    .requestPayload("GET " + integrationProperties.getGoldRate().getRateUrl())
                    .responsePayload(response)
                    .status(status)
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            log.error("[GoldValuation] Failed to save audit log: {}", e.getMessage());
        }
    }

    private record RateSnapshot(BigDecimal ratePerGram, Instant timestamp) {
    }

    private record CachedRate(RateSnapshot snapshot, Instant expiresAt) {
    }
}
