package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.derived.AuthoredDerivedCalculationSupport;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

            boolean sourceNa = source != null && Boolean.TRUE.equals(source.get("notApplicable"));
            boolean platformIntegrated = source != null && Boolean.TRUE.equals(source.get("platformIntegrated"));

            if ("INGREDIENT".equals(paramClass)) {
                if (!sourceNa && !platformIntegrated) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.SOURCE_NOT_INTEGRATED,
                            reasonLabel(BusinessReadinessReason.SOURCE_NOT_INTEGRATED),
                            nextFor(BusinessReadinessReason.SOURCE_NOT_INTEGRATED));
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
                if (!sourceNa && !platformIntegrated) {
                    return readyMap(out, BusinessReadiness.NOT_READY,
                            BusinessReadinessReason.SOURCE_NOT_INTEGRATED,
                            reasonLabel(BusinessReadinessReason.SOURCE_NOT_INTEGRATED),
                            nextFor(BusinessReadinessReason.SOURCE_NOT_INTEGRATED));
                }
                // lenderConfigured is a separate displayed axis — does not collapse CPES-capable RAW to NOT_READY
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

            // GOLDEN-PARAMETER-DEPENDENCY-INTEGRITY:
            // CPES capability alone is insufficient. Every mandatory structural dependency
            // (catalogue requiredPrimitives ∪ execution/authored deps) must itself be READY.
            // Runtime DATA_NOT_AVAILABLE / valueAvailable=false on a READY dep must NOT flip this.
            for (String depId : structuralDependencyIds(def, execution)) {
                if (depId == null || depId.isBlank() || depId.equals(id)) continue;
                Map<String, Object> depState = CanonicalParameterStateService.state(depId);
                String depBr = String.valueOf(depState.getOrDefault("businessReadiness", ""));
                String depReason = String.valueOf(depState.getOrDefault("businessReadinessReason", ""));
                if (BusinessReadiness.READY.name().equals(depBr)) {
                    continue;
                }
                if (BusinessReadinessReason.NOT_APPLICABLE.name().equals(depReason)) {
                    continue;
                }
                // Unresolved / blank / NOT_READY (including RAW_FIELD_NOT_AVAILABLE, SOURCE_*, CALC_*)
                return readyMap(out, BusinessReadiness.NOT_READY,
                        BusinessReadinessReason.DEPENDENCY_NOT_READY,
                        "Dependency not ready: " + depId + " (" + depReason + ")",
                        "Resolve dependency " + depId);
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

    /**
     * Mandatory structural dependency IDs for readiness composition.
     * Same identity used by catalogue "How is this calculated?", CPS, and runtime authored defs.
     * Filter/classification tokens in seed primitives (e.g. CHEQUE_RETURN) are not parameter IDs.
     * Only catalogue-resolvable canonical IDs participate in readiness composition.
     */
    public static List<String> structuralDependencyIds(
            CanonicalParameterDefinition def, Map<String, Object> execution) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (def != null && def.requiredPrimitives() != null) {
            for (String p : def.requiredPrimitives()) {
                addCanonicalDependency(ids, p);
            }
        }
        Object deps = execution != null ? execution.get("dependencies") : null;
        if (deps instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) addCanonicalDependency(ids, String.valueOf(o));
            }
        }
        if (def != null) {
            AuthoredDerivedCalculationSupport.latestExecutableDependencies(def.id())
                    .ifPresent(list -> {
                        for (String s : list) addCanonicalDependency(ids, s);
                    });
        }
        return new ArrayList<>(ids);
    }

    private static void addCanonicalDependency(Set<String> ids, String raw) {
        if (raw == null) return;
        String p = raw.trim();
        if (p.isBlank()) return;
        // Seed sometimes stores filter labels (EMI, CHEQUE_RETURN) — not canonical parameter IDs
        if (!p.contains(".")) return;
        // Only catalogue-resolvable IDs participate in readiness composition (one identity).
        // Dangling seed primitives (e.g. bank.transaction collection root not yet catalogued)
        // are catalogue debt — not silent READY inference from CPES alone when resolvable deps fail.
        if (PolicyStudioConvergencePresenter.registry().findById(p).isEmpty()) {
            return;
        }
        ids.add(p);
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
