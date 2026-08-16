package com.los.core.creditintelligence.policystudio.parameters.derived;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Safe typed expression evaluator for GACAT derived parameters.
 * No arbitrary code — only REF / CONST / arithmetic / compare / IF /
 * EVAL_AS_OF / MONTHS_SINCE_LAST_MATCH / COUNT_PERIODS_MATCHING over exact GACAT IDs.
 * Missing inputs → DATA_INSUFFICIENT (never default to zero).
 */
public final class SafeDerivedExpressionEvaluator {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String STATUS_INVALID = "INVALID_EXPRESSION";

    /** Convention: evaluation-date authority supplied by runtime (never silent wall-clock). */
    public static final String INPUT_EVAL_AS_OF = "__eval_as_of";

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
        if ("EVAL_AS_OF".equals(op)) {
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
            case "EVAL_AS_OF" -> evalAsOf(inputs);
            case "MONTHS_SINCE_LAST_MATCH" -> evalMonthsSinceLastMatch(m, inputs);
            case "COUNT_PERIODS_MATCHING" -> evalCountPeriodsMatching(m, inputs);
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

    private static Object evalAsOf(Map<String, Object> inputs) {
        Object v = inputs.get(INPUT_EVAL_AS_OF);
        if (v == null) v = inputs.get("evalAsOf");
        if (v == null) v = inputs.get("evaluationDate");
        if (v == null) {
            return new Missing("Missing evaluation date (EVAL_AS_OF / " + INPUT_EVAL_AS_OF + ")");
        }
        return parseYearMonth(v);
    }

    @SuppressWarnings("unchecked")
    private static Object evalMonthsSinceLastMatch(Map<String, Object> m, Map<String, Object> inputs) {
        Object historyNode = m.get("history");
        Object historyVal = evalNode(historyNode, inputs);
        if (historyVal instanceof Missing) return historyVal;

        Object asOfNode = m.get("asOf");
        Object asOfVal = asOfNode == null
                ? evalAsOf(inputs)
                : evalNode(asOfNode, inputs);
        if (asOfVal instanceof Missing) return asOfVal;
        YearMonth asOf = asOfVal instanceof YearMonth ym ? ym : parseYearMonth(asOfVal);

        String matchField = String.valueOf(m.getOrDefault("matchField", "dpd")).trim();
        String dateField = String.valueOf(m.getOrDefault("dateField", "month")).trim();
        String matchOp = String.valueOf(m.getOrDefault("matchOp", "GT")).trim().toUpperCase(Locale.ROOT);
        double matchValue = toDouble(m.get("matchValue"));

        List<?> rows;
        if (historyVal instanceof List<?> list) {
            rows = list;
        } else if (historyVal instanceof Map<?, ?> single) {
            rows = List.of(single);
        } else {
            throw new IllegalArgumentException("MONTHS_SINCE_LAST_MATCH history must be a list of observations");
        }

        YearMonth latestMatch = null;
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowRaw)) continue;
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            Object matchRaw = firstPresent(row, matchField, "DaysPastDue", "dpd", "DPD");
            Object dateRaw = firstPresent(row, dateField, "YearMonth", "month", "observationMonth", "period");
            if (matchRaw == null || dateRaw == null) continue;
            double dpd = toDouble(matchRaw);
            if (!compareMatch(matchOp, dpd, matchValue)) continue;
            YearMonth ym = parseYearMonth(dateRaw);
            if (latestMatch == null || ym.isAfter(latestMatch)) {
                latestMatch = ym;
            }
        }
        if (latestMatch == null) {
            // No overdue observation → treat as large clean history from earliest unknown: DATA_INSUFFICIENT
            // is safer than inventing infinity. Callers may supply sentinel; we report insufficient.
            return new Missing("No matching overdue observation in payment history");
        }
        long months = ChronoUnit.MONTHS.between(latestMatch, asOf);
        return Math.max(0L, months);
    }

    @SuppressWarnings("unchecked")
    private static Object evalCountPeriodsMatching(Map<String, Object> m, Map<String, Object> inputs) {
        Object historyVal = evalNode(m.get("history"), inputs);
        if (historyVal instanceof Missing) return historyVal;
        Object asOfVal = m.get("asOf") == null ? evalAsOf(inputs) : evalNode(m.get("asOf"), inputs);
        if (asOfVal instanceof Missing) return asOfVal;
        YearMonth asOf = asOfVal instanceof YearMonth ym ? ym : parseYearMonth(asOfVal);

        String matchField = String.valueOf(m.getOrDefault("matchField", "dpd")).trim();
        String dateField = String.valueOf(m.getOrDefault("dateField", "month")).trim();
        String matchOp = String.valueOf(m.getOrDefault("matchOp", "GTE")).trim().toUpperCase(Locale.ROOT);
        double matchValue = toDouble(m.get("matchValue"));
        int windowMonths = m.get("windowMonths") == null ? 6 : (int) toDouble(m.get("windowMonths"));
        if (windowMonths < 1) throw new IllegalArgumentException("windowMonths must be >= 1");
        boolean distinct = !Boolean.FALSE.equals(m.get("distinctPeriods"));
        YearMonth earliest = asOf.minusMonths(windowMonths - 1L);

        List<?> rows;
        if (historyVal instanceof List<?> list) {
            rows = list;
        } else if (historyVal instanceof Map<?, ?> single) {
            rows = List.of(single);
        } else {
            throw new IllegalArgumentException("COUNT_PERIODS_MATCHING history must be a list of observations");
        }

        LinkedHashSet<YearMonth> matched = new LinkedHashSet<>();
        long rawCount = 0;
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowRaw)) continue;
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            Object matchRaw = firstPresent(row, matchField, "DaysPastDue", "dpd", "DPD");
            Object dateRaw = firstPresent(row, dateField, "YearMonth", "month", "observationMonth", "period");
            if (matchRaw == null || dateRaw == null) continue;
            YearMonth ym = parseYearMonth(dateRaw);
            if (ym.isBefore(earliest) || ym.isAfter(asOf)) continue;
            if (!compareMatch(matchOp, toDouble(matchRaw), matchValue)) continue;
            rawCount++;
            matched.add(ym);
        }
        return distinct ? (long) matched.size() : rawCount;
    }

    private static boolean compareMatch(String op, double left, double right) {
        int cmp = Double.compare(left, right);
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            case "EQ" -> cmp == 0;
            default -> throw new IllegalArgumentException("Unsupported matchOp: " + op);
        };
    }

    private static Object firstPresent(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            if (k == null || k.isBlank()) continue;
            if (row.containsKey(k) && row.get(k) != null) return row.get(k);
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(k) && e.getValue() != null) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static YearMonth parseYearMonth(Object v) {
        if (v instanceof YearMonth ym) return ym;
        if (v instanceof LocalDate ld) return YearMonth.from(ld);
        if (v instanceof java.util.Date d) {
            return YearMonth.from(d.toInstant().atZone(java.time.ZoneOffset.UTC).toLocalDate());
        }
        String s = String.valueOf(v).trim();
        if (s.length() >= 7 && s.charAt(4) == '-') {
            try {
                return YearMonth.parse(s.substring(0, 7));
            } catch (Exception ignored) {
                // fall through
            }
        }
        try {
            return YearMonth.from(LocalDate.parse(s.substring(0, Math.min(10, s.length()))));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Not a month/date: " + v);
        }
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
            case "EVAL_AS_OF" -> {
                // leaf — evaluation date supplied by runtime inputs
            }
            case "MONTHS_SINCE_LAST_MATCH" -> {
                if (m.get("history") == null) {
                    throw new IllegalArgumentException("MONTHS_SINCE_LAST_MATCH requires history");
                }
                evalShape(m.get("history"));
                if (m.get("asOf") != null) evalShape(m.get("asOf"));
                if (!m.containsKey("matchValue")) {
                    throw new IllegalArgumentException("MONTHS_SINCE_LAST_MATCH requires matchValue");
                }
                String mop = String.valueOf(m.getOrDefault("matchOp", "GT")).trim().toUpperCase(Locale.ROOT);
                if (!Set.of("GT", "GTE", "LT", "LTE", "EQ").contains(mop)) {
                    throw new IllegalArgumentException("Unsupported matchOp: " + mop);
                }
            }
            case "COUNT_PERIODS_MATCHING" -> {
                if (m.get("history") == null) {
                    throw new IllegalArgumentException("COUNT_PERIODS_MATCHING requires history");
                }
                evalShape(m.get("history"));
                if (m.get("asOf") != null) evalShape(m.get("asOf"));
                if (!m.containsKey("matchValue")) {
                    throw new IllegalArgumentException("COUNT_PERIODS_MATCHING requires matchValue");
                }
                String mop2 = String.valueOf(m.getOrDefault("matchOp", "GTE")).trim().toUpperCase(Locale.ROOT);
                if (!Set.of("GT", "GTE", "LT", "LTE", "EQ").contains(mop2)) {
                    throw new IllegalArgumentException("Unsupported matchOp: " + mop2);
                }
            }
            default -> throw new IllegalArgumentException("Unsupported op: " + op);
        }
    }

    private record Missing(String reason) {}
}
