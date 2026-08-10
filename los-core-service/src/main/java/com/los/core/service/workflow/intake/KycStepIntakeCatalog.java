package com.los.core.service.workflow.intake;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps KYC workflow step types to intake field keys and default document types.
 */
public final class KycStepIntakeCatalog {

    public static final String POLICY_LEGACY = "LEGACY";
    public static final String POLICY_WORKFLOW_DRIVEN = "WORKFLOW_DRIVEN";

    private KycStepIntakeCatalog() {
    }

    public record StepIntakeMeta(String fieldKey, List<String> defaultDocumentTypes) {
    }

    private static final Map<String, StepIntakeMeta> CATALOG = Map.ofEntries(
            Map.entry("PAN_VERIFY", new StepIntakeMeta("panNumber", List.of("PAN_CARD"))),
            Map.entry("AADHAAR_OTP", new StepIntakeMeta("aadhaar", List.of("AADHAAR"))),
            Map.entry("VOTER_ID_VERIFY", new StepIntakeMeta("voterId", List.of())),
            Map.entry("DL_VERIFY", new StepIntakeMeta("dlNumber", List.of())),
            Map.entry("GSTIN_VERIFY", new StepIntakeMeta("gstin", List.of("GST_RETURN"))),
            Map.entry("BANK_PENNY_DROP", new StepIntakeMeta("bankAccountNumber", List.of("BANK_STATEMENT"))),
            Map.entry("FACE_MATCH", new StepIntakeMeta(null, List.of("PHOTOGRAPH"))),
            Map.entry("LIVENESS", new StepIntakeMeta(null, List.of("PHOTOGRAPH"))),
            Map.entry("UDYAM_VERIFY", new StepIntakeMeta("udyam", List.of())),
            Map.entry("CIN_MCA21", new StepIntakeMeta("cin", List.of())),
            Map.entry("ITR_RETURN_FORMS", new StepIntakeMeta(null, List.of("ITR"))),
            Map.entry("GST_ANALYSIS", new StepIntakeMeta(null, List.of("GST_RETURN")))
    );

    public static StepIntakeMeta metaForStep(String stepName) {
        if (stepName == null || stepName.isBlank()) {
            return null;
        }
        return CATALOG.get(stepName.trim().toUpperCase());
    }

    public static boolean isWorkflowDriven(Map<String, Object> intakeConfig) {
        if (intakeConfig == null || intakeConfig.isEmpty()) {
            return false;
        }
        return POLICY_WORKFLOW_DRIVEN.equalsIgnoreCase(String.valueOf(intakeConfig.getOrDefault("policy", "")));
    }

    public static Map<String, Object> defaultWorkflowDrivenIntakeConfig() {
        Map<String, Object> personalFields = new LinkedHashMap<>();
        personalFields.put("dateOfBirth", Map.of("collect", true, "required", true));
        personalFields.put("gender", Map.of(
                "collect", true,
                "required", false,
                "allowedValues", List.of("MALE", "FEMALE", "OTHER", "PREFER_NOT_TO_SAY")));
        personalFields.put("occupation", Map.of("collect", true, "required", true));
        personalFields.put("loanPurpose", Map.of("collect", true, "required", true));

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("policy", POLICY_WORKFLOW_DRIVEN);
        config.put("personalFields", personalFields);
        config.put("ageRules", Map.of("enabled", false, "minAge", 18, "maxAge", 70));
        config.put("tenureRules", Map.of("inputMode", "numeric", "min", 1, "max", 360));
        config.put("occupationRules", Map.of("options", IntakeOptionCatalog.defaultOccupationOptionsMaps()));
        config.put("loanPurposeRules", Map.of("options", IntakeOptionCatalog.defaultLoanPurposeOptionsMaps()));
        config.put("customFields", List.of());
        config.put("mandatoryFieldGroups", List.of());
        config.put("standaloneDocuments", List.of());
        return config;
    }

    public static Map<String, Object> legacyIntakeConfig() {
        return Map.of("policy", POLICY_LEGACY);
    }

    /**
     * Returns true when the merged KYC payload has the minimum identity input to attempt this step.
     */
    public static boolean hasExecutionPayload(String stepName, Map<String, Object> payload) {
        if (stepName == null || stepName.isBlank()) {
            return false;
        }
        String step = stepName.trim().toUpperCase();
        return switch (step) {
            case "PAN_VERIFY" -> hasAny(payload, "panNumber", "pan");
            case "AADHAAR_OTP" -> hasAny(payload, "aadhaarNumber", "aadhaarLast4", "aadhaar");
            case "VOTER_ID_VERIFY" -> hasAny(payload, "epicNo", "voterId");
            case "DL_VERIFY" -> hasAny(payload, "dlNo", "drivingLicenseNumber", "dlNumber");
            case "GSTIN_VERIFY" -> hasAny(payload, "gstin");
            case "UDYAM_VERIFY" -> hasAny(payload, "udyamRegistrationNo", "udyam", "udyamNumber");
            case "BANK_PENNY_DROP" -> hasAny(payload, "accountNumber", "bankAccountNumber")
                    && hasAny(payload, "ifsc");
            case "CIN_MCA21" -> hasAny(payload, "cin");
            case "MNRL" -> hasAny(payload, "mobile", "phone");
            // Borrower portal credentials only — staff Run KYC reuses SUCCESS result (no password in payload).
            case "ITR_RETURN_FORMS" -> true;
            // Borrower multi-PDF + GSTIN + consent; staff reuses latest phase result.
            case "GST_ANALYSIS" -> true;
            default -> true;
        };
    }

    private static boolean hasAny(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return false;
        }
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && !String.valueOf(value).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
