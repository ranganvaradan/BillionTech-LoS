package com.los.core.creditintelligence.policystudio.parameters.semantic;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Projects Wave-4 semantic metadata into catalogue business views.
 * Does not alter CPES capability or legacy flag authority.
 */
public final class GacatSemanticProjection {

    private GacatSemanticProjection() {}

    public static Map<String, Object> project(CanonicalParameterDefinition def) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (def == null) {
            return m;
        }
        GacatSemanticRegistry registry = GacatSemanticRegistry.shared();
        return project(def, registry);
    }

    public static Map<String, Object> project(CanonicalParameterDefinition def, GacatSemanticRegistry registry) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("semanticVersion", GacatSemanticTaxonomy.SEMANTIC_VERSION);
        registry.find(def.id()).ifPresentOrElse(e -> {
            m.put("parameterClass", e.parameterClass().name());
            m.put("semanticCardinality", e.cardinality().name());
            m.put("calculationMode", e.calculationMode().name());
            m.put("semanticValueType", e.valueType().name());
            m.put("semanticUnit", e.unit().name());
            m.put("policySelectableDefault", e.policySelectableDefault());
            m.put("sourceDomain", e.sourceDomain());
            m.put("aliasGroup", e.aliasGroup());
            m.put("overlapRelation", e.overlapRelation().name());
            if (e.overlapNote() != null) m.put("overlapNote", e.overlapNote());
            if (e.semanticIssue() != null) m.put("semanticIssue", e.semanticIssue());
            m.put("legacyFlagsAuthority", "LEGACY_EXECUTION_METADATA");
            m.put("legacyImplemented", e.legacyImplemented());
            m.put("legacyProductionReady", e.legacyProductionReady());
            m.put("legacyType", e.legacyType());
            // Honesty: semantic calculationMode ≠ executability
            m.put("calculationModeDoesNotImplyCapability", true);
        }, () -> {
            m.put("parameterClass", GacatSemanticTaxonomy.ParameterClass.UNKNOWN.name());
            m.put("semanticIssue", "NEEDS_SEMANTIC_REVIEW:not_in_registry");
        });
        return m;
    }

    /** Merge semantic block into an existing business view map (non-destructive). */
    public static void mergeIntoBusinessView(Map<String, Object> businessView, CanonicalParameterDefinition def) {
        if (businessView == null || def == null) return;
        businessView.put("semantic", project(def));
        // Surface class for minimal Data & Parameters presentation without UI redesign
        Object sem = businessView.get("semantic");
        if (sem instanceof Map<?, ?> sm && sm.get("parameterClass") != null) {
            businessView.put("parameterClass", sm.get("parameterClass"));
            businessView.put("policySelectableDefault", sm.get("policySelectableDefault"));
        }
    }
}
