package com.los.core.service.integration.providers.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.enums.KycStepType;
import com.los.core.service.audit.IntegrationApiAuditService;
import com.los.core.service.integration.providers.IKycProvider;
import com.los.core.service.loan.ApplicantIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component("authbridgeKycProvider")
@RequiredArgsConstructor
public class AuthbridgeKycProvider implements IKycProvider {

    private static final Set<KycStepType> SUPPORTED = Set.of(
            KycStepType.AADHAAR_OTP, KycStepType.PAN_VERIFY, KycStepType.GSTIN_VERIFY,
            KycStepType.UDYAM_VERIFY, KycStepType.CIN_MCA21, KycStepType.BANK_PENNY_DROP,
            KycStepType.MOBILE_OTP, KycStepType.CKYC_DOWNLOAD, KycStepType.AML_SCREENING
    );

    private final IntegrationApiAuditService integrationApiAuditService;
    private final ObjectMapper objectMapper;

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        log.info("[Authbridge] Executing KYC step: {} with payload keys: {}", stepType, payload.keySet());

        Instant requestTime = Instant.now();
        String transactionId = "AB-" + UUID.randomUUID().toString().substring(0, 8);

        KycVerificationResult result = switch (stepType) {
            case AADHAAR_OTP -> simulateAadhaarOtp(payload, transactionId);
            case PAN_VERIFY -> simulatePanVerify(payload, transactionId);
            case GSTIN_VERIFY -> simulateGstinVerify(payload, transactionId);
            case BANK_PENNY_DROP -> simulatePennyDrop(payload, transactionId);
            case MOBILE_OTP -> simulateMobileOtp(payload, transactionId);
            default -> new KycVerificationResult(true, 0.95, Map.of("verified", true), transactionId, null);
        };

        Instant responseTime = Instant.now();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", result.success());
        response.put("confidenceScore", result.confidenceScore());
        response.put("parsedData", result.parsedData());
        response.put("transactionId", result.transactionId());
        if (result.errorMessage() != null) {
            response.put("errorMessage", result.errorMessage());
        }
        integrationApiAuditService.recordJson(
                "AUTHBRIDGE",
                "AUTHBRIDGE_" + stepType.name(),
                payload,
                response,
                result.success() ? "SUCCESS" : "FAILED",
                result.success() ? 200 : 400,
                result.errorMessage(),
                result.transactionId(),
                IntegrationApiAuditService.applicationIdFromPayload(payload),
                requestTime,
                responseTime,
                Duration.between(requestTime, responseTime).toMillis(),
                objectMapper);

        return result;
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return SUPPORTED.contains(stepType);
    }

    @Override
    public String getProviderName() {
        return "AUTHBRIDGE";
    }

    @Override
    public int getPriority() {
        return 10;
    }

    private KycVerificationResult simulateAadhaarOtp(Map<String, Object> payload, String txnId) {
        String aadhaarNumber = (String) payload.getOrDefault("aadhaarNumber", "");
        if (aadhaarNumber.length() != 12) {
            return new KycVerificationResult(false, 0.0, null, txnId, "Invalid Aadhaar number format");
        }
        Map<String, Object> parsed = Map.of(
                "name", payload.getOrDefault("name", ""),
                "dob", payload.getOrDefault("dob", ""),
                "gender", payload.getOrDefault("gender", ""),
                "address", Map.of("state", "Karnataka", "pincode", "560001"),
                "maskedAadhaar", "XXXX-XXXX-" + aadhaarNumber.substring(8)
        );
        return new KycVerificationResult(true, 0.99, parsed, txnId, null);
    }

    private KycVerificationResult simulatePanVerify(Map<String, Object> payload, String txnId) {
        String pan = (String) payload.getOrDefault("panNumber", "");
        if (pan.length() != 10) {
            return new KycVerificationResult(false, 0.0, null, txnId, "Invalid PAN format");
        }
        Map<String, Object> parsed = Map.of(
                "panNumber", pan,
                "name", payload.getOrDefault("name", ""),
                "panStatus", "ACTIVE",
                "lastUpdated", "2025-01-15",
                "aadhaarLinked", true
        );
        return new KycVerificationResult(true, 0.98, parsed, txnId, null);
    }

    private KycVerificationResult simulateGstinVerify(Map<String, Object> payload, String txnId) {
        String gstin = (String) payload.getOrDefault("gstin", "");
        if (gstin.length() != 15) {
            return new KycVerificationResult(false, 0.0, null, txnId, "Invalid GSTIN format");
        }
        Map<String, Object> parsed = Map.of(
                "gstin", gstin,
                "legalName", payload.getOrDefault("businessName", ""),
                "tradeName", payload.getOrDefault("tradeName", ""),
                "status", "ACTIVE",
                "registrationDate", "2020-04-01",
                "businessType", "Regular"
        );
        return new KycVerificationResult(true, 0.97, parsed, txnId, null);
    }

    private KycVerificationResult simulatePennyDrop(Map<String, Object> payload, String txnId) {
        String accountNumber = (String) payload.getOrDefault("accountNumber", "");
        String ifsc = (String) payload.getOrDefault("ifsc", "");
        if (accountNumber.isEmpty() || ifsc.isEmpty()) {
            return new KycVerificationResult(false, 0.0, null, txnId, "Account number and IFSC required");
        }
        Map<String, Object> parsed = Map.of(
                "accountNumber", accountNumber,
                "ifsc", ifsc,
                "accountHolderName", payload.getOrDefault("name", ""),
                "bankName", "Simulated Bank",
                "accountStatus", "ACTIVE"
        );
        return new KycVerificationResult(true, 0.96, parsed, txnId, null);
    }

    private KycVerificationResult simulateMobileOtp(Map<String, Object> payload, String txnId) {
        String mobile = resolveNormalizedMobile(payload);
        if (mobile.isBlank()) {
            // Payload mapping / missing field — not "wrong number entered by borrower"
            return new KycVerificationResult(false, 0.0, null, txnId, "Mobile number is required for verification");
        }
        if (mobile.length() < 10) {
            return new KycVerificationResult(false, 0.0, null, txnId, "Invalid mobile number");
        }
        // Mask in response payload — never echo full number unnecessarily beyond last 4 for sandbox audit
        String masked = "XXXXXX" + mobile.substring(mobile.length() - 4);
        return new KycVerificationResult(
                true,
                0.99,
                Map.of("mobileVerified", true, "mobileNumberMasked", masked),
                txnId,
                null);
    }

    /** Prefer mobileNumber; fall back to intake aliases mobile|phone|borrowerMobile. */
    private static String resolveNormalizedMobile(Map<String, Object> payload) {
        if (payload == null) {
            return "";
        }
        for (String key : new String[] {"mobileNumber", "mobile", "phone", "borrowerMobile"}) {
            Object v = payload.get(key);
            if (v == null) {
                continue;
            }
            String normalized = ApplicantIdentityResolver.normalizeIndianMobileDigits(String.valueOf(v));
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }
}
