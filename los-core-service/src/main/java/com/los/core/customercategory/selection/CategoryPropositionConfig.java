package com.los.core.customercategory.selection;

import com.los.core.customercategory.CustomerCategoryEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads proposition + safe disambiguation config from Category.governanceJson.
 * Does not invent credit thresholds.
 */
public final class CategoryPropositionConfig {

    private CategoryPropositionConfig() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> proposition(CustomerCategoryEntity cat) {
        Map<String, Object> g = cat.getGovernanceJson() != null ? cat.getGovernanceJson() : Map.of();
        Object p = g.get("proposition");
        if (p instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> disambiguationAttributes(CustomerCategoryEntity cat) {
        Map<String, Object> g = cat.getGovernanceJson() != null ? cat.getGovernanceJson() : Map.of();
        Object d = g.get("disambiguation");
        if (!(d instanceof Map<?, ?> dm)) {
            return Map.of();
        }
        Object attrs = dm.get("attributes");
        if (!(attrs instanceof Map<?, ?> am)) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        am.forEach((k, v) -> {
            List<String> vals = new ArrayList<>();
            if (v instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null) {
                        vals.add(String.valueOf(o));
                    }
                }
            } else if (v != null) {
                vals.add(String.valueOf(v));
            }
            out.put(String.valueOf(k), vals);
        });
        return out;
    }

    @SuppressWarnings("unchecked")
    public static List<String> programmeTags(CustomerCategoryEntity cat) {
        Map<String, Object> g = cat.getGovernanceJson() != null ? cat.getGovernanceJson() : Map.of();
        Object d = g.get("disambiguation");
        if (!(d instanceof Map<?, ?> dm)) {
            return List.of();
        }
        Object tags = dm.get("programmeTags");
        if (!(tags instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o != null) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    public static boolean allowAutoSingleMatch(CustomerCategoryEntity cat) {
        Object v = proposition(cat).get("allowAutoSingleMatch");
        return v == null || Boolean.TRUE.equals(v);
    }

    public static int displayOrder(CustomerCategoryEntity cat) {
        Object v = proposition(cat).get("displayOrder");
        if (v instanceof Number n) {
            return n.intValue();
        }
        return 1000;
    }

    public static String customerFacingName(CustomerCategoryEntity cat) {
        Object n = proposition(cat).get("customerFacingName");
        if (n != null && !String.valueOf(n).isBlank()) {
            return String.valueOf(n);
        }
        return cat.getName();
    }

    public static String shortDescription(CustomerCategoryEntity cat) {
        Object d = proposition(cat).get("shortDescription");
        if (d != null && !String.valueOf(d).isBlank()) {
            return String.valueOf(d);
        }
        return cat.getDescription();
    }

    public static void putPropositionConfig(CustomerCategoryEntity cat, Map<String, Object> body) {
        if (cat.getGovernanceJson() == null) {
            cat.setGovernanceJson(new LinkedHashMap<>());
        }
        if (body.get("proposition") instanceof Map<?, ?> p) {
            Map<String, Object> copy = new LinkedHashMap<>();
            p.forEach((k, v) -> copy.put(String.valueOf(k), v));
            cat.getGovernanceJson().put("proposition", copy);
        }
        if (body.get("disambiguation") instanceof Map<?, ?> d) {
            Map<String, Object> copy = new LinkedHashMap<>();
            d.forEach((k, v) -> copy.put(String.valueOf(k), v));
            cat.getGovernanceJson().put("disambiguation", copy);
        }
    }
}
