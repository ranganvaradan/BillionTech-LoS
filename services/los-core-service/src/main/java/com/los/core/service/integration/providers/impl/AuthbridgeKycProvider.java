package com.los.core.service.integration.providers.impl;

import com.los.core.model.enums.KycStepType;
import com.los.core.service.integration.providers.IKycProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component("authbridgeKycProvider")
public class AuthbridgeKycProvider implements IKycProvider {

    private static final Set<KycStepType> SUPPORTED = Set.of(
            KycStepType.AADHAAR_OTP, KycStepType.PAN_VERIFY, KycStepType.GSTIN_VERIFY,
            KycStepType.UDYAM_VERIFY, KycStepType.CIN_MCA21, KycStepType.BANK_PENNY_DROP,
            KycStepType.MOBILE_OTP, KycStepType.CKYC_DOWNLOAD, KycStepType.AML_SCREENING
    );

    @Override
    public KycVerificationResult verify(KycStepType stepType, Map<String, Object> payload) {
        log.info("[Authbridge] Executing KYC step: {} with payload keys: {}", stepType, payload.keySet());

        String transactionId = "AB-" + UUID.randomUUID().toString().substring(0, 8);

        // Simulate provider response based on step type
        return switch (stepType) {
            case AADHAAR_OTP -> simulateAadhaarOtp(payload, transactionId);
            case PAN_VERIFY -> simulatePanVerify(payload, transactionId);
            case GSTIN_VERIFY -> simulateGstinVerify(payload, transactionId);
            case BANK_PENNY_DROP -> simulatePennyDrop(payload, transactionId);
            case MOBILE_OTP -> simulateMobileOtp(payload, transactionId);
            default -> new KycVerificationResult(true, 0.95, Map.of("verified", true), transactionId, null);
        };
    }

    @Override
    public boolean supports(KycStepType stepType) {
        return SUPPORTED.contains(stepType);
    }

    @Override
    public String getProviderName() {
        return "AUTHBRIDGE";
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
                "bankName", "State Bank of India",
                "branchName", "MG Road Branch",
                "accountStatus", "ACTIVE"
        );
        return new KycVerificationResult(true, 0.99, parsed, txnId, null);
    }

    private KycVerificationResult simulateMobileOtp(Map<String, Object> payload, String txnId) {
        return new KycVerificationResult(true, 1.0,
                Map.of("mobile", payload.getOrDefault("mobile", ""), "verified", true),
                txnId, null);
    }
}
