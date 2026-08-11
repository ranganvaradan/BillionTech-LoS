package com.los.core.service.readiness;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administration → Data & Parameters (CanonicalParameterRegistry read model only).
 * GACAT-SOURCE-CATALOGUE-RECOVERY-1 — richer source counts + capability status.
 */
@Service
public class DataParametersAdminService {

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    public Map<String, Object> overview() {
        CanonicalParameterRegistry reg = registry();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", "Data & Parameters");
        out.put("subtitle", "What the institution can know — CanonicalParameterRegistry read model.");
        out.put("readModelOnly", true);
        out.put("allowCanonicalAuthority", false);
        out.put("inventoryVersion", "GACAT-SOURCE-CATALOGUE-RECOVERY-1");
        out.put("sources", reg.sources());
        out.put("catalogue", reg.catalogueView());
        out.put("bySourceSummary", bySourceSummary(reg));
        out.put("gapsManual", gapsManual(reg));
        out.put("workflowProvides", WorkflowParameterProvidesCatalog.catalogueView());
        out.put("totals", totals(reg));
        out.put("resolutionOrder", List.of(
                "Application → Product dimensions",
                "Workflow",
                "Live Rule Set",
                "Live Scorecard",
                "Studio Policy publication metadata only (not production authority)"));
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

    public Map<String, Object> search(String q) {
        Map<String, Object> search = registry().search(q);
        search.put("allowCanonicalAuthority", false);
        return search;
    }

    public Map<String, Object> parameterDetail(String id) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        registry().findById(id).ifPresentOrElse(def -> {
            out.put("parameter", enrich(def));
            out.put("found", true);
        }, () -> {
            out.put("found", false);
            out.put("message", "Unknown parameter id");
        });
        return out;
    }

    private List<Map<String, Object>> bySourceSummary(CanonicalParameterRegistry reg) {
        List<Map<String, Object>> rows = new ArrayList<>();
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
            rows.add(row);
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
        m.put("lineage", lineage(d));
        Map<String, Object> advanced = new LinkedHashMap<>();
        advanced.put("existingImplementationBinding", d.existingImplementationBinding());
        advanced.put("liveRuleParameter", d.liveRuleParameter());
        advanced.put("liveScorecardParameter", d.liveScorecardParameter());
        advanced.put("id", d.id());
        if (d.capability() != null) {
            advanced.put("providerFieldPath", d.capability().providerFieldPath());
            advanced.put("schema", d.capability().schema());
            advanced.put("cardinality", d.capability().cardinality());
        }
        m.put("advanced", advanced);
        return m;
    }

    private Map<String, Object> lineage(CanonicalParameterDefinition d) {
        Map<String, Object> lin = new LinkedHashMap<>();
        lin.put("type", d.type());
        lin.put("howCalculated", d.calculationSummary());
        lin.put("calculationSummary", d.calculationSummary());
        lin.put("requiredPrimitives", d.requiredPrimitives() == null ? List.of() : d.requiredPrimitives());
        lin.put("rawInputs", d.requiredPrimitives() == null ? List.of() : d.requiredPrimitives());
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
            lin.put("businessLineage", d.calculationSummary() == null
                    ? "Derived from required primitives"
                    : d.calculationSummary());
        }
        return lin;
    }
}
