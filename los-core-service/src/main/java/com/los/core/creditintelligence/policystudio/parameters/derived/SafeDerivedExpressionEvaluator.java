package com.los.core.creditintelligence.policystudio.parameters.derived;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Safe typed expression evaluator for GACAT derived parameters.
 * No arbitrary code — only REF / CONST / arithmetic / compare / IF over exact GACAT IDs.
 * Missing inputs → DATA_INSUFFICIENT (never default to zero).
 */
public final class SafeDerivedExpressionEvaluator {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String STATUS_INVALID = "INVALID_EXPRESSION";

    private SafeDerivedExpressionEvaluator() {}

    public record EvalResult(String status, Object value, String reason) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("status", status);
            m.put("value", value);
            if (reason != null) m.put("reason", reason);
            return m;
        }
    }

    public static Set<String> collectDependencies(Map<String, Object> expr) {
        LinkedHashSet<String> deps = new LinkedHashSet<>();
        walkDeps(expr, deps);
        return deps;
    }

    @SuppressWarnings("unchecked")
    private static void walkDeps(Object node, Set<String> deps) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        if ("REF".equals(op)) {
            Object id = m.get("id");
            if (id != null) {
                String s = String.valueOf(id).trim();
                if (!s.isEmpty()) deps.add(s);
            }
            return;
        }
        for (Object v : m.values()) {
            if (v instanceof Map || v instanceof List) {
                if (v instanceof List<?> list) {
                    for (Object item : list) walkDeps(item, deps);
                } else {
                    walkDeps(v, deps);
                }
            }
        }
    }

    public static EvalResult evaluate(Map<String, Object> expr, Map<String, Object> inputs) {
        try {
            Object v = evalNode(expr, inputs == null ? Map.of() : inputs);
            if (v instanceof Missing) {
                return new EvalResult(STATUS_DATA_INSUFFICIENT, null, ((Missing) v).reason);
            }
            return new EvalResult(STATUS_OK, v, null);
        } catch (IllegalArgumentException ex) {
            return new EvalResult(STATUS_INVALID, null, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Object evalNode(Object node, Map<String, Object> inputs) {
        if (!(node instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Expression node must be an object");
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "REF" -> evalRef(m, inputs);
            case "CONST" -> m.get("value");
            case "ADD", "SUB", "MUL", "DIV" -> evalArithmetic(op, m, inputs);
            case "GT", "GTE", "LT", "LTE", "EQ" -> evalCompare(op, m, inputs);
            case "IF" -> evalIf(m, inputs);
            default -> throw new IllegalArgumentException("Unsupported op: " + op);
        };
    }

    private static Object evalRef(Map<String, Object> m, Map<String, Object> inputs) {
        String id = String.valueOf(m.getOrDefault("id", "")).trim();
        if (id.isEmpty()) throw new IllegalArgumentException("REF requires id");
        if (!inputs.containsKey(id) || inputs.get(id) == null) {
            return new Missing("Missing input: " + id);
        }
        return inputs.get(id);
    }

    private static Object evalArithmetic(String op, Map<String, Object> m, Map<String, Object> inputs) {
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        double a = toDouble(left);
        double b = toDouble(right);
        return switch (op) {
            case "ADD" -> a + b;
            case "SUB" -> a - b;
            case "MUL" -> a * b;
            case "DIV" -> {
                if (b == 0.0d) throw new IllegalArgumentException("Division by zero");
                yield a / b;
            }
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static Object evalCompare(String op, Map<String, Object> m, Map<String, Object> inputs) {
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        int cmp = Double.compare(toDouble(left), toDouble(right));
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            case "EQ" -> cmp == 0;
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static Object evalIf(Map<String, Object> m, Map<String, Object> inputs) {
        Object cond = evalNode(m.get("when"), inputs);
        if (cond instanceof Missing) return cond;
        boolean ok = cond instanceof Boolean b ? b : toDouble(cond) != 0.0d;
        return evalNode(ok ? m.get("then") : m.get("else"), inputs);
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0d : 0.0d;
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Not a number: " + s);
            }
        }
        throw new IllegalArgumentException("Not a number: " + v);
    }

    /** Validate expression shape + that every REF is an allowed GACAT id. */
    public static List<String> validate(Map<String, Object> expr, Set<String> allowedCanonicalIds) {
        List<String> errors = new ArrayList<>();
        if (expr == null || expr.isEmpty()) {
            errors.add("Expression is required");
            return errors;
        }
        try {
            collectDependencies(expr);
            evalShape(expr);
        } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
            return errors;
        }
        for (String dep : collectDependencies(expr)) {
            if (allowedCanonicalIds == null || !allowedCanonicalIds.contains(dep)) {
                errors.add("Unknown or disallowed GACAT input: " + dep);
            }
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static void evalShape(Object node) {
        if (!(node instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Expression node must be an object");
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        switch (op) {
            case "REF" -> {
                if (m.get("id") == null || String.valueOf(m.get("id")).isBlank()) {
                    throw new IllegalArgumentException("REF requires id");
                }
            }
            case "CONST" -> {
                if (!m.containsKey("value")) throw new IllegalArgumentException("CONST requires value");
            }
            case "ADD", "SUB", "MUL", "DIV", "GT", "GTE", "LT", "LTE", "EQ" -> {
                evalShape(m.get("left"));
                evalShape(m.get("right"));
            }
            case "IF" -> {
                evalShape(m.get("when"));
                evalShape(m.get("then"));
                evalShape(m.get("else"));
            }
            default -> throw new IllegalArgumentException("Unsupported op: " + op);
        }
    }

    private record Missing(String reason) {}
}
