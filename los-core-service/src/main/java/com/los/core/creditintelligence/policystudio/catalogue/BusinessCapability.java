package com.los.core.creditintelligence.policystudio.catalogue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized business underwriting capability (POLICY-UX-2A read model).
 */
public record BusinessCapability(
        String businessCapabilityId,
        String businessName,
        String description,
        CapabilityDomain domain,
        String factOrMeasure,
        String dataSource,
        List<String> supportedOperators,
        List<ParameterDefinition> parameterDefinitions,
        List<String> supportedTreatments,
        boolean parameterisable,
        boolean manualInputPossible,
        boolean manualReviewPossible,
        boolean productionSupported,
        boolean studioSupported,
        String dataAvailability,
        List<ImplementationBinding> implementationBindings
) {
    public BusinessCapability {
        supportedOperators = supportedOperators == null ? List.of() : List.copyOf(supportedOperators);
        parameterDefinitions = parameterDefinitions == null ? List.of() : List.copyOf(parameterDefinitions);
        supportedTreatments = supportedTreatments == null ? List.of() : List.copyOf(supportedTreatments);
        implementationBindings = implementationBindings == null ? List.of() : List.copyOf(implementationBindings);
    }

    public Map<String, Object> toBusinessView(boolean advanced) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("businessCapabilityId", businessCapabilityId);
        m.put("businessName", businessName);
        m.put("description", description);
        m.put("domain", domain.name());
        m.put("domainLabel", domain.displayName());
        m.put("factOrMeasure", factOrMeasure);
        m.put("dataSource", dataSource);
        m.put("supportedOperators", supportedOperators);
        m.put("parameterDefinitions", parameterDefinitions.stream().map(ParameterDefinition::toBusinessView).toList());
        m.put("supportedTreatments", supportedTreatments);
        m.put("parameterisable", parameterisable);
        m.put("manualInputPossible", manualInputPossible);
        m.put("manualReviewPossible", manualReviewPossible);
        m.put("productionSupported", productionSupported);
        m.put("studioSupported", studioSupported);
        m.put("dataAvailability", dataAvailability);
        m.put("implementationBindings", implementationBindings.stream()
                .map(b -> b.toBusinessView(advanced))
                .toList());
        return m;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String businessCapabilityId;
        private String businessName;
        private String description;
        private CapabilityDomain domain;
        private String factOrMeasure;
        private String dataSource;
        private List<String> supportedOperators = new ArrayList<>();
        private List<ParameterDefinition> parameterDefinitions = new ArrayList<>();
        private List<String> supportedTreatments = new ArrayList<>();
        private boolean parameterisable = true;
        private boolean manualInputPossible;
        private boolean manualReviewPossible = true;
        private boolean productionSupported;
        private boolean studioSupported;
        private String dataAvailability = "AUTOMATIC";
        private List<ImplementationBinding> implementationBindings = new ArrayList<>();

        private Builder(String businessCapabilityId) {
            this.businessCapabilityId = businessCapabilityId;
        }

        public Builder name(String v) { this.businessName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder domain(CapabilityDomain v) { this.domain = v; return this; }
        public Builder fact(String v) { this.factOrMeasure = v; return this; }
        public Builder dataSource(String v) { this.dataSource = v; return this; }
        public Builder operators(String... ops) {
            this.supportedOperators = List.of(ops);
            return this;
        }
        public Builder param(ParameterDefinition p) {
            this.parameterDefinitions.add(p);
            return this;
        }
        public Builder treatments(String... t) {
            this.supportedTreatments = List.of(t);
            return this;
        }
        public Builder parameterisable(boolean v) { this.parameterisable = v; return this; }
        public Builder manualInput(boolean v) { this.manualInputPossible = v; return this; }
        public Builder manualReview(boolean v) { this.manualReviewPossible = v; return this; }
        public Builder production(boolean v) { this.productionSupported = v; return this; }
        public Builder studio(boolean v) { this.studioSupported = v; return this; }
        public Builder availability(String v) { this.dataAvailability = v; return this; }
        public Builder binding(ImplementationBinding b) {
            this.implementationBindings.add(b);
            return this;
        }

        public BusinessCapability build() {
            return new BusinessCapability(
                    businessCapabilityId, businessName, description, domain, factOrMeasure, dataSource,
                    supportedOperators, parameterDefinitions, supportedTreatments,
                    parameterisable, manualInputPossible, manualReviewPossible,
                    productionSupported, studioSupported, dataAvailability, implementationBindings);
        }
    }
}
