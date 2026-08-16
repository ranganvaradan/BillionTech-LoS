package com.los.core.creditintelligence.policystudio.parameters.semantic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Wave-4 semantic classification row for a GACAT canonical ID.
 */
public record GacatSemanticEntry(
        String canonicalId,
        String family,
        GacatSemanticTaxonomy.ParameterClass parameterClass,
        GacatSemanticTaxonomy.Cardinality cardinality,
        GacatSemanticTaxonomy.CalculationMode calculationMode,
        GacatSemanticTaxonomy.ValueType valueType,
        GacatSemanticTaxonomy.SemanticUnit unit,
        boolean policySelectableDefault,
        String sourceDomain,
        List<String> dependencies,
        String legacyType,
        boolean legacyImplemented,
        boolean legacyProductionReady,
        String semanticIssue,
        String aliasGroup,
        GacatSemanticTaxonomy.OverlapRelation overlapRelation,
        String overlapNote
) {
    public GacatSemanticEntry {
        if (dependencies == null) dependencies = List.of();
        if (parameterClass == null) parameterClass = GacatSemanticTaxonomy.ParameterClass.UNKNOWN;
        if (cardinality == null) cardinality = GacatSemanticTaxonomy.Cardinality.UNKNOWN;
        if (calculationMode == null) calculationMode = GacatSemanticTaxonomy.CalculationMode.UNKNOWN;
        if (valueType == null) valueType = GacatSemanticTaxonomy.ValueType.UNKNOWN;
        if (unit == null) unit = GacatSemanticTaxonomy.SemanticUnit.UNKNOWN;
        if (overlapRelation == null) overlapRelation = GacatSemanticTaxonomy.OverlapRelation.NONE;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canonicalId", canonicalId);
        m.put("family", family);
        m.put("parameterClass", parameterClass.name());
        m.put("cardinality", cardinality.name());
        m.put("calculationMode", calculationMode.name());
        m.put("valueType", valueType.name());
        m.put("unit", unit.name());
        m.put("policySelectableDefault", policySelectableDefault);
        m.put("sourceDomain", sourceDomain);
        m.put("dependencies", dependencies);
        m.put("legacyType", legacyType);
        m.put("legacyImplemented", legacyImplemented);
        m.put("legacyProductionReady", legacyProductionReady);
        m.put("legacyFlagsAuthority", "LEGACY_EXECUTION_METADATA");
        m.put("semanticIssue", semanticIssue);
        m.put("aliasGroup", aliasGroup);
        m.put("overlapRelation", overlapRelation.name());
        if (overlapNote != null) m.put("overlapNote", overlapNote);
        m.put("semanticVersion", GacatSemanticTaxonomy.SEMANTIC_VERSION);
        return m;
    }
}
