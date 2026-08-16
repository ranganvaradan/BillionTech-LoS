package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.ParameterExecutabilitySupport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * DP-1 — single derived readiness / source lineage projection over GACAT + Gate3 + workflow-provides.
 * Read-only: does not mutate catalogue flags or create a parallel readiness authority.
 */
public final class GacatParameterReadinessProjection {

    public static final String OVERALL_PRODUCTION_READY = "PRODUCTION_READY";
    public static final String OVERALL_RUNTIME_READY_NONPROD = "RUNTIME_READY_NONPROD";
    public static final String OVERALL_POLICY_TEST_ONLY = "POLICY_TEST_ONLY";
    public static final String OVERALL_CATALOGUE_ONLY = "CATALOGUE_ONLY";
    public static final String OVERALL_READINESS_UNKNOWN = "READINESS_UNKNOWN";

    public static final String SOURCE_PROVIDER = "PROVIDER";
    public static final String SOURCE_APPLICATION_INPUT = "APPLICATION_INPUT";
    public static final String SOURCE_WORKFLOW = "WORKFLOW";
    public static final String SOURCE_INTERNAL_SYSTEM = "INTERNAL_SYSTEM";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_DERIVED = "DERIVED";
    public static final String SOURCE_UNKNOWN = "UNKNOWN";

    public static final String PROVIDER_REGISTERED = "REGISTERED";
    public static final String PROVIDER_INFERRED = "INFERRED";
    public static final String PROVIDER_UNKNOWN = "UNKNOWN";
    public static final String PROVIDER_NOT_APPLICABLE = "NOT_APPLICABLE";

    private GacatParameterReadinessProjection() {}

    /** Project readiness + source lineage for a catalogue definition. */
    public static Map<String, Object> project(CanonicalParameterDefinition def) {
        Objects.requireNonNull(def, "def");
        Map<String, Object> gate3 = ParameterExecutabilitySupport.evaluate(def);
        Map<String, Object> workflow = WorkflowParameterProvidesCatalog.lookupForParameter(def.id());

        CanonicalParameterDefinition.Capability cap = Objects.requireNonNull(
                def.capability(), "capability");

        boolean implemented = cap.implemented();
        boolean sourceAvailable = cap.sourceAvailable();
        // Execution capability — Gate3 facade over CanonicalParameterExecutionService only
        boolean policyTestReady = Boolean.TRUE.equals(gate3.get("policyTestReady"));
        boolean runtimeReady = Boolean.TRUE.equals(gate3.get("runtimeReady"));
        // Production certification not established — catalogue boolean is legacy claim only
        boolean catalogueProductionReadyClaim = cap.productionReady();
        boolean productionReady = Boolean.TRUE.equals(gate3.get("productionReady")); // always false until cert authority
        boolean manual = CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(def.type());

        boolean workflowAvailable = Boolean.TRUE.equals(workflow.get("workflowAvailable"))
                || Boolean.TRUE.equals(workflow.get("integrationAvailable"));
        String path = cap.providerFieldPath();
        boolean mappingAvailable = path != null && !path.isBlank();
        String binding = def.existingImplementationBinding();
        boolean calculatorAvailable = binding != null && !binding.isBlank()
                && !"DEFINED_NOT_IMPLEMENTED".equalsIgnoreCase(binding)
                && !"UNRESOLVED".equalsIgnoreCase(binding);
        // Explicit provider_code not populated on bindings today (audit: 0).
        boolean providerBound = false;
        boolean provenanceAvailable = (def.calculationSummary() != null && !def.calculationSummary().isBlank())
                || (def.requiredPrimitives() != null && !def.requiredPrimitives().isEmpty())
                || gate3.get("provenanceModel") != null;

        String sourceType = deriveSourceType(def, workflow, mappingAvailable);
        Map<String, Object> provider = deriveProviderDisplay(cap, workflow, sourceType);

        String executionState = String.valueOf(gate3.getOrDefault("executionState", ""));
        List<String> reasons = new ArrayList<>();
        String overall = deriveOverall(
                def, false, implemented, sourceAvailable, manual,
                policyTestReady, runtimeReady, executionState, reasons);
        if (catalogueProductionReadyClaim) {
            reasons.add("Legacy catalogue production_ready claim present — not certification truth");
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("implemented", implemented);
        facts.put("sourceAvailable", sourceAvailable);
        facts.put("policyTestReady", policyTestReady);
        facts.put("runtimeReady", runtimeReady);
        facts.put("productionReady", productionReady);
        facts.put("legacyCatalogueProductionReadyClaim", catalogueProductionReadyClaim);
        facts.put("workflowAvailable", workflowAvailable);
        facts.put("providerBound", providerBound);
        facts.put("mappingAvailable", mappingAvailable);
        facts.put("calculatorAvailable", calculatorAvailable);
        facts.put("provenanceAvailable", provenanceAvailable);

        Map<String, Object> consumers = deriveConsumers(def, gate3, workflow);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("canonicalParameterId", def.id());
        out.put("sourceFamily", def.evaluatedFrom());
        out.put("sourceType", sourceType);
        out.put("overallReadiness", overall);
        out.put("overallReadinessReasons", reasons);
        out.put("facts", facts);
        out.put("implemented", implemented);
        out.put("sourceAvailable", sourceAvailable);
        out.put("policyTestReady", policyTestReady);
        out.put("runtimeReady", runtimeReady);
        out.put("productionReady", productionReady);
        out.put("legacyCatalogueProductionReadyClaim", catalogueProductionReadyClaim);
        out.put("productionCertification", gate3.get("productionCertification"));
        out.put("workflowAvailable", workflowAvailable);
        out.put("providerBound", providerBound);
        out.put("mappingAvailable", mappingAvailable);
        out.put("calculatorAvailable", calculatorAvailable);
        out.put("provenanceAvailable", provenanceAvailable);
        out.put("provider", provider);
        out.put("workflow", workflow);
        Map<String, Object> gate3View = new LinkedHashMap<>();
        gate3View.put("executionState", gate3.get("executionState"));
        gate3View.put("policyTestReady", policyTestReady);
        gate3View.put("runtimeReady", runtimeReady);
        gate3View.put("productionReady", gate3.get("productionReady"));
        gate3View.put("blockers", gate3.getOrDefault("blockers", List.of()));
        gate3View.put("provenanceModel", gate3.get("provenanceModel"));
        out.put("gate3", gate3View);
        out.put("consumers", consumers);
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("providerFieldPath", path == null ? "" : path);
        mapping.put("implementationBinding", binding == null ? "" : binding);
        mapping.put("parameterKind", def.type() == null ? "" : def.type());
        mapping.put("schema", cap.schema() == null ? "" : cap.schema());
        out.put("mapping", mapping);
        out.put("dp1", true);
        out.put("readModelOnly", true);
        return out;
    }

    static String deriveOverall(
            CanonicalParameterDefinition def,
            boolean catalogueProductionReady,
            boolean implemented,
            boolean sourceAvailable,
            boolean manual,
            boolean policyTestReady,
            boolean runtimeReady,
            String executionState,
            List<String> reasons) {

        // Contradictions → UNKNOWN (do not silently promote)
        if (catalogueProductionReady && !implemented && !manual) {
            reasons.add("productionReady=true but implemented=false");
            return OVERALL_READINESS_UNKNOWN;
        }
        if (catalogueProductionReady && !sourceAvailable && !manual) {
            reasons.add("productionReady=true but sourceAvailable=false");
            return OVERALL_READINESS_UNKNOWN;
        }
        // policyTestReady must not imply productionReady — already handled by requiring catalogue flag
        if (catalogueProductionReady && (implemented || manual)) {
            reasons.add(manual
                    ? "MANUAL / application-authorised — provider_code not required"
                    : "GACAT productionReady + implemented; Gate3=" + executionState);
            return OVERALL_PRODUCTION_READY;
        }
        if (ParameterExecutabilitySupport.RUNTIME_READY_NONPROD.equals(executionState)
                || (implemented && runtimeReady && !catalogueProductionReady)) {
            reasons.add("Implemented runtime path without production certification; Gate3=" + executionState);
            return OVERALL_RUNTIME_READY_NONPROD;
        }
        if (ParameterExecutabilitySupport.POLICY_TEST_READY.equals(executionState)
                || (implemented && policyTestReady && !runtimeReady && !catalogueProductionReady)) {
            reasons.add("Policy Test / studio path only; Gate3=" + executionState);
            return OVERALL_POLICY_TEST_ONLY;
        }
        if (!implemented
                || ParameterExecutabilitySupport.DERIVATION_DEFINED_NOT_IMPLEMENTED.equals(executionState)
                || ParameterExecutabilitySupport.SOURCE_AVAILABLE_NOT_BOUND.equals(executionState)
                || ParameterExecutabilitySupport.DATA_SOURCE_UNAVAILABLE.equals(executionState)) {
            reasons.add("Catalogue presence without executable production path; Gate3=" + executionState);
            return OVERALL_CATALOGUE_ONLY;
        }
        reasons.add("Insufficient or ambiguous Gate3/capability combination; Gate3=" + executionState);
        return OVERALL_READINESS_UNKNOWN;
    }

    public static String deriveSourceType(
            CanonicalParameterDefinition def,
            Map<String, Object> workflow,
            boolean mappingAvailable) {
        String type = def.type() == null ? "" : def.type().trim().toUpperCase(Locale.ROOT);
        String family = def.evaluatedFrom() == null ? "" : def.evaluatedFrom().trim().toLowerCase(Locale.ROOT);

        if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(type)
                || family.contains("application")
                || family.contains("manual")
                || family.contains("program")
                || family.contains("product")) {
            if (family.contains("application") || family.contains("product") || family.contains("program")) {
                return SOURCE_APPLICATION_INPUT;
            }
            if (family.contains("manual") || CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(type)) {
                return SOURCE_MANUAL;
            }
            return SOURCE_APPLICATION_INPUT;
        }
        if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(type)
                && (family.contains("computed") || family.contains("derived") || family.contains("ratio"))) {
            return SOURCE_DERIVED;
        }
        // Provider-sourced families first (workflow may acquire them via BUREAU_PULL etc.)
        if (family.contains("bureau") || family.contains("bank") || family.contains("gst")
                || family.contains("account aggregator") || family.contains("financial")
                || family.contains("itr")
                || Boolean.TRUE.equals(workflow.get("integrationAvailable"))
                || mappingAvailable) {
            if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(type)
                    && !family.contains("bureau") && !family.contains("bank") && !family.contains("gst")
                    && !family.contains("financial") && !family.contains("itr")) {
                return SOURCE_DERIVED;
            }
            return SOURCE_PROVIDER;
        }
        if (family.contains("kyc")
                || Boolean.TRUE.equals(workflow.get("workflowAvailable"))) {
            return SOURCE_WORKFLOW;
        }
        if (family.contains("computed") || family.contains("internal")) {
            return SOURCE_INTERNAL_SYSTEM;
        }
        if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(type)) {
            return SOURCE_DERIVED;
        }
        return SOURCE_UNKNOWN;
    }

    public static Map<String, Object> deriveProviderDisplay(
            CanonicalParameterDefinition.Capability cap,
            Map<String, Object> workflow,
            String sourceType) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (SOURCE_MANUAL.equals(sourceType)
                || SOURCE_APPLICATION_INPUT.equals(sourceType)
                || SOURCE_WORKFLOW.equals(sourceType)
                || SOURCE_INTERNAL_SYSTEM.equals(sourceType)) {
            p.put("status", PROVIDER_NOT_APPLICABLE);
            p.put("label", null);
            p.put("evidence", "Source type " + sourceType + " does not require an external provider_code");
            return p;
        }
        p.put("registeredProviderCode", null);

        List<String> evidence = new ArrayList<>();
        String inferred = null;
        String path = cap.providerFieldPath() == null ? "" : cap.providerFieldPath();
        String schema = cap.schema() == null ? "" : cap.schema();

        @SuppressWarnings("unchecked")
        List<String> integrations = workflow.get("integrations") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of();
        if (integrations.contains("ACCOUNT_AGGREGATOR")) {
            inferred = "Account Aggregator integration";
            evidence.add("WorkflowParameterProvidesCatalog.INTEGRATION_PROVIDES:ACCOUNT_AGGREGATOR");
        } else if (integrations.contains("BANK_STATEMENT_DOCUMENT")) {
            inferred = "Bank statement / BSA document path";
            evidence.add("WorkflowParameterProvidesCatalog.INTEGRATION_PROVIDES:BANK_STATEMENT_DOCUMENT");
        }

        // Structured Equifax XML path in capability (not name-guessing)
        if (inferred == null && (path.contains("InquiryResponse") || path.contains("History48Months")
                || path.contains("sch:Score") || path.contains("DaysPastDue"))) {
            inferred = "Equifax bureau XML path";
            evidence.add("capability.providerFieldPath contains Equifax response structure");
        }
        if (inferred == null && schema.toUpperCase(Locale.ROOT).contains("GST")) {
            inferred = "GST analysis integration";
            evidence.add("capability.schema=" + schema);
        }
        if (inferred == null && (schema.toUpperCase(Locale.ROOT).contains("BUREAU")
                || schema.toUpperCase(Locale.ROOT).contains("EQUIFAX"))) {
            inferred = "Bureau provider pipeline";
            evidence.add("capability.schema=" + schema);
        }

        if (inferred != null) {
            p.put("status", PROVIDER_INFERRED);
            p.put("label", inferred);
            p.put("evidence", evidence);
        } else {
            p.put("status", PROVIDER_UNKNOWN);
            p.put("label", null);
            p.put("evidence", List.of(
                    "provider_code not registered on ci_gacat_canonical_parameter_source_binding",
                    path.isBlank() ? "no providerFieldPath" : "providerFieldPath present but provider identity not registered"));
        }
        return p;
    }

    public static Map<String, Object> deriveConsumers(
            CanonicalParameterDefinition def,
            Map<String, Object> gate3,
            Map<String, Object> workflow) {
        Map<String, Object> c = new LinkedHashMap<>();
        List<String> scorecard = new ArrayList<>();
        List<String> liveUw = new ArrayList<>();
        List<String> runtime = new ArrayList<>();
        List<String> workflowConsumers = new ArrayList<>();

        if (def.liveScorecardParameter() != null && !def.liveScorecardParameter().isBlank()) {
            scorecard.add(def.liveScorecardParameter());
        }
        if (def.liveRuleParameter() != null && !def.liveRuleParameter().isBlank()) {
            liveUw.add(def.liveRuleParameter());
        }
        Object aliases = gate3.get("runtimeFactAliases");
        if (aliases instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    runtime.add(String.valueOf(o));
                }
            }
        }
        if (workflow.get("productionSteps") instanceof List<?> list) {
            list.stream().map(String::valueOf).forEach(workflowConsumers::add);
        }
        if (workflow.get("studioOnlySteps") instanceof List<?> list) {
            list.stream().map(String::valueOf).forEach(workflowConsumers::add);
        }
        if (workflow.get("integrations") instanceof List<?> list) {
            list.stream().map(String::valueOf).forEach(workflowConsumers::add);
        }

        c.put("policyStudio", List.of()); // no proven Policy document index in DP-1
        c.put("scorecardLegacyKeys", scorecard);
        c.put("liveUnderwritingLegacyKeys", liveUw);
        c.put("runtimeFactAliases", runtime);
        c.put("workflowOrIntegrations", workflowConsumers.stream().distinct().toList());
        c.put("note", "Policy document consumers not indexed in DP-1; scorecard/live keys from GACAT live* fields only");
        return c;
    }

    /** Sections A–G for parameter detail (structured primary UX). */
    public static Map<String, Object> detailSections(CanonicalParameterDefinition def, Map<String, Object> projection) {
        CanonicalParameterDefinition.Capability cap = Objects.requireNonNull(
                def.capability(), "capability");
        Map<String, Object> sections = new LinkedHashMap<>();

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("canonicalId", def.id());
        definition.put("displayName", def.businessName());
        definition.put("description", def.calculationSummary());
        definition.put("datatype", cap.schema());
        definition.put("unit", def.unit());
        definition.put("period", def.period());
        definition.put("parameterKind", def.type());
        sections.put("definition", definition);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceFamily", def.evaluatedFrom());
        source.put("sourceType", projection.get("sourceType"));
        source.put("provider", projection.get("provider"));
        source.put("workflow", projection.get("workflow"));
        sections.put("source", source);

        Map<String, Object> mappingCalc = new LinkedHashMap<>();
        mappingCalc.put("rawSourcePaths", List.of(
                cap.providerFieldPath() == null || cap.providerFieldPath().isBlank()
                        ? "" : cap.providerFieldPath()));
        mappingCalc.put("mapperNormalizer", cap.transformation());
        mappingCalc.put("calculator", def.existingImplementationBinding());
        mappingCalc.put("aggregation", cap.aggregation());
        mappingCalc.put("filters", cap.filtersEligibility());
        mappingCalc.put("derivedOrRaw", def.type());
        mappingCalc.put("mapping", projection.get("mapping"));
        sections.put("mappingCalculation", mappingCalc);

        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("overallReadiness", projection.get("overallReadiness"));
        readiness.put("overallReadinessReasons", projection.get("overallReadinessReasons"));
        readiness.put("facts", projection.get("facts"));
        readiness.put("gate3", projection.get("gate3"));
        sections.put("readiness", readiness);

        Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("missingDataTreatment", cap.missingDataTreatment());
        missing.put("availability", def.availability());
        sections.put("missingData", missing);

        sections.put("consumers", projection.get("consumers"));

        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("calculationSummary", def.calculationSummary());
        provenance.put("requiredPrimitives", def.requiredPrimitives());
        provenance.put("provenanceModel", ((Map<?, ?>) projection.get("gate3")).get("provenanceModel"));
        provenance.put("aliases", def.aliases());
        sections.put("provenance", provenance);

        return sections;
    }

    /** Known catalogue drift (visibility only — no mutation). */
    public static List<Map<String, Object>> knownCatalogueDriftNotes() {
        List<Map<String, Object>> notes = new ArrayList<>();
        // DP-2B: bureau.inquiries.last_3m restored via V131; collateral.ltv reconciled into Java seed.
        notes.add(Map.of(
                "code", "META_SEED_COUNT_STALE",
                "detail", "Internal ci_gacat_catalogue_meta.seed_count may lag actual parameter row count — cosmetic meta only."));
        notes.add(Map.of(
                "code", "POLICY_SNAPSHOT_TOKENS_UNRESOLVED",
                "detail", "10 Policy session-snapshot tokens remain unbound to exact GACAT IDs (no fuzzy bind in DP-2B)."));
        return notes;
    }
}
