package com.los.core.service.collateral;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.model.entity.CollateralValuation;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.repository.CollateralValuationRepository;
import com.los.core.service.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Property geo-location verification via Google Maps Geocoding API.
 * Persists lat/lng and formatted address into {@code collateral_valuations.details}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyGeoTagService {

    private static final Pattern PINCODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

    private final IntegrationProperties integrationProperties;
    private final CollateralValuationRepository valuationRepository;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> geoTagProperty(String address, UUID valuationId) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Property address is required for geo-tagging.");
        }

        CollateralValuation valuation = valuationRepository.findById(valuationId)
                .orElseThrow(() -> new ResourceNotFoundException("Valuation not found: " + valuationId));

        String trimmedAddress = address.trim();
        Map<String, Object> geoResult = resolveGeoCoordinates(trimmedAddress);
        geoResult.put("valuationId", valuationId.toString());
        geoResult.put("inputAddress", trimmedAddress);

        Map<String, Object> details = valuation.getDetails() != null
                ? new HashMap<>(valuation.getDetails())
                : new HashMap<>();
        details.put("geo", geoResult);
        valuation.setDetails(details);

        if (geoResult.get("formattedAddress") instanceof String formatted && !formatted.isBlank()) {
            valuation.setAddress(formatted);
        } else if (valuation.getAddress() == null || valuation.getAddress().isBlank()) {
            valuation.setAddress(trimmedAddress);
        }

        valuationRepository.save(valuation);

        auditService.logEvent(valuation.getApplicationId(), "COLLATERAL_GEO_TAGGED",
                Map.of(
                        "valuationId", valuationId.toString(),
                        "lat", String.valueOf(geoResult.get("lat")),
                        "lng", String.valueOf(geoResult.get("lng")),
                        "geoVerified", String.valueOf(geoResult.get("geoVerified")),
                        "simulated", String.valueOf(geoResult.get("simulated"))
                ));

        log.info("[PropertyGeoTag] Tagged valuation {} at {},{} verified={}",
                valuationId, geoResult.get("lat"), geoResult.get("lng"), geoResult.get("geoVerified"));

        return geoResult;
    }

    private Map<String, Object> resolveGeoCoordinates(String address) {
        IntegrationProperties.GoogleMapsProperties config = integrationProperties.getGoogleMaps();
        if (config.isSimulation() || config.getApiKey() == null || config.getApiKey().isBlank()) {
            log.info("[PropertyGeoTag] Simulation mode for address: {}", address);
            return simulatedGeoResult(address);
        }
        return callGoogleGeocoding(config, address);
    }

    private Map<String, Object> callGoogleGeocoding(IntegrationProperties.GoogleMapsProperties config,
                                                     String address) {
        String url = UriComponentsBuilder.fromHttpUrl(config.getGeocodeUrl())
                .queryParam("address", address)
                .queryParam("key", config.getApiKey())
                .build()
                .encode()
                .toUriString();

        Instant requestTime = Instant.now();
        String transactionId = "GEO-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            saveAuditLog(address, response.body(),
                    response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Google Geocoding API returned HTTP " + response.statusCode());
            }

            return parseGoogleResponse(response.body(), address);

        } catch (Exception e) {
            log.error("[PropertyGeoTag] Geocoding failed: {}", e.getMessage(), e);
            saveAuditLog(address, null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            throw new IllegalStateException("Google Geocoding API error: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseGoogleResponse(String responseBody, String inputAddress) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String status = root.path("status").asText("");
        if (!"OK".equals(status)) {
            throw new IllegalStateException("Google Geocoding status: " + status);
        }

        JsonNode first = root.path("results").path(0);
        if (!first.isObject()) {
            throw new IllegalStateException("Google Geocoding returned no results.");
        }

        JsonNode location = first.path("geometry").path("location");
        double lat = location.path("lat").asDouble();
        double lng = location.path("lng").asDouble();
        String formattedAddress = first.path("formatted_address").asText(inputAddress);
        String pinCode = extractPinCodeFromComponents(first);
        if (pinCode.isBlank()) {
            pinCode = extractPinCodeFromText(inputAddress);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lat", lat);
        result.put("lng", lng);
        result.put("formattedAddress", formattedAddress);
        result.put("pinCode", pinCode);
        result.put("geoVerified", true);
        result.put("simulated", false);
        result.put("provider", "GOOGLE_MAPS");
        return result;
    }

    private Map<String, Object> simulatedGeoResult(String address) {
        int hash = Math.abs(address.hashCode());
        double lat = 12.9716 + (hash % 1000) / 100000.0;
        double lng = 77.5946 + (hash % 1000) / 100000.0;
        String pinCode = extractPinCodeFromText(address);
        if (pinCode.isBlank()) {
            pinCode = "560001";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("lat", lat);
        result.put("lng", lng);
        result.put("formattedAddress", address + ", Bengaluru, Karnataka " + pinCode + ", India");
        result.put("pinCode", pinCode);
        result.put("geoVerified", true);
        result.put("simulated", true);
        result.put("provider", "SIMULATION");
        return result;
    }

    private static String extractPinCodeFromComponents(JsonNode resultNode) {
        JsonNode components = resultNode.path("address_components");
        if (!components.isArray()) {
            return "";
        }
        for (JsonNode component : components) {
            JsonNode types = component.path("types");
            if (types.isArray()) {
                for (JsonNode type : types) {
                    if ("postal_code".equals(type.asText())) {
                        return component.path("long_name").asText("").trim();
                    }
                }
            }
        }
        return "";
    }

    private static String extractPinCodeFromText(String text) {
        Matcher matcher = PINCODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private void saveAuditLog(String request, String response, String status, Integer httpStatus,
                              String errorMsg, String txnId, Instant reqTime, Instant resTime, Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName("GOOGLE_MAPS")
                    .apiName("PROPERTY_GEO_TAG")
                    .requestPayload(request)
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
            log.error("[PropertyGeoTag] Failed to save audit log: {}", e.getMessage());
        }
    }
}
