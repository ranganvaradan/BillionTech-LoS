package com.los.plp.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.plp.config.PlpProperties;
import com.los.plp.dto.PlpApiResponse;
import com.los.plp.dto.PlpLoginResponse;
import com.los.plp.dto.request.PlpApplicationCleanupRequest;
import com.los.plp.dto.request.PlpAnchorSyncRequest;
import com.los.plp.dto.request.PlpBorrowerProgramMappingRequest;
import com.los.plp.dto.request.PlpBorrowerSyncRequest;
import com.los.plp.dto.request.PlpProgramSyncRequest;
import com.los.plp.dto.request.PlpSubProgramBorrowerLinkRequest;
import com.los.plp.dto.request.PlpSubProgramSyncRequest;
import com.los.plp.dto.response.PlpApplicationCleanupResponse;
import com.los.plp.dto.response.PlpAnchorSyncData;
import com.los.plp.dto.response.PlpBorrowerProgramMappingData;
import com.los.plp.dto.response.PlpBorrowerSyncData;
import com.los.plp.dto.response.PlpProgramSyncData;
import com.los.plp.dto.response.PlpSubProgramBorrowerLinkData;
import com.los.plp.dto.response.PlpSubProgramSyncData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlpIntegrationClient {

    private static final String SOURCE_SYSTEM = "LOS";
    private static final String PATH_LOGIN = "/api/v1/auth/login";
    private static final String PATH_ANCHORS = "/api/v1/integrations/los/anchors";
    private static final String PATH_PROGRAMS = "/api/v1/integrations/los/programs";
    private static final String PATH_SUB_PROGRAMS = "/api/v1/integrations/los/sub-programs";
    private static final String PATH_BORROWERS = "/api/v1/integrations/los/borrowers";
    private static final String PATH_LINKS = "/api/v1/integrations/los/sub-program-borrower-links";
    private static final String PATH_MAPPINGS = "/api/v1/integrations/los/borrower-program-mappings";
    private static final String PATH_APPLICATION_CLEANUP = "/api/v1/integrations/los/application-cleanup";

    private static final int MAX_LOG_BODY_CHARS = 4096;

    private static final TypeReference<PlpApiResponse<PlpAnchorSyncData>> ANCHOR_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpProgramSyncData>> PROGRAM_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpSubProgramSyncData>> SUB_PROGRAM_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpBorrowerSyncData>> BORROWER_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpSubProgramBorrowerLinkData>> LINK_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpBorrowerProgramMappingData>> MAPPING_TYPE = new TypeReference<>() {};
    private static final TypeReference<PlpApiResponse<PlpApplicationCleanupResponse>> CLEANUP_TYPE = new TypeReference<>() {};

    @Qualifier("plpRestClient")
    private final RestClient plpRestClient;
    private final PlpProperties plpProperties;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile long accessTokenExpiryEpochMillis;

    public PlpApiResponse<PlpAnchorSyncData> syncAnchor(PlpAnchorSyncRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_ANCHORS, request, ANCHOR_TYPE);
    }

    public PlpApiResponse<PlpProgramSyncData> syncProgram(PlpProgramSyncRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_PROGRAMS, request, PROGRAM_TYPE);
    }

    public PlpApiResponse<PlpSubProgramSyncData> syncSubProgram(PlpSubProgramSyncRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_SUB_PROGRAMS, request, SUB_PROGRAM_TYPE);
    }

    public PlpApiResponse<PlpBorrowerSyncData> syncBorrower(PlpBorrowerSyncRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_BORROWERS, request, BORROWER_TYPE);
    }

    public PlpApiResponse<PlpSubProgramBorrowerLinkData> syncSubProgramBorrowerLink(PlpSubProgramBorrowerLinkRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_LINKS, request, LINK_TYPE);
    }

    public PlpApiResponse<PlpBorrowerProgramMappingData> syncBorrowerProgramMapping(PlpBorrowerProgramMappingRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        return post(PATH_MAPPINGS, request, MAPPING_TYPE);
    }

    public PlpApplicationCleanupResponse cleanupApplication(PlpApplicationCleanupRequest request) {
        request.setSourceSystem(SOURCE_SYSTEM);
        PlpApiResponse<PlpApplicationCleanupResponse> response = post(PATH_APPLICATION_CLEANUP, request, CLEANUP_TYPE);
        return response.getData();
    }

    public boolean isEnabled() {
        return plpProperties.isEnabled();
    }

    /**
     * Returns a valid bearer access token (refreshing via IAM login when stale) for sibling clients that need
     * to call other PLP gateway routes (e.g. borrower-scoped invoice/loan endpoints) with the same machine
     * identity. Throws {@link PlpIntegrationException} when PLP is disabled or login fails.
     */
    public String currentBearerToken() {
        if (!plpProperties.isEnabled()) {
            throw new PlpIntegrationException("PLP integration is disabled (los.plp.enabled=false)");
        }
        return currentAccessToken();
    }

    private <T> PlpApiResponse<T> post(String path, Object body, TypeReference<PlpApiResponse<T>> type) {
        if (!plpProperties.isEnabled()) {
            throw new PlpIntegrationException("PLP sync is disabled (los.plp.enabled=false)");
        }
        return postAuthenticated(path, body, type, false);
    }

    private static final int MAX_UNAVAILABLE_RETRIES = 4;
    private static final long UNAVAILABLE_RETRY_DELAY_MS = 2_000;

    private <T> PlpApiResponse<T> postAuthenticated(String path, Object body, TypeReference<PlpApiResponse<T>> type, boolean retried401AfterRefresh) {
        return postAuthenticated(path, body, type, retried401AfterRefresh, 0);
    }

    private <T> PlpApiResponse<T> postAuthenticated(String path, Object body, TypeReference<PlpApiResponse<T>> type, boolean retried401AfterRefresh, int unavailableRetries) {
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            bodyJson = "<serialize failed: " + e.getMessage() + ">";
        }

        String accessToken = currentAccessToken();
        HttpHeaders logHeaders = new HttpHeaders();
        logHeaders.setBearerAuth(accessToken);

        log.info("[PLP][REQUEST] POST {} — headers: {}", path, summarizeHeadersForLog(logHeaders));
        log.info("[PLP][REQUEST] Body: {}", truncateForLog(bodyJson));

        String bearer = "Bearer " + accessToken;

        try {
            ResponseEntity<String> entity = plpRestClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
            log.info("[PLP][RESPONSE] Status: {}, Body: {}", entity.getStatusCode(), truncateForLog(entity.getBody()));
            String raw = entity.getBody();
            PlpApiResponse<T> response = objectMapper.readValue(raw, type);
            if (response == null || !"SUCCESS".equalsIgnoreCase(response.getStatus())) {
                String msg = response != null && response.getMessage() != null
                        ? response.getMessage()
                        : "PLP returned non-SUCCESS status";
                throw new PlpIntegrationException(msg);
            }
            return response;
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            log.info("[PLP][RESPONSE] Status: {}, Body: {}", e.getStatusCode(),
                    truncateForLog(e.getResponseBodyAsString()));
            if (shouldRetryUnauthorized(e, retried401AfterRefresh)) {
                clearCachedAccessToken();
                return postAuthenticated(path, body, type, true, unavailableRetries);
            }
            if (shouldRetryUnavailable(e, unavailableRetries)) {
                log.warn("[PLP] {} {} — retry {}/{} after {}ms (Eureka/program-service may still be registering)",
                        e.getStatusCode(), path, unavailableRetries + 1, MAX_UNAVAILABLE_RETRIES, UNAVAILABLE_RETRY_DELAY_MS);
                sleepQuietly(UNAVAILABLE_RETRY_DELAY_MS);
                return postAuthenticated(path, body, type, retried401AfterRefresh, unavailableRetries + 1);
            }
            throw new PlpIntegrationException(
                    "PLP HTTP " + e.getStatusCode() + ": " + safeBody(e.getResponseBodyAsString()));
        } catch (Exception e) {
            log.error("PLP call failed for {}: {}", path, e.getMessage());
            throw new PlpIntegrationException(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static boolean shouldRetryUnauthorized(RestClientResponseException e, boolean alreadyRetried) {
        HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
        return resolved == HttpStatus.UNAUTHORIZED && !alreadyRetried;
    }

    private static boolean shouldRetryUnavailable(RestClientResponseException e, int attemptsSoFar) {
        if (attemptsSoFar >= MAX_UNAVAILABLE_RETRIES) {
            return false;
        }
        HttpStatus resolved = HttpStatus.resolve(e.getStatusCode().value());
        return resolved == HttpStatus.SERVICE_UNAVAILABLE || resolved == HttpStatus.BAD_GATEWAY;
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String summarizeHeadersForLog(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        if (contentType != null && !contentType.isEmpty()) {
            sb.append(HttpHeaders.CONTENT_TYPE).append("=").append(contentType);
        }
        String auth = maskAuthorization(headers.getFirst(HttpHeaders.AUTHORIZATION));
        if (auth != null && !auth.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(HttpHeaders.AUTHORIZATION).append("=").append(auth);
        }
        return sb.toString();
    }

    private static String maskAuthorization(String auth) {
        if (auth == null || auth.isBlank()) {
            return "";
        }
        if (auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            if (log.isDebugEnabled()) {
                return "Bearer *** (len=" + (auth.length() - 7) + ")";
            }
            return "Bearer ***";
        }
        return "***";
    }

    /** Returns raw JWT access token (no "Bearer "); refreshes via IAM login when cache is stale or empty. */
    private String currentAccessToken() {
        long now = System.currentTimeMillis();
        String cached = cachedAccessToken;
        if (cached != null && !cached.isBlank() && now < accessTokenExpiryEpochMillis) {
            return cached.trim();
        }
        synchronized (this) {
            if (cachedAccessToken != null && !cachedAccessToken.isBlank() && now < accessTokenExpiryEpochMillis) {
                return cachedAccessToken.trim();
            }
            loginAndCacheAccessTokenLocked();
            if (cachedAccessToken == null || cachedAccessToken.isBlank()) {
                throw new PlpIntegrationException("PLP IAM login returned empty access token");
            }
            return cachedAccessToken.trim();
        }
    }

    private void clearCachedAccessToken() {
        synchronized (this) {
            cachedAccessToken = null;
            accessTokenExpiryEpochMillis = 0;
        }
    }

    private void loginAndCacheAccessTokenLocked() {
        String email = plpProperties.getIntegrationEmail();
        String password = plpProperties.getIntegrationPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new PlpIntegrationException(
                    "PLP API gateway requires JWT for integration calls. Configure los.plp.integration-email and "
                            + "los.plp.integration-password (PLP IAM ACTIVE user; login at {los.plp.base-url}/api/v1/auth/login). "
                            + "Ensure los.plp.base-url reaches the same PLP gateway front door that protects "
                            + "/api/v1/integrations/los/**.");
        }

        LinkedHashMap<String, String> loginBody = new LinkedHashMap<>(2);
        loginBody.put("email", email.trim());
        loginBody.put("password", password);

        HttpHeaders logHeaders = new HttpHeaders();
        logHeaders.setContentType(MediaType.APPLICATION_JSON);
        log.info("[PLP][REQUEST] POST {} — headers: {}", PATH_LOGIN, summarizeHeadersForLog(logHeaders));
        log.info("[PLP][REQUEST] Body: {}",
                truncateForLog("{\"email\":\"" + email.trim() + "\",\"password\":\"***\"}"));

        String raw;
        try {
            ResponseEntity<String> entity = plpRestClient.post()
                    .uri(PATH_LOGIN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(loginBody)
                    .retrieve()
                    .toEntity(String.class);
            log.info("[PLP][RESPONSE] Status: {}, Body: {}", entity.getStatusCode(), truncateForLog(entity.getBody()));
            raw = entity.getBody();
        } catch (RestClientResponseException e) {
            log.info("[PLP][RESPONSE] Status: {}, Body: {}", e.getStatusCode(),
                    truncateForLog(e.getResponseBodyAsString()));
            throw new PlpIntegrationException(
                    "PLP IAM login failed: HTTP " + e.getStatusCode() + ": " + safeBody(e.getResponseBodyAsString()));
        } catch (Exception e) {
            // Covers RestClientException (read timeout, connection refused, malformed response, etc.) so
            // callers in sync services can catch this uniformly as PlpIntegrationException and record
            // SYNC_FAILED, instead of letting the raw exception escape (e.g. into TransactionSynchronization
            // afterCommit hooks where it surfaces as an unhandled error).
            log.error("PLP IAM login transport error: {}", e.getMessage());
            throw new PlpIntegrationException(
                    "PLP IAM login failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }

        try {
            PlpLoginResponse login = objectMapper.readValue(raw, PlpLoginResponse.class);
            if (login == null || login.getAccessToken() == null || login.getAccessToken().isBlank()) {
                throw new PlpIntegrationException("PLP IAM login response missing accessToken");
            }
            long skewSec = Math.max(0, plpProperties.getTokenExpirySkewSeconds());
            long ttlSec = login.getExpiresIn();
            if (ttlSec <= 0) {
                ttlSec = 3600;
            }
            long effectiveTtl = Math.max(60, ttlSec - skewSec);
            cachedAccessToken = login.getAccessToken().trim();
            accessTokenExpiryEpochMillis = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(effectiveTtl);
        } catch (PlpIntegrationException e) {
            throw e;
        } catch (Exception e) {
            throw new PlpIntegrationException("PLP IAM login: failed to parse response: " + e.getMessage());
        }
    }

    private static String truncateForLog(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= MAX_LOG_BODY_CHARS ? s : s.substring(0, MAX_LOG_BODY_CHARS) + "...(truncated)";
    }

    private static String safeBody(String body) {
        return truncateForLog(body);
    }
}
