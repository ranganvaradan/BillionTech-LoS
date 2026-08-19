package com.los.core.service.readiness;

import com.los.core.config.IntegrationProperties;
import com.los.core.creditintelligence.policystudio.parameters.AuthoringValueTypes;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueAuthority;
import com.los.core.creditintelligence.policystudio.parameters.GacatCatalogueRepository;
import com.los.core.creditintelligence.policystudio.parameters.PolicyAuthorableParameterProjection;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.GacatSourceFamily;
import com.los.core.creditintelligence.policystudio.sourceintegration.CanonicalSourceIntegrationAuthority;
import com.los.core.creditintelligence.policystudio.sourceintegration.PlatformSourceConnectorCatalog;
import com.los.core.creditintelligence.policystudio.truth.CanonicalParameterStateService;
import com.los.core.creditintelligence.policystudio.truth.SurfaceCanonicalTruthFacade;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Administration → Data & Parameters (CanonicalParameterRegistry read model only).
 * GACAT-PERSISTENCE-1 — DB-backed catalogue via registry abstraction (Admin remains read-only).
 */
@Service
public class DataParametersAdminService {

    private final GacatCatalogueRepository catalogueRepository;
    private final IntegrationProperties integrationProperties;

    /** Unit-test convenience when JDBC catalogue is unavailable. */
    public DataParametersAdminService() {
        this(null, null);
    }

    public DataParametersAdminService(GacatCatalogueRepository catalogueRepository) {
        this(catalogueRepository, null);
    }

    /** Production: Spring must inject the JDBC catalogue repository (do not use the no-arg ctor). */
    @Autowired
    public DataParametersAdminService(
            GacatCatalogueRepository catalogueRepository,
            @Autowired(required = false) IntegrationProperties integrationProperties) {
        this.catalogueRepository = catalogueRepository;
        this.integrationProperties = integrationProperties;
    }

    @PostConstruct
    void installSourceIntegrationLenderProbe() {
        CanonicalSourceIntegrationAuthority.installLenderProbe((key, familyLabel) -> {
            if (key == PlatformSourceConnectorCatalog.SourceKey.APPLICATION
                    || key == PlatformSourceConnectorCatalog.SourceKey.INTERNAL) {
                return true;
            }
            if (key == PlatformSourceConnectorCatalog.SourceKey.BUREAU_RETAIL) {
                if (integrationProperties == null) return false;
                var eq = integrationProperties.getEquifax();
                return eq != null && (eq.isConfigured() || eq.isSimulation());
            }
            if (key == PlatformSourceConnectorCatalog.SourceKey.BUREAU_COMMERCIAL
                    || key == PlatformSourceConnectorCatalog.SourceKey.FINANCIAL_ITR
                    || key == PlatformSourceConnectorCatalog.SourceKey.UNKNOWN) {
                return false;
            }
            // Bank / AA / GST / KYC: platform connector present; no separate commercial subscription table.
            // Lender configuration follows platform integration until a durable subscription store exists.
            return PlatformSourceConnectorCatalog.entry(key).platformIntegrated();
        });
    }

    @PreDestroy
    void clearSourceIntegrationLenderProbe() {
        CanonicalSourceIntegrationAuthority.clearLenderProbe();
    }

    private DataParametersCapabilitySemantics.LenderSourceSubscriptionProbe subscriptionProbe() {
        return family -> {
            String f = family == null ? "" : family.toLowerCase(Locale.ROOT);
            if (integrationProperties == null) {
                return DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED;
            }
            if (f.contains("bureau") && !f.contains("commercial")) {
                var eq = integrationProperties.getEquifax();
                if (eq != null && (eq.isConfigured() || eq.isSimulation())) {
                    return DataParametersCapabilitySemantics.LENDER_SUBSCRIBED;
                }
                return DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED;
            }
            // Other provider families: no dedicated subscription table yet
            return DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED;
        };
    }

    private boolean dbCataloguePresent() {
        return catalogueRepository != null && catalogueRepository.tablesPresent();
    }

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    public Map<String, Object> overview() {
        CanonicalParameterRegistry reg = registry();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Data & Parameters");
        out.put("subtitle", "What the institution can know — CanonicalParameterRegistry (DB-backed).");
        out.put("readModelOnly", true);
        out.put("adminWriteEnabled", false);
        out.put("allowCanonicalAuthority", false);
        out.put("inventoryVersion", reg.inventoryVersion());
        out.put("catalogueAuthority", reg.authority());
        out.put("javaSeedIsRuntimeAuthority",
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY.equals(reg.authority()));
        out.put("sources", reg.sources());
        out.put("catalogue", reg.catalogueView());
        out.put("bySourceSummary", bySourceSummary(reg));
        out.put("sourceCapabilitySummary", sourceCapabilitySummary(reg));
        out.put("gapsManual", gapsManual(reg));
        out.put("workflowProvides", WorkflowParameterProvidesCatalog.catalogueView());
        out.put("totals", totals(reg));
        out.put("dp1", true);
        out.put("capabilitySemantics", true);
        out.put("capabilityModel", "DATA-PARAMETERS-CAPABILITY-SEMANTICS-1");
        out.put("applicationDataStateExcluded", true);
        out.put("readinessProjection", "GacatParameterReadinessProjection");
        out.put("knownCatalogueDrift", GacatParameterReadinessProjection.knownCatalogueDriftNotes());
        out.put("sourceIntegrationStatuses", List.of(
                DataParametersCapabilitySemantics.SOURCE_PLATFORM_PRODUCTION_READY,
                DataParametersCapabilitySemantics.SOURCE_PLATFORM_NOT_INTEGRATED,
                DataParametersCapabilitySemantics.SOURCE_PLATFORM_NOT_APPLICABLE));
        out.put("parameterSupportStatuses", List.of(
                DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_RAW,
                DataParametersCapabilitySemantics.SUPPORT_SUPPORTED_DERIVED,
                DataParametersCapabilitySemantics.SUPPORT_PROVIDER_DOES_NOT_SUPPORT,
                DataParametersCapabilitySemantics.SUPPORT_CALCULATION_NOT_IMPLEMENTED,
                DataParametersCapabilitySemantics.SUPPORT_SOURCE_NOT_INTEGRATED,
                DataParametersCapabilitySemantics.SUPPORT_NOT_APPLICABLE));
        out.put("lenderSubscriptionStatuses", List.of(
                DataParametersCapabilitySemantics.LENDER_SUBSCRIBED,
                DataParametersCapabilitySemantics.LENDER_NOT_YET_SUBSCRIBED,
                DataParametersCapabilitySemantics.LENDER_SUBSCRIPTION_SETUP_PENDING,
                DataParametersCapabilitySemantics.LENDER_NOT_APPLICABLE));
        out.put("sourceTypes", List.of(
                GacatParameterReadinessProjection.SOURCE_PROVIDER,
                GacatParameterReadinessProjection.SOURCE_APPLICATION_INPUT,
                GacatParameterReadinessProjection.SOURCE_WORKFLOW,
                GacatParameterReadinessProjection.SOURCE_INTERNAL_SYSTEM,
                GacatParameterReadinessProjection.SOURCE_MANUAL,
                GacatParameterReadinessProjection.SOURCE_DERIVED,
                GacatParameterReadinessProjection.SOURCE_UNKNOWN));
        out.put("overallReadinessStates", List.of(
                GacatParameterReadinessProjection.OVERALL_PRODUCTION_READY,
                GacatParameterReadinessProjection.OVERALL_RUNTIME_READY_NONPROD,
                GacatParameterReadinessProjection.OVERALL_POLICY_TEST_ONLY,
                GacatParameterReadinessProjection.OVERALL_CATALOGUE_ONLY,
                GacatParameterReadinessProjection.OVERALL_READINESS_UNKNOWN));
        out.put("subtitle",
                "See what information the LOS can collect, calculate or obtain from integrated sources, "
                        + "and whether it is ready for use in lending policies.");
        if (dbCataloguePresent()) {
            out.put("integrity", catalogueRepository.integrityReport());
        }
        out.put("resolutionOrder", List.of(
                "Application → Product dimensions",
                "Workflow",
                "Live Rule Set",
                "Live Scorecard",
                "Studio Policy publication metadata only (not production authority)"));
        return out;
    }

    /**
     * Data & Parameters surface filtered to the canonical Policy Studio authorable universe.
     * This must use the same governed authority as Policy Studio Add Rule / Change Parameter.
     */
    public Map<String, Object> overview(boolean authorableOnly) {
        if (!authorableOnly) return overview();
        Map<String, Object> out = overview();
        CanonicalParameterRegistry reg = registry();
        List<CanonicalParameterDefinition> authorable = PolicyAuthorableParameterProjection.authorableOf(reg);

        int raw = 0, derived = 0, manual = 0;
        for (CanonicalParameterDefinition d : authorable) {
            if (CanonicalParameterDefinition.RAW.equals(d.type())) raw++;
            else if (CanonicalParameterDefinition.MANUAL.equals(d.type())) manual++;
            else derived++;
        }

        if (out.get("totals") instanceof Map<?, ?> tm) {
            @SuppressWarnings("unchecked")
            Map<String, Object> totals = (Map<String, Object>) tm;
            totals.put("registryCount", authorable.size());
            totals.put("rawCount", raw);
            totals.put("derivedCount", derived);
            totals.put("manualCount", manual);
        }
        return out;
    }

    public Map<String, Object> browseBySource(String source) {
        CanonicalParameterRegistry reg = registry();
        Map<String, Object> browse = reg.browseBySource(source);
        browse.put("raw", enrichList(reg, browse.get("raw")));
        browse.put("derived", enrichList(reg, browse.get("derived")));
        browse.put("manual", enrichList(reg, browse.get("manual")));
        browse.put("allowCanonicalAuthority", false);
        browse.put("readModelOnly", true);
        return browse;
    }

    public Map<String, Object> browseBySource(String source, boolean authorableOnly) {
        if (!authorableOnly) return browseBySource(source);
        Map<String, Object> browse = browseBySource(source);

        List<Map<String, Object>> raw = filterAuthorableRows(asListOfMap(browse.get("raw")));
        List<Map<String, Object>> derived = filterAuthorableRows(asListOfMap(browse.get("derived")));
        List<Map<String, Object>> manual = filterAuthorableRows(asListOfMap(browse.get("manual")));

        browse.put("raw", raw);
        browse.put("derived", derived);
        browse.put("manual", manual);
        browse.put("rawCount", raw.size());
        browse.put("derivedCount", derived.size());
        browse.put("manualCount", manual.size());
        browse.put("count", raw.size() + derived.size() + manual.size());
        return browse;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> enrichList(CanonicalParameterRegistry reg, Object listObj) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(listObj instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = String.valueOf(m.get("id"));
            reg.findById(id).ifPresentOrElse(def -> out.add(enrich(def)), () -> out.add((Map<String, Object>) m));
        }
        return out;
    }

    private static List<Map<String, Object>> asListOfMap(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> row = new LinkedHashMap<>((Map<String, Object>) m);
            out.add(row);
        }
        return out;
    }

    private static List<Map<String, Object>> filterAuthorableRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        return rows.stream()
                .filter(r -> PolicyAuthorableParameterProjection.isAuthorable(String.valueOf(r.get("id"))))
                .toList();
    }

    public Map<String, Object> search(String q) {
        CanonicalParameterRegistry reg = registry();
        Map<String, Object> search = reg.search(q);
        search.put("allowCanonicalAuthority", false);
        search.put("results", enrichList(reg, search.get("results")));
        search.put("dp1", true);
        return search;
    }

    public Map<String, Object> search(String q, boolean authorableOnly) {
        if (!authorableOnly) return search(q);
        Map<String, Object> search = search(q);
        List<Map<String, Object>> results = filterAuthorableRows(asListOfMap(search.get("results")));
        search.put("results", results);
        search.put("count", results.size());
        return search;
    }

    public Map<String, Object> parameterDetail(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        out.put("catalogueAuthority", registry().authority());
        out.put("adminWriteEnabled", false);
        out.put("dp1", true);
        registry().findById(id).ifPresentOrElse(def -> {
            Map<String, Object> enriched = enrich(def);
            out.put("parameter", enriched);
            out.put("sections", enriched.get("sections"));
            out.put("readiness", enriched.get("readiness"));
            out.put("capability", enriched.get("capability"));
            out.put("found", true);
            out.put("capabilitySemantics", true);
            out.put("applicationDataStateExcluded", true);
        }, () -> {
            out.put("found", false);
            out.put("message", "Unknown parameter id");
        });
        return out;
    }

    private List<Map<String, Object>> bySourceSummary(CanonicalParameterRegistry reg) {
        List<Map<String, Object>> rows = new ArrayList<>();
        DataParametersCapabilitySemantics.LenderSourceSubscriptionProbe probe = subscriptionProbe();
        for (String source : reg.sources()) {
            Map<String, Object> browse = reg.browseBySource(source);
            int count = browse.get("count") instanceof Number n ? n.intValue() : 0;
            if (count == 0 && ("Customer / Borrower".equals(source) || "Manual Input".equals(source))) {
                // still show empty families for discovery honesty
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("rawCount", browse.get("rawCount"));
            row.put("derivedCount", browse.get("derivedCount"));
            row.put("manualCount", browse.get("manualCount"));
            row.put("liveCount", browse.get("liveCount"));
            row.put("count", browse.get("count"));
            List<CanonicalParameterDefinition> familyParams = reg.all().stream()
                    .filter(d -> GacatSourceFamily.sameFamily(source, d.evaluatedFrom()))
                    .toList();
            Map<String, Object> capSummary = DataParametersCapabilitySemantics.sourceFamilySummary(
                    source, familyParams, probe);
            row.put("platformIntegration", capSummary.get("platformIntegration"));
            row.put("platformLabel", capSummary.get("platformLabel"));
            row.put("providerLabel", capSummary.get("providerLabel"));
            row.put("yourOrganisation", capSummary.get("yourOrganisation"));
            row.put("yourOrganisationLabel", capSummary.get("yourOrganisationLabel"));
            row.put("parameterSupportCounts", capSummary.get("parameterSupportCounts"));
            rows.add(row);
        }
        return rows;
    }

    /** Source-level capability glance for lender UX (section I). */
    private List<Map<String, Object>> sourceCapabilitySummary(CanonicalParameterRegistry reg) {
        DataParametersCapabilitySemantics.LenderSourceSubscriptionProbe probe = subscriptionProbe();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String source : reg.sources()) {
            List<CanonicalParameterDefinition> familyParams = reg.all().stream()
                    .filter(d -> GacatSourceFamily.sameFamily(source, d.evaluatedFrom()))
                    .toList();
            if (familyParams.isEmpty()) continue;
            rows.add(DataParametersCapabilitySemantics.sourceFamilySummary(source, familyParams, probe));
        }
        return rows;
    }

    private Map<String, Object> totals(CanonicalParameterRegistry reg) {
        int raw = 0, derived = 0, manual = 0, live = 0, implemented = 0;
        for (CanonicalParameterDefinition d : reg.all()) {
            if (CanonicalParameterDefinition.RAW.equals(d.type())) raw++;
            else if (CanonicalParameterDefinition.MANUAL.equals(d.type())) manual++;
            else derived++;
            if (d.capability() != null) {
                if (d.capability().productionReady()) live++;
                if (d.capability().implemented()) implemented++;
            }
        }
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("registryCount", reg.all().size());
        t.put("rawCount", raw);
        t.put("derivedCount", derived);
        t.put("manualCount", manual);
        t.put("implementedCount", implemented);
        t.put("productionReadyCount", live);
        t.put("beforeExpansionBaseline", 28);
        return t;
    }

    private Map<String, Object> gapsManual(CanonicalParameterRegistry reg) {
        List<Map<String, Object>> manual = new ArrayList<>();
        List<Map<String, Object>> unresolvedBinding = new ArrayList<>();
        List<Map<String, Object>> definedNotImplemented = new ArrayList<>();
        for (CanonicalParameterDefinition d : reg.all()) {
            if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(d.type())) {
                manual.add(enrich(d));
            }
            if (d.existingImplementationBinding() == null || d.existingImplementationBinding().isBlank()
                    || "UNRESOLVED".equalsIgnoreCase(String.valueOf(d.availability()))
                    || "DEFINED_NOT_IMPLEMENTED".equals(d.existingImplementationBinding())) {
                if ("DEFINED_NOT_IMPLEMENTED".equals(d.existingImplementationBinding())) {
                    definedNotImplemented.add(enrich(d));
                } else if (d.existingImplementationBinding() == null || d.existingImplementationBinding().isBlank()) {
                    unresolvedBinding.add(enrich(d));
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("manualParameters", manual);
        out.put("manualCount", manual.size());
        out.put("availabilityGaps", unresolvedBinding);
        out.put("availabilityGapCount", unresolvedBinding.size());
        out.put("definedNotImplemented", definedNotImplemented);
        out.put("definedNotImplementedCount", definedNotImplemented.size());
        return out;
    }

    private Map<String, Object> enrich(CanonicalParameterDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>(d.toBusinessView());
        m.put("source", d.evaluatedFrom());
        m.put("lineage", lineage(d));
        // FINAL-CANONICAL-PARAMETER-STATE — D&P is a pure consumer of CanonicalParameterState
        Map<String, Object> truth = CanonicalParameterStateService.state(d.id());
        Map<String, Object> surface = SurfaceCanonicalTruthFacade.forSurface(
                SurfaceCanonicalTruthFacade.DATA_PARAMETERS, d.id());
        m.put("canonicalTruth", truth);
        m.put("canonicalParameterState", truth);
        m.put("truthSurface", surface);
        m.put("parameterStateAuthority", CanonicalParameterStateService.AUTHORITY);
        m.put("primaryStatus", truth.get("primaryStatus"));
        m.put("primaryStatusLabel", truth.get("primaryStatusLabel"));
        m.put("nextAction", truth.get("nextAction"));
        m.put("calculationExplanation", truth.get("calculationExplanation"));
        m.put("parameterClassLabel", truth.get("parameterClassLabel"));
        m.put("executionLabel", truth.get("executionLabel"));
        m.put("certificationLabel", truth.get("certificationLabel"));

        Map<String, Object> readiness = GacatParameterReadinessProjection.project(d);
        m.put("readiness", readiness);
        m.put("sourceType", readiness.get("sourceType"));
        m.put("sourceFamily", readiness.get("sourceFamily"));
        // Primary lender status from state — legacy overall under Advanced only
        m.put("overallReadiness", truth.get("primaryStatus"));
        m.put("overallReadinessReasons", List.of(
                "primaryStatus from CanonicalParameterStateService",
                String.valueOf(truth.get("primaryStatusLabel"))));
        m.put("provider", readiness.get("provider"));
        m.put("workflow", readiness.get("workflow"));
        m.put("consumers", readiness.get("consumers"));
        m.put("mapping", readiness.get("mapping"));
        // Keep Gate3 factual dims from projection (honest; do not invent)
        m.put("workflowAvailable", readiness.get("workflowAvailable"));
        m.put("providerBound", readiness.get("providerBound"));
        m.put("mappingAvailable", readiness.get("mappingAvailable"));
        m.put("calculatorAvailable", readiness.get("calculatorAvailable"));
        m.put("provenanceAvailable", readiness.get("provenanceAvailable"));

        Map<String, Object> capability = DataParametersCapabilitySemantics.project(
                d, readiness, subscriptionProbe());
        m.put("capability", capability);
        m.put("platformIntegration", capability.get("platformIntegration"));
        m.put("parameterSupport", capability.get("parameterSupport"));
        m.put("yourOrganisation", capability.get("yourOrganisation"));
        m.put("policyDesign", capability.get("policyDesign"));
        // Live use ONLY from certification ledger via truth projection
        @SuppressWarnings("unchecked")
        Map<String, Object> liveFromTruth = truth.get("liveUseDisplay") instanceof Map<?, ?>
                ? (Map<String, Object>) truth.get("liveUseDisplay")
                : Map.of("available", false, "status", "UNCERTIFIED", "label", "Not approved for live use");
        m.put("liveUse", liveFromTruth);
        m.put("availableForProductionPolicyUse", liveFromTruth);
        m.put("canBillionTechSupport", capability.get("canBillionTechSupport"));
        m.put("canBillionTechSupportLabel", capability.get("canBillionTechSupportLabel"));
        m.put("applicationDataStateExcluded", true);
        m.put("capabilitySemantics", true);
        m.put("catalogueImplementedIsNotReadiness", true);
        m.put("catalogueProductionReadyIsNotLiveStatus", true);
        // Ensure section liveUse mirrors certification truth (not catalogue)
        capability.put("liveUse", liveFromTruth);
        capability.put("availableForProductionPolicyUse", liveFromTruth);
        capability.put("productionReady", false);
        capability.put("productionCertified",
                "CERTIFIED".equals(String.valueOf(liveFromTruth.get("status"))));

        Map<String, Object> legacySections = GacatParameterReadinessProjection.detailSections(d, readiness);
        Map<String, Object> sections = lenderFacingSections(d, readiness, capability, legacySections, truth);
        m.put("sections", sections);
        Map<String, Object> version = dbCataloguePresent()
                ? catalogueRepository.parameterVersionView(d.id()) : Map.of();
        if (!version.isEmpty()) {
            m.put("definitionVersion", version.get("definitionVersion"));
            m.put("effectiveFrom", version.get("effectiveFrom"));
            m.put("version", version);
        } else {
            m.put("definitionVersion", 1);
        }
        List<Map<String, String>> allowed = List.of();
        if (dbCataloguePresent()) {
            allowed = catalogueRepository.allowedValues(d.id());
        }
        if (allowed.isEmpty()) {
            // Authoring enums remain available even if DB overlay missed a row (still registry-backed).
            allowed = AuthoringValueTypes.allowedValues(d.id());
        }
        if (!allowed.isEmpty()) {
            m.put("allowedValues", allowed);
        }
        String valueControl = AuthoringValueTypes.valueControl(d);
        if (!allowed.isEmpty()) {
            valueControl = AuthoringValueTypes.CONTROL_ENUM;
        }
        m.put("authoringValueType", valueControl);
        m.put("valueType", valueControl);
        Map<String, Object> advanced = new LinkedHashMap<>();
        advanced.put("existingImplementationBinding", d.existingImplementationBinding());
        advanced.put("liveRuleParameter", d.liveRuleParameter());
        advanced.put("liveScorecardParameter", d.liveScorecardParameter());
        advanced.put("id", d.id());
        advanced.put("canonicalId", d.id());
        advanced.put("definitionVersion", m.get("definitionVersion"));
        advanced.put("authoringValueType", valueControl);
        if (d.capability() != null) {
            advanced.put("providerFieldPath", d.capability().providerFieldPath());
            advanced.put("schema", d.capability().schema());
            advanced.put("cardinality", d.capability().cardinality());
            advanced.put("capability", d.capability().toMap());
        }
        advanced.put("gate3", readiness.get("gate3"));
        advanced.put("readinessProjection", readiness);
        advanced.put("legacyOverallReadiness", readiness.get("overallReadiness"));
        advanced.put("legacyOverallReadinessReasons", readiness.get("overallReadinessReasons"));
        advanced.put("legacyDetailSections", legacySections);
        advanced.put("canonicalTruth", truth);
        advanced.put("policyTestReady", readiness.get("policyTestReady"));
        advanced.put("runtimeReady", readiness.get("runtimeReady"));
        advanced.put("productionReady", false); // never catalogue claim as live authority
        advanced.put("catalogueProductionReadyLegacyClaim", readiness.get("legacyCatalogueProductionReadyClaim"));
        advanced.put("certification", truth.get("certification"));
        advanced.put("execution", truth.get("execution"));
        advanced.put("providerBound", readiness.get("providerBound"));
        advanced.put("mappingAvailable", readiness.get("mappingAvailable"));
        advanced.put("calculatorAvailable", readiness.get("calculatorAvailable"));
        advanced.put("workflowAvailable", readiness.get("workflowAvailable"));
        advanced.put("provenanceAvailable", readiness.get("provenanceAvailable"));
        advanced.put("note", "Single Advanced block — engineering evidence; primary status is primaryStatusLabel");
        m.put("advanced", advanced);
        m.put("catalogueAuthority", registry().authority());
        m.put("dp1", true);
        return m;
    }

    /**
     * Lender-facing sections: capability semantics first; Gate3/PT/RT/providerBound under Advanced.
     */
    private Map<String, Object> lenderFacingSections(
            CanonicalParameterDefinition d,
            Map<String, Object> readiness,
            Map<String, Object> capability,
            Map<String, Object> legacySections,
            Map<String, Object> truth) {
        Map<String, Object> sections = new LinkedHashMap<>();

        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("canonicalId", d.id());
        definition.put("displayName", d.businessName());
        definition.put("description", truth.get("calculationExplanation") != null
                ? truth.get("calculationExplanation")
                : d.calculationSummary());
        if (d.capability() != null) {
            definition.put("datatype", d.capability().schema());
        }
        definition.put("unit", d.unit());
        definition.put("period", d.period());
        definition.put("parameterKind", d.type());
        definition.put("parameterClassLabel", truth.get("parameterClassLabel"));
        sections.put("definition", definition);

        @SuppressWarnings("unchecked")
        Map<String, Object> platform = (Map<String, Object>) capability.get("platformIntegration");
        @SuppressWarnings("unchecked")
        Map<String, Object> support = (Map<String, Object>) capability.get("parameterSupport");
        @SuppressWarnings("unchecked")
        Map<String, Object> org = (Map<String, Object>) capability.get("yourOrganisation");
        @SuppressWarnings("unchecked")
        Map<String, Object> design = (Map<String, Object>) capability.get("policyDesign");
        @SuppressWarnings("unchecked")
        Map<String, Object> live = (Map<String, Object>) capability.get("liveUse");

        Map<String, Object> lenderCapability = new LinkedHashMap<>();
        lenderCapability.put("primaryStatus", truth.get("primaryStatus"));
        lenderCapability.put("primaryStatusLabel", truth.get("primaryStatusLabel"));
        lenderCapability.put("nextAction", truth.get("nextAction"));
        lenderCapability.put("executionLabel", truth.get("executionLabel"));
        lenderCapability.put("certificationLabel", truth.get("certificationLabel"));
        lenderCapability.put("calculationExplanation", truth.get("calculationExplanation"));
        lenderCapability.put("canBillionTechSupport", capability.get("canBillionTechSupportLabel"));
        lenderCapability.put("source", d.evaluatedFrom());
        lenderCapability.put("sourceType", readiness.get("sourceType"));
        lenderCapability.put("platformIntegration", platform == null ? null : platform.get("label"));
        lenderCapability.put("platformIntegrationStatus", platform == null ? null : platform.get("status"));
        lenderCapability.put("providerLabel", platform == null ? null : platform.get("providerLabel"));
        lenderCapability.put("parameterSupport", support == null ? null : support.get("businessLabel"));
        lenderCapability.put("parameterSupportStatus", support == null ? null : support.get("status"));
        lenderCapability.put("how", truth.get("calculationExplanation") != null
                ? truth.get("calculationExplanation")
                : (support == null ? null : support.get("businessHow")));
        lenderCapability.put("yourOrganisation", org == null ? null : org.get("label"));
        lenderCapability.put("yourOrganisationStatus", org == null ? null : org.get("status"));
        lenderCapability.put("policyDesign", design == null ? null : design.get("label"));
        lenderCapability.put("policyDesignAvailable", design == null ? null : design.get("available"));
        lenderCapability.put("policyDesignReason", design == null ? null : design.get("reason"));
        lenderCapability.put("liveUse", live == null ? null : live.get("label"));
        lenderCapability.put("liveUseStatus", live == null ? null : live.get("status"));
        lenderCapability.put("liveUseReason", live == null ? null : live.get("reason"));
        lenderCapability.put("applicationDataStateExcluded", true);
        sections.put("lenderCapability", lenderCapability);

        // Preserve legacy section keys for older clients / diagnostics
        sections.put("source", legacySections.get("source"));
        sections.put("mappingCalculation", legacySections.get("mappingCalculation"));
        sections.put("missingData", legacySections.get("missingData"));
        sections.put("consumers", legacySections.get("consumers"));
        sections.put("provenance", legacySections.get("provenance"));

        Map<String, Object> engineeringReadiness = new LinkedHashMap<>();
        engineeringReadiness.put("note", "Moved under Advanced — not primary lender status");
        engineeringReadiness.put("overallReadiness", readiness.get("overallReadiness"));
        engineeringReadiness.put("overallReadinessReasons", readiness.get("overallReadinessReasons"));
        engineeringReadiness.put("facts", readiness.get("facts"));
        engineeringReadiness.put("gate3", readiness.get("gate3"));
        sections.put("engineeringReadinessAdvanced", engineeringReadiness);
        return sections;
    }

    private Map<String, Object> lineage(CanonicalParameterDefinition d) {
        Map<String, Object> lin = new LinkedHashMap<>();
        lin.put("type", d.type());
        String how = d.calculationSummary();
        Optional<String> authoredHow =
                com.los.core.creditintelligence.policystudio.parameters.derived
                        .AuthoredDerivedCalculationSupport.latestExecutableHow(d.id());
        if (authoredHow.isPresent()) {
            how = authoredHow.get();
        }
        lin.put("howCalculated", how);
        lin.put("calculationSummary", how);
        List<String> prims = d.requiredPrimitives() == null ? List.of() : d.requiredPrimitives();
        if (dbCataloguePresent()) {
            List<Map<String, Object>> persisted = catalogueRepository.lineageRows(d.id());
            if (!persisted.isEmpty()) {
                lin.put("persistedInputs", persisted);
                prims = persisted.stream().map(r -> String.valueOf(r.get("input"))).toList();
            }
        }
        Optional<List<String>> authoredDeps =
                com.los.core.creditintelligence.policystudio.parameters.derived
                        .AuthoredDerivedCalculationSupport.latestExecutableDependencies(d.id());
        List<String> displayInputs = prims;
        if (authoredDeps.isPresent()) {
            displayInputs = com.los.core.creditintelligence.policystudio.parameters.derived
                    .AuthoredDerivedCalculationSupport.humanizeInputs(
                            authoredDeps.get(),
                            Map.of("matchField", "dpd", "dateField", "month"));
        }
        lin.put("requiredPrimitives", prims);
        lin.put("rawInputs", displayInputs);
        lin.put("inputs", displayInputs);
        lin.put("period", d.period());
        lin.put("window", d.period());
        lin.put("unit", d.unit());
        if (d.capability() != null) {
            lin.put("filters", d.capability().filtersEligibility());
            lin.put("transformation", d.capability().transformation());
            lin.put("aggregation", d.capability().aggregation());
            lin.put("missingDataTreatment", d.capability().missingDataTreatment());
            lin.put("implementationBinding", d.existingImplementationBinding());
            lin.put("productionStatus", d.capability().primaryStatus());
        }
        if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(d.type())) {
            lin.put("businessLineage", how == null
                    ? "Derived from required primitives"
                    : how);
        }
        return lin;
    }
}
