package com.los.core.creditintelligence.policystudio.dsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Structural semantic comparison of PolicyDsl ASTs, including outcome polarity.
 * Used so catalogue ingestion cannot silently replace a richer authored AST with a weaker template.
 */
public final class PolicyDslSemanticEquivalence {

    private PolicyDslSemanticEquivalence() {}

    /**
     * @return true when both rules accept/reject the same conditions (after polarity normalize).
     */
    public static boolean equivalent(
            Map<String, Object> leftExpr,
            String leftOnTrue,
            String leftOnFalse,
            Map<String, Object> rightExpr,
            String rightOnTrue,
            String rightOnFalse) {
        if (!isExecutableAst(leftExpr) || !isExecutableAst(rightExpr)) {
            return false;
        }
        Object leftPass = toPassCondition(leftExpr, leftOnTrue, leftOnFalse);
        Object rightPass = toPassCondition(rightExpr, rightOnTrue, rightOnFalse);
        return Objects.equals(normalize(leftPass), normalize(rightPass));
    }

    public static boolean isExecutableAst(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        Object op = expr.get("op");
        if (op == null) {
            return false;
        }
        String o = String.valueOf(op).toUpperCase(Locale.ROOT);
        return !"UNKNOWN".equals(o) && !"CLASSIFICATION".equals(o) && !"HARD".equals(o);
    }

    private static Object toPassCondition(Map<String, Object> expr, String onTrue, String onFalse) {
        if (isPassOutcome(onTrue) && isFailOutcome(onFalse)) {
            return expr;
        }
        if (isFailOutcome(onTrue) && isPassOutcome(onFalse)) {
            return Map.of("op", "NOT", "arg", expr);
        }
        // Ambiguous outcomes — compare raw expression only
        return expr;
    }

    private static boolean isPassOutcome(String o) {
        if (o == null) return false;
        String u = o.toUpperCase(Locale.ROOT);
        return u.contains("PASS") || u.equals("ALLOW") || u.equals("APPROVE");
    }

    private static boolean isFailOutcome(String o) {
        if (o == null) return false;
        String u = o.toUpperCase(Locale.ROOT);
        return u.contains("FAIL") || u.contains("REJECT") || u.equals("DECLINE");
    }

    @SuppressWarnings("unchecked")
    static Object normalize(Object node) {
        if (node == null) {
            return null;
        }
        if (!(node instanceof Map<?, ?> raw)) {
            return canonicalizeScalar(node);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            m.put(String.valueOf(e.getKey()), e.getValue());
        }

        // Unwrap const wrappers early when present as whole node
        if (m.size() == 1 && m.containsKey("const")) {
            return canonicalizeScalar(m.get("const"));
        }

        // Unify fact/metric leaf refs
        if (m.containsKey("metric") && m.size() == 1) {
            return Map.of("ref", String.valueOf(m.get("metric")));
        }
        if (m.containsKey("fact") && m.size() == 1) {
            return Map.of("ref", String.valueOf(m.get("fact")));
        }

        String op = m.get("op") == null ? null : String.valueOf(m.get("op")).toUpperCase(Locale.ROOT);

        // Push NOT through comparison inverses
        if ("NOT".equals(op)) {
            Object arg = normalize(m.get("arg"));
            Object inverted = invertComparison(arg);
            if (inverted != null) {
                return inverted;
            }
            Map<String, Object> out = new TreeMap<>();
            out.put("op", "NOT");
            out.put("arg", arg);
            return out;
        }

        if ("OR".equals(op) || "AND".equals(op)) {
            List<Object> args = new ArrayList<>();
            Object rawArgs = m.get("args");
            if (rawArgs instanceof Collection<?> c) {
                for (Object a : c) {
                    args.add(normalize(a));
                }
            }
            args.sort(Comparator.comparing(PolicyDslSemanticEquivalence::stableKey));
            Map<String, Object> out = new TreeMap<>();
            out.put("op", op);
            out.put("args", args);
            return out;
        }

        if (isComparison(op)) {
            Object left = normalize(m.get("left"));
            Object right = normalize(m.get("right"));
            // Canonicalize EQ/NE operand order when one side is a ref
            if (("EQ".equals(op) || "NE".equals(op)) && isRef(right) && !isRef(left)) {
                Object tmp = left;
                left = right;
                right = tmp;
            }
            Map<String, Object> out = new TreeMap<>();
            out.put("op", op);
            out.put("left", left);
            out.put("right", right);
            return out;
        }

        Map<String, Object> out = new TreeMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            out.put(e.getKey(), normalize(e.getValue()));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object invertComparison(Object normalized) {
        if (!(normalized instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        raw.forEach((k, v) -> m.put(String.valueOf(k), v));
        String op = m.get("op") == null ? null : String.valueOf(m.get("op")).toUpperCase(Locale.ROOT);
        if (op == null) {
            return null;
        }
        String inv = switch (op) {
            case "LT" -> "GTE";
            case "LTE" -> "GT";
            case "GT" -> "LTE";
            case "GTE" -> "LT";
            case "EQ" -> "NE";
            case "NE" -> "EQ";
            default -> null;
        };
        if (inv == null) {
            return null;
        }
        Map<String, Object> out = new TreeMap<>();
        out.put("op", inv);
        out.put("left", m.get("left"));
        out.put("right", m.get("right"));
        return out;
    }

    private static boolean isComparison(String op) {
        return op != null && List.of("LT", "LTE", "GT", "GTE", "EQ", "NE").contains(op);
    }

    private static boolean isRef(Object o) {
        return o instanceof Map<?, ?> m && m.containsKey("ref");
    }

    private static Object canonicalizeScalar(Object v) {
        if (v instanceof Number n) {
            // Prefer long for integer thresholds so 650 and 650.0 match
            if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (Math.rint(d) == d) {
                    return (long) d;
                }
                return d;
            }
            return n.longValue();
        }
        if (v instanceof Boolean || v instanceof String) {
            return v;
        }
        return v;
    }

    private static String stableKey(Object o) {
        return String.valueOf(o);
    }
}
