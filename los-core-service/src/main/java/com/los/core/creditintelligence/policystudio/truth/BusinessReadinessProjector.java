package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GOLDEN-PARAMETER-TRUTH — sole projector for businessReadiness READY/NOT_READY.
 * Consumers must not recompute; they read CanonicalParameterState.
 */
public final class BusinessReadinessProjector {

    /** Shared across recursive CPS.state(dep) calls on the same thread. */
    private static final ThreadLocal<Set<String>> VISITING = ThreadLocal.withInitial(HashSet::new);

    private BusinessReadinessProjector() {}

    public static Map<String, Object> project(
            CanonicalParameterDefinition def,
            Map<String, Object> semantic,
            Map<String, Object> source,
            Map<String, Object> execution,
            Map<String, Object> calculation) {

        Map<String, Object> out = new LinkedHashMap<>();
        if (def == null) {
            return readyMap(out, BusinessReadiness.NOT_READY, BusinessReadinessReason.NOT_IN_CATALOGUE,
                    "Not in catalogue", null);
        }

        String id = def.id();
        Set<String> visiting = VISITING.get();
        if (!visiting.add(id)) {
            return readyMap(out, BusinessReadiness.NOT_READY, BusinessReadinessReason.CALCULATION_INVALID,
                    "Dependency cycle", "Contact BillionTech");
        }

        try {
            String paramClass = String.valueOf(semantic.getOrDefault("parameterClass", ""));
            String mode = String.valueOf(semantic.getOrDefault("calculationMode", ""));
            boolean capability = Boolean.TRUE.equals(execution.get("capability"));
            String execStatus = execution.get("status") == null ? null : String.valueOf(execution.get("status"));
            boolean calcRequired = Boolean.TRUE.equals(calculation.get("required"))
                    || Boolean.TRUE.equals(calculation.get("definitionRequired"));

            if ("CONFIGURATION".equals(paramClass) || "DECISION_OUTPUT".equals(paramClass)) {
                return readyMap(out, BusinessReadiness.NOT_READY, BusinessReadinessReason.NOT_APPLICABLE,
                        "CONFIGURATION".equals(paramClass) ? "Configuration" : "Decision output",
                        null);
            }

            if ("MANUAL_INPUT".equals(paramClass) || "MANUAL".equalsIgnoreCase(mode)) {
                return readyMap(out, BusinessReadiness.READY, BusinessReadinessReason.MANUAL_INPUT,
                        "Needs manual input", "Provide manual input");
            }

            String sourceBlock = CanonicalSourceIntegrationAuthority.structuralBlockReason(source);
            boolean sourceNa = source != null && Boolean.TRUE.equals(source.get("notApplicable"));

            if ("INGREDIENT".equals(paramClass)) {
                if (sourceBlock != null) {
                    BusinessReadinessReason r = "SOURCE_NOT_CONFIGURED".equals(sourceBlock)
                            ? BusinessReadinessReason.SOURCE_NOT_CONFIGURED
                            : BusinessReadinessReason.SOURCE_NOT_INTEGRATED;
                    return readyMap(out, BusinessReadiness.NOT_READY, r,
                            reasonLabel(r), nextFor(r));
                }
                if (!capability) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.RAW_FIELD_NOT_AVAILABLE,
                            "Raw field not available", "Connect / map source field");
                }
                return readyMap(out, BusinessReadiness.READY, BusinessReadinessReason.READY,
                        "Ready", valueAvailable(execution) ? null : "Get data / Complete source step");
            }

            boolean raw = "RAW".equalsIgnoreCase(mode);

            if (raw) {
                if (!sourceNa && sourceBlock != null) {
                    BusinessReadinessReason r = "SOURCE_NOT_CONFIGURED".equals(sourceBlock)
                            ? BusinessReadinessReason.SOURCE_NOT_CONFIGURED
                            : BusinessReadinessReason.SOURCE_NOT_INTEGRATED;
                    return readyMap(out, BusinessReadiness.NOT_READY, r, reasonLabel(r), nextFor(r));
                }
                if (!capability) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.RAW_FIELD_NOT_AVAILABLE,
                            "Raw field not available", "Map / enable source field");
                }
                return readyMap(out, BusinessReadiness.READY, BusinessReadinessReason.READY,
                        "Ready", valueAvailable(execution) ? null : "Get data / Complete source step");
            }

            // Derived / authored / built-in
            if (!capability) {
                if (calcRequired
                        || ExecutionStatus.CALCULATION_NOT_DEFINED.name().equals(execStatus)
                        || ExecutionStatus.NOT_EXECUTABLE.name().equals(execStatus)) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.CALCULATION_NOT_DEFINED,
                            "Calculation not defined", "Set up calculation");
                }
                return readyMap(out, BusinessReadiness.NOT_READY,
                        BusinessReadinessReason.CALCULATION_INVALID,
                        "Calculation not executable", "Fix calculation");
            }

            // CPES capability=true means the calculation is structurally executable.
            // Runtime DEPENDENCY_NOT_AVAILABLE / DATA_NOT_AVAILABLE must NOT flip business readiness.
            // Recurse only into authored business deps that are themselves structurally NOT_READY
            // for calculation reasons (not ingredient/runtime data gaps).
            for (String depId : dependencyIds(execution, id)) {
                if (depId == null || depId.isBlank() || depId.equals(id)) continue;
                Map<String, Object> depState = CanonicalParameterStateService.state(depId);
                if (!BusinessReadiness.NOT_READY.name().equals(String.valueOf(depState.get("businessReadiness")))) {
                    continue;
                }
                String depReason = String.valueOf(depState.getOrDefault("businessReadinessReason", ""));
                if (BusinessReadinessReason.CALCULATION_NOT_DEFINED.name().equals(depReason)
                        || BusinessReadinessReason.CALCULATION_INVALID.name().equals(depReason)
                        || BusinessReadinessReason.DEPENDENCY_NOT_READY.name().equals(depReason)) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.DEPENDENCY_NOT_READY,
                            "Dependency not ready: " + depId + " (" + depReason + ")",
                            "Resolve dependency " + depId);
                }
            }

            return readyMap(out, BusinessReadiness.READY, BusinessReadinessReason.READY,
                    "Ready", valueAvailable(execution) ? null : "Get data / Complete source step");
        } finally {
            visiting.remove(id);
            if (visiting.isEmpty()) {
                VISITING.remove();
            }
        }
    }

    private static List<String> dependencyIds(Map<String, Object> execution, String selfId) {
        Object deps = execution.get("dependencies");
        if (deps instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList();
        }
        return AuthoredDerivedCalculationSupport.latestExecutableDependencies(selfId).orElse(List.of());
    }

    private static boolean valueAvailable(Map<String, Object> execution) {
        return Boolean.TRUE.equals(execution.get("valueAvailable"));
    }

    private static Map<String, Object> readyMap(
            Map<String, Object> out,
            BusinessReadiness readiness,
            BusinessReadinessReason reason,
            String label,
            String nextAction) {
        out.put("businessReadiness", readiness.name());
        out.put("businessReadinessReason", reason.name());
        out.put("businessReadinessLabel", label);
        out.put("primaryStatus", readiness.name());
        out.put("primaryStatusLabel", label);
        out.put("nextAction", nextAction);
        out.put("presentationKind", presentationKind(reason));
        return out;
    }

    private static String presentationKind(BusinessReadinessReason reason) {
        return switch (reason) {
            case MANUAL_INPUT -> "MANUAL_INPUT";
            case CALCULATION_NOT_DEFINED, CALCULATION_INVALID -> "CALCULATION_SETUP";
            case SOURCE_NOT_INTEGRATED, SOURCE_NOT_CONFIGURED, RAW_FIELD_NOT_AVAILABLE -> "SOURCE";
            case DEPENDENCY_NOT_READY -> "DEPENDENCY";
            case NOT_APPLICABLE, NOT_SUPPORTED -> "EXCLUDED";
            default -> "READY";
        };
    }

    public static String reasonLabel(BusinessReadinessReason r) {
        return switch (r) {
            case READY -> "Ready";
            case SOURCE_NOT_INTEGRATED -> "Source not integrated";
            case SOURCE_NOT_CONFIGURED -> "Source not configured";
            case RAW_FIELD_NOT_AVAILABLE -> "Raw field not available";
            case CALCULATION_NOT_DEFINED -> "Calculation not defined";
            case CALCULATION_INVALID -> "Calculation invalid";
            case DEPENDENCY_NOT_READY -> "Dependency not ready";
            case MANUAL_INPUT -> "Needs manual input";
            case NOT_SUPPORTED -> "Not supported";
            case NOT_APPLICABLE -> "Not applicable";
            case NOT_IN_CATALOGUE -> "Not in catalogue";
        };
    }

    private static String nextFor(BusinessReadinessReason r) {
        return switch (r) {
            case SOURCE_NOT_INTEGRATED -> "Connect source / Contact BillionTech";
            case SOURCE_NOT_CONFIGURED -> "Configure / subscribe source";
            case RAW_FIELD_NOT_AVAILABLE -> "Map / enable source field";
            case CALCULATION_NOT_DEFINED, CALCULATION_INVALID -> "Set up calculation";
            case DEPENDENCY_NOT_READY -> "Resolve dependencies";
            case MANUAL_INPUT -> "Provide manual input";
            default -> null;
        };
    }
}
