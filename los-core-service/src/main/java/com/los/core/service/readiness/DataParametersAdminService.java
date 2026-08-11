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
 * LOS-LIVE-READINESS-1 — Administration → Data & Parameters (registry read model only).
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
        out.put("sources", reg.sources());
        out.put("catalogue", reg.catalogueView());
        out.put("bySourceSummary", bySourceSummary(reg));
        out.put("gapsManual", gapsManual(reg));
        out.put("workflowProvides", WorkflowParameterProvidesCatalog.catalogueView());
        out.put("resolutionOrder", List.of(
                "Application → Product dimensions",
                "Workflow",
                "Live Rule Set",
                "Live Scorecard",
                "Studio Policy publication metadata only (not production authority)"));
        return out;
    }

    public Map<String, Object> browseBySource(String source) {
        Map<String, Object> browse = registry().browseBySource(source);
        browse.put("allowCanonicalAuthority", false);
        browse.put("readModelOnly", true);
        return browse;
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("rawCount", sizeOf(browse.get("raw")));
            row.put("derivedCount", sizeOf(browse.get("derived")));
            row.put("manualCount", sizeOf(browse.get("manual")));
            row.put("count", browse.get("count"));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> gapsManual(CanonicalParameterRegistry reg) {
        List<Map<String, Object>> manual = new ArrayList<>();
        List<Map<String, Object>> unresolvedBinding = new ArrayList<>();
        for (CanonicalParameterDefinition d : reg.all()) {
            if (CanonicalParameterDefinition.MANUAL.equalsIgnoreCase(d.type())) {
                manual.add(enrich(d));
            }
            if (d.existingImplementationBinding() == null || d.existingImplementationBinding().isBlank()
                    || "UNRESOLVED".equalsIgnoreCase(String.valueOf(d.availability()))) {
                unresolvedBinding.add(enrich(d));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("manualParameters", manual);
        out.put("manualCount", manual.size());
        out.put("availabilityGaps", unresolvedBinding);
        out.put("availabilityGapCount", unresolvedBinding.size());
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
        m.put("advanced", advanced);
        return m;
    }

    private Map<String, Object> lineage(CanonicalParameterDefinition d) {
        Map<String, Object> lin = new LinkedHashMap<>();
        lin.put("type", d.type());
        lin.put("calculationSummary", d.calculationSummary());
        lin.put("requiredPrimitives", d.requiredPrimitives() == null ? List.of() : d.requiredPrimitives());
        lin.put("period", d.period());
        lin.put("unit", d.unit());
        if (CanonicalParameterDefinition.DERIVED.equalsIgnoreCase(d.type())) {
            lin.put("businessLineage", d.calculationSummary() == null
                    ? "Derived from required primitives"
                    : d.calculationSummary());
        }
        return lin;
    }

    private static int sizeOf(Object o) {
        return o instanceof List<?> l ? l.size() : 0;
    }
}
