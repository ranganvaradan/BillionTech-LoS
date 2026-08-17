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
 *
 * <p>GOLDEN-PARAMETER-TRUTH: primaryStatus is businessReadiness READY/NOT_READY
 * from {@link BusinessReadinessProjector}. Certification/value remain separate axes.
 */
public final class LenderTruthDisplayMapper {

    private LenderTruthDisplayMapper() {}

    public static Map<String, Object> primary(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            Map<String, Object> execution,
            Map<String, Object> calculation,
            Map<String, Object> certification) {
        return primary(def, semantic, Map.of(), execution, calculation, certification);
    }

    public static Map<String, Object> primary(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            Map<String, Object> source,
            Map<String, Object> execution,
            Map<String, Object> calculation,
            Map<String, Object> certification) {

        Map<String, Object> readiness = BusinessReadinessProjector.project(
                def, semantic, source, execution, calculation);

        boolean capability = Boolean.TRUE.equals(execution.get("capability"));
        boolean valueAvailable = Boolean.TRUE.equals(execution.get("valueAvailable"));
        boolean calcRequired = Boolean.TRUE.equals(calculation.get("required"));
        String status = execution.get("status") == null ? null : String.valueOf(execution.get("status"));
        String certStatus = firstCertStatus(certification);
        String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));

        Map<String, Object> m = new LinkedHashMap<>();
        m.putAll(readiness);
        // Keep certification / execution labels as secondary presentation — never override businessReadiness
        m.put("parameterClassLabel", parameterClassLabel(semantic));
        m.put("liveUseDisplay", liveUseDisplay(certStatus, capability));
        m.put("executionLabel", executionLabel(capability, valueAvailable, status, calcRequired, semantic));
        m.put("certificationLabel", certificationLabel(certStatus));
        m.put("neverUseCatalogueProductionReadyAsLive", true);
        m.put("calculationExplanation", calculation.get("explanation"));
        if ("INGREDIENT".equals(paramClass)) {
            m.put("calculationSetup", "NOT_APPLICABLE");
            if (m.get("calculationExplanation") == null) {
                m.put("calculationExplanation", "Source ingredient — not a lender calculation setup item.");
            }
        } else if ("CONFIGURATION".equals(paramClass) || "DECISION_OUTPUT".equals(paramClass)) {
            m.put("calculationSetup", "NOT_APPLICABLE");
        } else if (BusinessReadinessReason.CALCULATION_NOT_DEFINED.name()
                .equals(String.valueOf(readiness.get("businessReadinessReason")))
                || BusinessReadinessReason.CALCULATION_INVALID.name()
                .equals(String.valueOf(readiness.get("businessReadinessReason")))) {
            m.put("calculationSetup", "REQUIRED");
        } else {
            m.put("calculationSetup", "NOT_REQUIRED");
        }
        return m;
    }

    public static String calculationExplanation(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            boolean capability,
            boolean calcRequired,
            boolean manual,
            boolean raw) {
        if ("INGREDIENT".equals(String.valueOf(semantic.get("parameterClass")))) {
            return "Source ingredient — not a lender calculation setup item.";
        }
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
        if (er != null && er.status() == ExecutionStatus.INPUT_REQUIRED) return "Provide manual input";
        if (er != null && !er.valueAvailable() && capability) return "Get data / Complete source step";
        if (CertificationStatus.CERTIFIED.name().equals(cert)) return null;
        if (CertificationStatus.REVOKED.name().equals(cert)) return "Re-certify via authorized admin";
        return "Test rule/policy";
    }

    private static String executionLabel(
            boolean capability,
            boolean valueAvailable,
            String status,
            boolean calcRequired,
            Map<String, Object> semantic) {
        if ("INGREDIENT".equals(String.valueOf(semantic.get("parameterClass")))) {
            if (capability && valueAvailable) return "Source available";
            if (capability) return "Data unavailable";
            return "Source field unavailable";
        }
        if (isManualClass(semantic)) {
            if (valueAvailable) return "Manual value available";
            return "Manual input required";
        }
        if (!capability || calcRequired) return "Not executable";
        if (valueAvailable) return "Value available";
        if (ExecutionStatus.DEPENDENCY_NOT_AVAILABLE.name().equals(status)) return "Dependency data unavailable";
        if (ExecutionStatus.DATA_NOT_AVAILABLE.name().equals(status)) return "Data unavailable";
        return "Executable when data available";
    }

    private static String liveUseDisplay(String certStatus, boolean capability) {
        if (CertificationStatus.CERTIFIED.name().equals(certStatus) && capability) {
            return "Certified for live use";
        }
        if (CertificationStatus.REVOKED.name().equals(certStatus)) {
            return "Certification revoked";
        }
        return "Not certified for live use";
    }

    private static String certificationLabel(String certStatus) {
        if (CertificationStatus.CERTIFIED.name().equals(certStatus)) return "Certified";
        if (CertificationStatus.REVOKED.name().equals(certStatus)) return "Revoked";
        return "Not approved for live use";
    }

    private static String parameterClassLabel(Map<String, Object> semantic) {
        String pc = String.valueOf(semantic.getOrDefault("parameterClass", ""));
        return switch (pc) {
            case "INGREDIENT" -> "Source ingredient";
            case "MANUAL_INPUT" -> "Manual input";
            case "CONFIGURATION" -> "Configuration";
            case "DECISION_OUTPUT" -> "Decision output";
            case "BUSINESS_PARAMETER" -> "Business parameter";
            default -> pc.isBlank() || "null".equals(pc) ? "Parameter" : pc;
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
