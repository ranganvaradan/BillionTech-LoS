package com.los.core.creditintelligence.decisionpolicy.kyc;

import com.los.core.creditintelligence.decisionpolicy.KycRequirementType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoring registry entries for KYC facts proven by repository capabilities.
 * Unsupported capabilities (PEP/UBO/sanctions) are intentionally omitted.
 */
public final class KycFactCatalog {

    private KycFactCatalog() {}

    public static List<Map<String, Object>> registryEntries() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(fact("kyc.pan.present", "PAN present on application / evidence", "BOOLEAN",
                "IDENTITY_PAN", true, true, KycRequirementType.INFORMATION_REQUIREMENT));
        list.add(fact("kyc.pan.verified", "PAN verified by configured identity provider", "BOOLEAN",
                "IDENTITY_PAN", true, false, KycRequirementType.VERIFICATION));
        list.add(fact("kyc.pan.name_match", "PAN name matches application name (when provider supplies match)", "BOOLEAN",
                "IDENTITY_PAN", false, true, KycRequirementType.MATCH_REQUIREMENT));
        list.add(fact("kyc.pan.verification_status", "Normalized PAN verification business outcome", "STRING",
                "IDENTITY_PAN", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.ckyc.available", "CKYC record available", "BOOLEAN",
                "IDENTITY_CKYC", true, false, KycRequirementType.INFORMATION_REQUIREMENT));
        list.add(fact("kyc.ckyc.verified", "CKYC download/verification succeeded", "BOOLEAN",
                "IDENTITY_CKYC", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.aadhaar.verified", "Aadhaar OTP / verification succeeded", "BOOLEAN",
                "IDENTITY_AADHAAR", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.gstin.present", "GSTIN present on application", "BOOLEAN",
                "IDENTITY_GSTIN", true, true, KycRequirementType.INFORMATION_REQUIREMENT));
        list.add(fact("kyc.gstin.verified", "GSTIN verified", "BOOLEAN",
                "IDENTITY_GSTIN", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.cin.present", "CIN present on application", "BOOLEAN",
                "IDENTITY_CIN", true, true, KycRequirementType.INFORMATION_REQUIREMENT));
        list.add(fact("kyc.cin.verified", "CIN / MCA21 verified", "BOOLEAN",
                "IDENTITY_CIN", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.udyam.verified", "Udyam verification succeeded", "BOOLEAN",
                "IDENTITY_UDYAM", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.bank_account.verified", "Bank account penny-drop verified", "BOOLEAN",
                "IDENTITY_BANK", true, false, KycRequirementType.VERIFICATION));

        list.add(fact("kyc.vkyc.completed", "Video KYC completed (or PKYC completed as allowed)", "BOOLEAN",
                "IDENTITY_VKYC", true, false, KycRequirementType.COMPLETION_REQUIREMENT));
        list.add(fact("kyc.vkyc.result", "VKYC result status string when known", "STRING",
                "IDENTITY_VKYC", true, false, KycRequirementType.COMPLETION_REQUIREMENT));

        list.add(fact("kyc.pkyc.completed", "Physical KYC completed", "BOOLEAN",
                "IDENTITY_VKYC", true, true, KycRequirementType.COMPLETION_REQUIREMENT));

        list.add(fact("kyc.overall.outcome", "Normalized Decision Policy KYC aggregate outcome", "STRING",
                "IDENTITY_KYC", true, false, KycRequirementType.ELIGIBILITY_CONDITION));
        list.add(fact("kyc.overall.technical_status", "Technical continuity status for overall KYC", "STRING",
                "IDENTITY_KYC", true, false, KycRequirementType.VERIFICATION));
        return list;
    }

    public static List<String> supportedFactPaths() {
        return registryEntries().stream().map(e -> String.valueOf(e.get("code"))).toList();
    }

    /** Capabilities that must NOT be marked AVAILABLE. */
    public static List<String> unsupportedCapabilityCodes() {
        return List.of(
                "kyc.pep.screened",
                "kyc.ubo.verified",
                "kyc.sanctions.cleared",
                "kyc.authorised_signatory.verified");
    }

    private static Map<String, Object> fact(
            String code,
            String description,
            String dataType,
            String sourceFamily,
            boolean available,
            boolean manualCapturePossible,
            KycRequirementType requirementType
    ) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("canonicalPath", code);
        m.put("type", "FACT");
        m.put("description", description);
        m.put("businessName", businessName(code));
        m.put("unit", null);
        m.put("dataType", dataType);
        m.put("periodSemantics", "POINT_IN_TIME");
        m.put("allowedOperators", List.of("EQ", "NE", "EXISTS", "IS_MISSING"));
        m.put("subject", "APPLICATION");
        m.put("sourceRequirements", List.of(sourceFamily));
        m.put("sourceFamily", sourceFamily);
        m.put("category", "KYC");
        m.put("decisionDomain", "KYC");
        m.put("kycRequirementType", requirementType.name());
        m.put("classificationRequirements", List.of("VERIFIED"));
        m.put("availability", available ? "AVAILABLE" : "UNAVAILABLE");
        m.put("manualCapturePossible", manualCapturePossible);
        m.put("verificationRequirement", requirementType == KycRequirementType.VERIFICATION
                || requirementType == KycRequirementType.COMPLETION_REQUIREMENT
                || requirementType == KycRequirementType.MATCH_REQUIREMENT);
        return m;
    }

    private static String businessName(String code) {
        return switch (code) {
            case "kyc.pan.present" -> "PAN Present";
            case "kyc.pan.verified" -> "PAN Verified";
            case "kyc.pan.name_match" -> "PAN Name Match";
            case "kyc.pan.verification_status" -> "PAN Verification Status";
            case "kyc.ckyc.available" -> "CKYC Available";
            case "kyc.ckyc.verified" -> "CKYC Verified";
            case "kyc.aadhaar.verified" -> "Aadhaar Verified";
            case "kyc.gstin.present" -> "GSTIN Present";
            case "kyc.gstin.verified" -> "GSTIN Verified";
            case "kyc.cin.present" -> "CIN Present";
            case "kyc.cin.verified" -> "CIN Verified";
            case "kyc.udyam.verified" -> "Udyam Verified";
            case "kyc.bank_account.verified" -> "Bank Account Verified";
            case "kyc.vkyc.completed" -> "VKYC Completed";
            case "kyc.vkyc.result" -> "VKYC Result";
            case "kyc.pkyc.completed" -> "PKYC Completed";
            case "kyc.overall.outcome" -> "KYC Overall Outcome";
            case "kyc.overall.technical_status" -> "KYC Technical Status";
            default -> code;
        };
    }
}
