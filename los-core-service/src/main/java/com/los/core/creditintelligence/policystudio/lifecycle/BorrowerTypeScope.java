package com.los.core.creditintelligence.policystudio.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * PRODUCT-CONFIG-ROUTING-CONVERGENCE-1 — borrower type multi-select semantics.
 * Empty list / null / contains ALL → ALL (any supported type).
 * Constrained list → OR match. Missing app borrower type → NOT MATCH when constrained.
 */
public final class BorrowerTypeScope {

    private BorrowerTypeScope() {}

    public static List<String> normalize(List<String> raw, String legacyScalar) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String s : raw) {
                if (s == null || s.isBlank()) continue;
                out.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        if (out.isEmpty() && legacyScalar != null && !legacyScalar.isBlank()) {
            out.add(legacyScalar.trim().toUpperCase(Locale.ROOT));
        }
        if (out.stream().anyMatch(BorrowerTypeScope::isAllToken)) {
            return List.of(); // explicit ALL → empty collection
        }
        return new ArrayList<>(out);
    }

    public static boolean isAll(List<String> normalized) {
        return normalized == null || normalized.isEmpty();
    }

    public static boolean matches(List<String> policyTypes, String legacyScalar, String appBorrowerType) {
        List<String> scope = normalize(policyTypes, legacyScalar);
        if (isAll(scope)) {
            return true;
        }
        if (appBorrowerType == null || appBorrowerType.isBlank()) {
            return false;
        }
        String app = appBorrowerType.trim().toUpperCase(Locale.ROOT);
        return scope.contains(app);
    }

    /** Overlap for conflict detection: ALL overlaps everything; else any shared code. */
    public static boolean overlaps(List<String> aTypes, String aLegacy, List<String> bTypes, String bLegacy) {
        List<String> a = normalize(aTypes, aLegacy);
        List<String> b = normalize(bTypes, bLegacy);
        if (isAll(a) || isAll(b)) return true;
        Set<String> bs = new LinkedHashSet<>(b);
        for (String x : a) {
            if (bs.contains(x)) return true;
        }
        return false;
    }

    public static String summaryLabel(List<String> normalized) {
        if (isAll(normalized)) return "All borrower types";
        return String.join(" + ", normalized);
    }

    /** Legacy scalar for columns that still require a single value: null when ALL/multi. */
    public static String legacyScalar(List<String> normalized) {
        if (isAll(normalized)) return null;
        if (normalized.size() == 1) return normalized.get(0);
        return null; // multi → do not invent a single scalar
    }

    private static boolean isAllToken(String s) {
        return "ALL".equalsIgnoreCase(s) || "*".equals(s);
    }
}
