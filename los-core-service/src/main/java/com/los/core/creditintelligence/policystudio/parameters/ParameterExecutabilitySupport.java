package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterCapabilityProjection;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * POLICY-STUDIO-GATE3 facade — execution capability comes only from
 * {@link com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService}
 * via {@link CanonicalParameterCapabilityProjection}.
 *
 * <p>Catalogue {@code implemented}/{@code production_ready} are never execution proof.
 */
public final class ParameterExecutabilitySupport {

    public static final String PRODUCTION_READY = "PRODUCTION_READY";
    public static final String RUNTIME_READY_NONPROD = "RUNTIME_READY_NONPROD";
    public static final String POLICY_TEST_READY = "POLICY_TEST_READY";
    public static final String DERIVATION_DEFINED_NOT_IMPLEMENTED = "DERIVATION_DEFINED_NOT_IMPLEMENTED";
    public static final String SOURCE_AVAILABLE_NOT_BOUND = "SOURCE_AVAILABLE_NOT_BOUND";
    public static final String MANUAL_AUTHORISED = "MANUAL_AUTHORISED";
    public static final String DATA_SOURCE_UNAVAILABLE = "DATA_SOURCE_UNAVAILABLE";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String NOT_EXECUTABLE = "NOT_EXECUTABLE";

    private ParameterExecutabilitySupport() {}

    /** Known PolicyDsl id ↔ snapshot/runtime fact-path aliases with TRUE_COMPAT semantics only. */
    public static List<String> runtimeFactAliases(String canonicalParameterId) {
        return List.copyOf(com.los.core.creditintelligence.policystudio.parameters.execution
                .CanonicalCompatibilityRegistry.trueCompatAliases(canonicalParameterId));
    }

    /** Inventory-only: true-compat + dangerous (never stamp dangerous onto exact operands). */
    public static List<String> inventoryFactAliases(String canonicalParameterId) {
        List<String> all = new ArrayList<>(runtimeFactAliases(canonicalParameterId));
        all.addAll(com.los.core.creditintelligence.policystudio.parameters.execution
                .CanonicalCompatibilityRegistry.dangerousAliases(canonicalParameterId));
        return List.copyOf(all);
    }

    public static Map<String, Object> evaluate(String parameterId) {
        if (parameterId == null || parameterId.isBlank()) {
            return unavailable(parameterId, "Empty parameter id");
        }
        Optional<CanonicalParameterDefinition> opt = CanonicalParameterRegistry.shared().findById(parameterId.trim());
        if (opt.isEmpty()) {
            // Authoring overlays — capability only if spine has a producer for the exact ID
            if (BusinessConceptResolver.WRITEOFF_NON_CC.equals(parameterId)
                    || BusinessConceptResolver.WRITEOFF_CC.equals(parameterId)) {
                return evaluateOverlay(parameterId);
            }
            return unavailable(parameterId, "Not in GACAT / CanonicalParameterRegistry");
        }
        return evaluate(opt.get());
    }

    public static Map<String, Object> evaluate(CanonicalParameterDefinition def) {
        Map<String, Object> proj = CanonicalParameterCapabilityProjection.project(def);
        Map<String, Object> out = base(def.id());
        out.put("canonicalParameterId", def.id());
        out.put("businessName", def.businessName());
        out.put("source", def.evaluatedFrom());
        out.put("type", def.type());
        out.put("normalizedFactBinding", def.existingImplementationBinding());
        out.put("calculatorBinding", def.existingImplementationBinding());
        out.put("aliases", def.aliases());
        out.put("runtimeFactAliases", runtimeFactAliases(def.id()));
        out.put("liveRuleParameter", def.liveRuleParameter());
        out.put("liveScorecardParameter", def.liveScorecardParameter());
        out.put("allowCanonicalAuthority", false);

        CanonicalParameterDefinition.Capability cap = def.capability() != null
                ? def.capability()
                : CanonicalParameterDefinition.Capability.defaultsFor(def.type(), def.evaluatedFrom());
        out.put("capability", cap.toMap());
        out.put("missingDataBehaviour", cap.missingDataTreatment() != null
                ? cap.missingDataTreatment()
                : "DATA_INSUFFICIENT");
        out.put("provenanceModel", provenanceModelFor(def, Boolean.TRUE.equals(proj.get("policyTestReady"))));

        out.put("executionState", proj.get("executionState"));
        out.put("policyTestReady", proj.get("policyTestReady"));
        out.put("runtimeReady", proj.get("runtimeReady"));
        // Production certification not established — never promote catalogue boolean
        out.put("productionReady", false);
        out.put("legacyCatalogueProductionReadyClaim",
                def.capability() != null && def.capability().productionReady());
        out.put("executable", proj.get("executable"));
        out.put("blockers", proj.get("blockers"));
        out.put("productionCertification", proj.get("productionCertification"));
        out.put("designable", proj.get("designable"));
        out.put("executionAuthority", "CanonicalParameterExecutionService");
        out.put("spineInstalled", ExecutionCapabilityAuthority.isInstalled());
        out.put("catalogueFlagsAreNotExecutionAuthority", true);
        out.put("certificationStatus", "NOT_ESTABLISHED");
        if (proj.get("executionContractSample") != null) {
            out.put("executionContractSample", proj.get("executionContractSample"));
        }
        return out;
    }

    /**
     * Studio overlays without GACAT rows — still require spine capability; never catalogue invent.
     */
    public static Map<String, Object> studioOverlay(String id, String calculator, List<String> ingredients) {
        return evaluateOverlay(id, calculator, ingredients);
    }

    private static Map<String, Object> evaluateOverlay(String id) {
        return evaluateOverlay(id, null, List.of());
    }

    private static Map<String, Object> evaluateOverlay(String id, String calculator, List<String> ingredients) {
        boolean pt = ExecutionCapabilityAuthority.hasExecutionCapability(id, EvaluationMode.POLICY_TEST);
        Map<String, Object> out = base(id);
        out.put("canonicalParameterId", id);
        out.put("source", "Bureau");
        out.put("type", CanonicalParameterDefinition.DERIVED);
        out.put("executionState", pt ? POLICY_TEST_READY : NOT_EXECUTABLE);
        out.put("policyTestReady", pt);
        out.put("runtimeReady", false);
        out.put("productionReady", false);
        out.put("executable", pt);
        out.put("calculatorBinding", calculator);
        out.put("normalizedFactBinding", null);
        out.put("rawIngredients", ingredients);
        out.put("authoringOverlay", true);
        out.put("missingDataBehaviour", "DATA_INSUFFICIENT");
        out.put("provenanceModel", "SPINE_OR_UNREGISTERED");
        out.put("runtimeFactAliases", List.of());
        List<String> blockers = new ArrayList<>();
        if (!pt) {
            blockers.add("Authoring overlay without CanonicalParameterExecutionService producer");
        }
        blockers.add("Studio/authoring overlay — production certification not established");
        out.put("blockers", blockers);
        out.put("allowCanonicalAuthority", false);
        out.put("executionAuthority", "CanonicalParameterExecutionService");
        return out;
    }

    public static void stampOnto(Map<String, Object> target, Map<String, Object> exec) {
        if (target == null || exec == null) return;
        target.put("executionState", exec.get("executionState"));
        target.put("policyTestReady", exec.get("policyTestReady"));
        target.put("runtimeReady", exec.get("runtimeReady"));
        target.put("productionReady", exec.get("productionReady"));
        target.put("missingDataBehaviour", exec.get("missingDataBehaviour"));
        target.put("runtimeFactAliases", exec.get("runtimeFactAliases"));
        target.put("executabilityBlockers", exec.get("blockers"));
        target.put("provenanceModel", exec.get("provenanceModel"));
        if (exec.get("productionCertification") != null) {
            target.put("productionCertification", exec.get("productionCertification"));
        }
        if (exec.get("legacyCatalogueProductionReadyClaim") != null) {
            target.put("legacyCatalogueProductionReadyClaim", exec.get("legacyCatalogueProductionReadyClaim"));
        }
    }

    private static String provenanceModelFor(CanonicalParameterDefinition def, boolean spineCapable) {
        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type())) {
            return "MANUAL_AUTHORISED";
        }
        if (spineCapable) {
            return "CANONICAL_PARAMETER_EXECUTION_SERVICE";
        }
        return "NO_REGISTERED_PRODUCER";
    }

    private static Map<String, Object> base(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canonicalParameterId", id);
        out.put("canonicalDefinitionVersion", "GACAT");
        return out;
    }

    private static Map<String, Object> unavailable(String id, String reason) {
        Map<String, Object> out = base(id);
        out.put("executionState", DATA_SOURCE_UNAVAILABLE);
        out.put("policyTestReady", false);
        out.put("runtimeReady", false);
        out.put("productionReady", false);
        out.put("executable", false);
        out.put("blockers", List.of(reason));
        out.put("allowCanonicalAuthority", false);
        out.put("executionAuthority", "CanonicalParameterExecutionService");
        return out;
    }
}
