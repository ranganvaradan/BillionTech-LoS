package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalFactMaterializer;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatCollectionElementContracts;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticEntry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticRegistry;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Wave-4: SafeDerived validation against GACAT semantic cardinality/types.
 * Does not change operator runtime behaviour or CPES capability.
 */
public final class SafeDerivedSemanticValidation {

    private SafeDerivedSemanticValidation() {}

    public static List<String> validate(Map<String, Object> expr, GacatSemanticRegistry registry) {
        List<String> errors = new ArrayList<>();
        if (expr == null || expr.isEmpty()) return errors;
        walk(expr, registry, errors);
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static void walk(Object node, GacatSemanticRegistry registry, List<String> errors) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        switch (op) {
            case "FILTER", "PROJECT", "TRAILING_WINDOW", "FLATTEN", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
                    "ANY", "ALL" -> {
                Object from = first(m, "from", "of", "source");
                if (from instanceof Map<?, ?> fm && "REF".equalsIgnoreCase(String.valueOf(fm.get("op")))) {
                    String id = String.valueOf(fm.get("id"));
                    Optional<GacatSemanticEntry> entry = registry.find(id);
                    // Execution collection keys (Wave-3) may not be in GACAT 169
                    if (entry.isEmpty() && isExecutionCollection(id)) {
                        if ("FILTER".equals(op) || "TRAILING_WINDOW".equals(op) || "PROJECT".equals(op)
                                || "SUM".equals(op) || "AVG".equals(op) || "COUNT".equals(op)) {
                            // valid collection/history ops
                        }
                        if ("TRAILING_WINDOW".equals(op) && !CanonicalFactMaterializer.PAYMENT_HISTORY.equals(id)
                                && !id.toLowerCase(Locale.ROOT).contains("history")
                                && !id.toLowerCase(Locale.ROOT).contains("transaction")) {
                            errors.add("TRAILING_WINDOW prefers HISTORY/temporal collection; got " + id);
                        }
                    } else if (entry.isPresent()) {
                        GacatSemanticEntry e = entry.get();
                        if (e.cardinality() == GacatSemanticTaxonomy.Cardinality.SCALAR) {
                            if ("FILTER".equals(op) || "PROJECT".equals(op) || "TRAILING_WINDOW".equals(op)
                                    || "FLATTEN".equals(op) || "DISTINCT".equals(op)) {
                                errors.add(op + " invalid on SCALAR parameter: " + id);
                            }
                            if ("SUM".equals(op) || "AVG".equals(op) || "COUNT".equals(op)
                                    || "MIN".equals(op) || "MAX".equals(op)
                                    || "ANY".equals(op) || "ALL".equals(op)) {
                                // aggregation ops require collection from REF — reject scalar REF as collection source
                                errors.add(op + " requires COLLECTION/HISTORY input, not SCALAR: " + id);
                            }
                        }
                        if ("TRAILING_WINDOW".equals(op)
                                && e.cardinality() != GacatSemanticTaxonomy.Cardinality.HISTORY
                                && e.valueType() != GacatSemanticTaxonomy.ValueType.HISTORY) {
                            errors.add("TRAILING_WINDOW requires HISTORY/temporal contract: " + id);
                        }
                        if (("SUM".equals(op) || "AVG".equals(op))
                                && e.cardinality() != GacatSemanticTaxonomy.Cardinality.SCALAR
                                && e.valueType() == GacatSemanticTaxonomy.ValueType.BOOLEAN) {
                            errors.add(op + " invalid over BOOLEAN collection: " + id);
                        }
                    }
                }
                // Field-level: SUM of projected BOOLEAN
                if ("SUM".equals(op) || "AVG".equals(op)) {
                    Object of = first(m, "of", "from");
                    if (of instanceof Map<?, ?> proj && "PROJECT".equalsIgnoreCase(String.valueOf(proj.get("op")))) {
                        String field = String.valueOf(proj.get("field") == null ? "" : proj.get("field"))
                                .toLowerCase(Locale.ROOT);
                        Object coll = proj.get("from") != null ? proj.get("from") : proj.get("source");
                        if (coll instanceof Map<?, ?> cm && "REF".equalsIgnoreCase(String.valueOf(cm.get("op")))) {
                            String collId = String.valueOf(cm.get("id"));
                            Map<String, String> schema = GacatCollectionElementContracts.rowSchemaFor(collId);
                            String vt = schema.get(field);
                            if ("BOOLEAN".equalsIgnoreCase(vt)) {
                                errors.add(op + " invalid over BOOLEAN field '" + field + "' on " + collId);
                            }
                        }
                    }
                }
            }
            default -> { /* no-op */ }
        }
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                if (v instanceof Map<?, ?>) walk(v, registry, errors);
                else {
                    for (Object item : (List<?>) v) walk(item, registry, errors);
                }
            }
        }
    }

    private static boolean isExecutionCollection(String id) {
        return CanonicalFactMaterializer.TRADELINES.equals(id)
                || CanonicalFactMaterializer.PAYMENT_HISTORY.equals(id)
                || CanonicalFactMaterializer.INQUIRIES.equals(id)
                || "bank.transactions".equals(id)
                || "bank.accounts".equals(id);
    }

    private static Object first(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            if (m.get(k) != null) return m.get(k);
        }
        return null;
    }
}
