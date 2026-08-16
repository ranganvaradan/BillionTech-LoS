package com.los.core.creditintelligence.policystudio.truth;

import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FINAL-CANONICAL-PARAMETER-STATE-REFACTOR-1 — sole authority for parameter state.
 *
 * <p>Consumers may add contextual overlays (rule lifecycle, test input values, acquisition).
 * They must never independently derive executable / calculation-setup / certification status.
 *
 * <p>Evolves {@link CanonicalParameterTruthProjection} rather than inventing a parallel model.
 * Serialized contract is a single map with nested axes + presentation.
 */
public final class CanonicalParameterStateService {

    public static final String AUTHORITY = "CanonicalParameterStateService";
    public static final String CONTRACT = "CanonicalParameterState";

    /** Surfaces that must obtain parameter state only via this service. */
    public static final List<String> CONSUMER_SURFACES = List.of(
            SurfaceCanonicalTruthFacade.DATA_PARAMETERS,
            SurfaceCanonicalTruthFacade.POLICY_STUDIO,
            SurfaceCanonicalTruthFacade.POLICY_INVENTORY,
            SurfaceCanonicalTruthFacade.SCORECARD_PICKER,
            SurfaceCanonicalTruthFacade.POLICY_TEST,
            SurfaceCanonicalTruthFacade.WORKFLOW_W6,
            SurfaceCanonicalTruthFacade.UNDERWRITING);

    private CanonicalParameterStateService() {}

    /** Default POLICY_TEST mode — primary lender/design state. */
    public static Map<String, Object> state(String canonicalId) {
        return state(canonicalId, EvaluationMode.POLICY_TEST, null, null);
    }

    public static Map<String, Object> state(
            String canonicalId,
            EvaluationMode mode,
            EvaluationContext context,
            LocalDate evaluationAsOf) {
        Map<String, Object> projected = CanonicalParameterTruthProjection.project(
                canonicalId, mode, context, evaluationAsOf);
        return toContract(projected, mode);
    }

    public static List<Map<String, Object>> stateAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> p : CanonicalParameterTruthProjection.projectAll()) {
            out.add(toContract(p, EvaluationMode.POLICY_TEST));
        }
        return out;
    }

    /** Surface view — presentation hints only; same CanonicalParameterState underneath. */
    public static Map<String, Object> forSurface(String surface, String canonicalId) {
        Map<String, Object> state = state(canonicalId);
        Map<String, Object> view = SurfaceCanonicalTruthFacade.forSurface(surface, canonicalId);
        // Ensure facade cannot diverge: stamp authoritative state contract reference
        view.put("parameterStateAuthority", AUTHORITY);
        view.put("parameterStateContract", CONTRACT);
        view.put("canonicalParameterState", state);
        view.put("primaryStatus", state.get("primaryStatus"));
        view.put("primaryStatusLabel", state.get("primaryStatusLabel"));
        view.put("nextAction", state.get("nextAction"));
        return view;
    }

    /**
     * Attach CanonicalParameterState onto an existing consumer row (inventory / operand / test input).
     * Overwrites competing readiness fields.
     */
    public static void stamp(Map<String, Object> row, String canonicalId) {
        if (row == null || canonicalId == null || canonicalId.isBlank()) return;
        Map<String, Object> st = state(canonicalId.trim());
        row.put("canonicalParameterState", st);
        row.put("parameterStateAuthority", AUTHORITY);
        row.put("primaryStatus", st.get("primaryStatus"));
        row.put("primaryStatusLabel", st.get("primaryStatusLabel"));
        row.put("nextAction", st.get("nextAction"));
        row.put("calculationExplanation", st.get("calculationExplanation"));
        row.put("parameterClassLabel", st.get("parameterClassLabel"));
        row.put("executionLabel", st.get("executionLabel"));
        row.put("certificationLabel", st.get("certificationLabel"));
        @SuppressWarnings("unchecked")
        Map<String, Object> execution = st.get("execution") instanceof Map<?, ?>
                ? (Map<String, Object>) st.get("execution") : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> calculation = st.get("calculation") instanceof Map<?, ?>
                ? (Map<String, Object>) st.get("calculation") : Map.of();
        boolean capable = Boolean.TRUE.equals(execution.get("capability"));
        row.put("policyTestReady", capable);
        row.put("calculationRequired", Boolean.TRUE.equals(calculation.get("definitionRequired")));
        // Demote legacy competing fields
        row.put("productionReady", false);
        row.put("catalogueImplementedIsNotReadiness", true);
        row.put("overallReadiness", st.get("primaryStatus"));
    }

    public static String primaryStatus(String canonicalId) {
        return String.valueOf(state(canonicalId).get("primaryStatus"));
    }

    public static String primaryStatusLabel(String canonicalId) {
        return String.valueOf(state(canonicalId).get("primaryStatusLabel"));
    }

    public static boolean isSourceIngredient(Map<String, Object> state) {
        @SuppressWarnings("unchecked")
        Map<String, Object> semantic = state.get("semantic") instanceof Map<?, ?>
                ? (Map<String, Object>) state.get("semantic") : Map.of();
        return "INGREDIENT".equals(String.valueOf(semantic.get("parameterClass")));
    }

    private static Map<String, Object> toContract(Map<String, Object> projected, EvaluationMode mode) {
        Map<String, Object> out = new LinkedHashMap<>(projected);
        out.put("stateAuthority", AUTHORITY);
        out.put("stateContract", CONTRACT);
        out.put("projectionAuthority", CanonicalParameterTruthProjection.AUTHORITY);
        out.put("evaluationMode", mode == null ? EvaluationMode.POLICY_TEST.name() : mode.name());
        // Normalize calculation axis names for the contract
        @SuppressWarnings("unchecked")
        Map<String, Object> calc = projected.get("calculation") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) projected.get("calculation"))
                : new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> semantic = projected.get("semantic") instanceof Map<?, ?>
                ? (Map<String, Object>) projected.get("semantic") : Map.of();
        String pc = String.valueOf(semantic.getOrDefault("parameterClass", ""));
        boolean ingredient = "INGREDIENT".equals(pc);
        boolean manual = "MANUAL_INPUT".equals(pc);
        boolean config = "CONFIGURATION".equals(pc) || "DECISION_OUTPUT".equals(pc);
        calc.put("applicable", !ingredient && !config);
        calc.putIfAbsent("definitionRequired", calc.get("required"));
        calc.put("setupNotApplicable", ingredient || config);
        out.put("calculation", calc);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = projected.get("acquisition") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) projected.get("acquisition"))
                : new LinkedHashMap<>();
        @SuppressWarnings("unchecked")
        Map<String, Object> execution = projected.get("execution") instanceof Map<?, ?>
                ? (Map<String, Object>) projected.get("execution") : Map.of();
        data.put("available", Boolean.TRUE.equals(execution.get("valueAvailable")));
        out.put("data", data);

        List<String> actions = new ArrayList<>();
        Object next = projected.get("nextAction");
        if (next != null && !String.valueOf(next).isBlank()) {
            actions.add(String.valueOf(next));
        }
        Map<String, Object> presentation = new LinkedHashMap<>();
        presentation.put("primaryStatus", projected.get("primaryStatus"));
        presentation.put("primaryStatusLabel", projected.get("primaryStatusLabel"));
        presentation.put("businessExplanation", projected.get("calculationExplanation"));
        presentation.put("allowedActions", actions);
        presentation.put("parameterClassLabel", projected.get("parameterClassLabel"));
        out.put("presentation", presentation);

        out.put("businessName", semantic.get("businessName"));
        if (manual) {
            out.put("manualInputAxis", true);
        }
        if (ingredient) {
            out.put("sourceIngredientAxis", true);
        }
        return out;
    }
}
