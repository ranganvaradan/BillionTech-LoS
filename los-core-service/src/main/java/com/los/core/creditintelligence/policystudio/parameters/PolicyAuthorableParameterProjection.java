package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticEntry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GOLDEN-POLICY-CONFIGURATION-TRUTH-AUDIT-1 — single Policy Studio authorable universe.
 * <p>
 * Authority: GACAT identity + Wave-4 {@code policySelectableDefault}. Not a hardcoded
 * exclusion list. Data &amp; Parameters source cards keep the full catalogue via
 * {@link CanonicalParameterRegistry#browseBySource(String)}; Policy Studio Add Rule
 * and Change Parameter must call this projection.
 */
public final class PolicyAuthorableParameterProjection {

    public static final String AUTHORITY = "GACAT + GacatSemanticRegistry.policySelectableDefault";

    private PolicyAuthorableParameterProjection() {}

    public static boolean isAuthorable(String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank()) {
            return false;
        }
        return GacatSemanticRegistry.shared().find(canonicalId.trim())
                .map(GacatSemanticEntry::policySelectableDefault)
                .orElse(false);
    }

    public static boolean isAuthorable(CanonicalParameterDefinition def) {
        return def != null && isAuthorable(def.id());
    }

    public static List<CanonicalParameterDefinition> authorableOf(CanonicalParameterRegistry registry) {
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        List<CanonicalParameterDefinition> out = new ArrayList<>();
        for (CanonicalParameterDefinition def : src.all()) {
            if (isAuthorable(def)) {
                out.add(def);
            }
        }
        return List.copyOf(out);
    }

    public static Optional<CanonicalParameterDefinition> findAuthorable(
            CanonicalParameterRegistry registry, String canonicalId) {
        if (canonicalId == null || canonicalId.isBlank()) {
            return Optional.empty();
        }
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        return src.findById(canonicalId.trim()).filter(PolicyAuthorableParameterProjection::isAuthorable);
    }

    /** Policy Studio Change Parameter / Add Rule catalogue (filtered). */
    public static Map<String, Object> catalogueView(CanonicalParameterRegistry registry) {
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        List<CanonicalParameterDefinition> authorable = authorableOf(src);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("count", authorable.size());
        out.put("unfilteredCount", src.all().size());
        out.put("parameters", authorable.stream().map(CanonicalParameterDefinition::toBusinessView).toList());
        out.put("sources", src.sources());
        out.put("readModelOnly", true);
        out.put("allowCanonicalAuthority", false);
        out.put("inventoryVersion", "POLICY-AUTHORABLE-GACAT");
        out.put("catalogueAuthority", src.authority());
        out.put("javaSeedIsRuntimeAuthority",
                GacatCatalogueAuthority.AUTHORITY_JAVA_SEED_TEST_ONLY.equals(src.authority()));
        out.put("authorableProjectionAuthority", AUTHORITY);
        out.put("policyAuthorableOnly", true);
        return out;
    }

    /** Policy Studio Change Parameter browse-by-source (filtered). */
    public static Map<String, Object> browseBySource(CanonicalParameterRegistry registry, String source) {
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        Map<String, Object> full = src.browseBySource(source);
        List<Map<String, Object>> raw = filterViews(castList(full.get("raw")));
        List<Map<String, Object>> derived = filterViews(castList(full.get("derived")));
        List<Map<String, Object>> manual = filterViews(castList(full.get("manual")));
        Map<String, Object> out = new LinkedHashMap<>(full);
        out.put("raw", raw);
        out.put("derived", derived);
        out.put("manual", manual);
        out.put("count", raw.size() + derived.size() + manual.size());
        out.put("rawCount", raw.size());
        out.put("derivedCount", derived.size());
        out.put("manualCount", manual.size());
        out.put("unfilteredCount", full.get("count"));
        out.put("authorableProjectionAuthority", AUTHORITY);
        out.put("policyAuthorableOnly", true);
        return out;
    }

    /** Policy Studio parameter search (filtered). */
    public static Map<String, Object> search(CanonicalParameterRegistry registry, String query) {
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        Map<String, Object> full = src.search(query);
        List<Map<String, Object>> results = filterViews(castList(full.get("results")));
        Map<String, Object> out = new LinkedHashMap<>(full);
        out.put("results", results);
        out.put("count", results.size());
        out.put("unfilteredCount", full.get("count"));
        out.put("authorableProjectionAuthority", AUTHORITY);
        out.put("policyAuthorableOnly", true);
        return out;
    }

    /**
     * Count of Policy Studio authorable rows that are not GACAT {@code policySelectableDefault}.
     * Ingredients / config metadata must be 0 in the Add Rule / Change Parameter universe.
     */
    public static int technicalMetadataAuthorableCount(CanonicalParameterRegistry registry) {
        CanonicalParameterRegistry src = registry == null
                ? CanonicalParameterRegistry.shared() : registry;
        int leaks = 0;
        for (CanonicalParameterDefinition def : authorableOf(src)) {
            var semantic = GacatSemanticRegistry.shared().find(def.id());
            if (semantic.isEmpty() || !semantic.get().policySelectableDefault()) {
                leaks++;
                continue;
            }
            GacatSemanticTaxonomy.ParameterClass cls = semantic.get().parameterClass();
            if (cls == GacatSemanticTaxonomy.ParameterClass.CONFIGURATION
                    || cls == GacatSemanticTaxonomy.ParameterClass.INGREDIENT) {
                leaks++;
            }
        }
        return leaks;
    }

    /** @deprecated use {@link #technicalMetadataAuthorableCount(CanonicalParameterRegistry)} */
    public static int technicalMetadataAuthorableLeakCount(CanonicalParameterRegistry registry) {
        return technicalMetadataAuthorableCount(registry);
    }

    private static List<Map<String, Object>> filterViews(List<Map<String, Object>> views) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> view : views) {
            Object id = view.get("id");
            if (id != null && isAuthorable(String.valueOf(id))) {
                out.add(view);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        return out;
    }
}
