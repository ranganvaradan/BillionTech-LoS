package com.los.core.creditintelligence.policystudio.parameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified parameter read-model entry (POLICY-CONVERGENCE / GACAT-PERSISTENCE-1).
 * Persisted in ci_gacat_* tables; this record remains the in-memory face for consumers.
 * Does not create a new engine.
 */
public record CanonicalParameterDefinition(
        String id,
        String businessName,
        String evaluatedFrom,
        String type,
        String unit,
        String period,
        String availability,
        String calculationSummary,
        List<String> requiredPrimitives,
        String existingImplementationBinding,
        List<String> aliases,
        String liveRuleParameter,
        String liveScorecardParameter,
        Capability capability
) {
    public static final String RAW = "RAW";
    public static final String DERIVED = "DERIVED";
    public static final String MANUAL = "MANUAL";

    public CanonicalParameterDefinition {
        if (requiredPrimitives == null) requiredPrimitives = List.of();
        if (aliases == null) aliases = List.of();
        if (capability == null) {
            capability = Capability.defaultsFor(type, evaluatedFrom);
        }
    }

    /**
     * Source-capability honesty model — registry inclusion ≠ production-ready.
     */
    public record Capability(
            String schema,
            boolean sourceAvailable,
            boolean normalized,
            boolean derivationDefined,
            boolean implemented,
            boolean productionReady,
            String cardinality,
            String providerFieldPath,
            String missingDataTreatment,
            String filtersEligibility,
            String transformation,
            String aggregation
    ) {
        public String primaryStatus() {
            if (productionReady) return "PRODUCTION_READY";
            if (implemented) return "IMPLEMENTED";
            if (derivationDefined) return "DERIVATION_DEFINED";
            if (normalized) return "NORMALIZED";
            if (sourceAvailable) return "SOURCE_AVAILABLE";
            return "UNKNOWN";
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("schema", schema);
            m.put("sourceAvailable", sourceAvailable);
            m.put("normalized", normalized);
            m.put("derivationDefined", derivationDefined);
            m.put("implemented", implemented);
            m.put("productionReady", productionReady);
            m.put("primaryStatus", primaryStatus());
            if (cardinality != null) m.put("cardinality", cardinality);
            if (providerFieldPath != null) m.put("providerFieldPath", providerFieldPath);
            if (missingDataTreatment != null) m.put("missingDataTreatment", missingDataTreatment);
            if (filtersEligibility != null) m.put("filtersEligibility", filtersEligibility);
            if (transformation != null) m.put("transformation", transformation);
            if (aggregation != null) m.put("aggregation", aggregation);
            return m;
        }

        public static Capability of(
                String schema,
                boolean sourceAvailable,
                boolean normalized,
                boolean derivationDefined,
                boolean implemented,
                boolean productionReady,
                String cardinality,
                String providerFieldPath,
                String missingDataTreatment,
                String filtersEligibility,
                String transformation,
                String aggregation
        ) {
            return new Capability(
                    schema, sourceAvailable, normalized, derivationDefined, implemented, productionReady,
                    cardinality, providerFieldPath, missingDataTreatment, filtersEligibility, transformation, aggregation);
        }

        static Capability defaultsFor(String type, String evaluatedFrom) {
            String schema = schemaFromSource(evaluatedFrom);
            boolean derived = DERIVED.equalsIgnoreCase(type);
            return of(schema, true, true, derived, false, false,
                    "SCALAR", null, null, null, null, null);
        }

        private static String schemaFromSource(String evaluatedFrom) {
            if (evaluatedFrom == null) return "UNKNOWN";
            String s = evaluatedFrom.toLowerCase();
            if (s.contains("commercial")) return "BUREAU_COMMERCIAL";
            if (s.contains("bureau")) return "BUREAU_RETAIL";
            if (s.contains("bank")) return "BANK_STATEMENT";
            if (s.contains("aggregator") || s.equals("aa")) return "ACCOUNT_AGGREGATOR";
            if (s.contains("gst")) return "GST";
            if (s.contains("financial") || s.contains("itr")) return "FINANCIAL_STATEMENTS";
            if (s.contains("kyc")) return "KYC";
            if (s.contains("application")) return "APPLICATION";
            if (s.contains("program") || s.contains("product")) return "PROGRAM_PRODUCT";
            if (s.contains("manual")) return "MANUAL_INPUT";
            if (s.contains("computed") || s.contains("derived")) return "COMPUTED";
            return "OTHER";
        }
    }

    public Map<String, Object> toBusinessView() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("businessName", businessName);
        m.put("evaluatedFrom", evaluatedFrom);
        m.put("type", type);
        if (unit != null) m.put("unit", unit);
        if (period != null) m.put("period", period);
        if (availability != null) m.put("availability", availability);
        if (calculationSummary != null) m.put("calculationSummary", calculationSummary);
        if (requiredPrimitives != null && !requiredPrimitives.isEmpty()) {
            m.put("requiredPrimitives", requiredPrimitives);
        }
        if (existingImplementationBinding != null) {
            m.put("existingImplementationBinding", existingImplementationBinding);
        }
        if (aliases != null && !aliases.isEmpty()) m.put("aliases", aliases);
        if (liveRuleParameter != null) m.put("liveRuleParameter", liveRuleParameter);
        if (liveScorecardParameter != null) m.put("liveScorecardParameter", liveScorecardParameter);
        Capability cap = capability != null ? capability : Capability.defaultsFor(type, evaluatedFrom);
        m.put("capability", cap.toMap());
        m.put("sourceAvailable", cap.sourceAvailable());
        m.put("normalized", cap.normalized());
        m.put("derivationDefined", cap.derivationDefined());
        m.put("implemented", cap.implemented());
        m.put("productionReady", cap.productionReady());
        m.put("capabilityStatus", cap.primaryStatus());
        // Gate-3 mode distinction (same Capability SSOT)
        Map<String, Object> exec = ParameterExecutabilitySupport.evaluate(this);
        m.put("executionState", exec.get("executionState"));
        m.put("policyTestReady", exec.get("policyTestReady"));
        m.put("runtimeReady", exec.get("runtimeReady"));
        m.put("policyTest", Boolean.TRUE.equals(exec.get("policyTestReady")) ? "READY" : "NOT READY");
        m.put("runtime", Boolean.TRUE.equals(exec.get("runtimeReady")) ? "READY" : "NOT READY");
        m.put("production", Boolean.TRUE.equals(exec.get("productionReady")) ? "READY" : "NOT READY");
        if (cap.cardinality() != null) m.put("cardinality", cap.cardinality());
        if (cap.providerFieldPath() != null) m.put("providerFieldPath", cap.providerFieldPath());
        return m;
    }
}
