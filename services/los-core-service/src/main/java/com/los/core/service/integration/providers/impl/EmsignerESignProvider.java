package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.config.IntegrationProperties;
import com.los.core.model.entity.ApiAuditLog;
import com.los.core.repository.ApiAuditLogRepository;
import com.los.core.service.integration.providers.IESignProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Emsigner Digital Signing Provider — adapted from legacy EmSignerIntegrationServiceFacadeImpl.
 *
 * Implements the full Emsigner signing flow:
 *   1. Generate AES session key
 *   2. Build JSON payload with document, signer info, auth token, callback URLs
 *   3. Encrypt JSON using session key (AES)
 *   4. Generate SHA-256 hash of JSON
 *   5. Encrypt hash using session key
 *   6. (In production) Encrypt session key with Emsigner's RSA public key
 *   7. POST encrypted data to Emsigner gateway
 *
 * Legacy reference: bl-core/.../facade/impl/EmSignerIntegrationServiceFacadeImpl.java
 */
@Slf4j
@Component("emsignerESignProvider")
@RequiredArgsConstructor
public class EmsignerESignProvider implements IESignProvider {

    private final IntegrationProperties integrationProperties;
    private final ApiAuditLogRepository apiAuditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public ESignInitResult initiateSigning(UUID applicationId, String documentStorageKey, Map<String, Object> signerInfo) {
        log.info("[Emsigner] Initiating eSign for application: {}", applicationId);

        IntegrationProperties.EmsignerProperties config = integrationProperties.getEmsigner();
        String transactionId = "ESIGN-" + UUID.randomUUID().toString().substring(0, 8);

        if (config.getAuthToken() == null || config.getAuthToken().isBlank()) {
            log.warn("[Emsigner] Auth token not configured — returning simulated response");
            return simulatedInitResult(applicationId, transactionId);
        }

        Instant requestTime = Instant.now();

        try {
            // Step 1: Generate AES session key (128-bit) — legacy: GenerateSessionKey()
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            SecretKey sessionKey = keyGen.generateKey();
            byte[] sessionKeyBytes = sessionKey.getEncoded();
            String sessionKeyBase64 = Base64.getEncoder().encodeToString(sessionKeyBytes);

            // Step 2: Build JSON payload — from legacy getDSign() / getAadharSign()
            String signerName = (String) signerInfo.getOrDefault("name", "");
            String referenceNo = (String) signerInfo.getOrDefault("referenceNumber", applicationId.toString());
            String signingLogId = String.valueOf(System.currentTimeMillis());

            Map<String, Object> jsonPayload = new LinkedHashMap<>();
            jsonPayload.put("Name", signerName);
            jsonPayload.put("FileType", "PDF");
            jsonPayload.put("SignatureType", signerInfo.getOrDefault("signatureType", 0));
            jsonPayload.put("SelectPage", "FIRST");
            jsonPayload.put("SignaturePosition", signerInfo.getOrDefault("signaturePosition", "Bottom-Left"));
            jsonPayload.put("SignatureMode", signerInfo.getOrDefault("signatureMode", "1"));
            jsonPayload.put("AuthToken", config.getAuthToken());
            jsonPayload.put("File", signerInfo.getOrDefault("fileBase64", ""));
            jsonPayload.put("PreviewRequired", true);
            jsonPayload.put("SUrl", config.getSuccessCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("FUrl", config.getFailureCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("CUrl", config.getCancelCallbackUrl() + "/" + signingLogId);
            jsonPayload.put("ReferenceNumber", referenceNo);
            jsonPayload.put("Enableuploadsignature", false);
            jsonPayload.put("Enablefontsignature", false);
            jsonPayload.put("EnableDrawSignature", false);
            jsonPayload.put("EnableeSignaturePad", false);
            jsonPayload.put("IsCompressed", false);
            jsonPayload.put("IsCosign", signerInfo.getOrDefault("isCosign", false));
            jsonPayload.put("EnableViewDocumentLink", false);
            jsonPayload.put("Storetodb", true);
            jsonPayload.put("IsGSTN", false);
            jsonPayload.put("IsGSTN3B", false);

            String jsonString = objectMapper.writeValueAsString(jsonPayload);
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

            // Step 3: Encrypt JSON using session key (AES/ECB/PKCS5Padding)
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKeyBytes, "AES"));
            byte[] encryptedJson = cipher.doFinal(jsonBytes);

            // Step 4: Generate SHA-256 hash of JSON
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(jsonBytes);

            // Step 5: Encrypt hash using session key
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKeyBytes, "AES"));
            byte[] encryptedHash = cipher.doFinal(hashBytes);

            // Step 6: Encode all as Base64
            String encryptedJsonB64 = Base64.getEncoder().encodeToString(encryptedJson);
            String encryptedHashB64 = Base64.getEncoder().encodeToString(encryptedHash);
            // Note: In production, the session key should be encrypted with Emsigner's RSA public key.
            // For now, we send the session key as Base64 (the public key file can be loaded later).
            String sessionKeyEncryptedB64 = sessionKeyBase64;

            // Step 7: POST to Emsigner gateway
            // The Emsigner API expects a form POST with: aspnetForm with eSignRequest, SigningServerURL, etc.
            // For API-based flow, we send JSON with the encrypted data
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("SymmetricKey", sessionKeyEncryptedB64);
            requestBody.put("JsonData", encryptedJsonB64);
            requestBody.put("Hash", encryptedHashB64);
            requestBody.put("ReferenceNumber", referenceNo);
            requestBody.put("SigningLogId", signingLogId);

            String requestJson = objectMapper.writeValueAsString(requestBody);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getUrl()))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Instant responseTime = Instant.now();
            long durationMs = Duration.between(requestTime, responseTime).toMillis();

            log.info("[Emsigner] HTTP {} in {}ms", response.statusCode(), durationMs);

            // Audit log — mask auth token
            String maskedPayload = jsonString.replaceAll("\"AuthToken\":\"[^\"]*\"", "\"AuthToken\":\"***\"");
            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_SIGN", maskedPayload,
                    response.body(), response.statusCode() == 200 ? "SUCCESS" : "FAILED",
                    response.statusCode(), null, transactionId, requestTime, responseTime, durationMs);

            if (response.statusCode() == 200) {
                // Parse response for signing URL
                JsonNode respNode = objectMapper.readTree(response.body());
                String signingUrl = respNode.path("signingUrl").asText(
                        respNode.path("SigningUrl").asText(
                                respNode.path("url").asText("")));
                if (signingUrl.isEmpty()) {
                    // Emsigner might return the URL in a different format
                    signingUrl = config.getUrl() + "?signingLogId=" + signingLogId;
                }
                return new ESignInitResult(true, transactionId, signingUrl, null);
            } else {
                return new ESignInitResult(false, transactionId, null,
                        "Emsigner API returned HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("[Emsigner] eSign initiation failed: {}", e.getMessage(), e);
            saveAuditLog("EMSIGNER", "EMSIGNER_INITIATE_SIGN", null,
                    null, "ERROR", null, e.getMessage(), transactionId,
                    requestTime, Instant.now(), null);
            return new ESignInitResult(false, transactionId, null,
                    "Emsigner error: " + e.getMessage());
        }
    }

    @Override
    public ESignStatusResult checkStatus(String eSignTransactionId) {
        log.info("[Emsigner] Checking eSign status for: {}", eSignTransactionId);

        IntegrationProperties.EmsignerProperties config = integrationProperties.getEmsigner();

        if (config.getAuthToken() == null || config.getAuthToken().isBlank()) {
            return new ESignStatusResult("COMPLETED", eSignTransactionId,
                    Map.of("simulated", true, "signerName", "Simulated", "signedAt", Instant.now().toString()),
                    null);
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            // Status check endpoint — appended to base URL
            String statusUrl = config.getUrl().replace("/gateway", "/status") + "/" + eSignTransactionId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("AuthToken", config.getAuthToken())
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode respNode = objectMapper.readTree(response.body());
                String status = respNode.path("status").asText("PENDING");
                Map<String, Object> signerDetails = new LinkedHashMap<>();
                signerDetails.put("signerName", respNode.path("signerName").asText(""));
                signerDetails.put("signedAt", respNode.path("signedAt").asText(""));
                signerDetails.put("certificateSerial", respNode.path("certificateSerial").asText(""));
                return new ESignStatusResult(status, eSignTransactionId, signerDetails, null);
            } else {
                return new ESignStatusResult("UNKNOWN", eSignTransactionId, Map.of(),
                        "Status check returned HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.error("[Emsigner] Status check failed: {}", e.getMessage());
            return new ESignStatusResult("ERROR", eSignTransactionId, Map.of(),
                    "Status check error: " + e.getMessage());
        }
    }

    @Override
    public byte[] downloadSignedDocument(String eSignTransactionId) {
        log.info("[Emsigner] Downloading signed document for: {}", eSignTransactionId);

        IntegrationProperties.EmsignerProperties config = integrationProperties.getEmsigner();

        if (config.getAuthToken() == null || config.getAuthToken().isBlank()) {
            return ("Signed document placeholder for " + eSignTransactionId).getBytes();
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                    .build();

            String downloadUrl = config.getUrl().replace("/gateway", "/download") + "/" + eSignTransactionId;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(downloadUrl))
                    .timeout(Duration.ofMillis(config.getReadTimeoutMs()))
                    .header("AuthToken", config.getAuthToken())
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                log.error("[Emsigner] Download failed with HTTP {}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.error("[Emsigner] Document download failed: {}", e.getMessage());
            return null;
        }
    }

    private ESignInitResult simulatedInitResult(UUID applicationId, String transactionId) {
        String signingUrl = "https://esign.example.com/sign/" + transactionId;
        return new ESignInitResult(true, transactionId, signingUrl, null);
    }

    private void saveAuditLog(String provider, String apiName, String request, String response,
                               String status, Integer httpStatus, String errorMsg, String txnId,
                               Instant reqTime, Instant resTime, Long durationMs) {
        try {
            ApiAuditLog audit = ApiAuditLog.builder()
                    .providerName(provider)
                    .apiName(apiName)
                    .requestPayload(request)
                    .responsePayload(response)
                    .status(status)
                    .httpStatusCode(httpStatus)
                    .errorMessage(errorMsg)
                    .transactionId(txnId)
                    .requestTime(reqTime)
                    .responseTime(resTime)
                    .durationMs(durationMs)
                    .build();
            apiAuditLogRepository.save(audit);
        } catch (Exception e) {
            log.error("[Emsigner] Failed to save audit log: {}", e.getMessage());
        }
    }
}
