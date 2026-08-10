package com.los.core.service.workflow;

import java.util.Locale;
import java.util.Map;

/**
 * Maps low-level workflow step codes into business process groups for process-level notification config.
 * This does not change workflow execution order/logic; it is only used during notification resolution.
 */
public final class WorkflowNotificationProcessMapper {

    private static final Map<String, String> STEP_TO_PROCESS = Map.ofEntries(
            Map.entry("PAN_VERIFY", "KYC"),
            Map.entry("AADHAAR_OTP", "KYC"),
            Map.entry("MOBILE_OTP", "KYC"),
            Map.entry("EMAIL_OTP", "KYC"),
            Map.entry("GSTIN_VERIFY", "KYC"),
            Map.entry("VOTER_ID_VERIFY", "KYC"),
            Map.entry("DL_VERIFY", "KYC"),
            Map.entry("BANK_PENNY_DROP", "KYC"),
            Map.entry("MNRL", "KYC"),
            Map.entry("FACE_MATCH", "KYC"),
            Map.entry("LIVENESS", "KYC"),
            Map.entry("CKYC_DOWNLOAD", "KYC"),
            Map.entry("CKYC_UPLOAD", "KYC"),
            Map.entry("UDYAM_VERIFY", "KYC"),
            Map.entry("CIN_MCA21", "KYC"),
            Map.entry("AML_SCREENING", "KYC"),
            Map.entry("VIDEO_KYC", "VKYC"),
            Map.entry("BUREAU_PULL", "UNDERWRITING"),
            Map.entry("ESIGN_KFS", "ESIGN"),
            Map.entry("ESIGN_AGREEMENT", "ESIGN")
    );

    private WorkflowNotificationProcessMapper() {
    }

    public static String processCodeForStep(String stepCode) {
        String n = normalize(stepCode);
        if (n.isEmpty()) {
            return "";
        }
        return STEP_TO_PROCESS.getOrDefault(n, n);
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}
