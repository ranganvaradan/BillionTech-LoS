package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.certification.CertificationStatus;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared lender-facing display mapping from canonical truth axes.
 * Surfaces may add context wording; they must not invent alternate state.
 */
public final class LenderTruthDisplayMapper {

    private LenderTruthDisplayMapper() {}

    public static Map<String, Object> primary(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            Map<String, Object> execution,
            Map<String, Object> calculation,
            Map<String, Object> certification) {

        boolean capability = Boolean.TRUE.equals(execution.get("capability"));
        boolean valueAvailable = Boolean.TRUE.equals(execution.get("valueAvailable"));
        boolean calcRequired = Boolean.TRUE.equals(calculation.get("required"));
        String status = execution.get("status") == null ? null : String.valueOf(execution.get("status"));
        String certStatus = firstCertStatus(certification);

        String primary;
        String label;
        String next;

        if (calcRequired || ExecutionStatus.CALCULATION_NOT_DEFINED.name().equals(status)
                || ExecutionStatus.NOT_EXECUTABLE.name().equals(status) && !capability) {
            if (isManualClass(semantic)) {
                primary = "NEEDS_MANUAL_INPUT";
                label = "Needs manual input";
                next = "Enter value";
            } else if (!capability) {
                primary = "CALCULATION_NEEDS_SETUP";
                label = "Calculation needs setup";
                next = "Set up calculation";
            } else {
                primary = "NOT_YET_SUPPORTED";
                label = "Not yet supported";
                next = "Contact BillionTech";
            }
        } else if (ExecutionStatus.INPUT_REQUIRED.name().equals(status) || isManualClass(semantic) && !valueAvailable) {
            primary = "NEEDS_MANUAL_INPUT";
            label = "Needs manual input";
            next = "Enter value";
        } else if (capability && CertificationStatus.CERTIFIED.name().equals(certStatus)) {
            // Live approval is primary when executable + certified (data availability is secondary)
            primary = "APPROVED_FOR_LIVE_USE";
            label = "Approved for live use";
            next = valueAvailable ? null : "Get data / Complete source step";
        } else if (capability && CertificationStatus.REVOKED.name().equals(certStatus)) {
            primary = "APPROVAL_REVOKED";
            label = "Approval revoked";
            next = "Re-certify via authorized admin";
        } else if (capability && valueAvailable) {
            primary = "READY_TO_TEST";
            label = "Ready to test";
            next = "Test rule/policy";
        } else if (capability) {
            // executable but uncertified — ready to test path, even if data missing
            if (!valueAvailable) {
                primary = "CAN_CALCULATE_WHEN_DATA_AVAILABLE";
                label = "Can calculate when data is available";
                next = "Get data / Complete source step";
            } else {
                primary = "READY_TO_TEST";
                label = "Ready to test";
                next = "Test rule/policy";
            }
        } else {
            primary = "NOT_YET_SUPPORTED";
            label = "Not yet supported";
            next = "Contact BillionTech";
        }

        // Certification never implied by catalogue
        if (!capability && CertificationStatus.CERTIFIED.name().equals(certStatus)) {
            // still show certified artifact but not executable path
            label = label + " (certified artifact — check execution)";
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("primaryStatus", primary);
        m.put("primaryStatusLabel", label);
        m.put("nextAction", next);
        m.put("parameterClassLabel", parameterClassLabel(semantic));
        m.put("liveUseDisplay", liveUseDisplay(certStatus, capability));
        m.put("executionLabel", executionLabel(capability, valueAvailable, status, calcRequired));
        m.put("certificationLabel", certificationLabel(certStatus));
        m.put("calculationExplanation", calculation.get("explanation"));
        m.put("neverUseCatalogueProductionReadyAsLive", true);
        return m;
    }

    public static String calculationExplanation(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            boolean capability,
            boolean calcRequired,
            boolean manual,
            boolean raw) {
        if (manual) {
            return "Entered by the lender or application intake.";
        }
        if (calcRequired || !capability) {
            Object mode = semantic.get("calculationMode");
            if (mode != null && "AUTHORED".equalsIgnoreCase(String.valueOf(mode))) {
                return "Calculation is not set up yet.";
            }
            if (!capability) {
                return "Calculation is not set up yet.";
            }
        }
        if (raw || "RAW".equalsIgnoreCase(String.valueOf(semantic.get("calculationMode")))) {
            String src = def.evaluatedFrom() == null ? "the source system" : def.evaluatedFrom();
            return "Taken directly from " + src + ".";
        }
        if (def.calculationSummary() != null && !def.calculationSummary().isBlank()) {
            return def.calculationSummary();
        }
        if ("BUILT_IN".equalsIgnoreCase(String.valueOf(semantic.get("calculationMode")))) {
            return "Calculated by BillionTech from available source data.";
        }
        if ("AUTHORED".equalsIgnoreCase(String.valueOf(semantic.get("calculationMode"))) && capability) {
            return def.calculationSummary() != null && !def.calculationSummary().isBlank()
                    ? def.calculationSummary()
                    : "Calculated from an approved derived definition.";
        }
        return "See Advanced for technical details.";
    }

    public static String nextAction(
            boolean capability,
            boolean calcRequired,
            ExecutionResult er,
            Map<String, Object> certification) {
        String cert = firstCertStatus(certification);
        if (calcRequired || !capability) return "Set up calculation";
        if (er != null && er.status() == ExecutionStatus.INPUT_REQUIRED) return "Enter value";
        if (er != null && !er.valueAvailable() && capability) return "Get data / Complete source step";
        if (CertificationStatus.CERTIFIED.name().equals(cert)) return null;
        if (CertificationStatus.REVOKED.name().equals(cert)) return "Re-certify via authorized admin";
        return "Test rule/policy";
    }

    private static String executionLabel(
            boolean capability, boolean valueAvailable, String status, boolean calcRequired) {
        if (calcRequired || ExecutionStatus.CALCULATION_NOT_DEFINED.name().equals(status)) {
            return "Calculation needs setup";
        }
        if (ExecutionStatus.NOT_EXECUTABLE.name().equals(status) && !capability) {
            return "Not yet supported";
        }
        if (ExecutionStatus.INPUT_REQUIRED.name().equals(status)) {
            return "Needs manual input";
        }
        if (capability && valueAvailable) {
            return "Available";
        }
        if (capability) {
            return "Can calculate when data is available";
        }
        return "Not yet supported";
    }

    private static String certificationLabel(String certStatus) {
        if (CertificationStatus.CERTIFIED.name().equals(certStatus)) {
            return "Approved for live use";
        }
        if (CertificationStatus.REVOKED.name().equals(certStatus)) {
            return "Approval revoked";
        }
        return "Not approved for live use";
    }

    private static Map<String, Object> liveUseDisplay(String certStatus, boolean capability) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean certified = CertificationStatus.CERTIFIED.name().equals(certStatus);
        m.put("available", certified && capability);
        m.put("status", certified ? "CERTIFIED" : "UNCERTIFIED");
        m.put("label", certificationLabel(certStatus));
        m.put("reason", certified
                ? "Exact artifact certified in production certification ledger"
                : "Certification ledger has no CERTIFIED grant — catalogue production_ready is not proof");
        return m;
    }

    private static String parameterClassLabel(Map<String, Object> semantic) {
        Object pc = semantic.get("parameterClass");
        if (pc == null) return "Parameter";
        return switch (String.valueOf(pc)) {
            case "BUSINESS_PARAMETER" -> "Business parameter";
            case "INGREDIENT" -> "Source ingredient";
            case "MANUAL_INPUT" -> "Manual input";
            case "CONFIGURATION" -> "Configuration";
            default -> String.valueOf(pc);
        };
    }

    private static boolean isManualClass(Map<String, Object> semantic) {
        return "MANUAL_INPUT".equals(String.valueOf(semantic.get("parameterClass")))
                || "MANUAL".equalsIgnoreCase(String.valueOf(semantic.get("calculationMode")));
    }

    private static String firstCertStatus(Map<String, Object> certification) {
        if (certification == null) return CertificationStatus.UNCERTIFIED.name();
        Object s = certification.get("status");
        if (s == null) s = certification.get("certificationStatus");
        return s == null ? CertificationStatus.UNCERTIFIED.name() : String.valueOf(s);
    }
}
