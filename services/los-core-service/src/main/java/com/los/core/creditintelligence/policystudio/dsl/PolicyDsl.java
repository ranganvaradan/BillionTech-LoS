package com.los.core.creditintelligence.policystudio.dsl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Helpers for candidate policy DSL JSON structures. */
public final class PolicyDsl {

    private PolicyDsl() {}

    public static Map<String, Object> op(String op, Object left, Object right) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", op);
        m.put("left", left);
        m.put("right", right);
        return m;
    }

    public static Map<String, Object> metric(String path) {
        return Map.of("metric", path);
    }

    public static Map<String, Object> fact(String path) {
        return Map.of("fact", path);
    }

    public static Map<String, Object> appField(String path) {
        return Map.of("applicationField", path);
    }

    public static Map<String, Object> and(Object... children) {
        return Map.of("op", "AND", "args", List.of(children));
    }

    public static Map<String, Object> or(Object... children) {
        return Map.of("op", "OR", "args", List.of(children));
    }

    public static Map<String, Object> not(Object child) {
        return Map.of("op", "NOT", "arg", child);
    }

    public static Map<String, Object> iff(Object condition, Object thenExpr, Object elseExpr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("op", "IF");
        m.put("condition", condition);
        m.put("then", thenExpr);
        m.put("else", elseExpr);
        return m;
    }

    public static Map<String, Object> divide(Object left, Object right) {
        return op("DIVIDE", left, right);
    }

    public static Map<String, Object> exists(Object ref) {
        return Map.of("op", "EXISTS", "arg", ref);
    }

    public static Map<String, Object> gte(Object left, Object right) {
        return op("GTE", left, right);
    }

    public static Map<String, Object> gt(Object left, Object right) {
        return op("GT", left, right);
    }

    public static Map<String, Object> lte(Object left, Object right) {
        return op("LTE", left, right);
    }

    public static Map<String, Object> lt(Object left, Object right) {
        return op("LT", left, right);
    }

    public static Map<String, Object> eq(Object left, Object right) {
        return op("EQ", left, right);
    }

    public static Map<String, Object> ne(Object left, Object right) {
        return op("NE", left, right);
    }
}
