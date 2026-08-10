package com.los.core.creditintelligence.decision.service;

import java.math.BigDecimal;
import java.util.Map;

/** Helpers for reading frozen metric/fact maps. */
public final class DecisionValueHelper {

    private DecisionValueHelper() {}

    public static BigDecimal num(Map<String, Object> map, String path) {
        Object raw = map == null ? null : map.get(path);
        Object v = unwrap(raw);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    public static BigDecimal numOrZero(Map<String, Object> map, String path) {
        BigDecimal n = num(map, path);
        return n == null ? BigDecimal.ZERO : n;
    }

    public static boolean has(Map<String, Object> map, String path) {
        if (map == null || !map.containsKey(path)) {
            return false;
        }
        Object raw = map.get(path);
        if (raw instanceof Map<?, ?> mm) {
            Object status = mm.get("dataStatus");
            if (status != null) {
                String s = String.valueOf(status).toUpperCase();
                if ("MISSING".equals(s) || "DATA_INSUFFICIENT".equals(s)) {
                    return false;
                }
            }
            return mm.get("value") != null || mm.get("v") != null;
        }
        return raw != null;
    }

    public static Object unwrap(Object raw) {
        if (raw instanceof Map<?, ?> mm) {
            Object status = mm.get("dataStatus");
            if (status != null) {
                String s = String.valueOf(status).toUpperCase();
                if ("MISSING".equals(s) || "DATA_INSUFFICIENT".equals(s)) {
                    return null;
                }
            }
            return mm.get("value") != null ? mm.get("value") : mm.get("v");
        }
        return raw;
    }

    public static BigDecimal bd(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    public static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return Map.of();
    }
}
