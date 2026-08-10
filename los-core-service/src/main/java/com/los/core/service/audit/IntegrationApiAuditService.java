package com.los.core.service.audit;

import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Central helper for persisting external integration request/response payloads
 * (Perfios, Authbridge, Equifax, Encore LMS, etc.) for support and audit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationApiAuditService {

    private static final int MAX_PAYLOAD = 50_000;

    private final ApiAuditLogRepository apiAuditLogRepository;

    public void record(
            String providerName,
            String apiName,
            String requestPayload,
            String responsePayload,
            String status,
            Integer httpStatusCode,
            String errorMessage,
            String transactionId,
            UUID applicationId,
            Instant requestTime,
            Instant responseTime,
            Long durationMs) {
        try {
            apiAuditLogRepository.save(ApiAuditLog.builder()
                    .providerName(truncate(providerName, 50))
                    .apiName(truncate(apiName, 100))
                    .requestPayload(truncatePayload(requestPayload))
                    .responsePayload(truncatePayload(responsePayload))
                    .status(truncate(status, 20))
                    .httpStatusCode(httpStatusCode)
                    .errorMessage(truncatePayload(errorMessage))
                    .transactionId(truncate(transactionId, 100))
                    .applicationId(applicationId)
                    .requestTime(requestTime)
                    .responseTime(responseTime)
                    .durationMs(durationMs)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist API audit for {}/{}: {}", providerName, apiName, e.getMessage());
        }
    }

    public void recordJson(
            String providerName,
            String apiName,
            Object request,
            Object response,
            String status,
            Integer httpStatusCode,
            String errorMessage,
            String transactionId,
            UUID applicationId,
            Instant requestTime,
            Instant responseTime,
            Long durationMs,
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        record(
                providerName,
                apiName,
                toJson(mapper, request),
                toJson(mapper, response),
                status,
                httpStatusCode,
                errorMessage,
                transactionId,
                applicationId,
                requestTime,
                responseTime,
                durationMs);
    }

    public static UUID applicationIdFromPayload(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = firstNonNull(
                payload.get("applicationId"),
                payload.get("_applicationId"),
                payload.get("losApplicationId"),
                payload.get("anchorApplicationId"));
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Best-effort extract of application UUID from a JSON request/response body. */
    public static UUID applicationIdFromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String[] keys = {"losApplicationId", "applicationId", "anchorApplicationId", "_applicationId"};
        for (String key : keys) {
            UUID id = extractUuidField(json, key);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private static UUID extractUuidField(String json, String key) {
        String marker = "\"" + key + "\"";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + marker.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = json.indexOf('"', start + 1);
        if (end < 0) {
            return null;
        }
        try {
            return UUID.fromString(json.substring(start + 1, end));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    public static String auditProviderFromPayload(Map<String, Object> payload, String fallback) {
        if (payload == null) {
            return fallback;
        }
        Object raw = payload.get("_auditProvider");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return fallback;
        }
        return String.valueOf(raw).trim();
    }

    private static String toJson(com.fasterxml.jackson.databind.ObjectMapper mapper, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String truncatePayload(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > MAX_PAYLOAD ? text.substring(0, MAX_PAYLOAD) + "... [TRUNCATED]" : text;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
