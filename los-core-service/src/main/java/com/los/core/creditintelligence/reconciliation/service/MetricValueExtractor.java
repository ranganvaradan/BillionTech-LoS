package com.los.core.creditintelligence.reconciliation.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Robust extraction of numeric values from CiMetricResult.value JSON maps.
 * Prefers keys: v, amount, value, number; also searches one level of nested maps.
 */
public final class MetricValueExtractor {

    private MetricValueExtractor() {
    }

    public static BigDecimal extract(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        BigDecimal direct = tryKeys(value);
        if (direct != null) {
            return direct;
        }
        for (Object nested : value.values()) {
            if (nested instanceof Map<?, ?> m) {
                Map<String, Object> cast = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (e.getKey() != null) {
                        cast.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                BigDecimal found = tryKeys(cast);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    public static BigDecimal extractOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (raw instanceof Map<?, ?> m) {
            Map<String, Object> cast = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    cast.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return extract(cast);
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                return null;
            }
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal tryKeys(Map<String, Object> value) {
        for (String key : new String[]{"v", "amount", "value", "number", "total", "sum"}) {
            if (value.containsKey(key)) {
                BigDecimal bd = coerce(value.get(key));
                if (bd != null) {
                    return bd;
                }
            }
        }
        return null;
    }

    private static BigDecimal coerce(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        try {
            String s = String.valueOf(o).trim();
            if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
                return null;
            }
            return new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }
}
