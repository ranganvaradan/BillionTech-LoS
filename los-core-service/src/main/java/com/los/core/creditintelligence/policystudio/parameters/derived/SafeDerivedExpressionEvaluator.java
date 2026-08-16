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
 * Closed JSON-AST VM — no arbitrary code. Wave-2 extends the same engine with
 * generic collection operators (FILTER / PROJECT / SUM / …). Missing REF inputs →
 * DATA_INSUFFICIENT (never default to zero). Empty collections are valid values.
 *
 * <p>No canonical-parameter-id branches in evaluation logic.
 */
public final class SafeDerivedExpressionEvaluator {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String STATUS_INVALID = "INVALID_EXPRESSION";

    /** Convention: evaluation-date authority supplied by runtime (never silent wall-clock). */
    public static final String INPUT_EVAL_AS_OF = "__eval_as_of";

    /** Row binding for FILTER / ANY / ALL predicates (FIELD resolves against this). */
    public static final String INPUT_CURRENT_ROW = "__current_row";

    /** Legacy + Wave-2 operators. Assistant must not claim CAN_CALCULATE for ops outside this set. */
    public static final Set<String> SUPPORTED_OPS = Set.of(
            "REF", "CONST", "ADD", "SUB", "MUL", "DIV",
            "GT", "GTE", "LT", "LTE", "EQ", "IF",
            "AND", "OR", "NOT",
            "EVAL_AS_OF", "MONTHS_SINCE_LAST_MATCH", "COUNT_PERIODS_MATCHING",
            "FILTER", "FIELD", "PROJECT", "COUNT", "SUM", "AVG", "MIN", "MAX",
            "DISTINCT", "ANY", "ALL", "FLATTEN", "TRAILING_WINDOW");

    private SafeDerivedExpressionEvaluator() {}

    public static boolean supportsOp(String op) {
        return op != null && SUPPORTED_OPS.contains(op.trim().toUpperCase(Locale.ROOT));
    }

    /** Flatten operator names present in an expression tree (for provenance). */
    public static List<String> operatorChain(Map<String, Object> expr) {
        LinkedHashSet<String> ops = new LinkedHashSet<>();
        walkOps(expr, ops);
        return List.copyOf(ops);
    }

    @SuppressWarnings("unchecked")
    private static void walkOps(Object node, Set<String> ops) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        Object op = m.get("op");
        if (op != null) ops.add(String.valueOf(op).trim().toUpperCase(Locale.ROOT));
        for (Object v : m.values()) {
            if (v instanceof Map || v instanceof List) {
                if (v instanceof List<?> list) {
                    for (Object item : list) walkOps(item, ops);
                } else {
                    walkOps(v, ops);
                }
            }
        }
    }

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
            case "AND" -> evalAnd(m, inputs);
            case "OR" -> evalOr(m, inputs);
            case "NOT" -> evalNot(m, inputs);
            case "IF" -> evalIf(m, inputs);
            case "EVAL_AS_OF" -> evalAsOf(inputs);
            case "MONTHS_SINCE_LAST_MATCH" -> evalMonthsSinceLastMatch(m, inputs);
            case "COUNT_PERIODS_MATCHING" -> evalCountPeriodsMatching(m, inputs);
            case "FILTER" -> evalFilter(m, inputs);
            case "FIELD" -> evalField(m, inputs);
            case "PROJECT" -> evalProject(m, inputs);
            case "COUNT" -> evalCount(m, inputs);
            case "SUM" -> evalNumericAgg("SUM", m, inputs);
            case "AVG" -> evalNumericAgg("AVG", m, inputs);
            case "MIN" -> evalNumericAgg("MIN", m, inputs);
            case "MAX" -> evalNumericAgg("MAX", m, inputs);
            case "DISTINCT" -> evalDistinct(m, inputs);
            case "ANY" -> evalAnyAll(true, m, inputs);
            case "ALL" -> evalAnyAll(false, m, inputs);
            case "FLATTEN" -> evalFlatten(m, inputs);
            case "TRAILING_WINDOW" -> evalTrailingWindow(m, inputs);
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
        rejectIncompatibleArithmeticTypes(m);
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        if (!isNumericValue(left) || !isNumericValue(right)) {
            throw new IllegalArgumentException(op + " requires numeric operands");
        }
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

    @SuppressWarnings("unchecked")
    private static void rejectIncompatibleArithmeticTypes(Map<String, Object> m) {
        SafeDerivedTypeModel.ExprType l = constTypeHint(m.get("left"));
        SafeDerivedTypeModel.ExprType r = constTypeHint(m.get("right"));
        if ((l == SafeDerivedTypeModel.ExprType.MONEY && SafeDerivedTypeModel.isTemporal(r))
                || (r == SafeDerivedTypeModel.ExprType.MONEY && SafeDerivedTypeModel.isTemporal(l))) {
            throw new IllegalArgumentException("Cannot combine MONEY with DATE/MONTH in arithmetic");
        }
    }

    @SuppressWarnings("unchecked")
    private static SafeDerivedTypeModel.ExprType constTypeHint(Object node) {
        if (!(node instanceof Map<?, ?> raw)) return SafeDerivedTypeModel.ExprType.UNKNOWN;
        Map<String, Object> m = (Map<String, Object>) raw;
        if (!"CONST".equalsIgnoreCase(String.valueOf(m.get("op")))) {
            return SafeDerivedTypeModel.ExprType.UNKNOWN;
        }
        Object hint = m.get("valueType") != null ? m.get("valueType") : m.get("type");
        return SafeDerivedTypeModel.ofConst(m.get("value"), hint);
    }

    private static Object evalCompare(String op, Map<String, Object> m, Map<String, Object> inputs) {
        rejectIncompatibleCompareTypes(op, m);
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        if ("EQ".equals(op)) {
            if (left instanceof Number && right instanceof Number) {
                return Double.compare(toDouble(left), toDouble(right)) == 0;
            }
            if (left instanceof Boolean || right instanceof Boolean) {
                return toBoolean(left) == toBoolean(right);
            }
            return String.valueOf(left).equalsIgnoreCase(String.valueOf(right));
        }
        // Ordered compares require numbers (or parseable numerics)
        if (!isNumericValue(left) || !isNumericValue(right)) {
            throw new IllegalArgumentException(op + " requires numeric operands, got "
                    + typeLabel(left) + " and " + typeLabel(right));
        }
        int cmp = Double.compare(toDouble(left), toDouble(right));
        return switch (op) {
            case "GT" -> cmp > 0;
            case "GTE" -> cmp >= 0;
            case "LT" -> cmp < 0;
            case "LTE" -> cmp <= 0;
            default -> throw new IllegalArgumentException(op);
        };
    }

    @SuppressWarnings("unchecked")
    private static void rejectIncompatibleCompareTypes(String op, Map<String, Object> m) {
        SafeDerivedTypeModel.ExprType l = constTypeHint(m.get("left"));
        SafeDerivedTypeModel.ExprType r = constTypeHint(m.get("right"));
        if ((SafeDerivedTypeModel.isTemporal(l) && r == SafeDerivedTypeModel.ExprType.MONEY)
                || (SafeDerivedTypeModel.isTemporal(r) && l == SafeDerivedTypeModel.ExprType.MONEY)) {
            throw new IllegalArgumentException("Cannot compare DATE/MONTH to MONEY");
        }
    }

    private static Object evalAnd(Map<String, Object> m, Map<String, Object> inputs) {
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        if (!toBoolean(left)) return false;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        return toBoolean(right);
    }

    private static Object evalOr(Map<String, Object> m, Map<String, Object> inputs) {
        Object left = evalNode(m.get("left"), inputs);
        if (left instanceof Missing) return left;
        if (toBoolean(left)) return true;
        Object right = evalNode(m.get("right"), inputs);
        if (right instanceof Missing) return right;
        return toBoolean(right);
    }

    private static Object evalNot(Map<String, Object> m, Map<String, Object> inputs) {
        Object of = m.get("of") != null ? m.get("of") : m.get("expr");
        Object v = evalNode(of, inputs);
        if (v instanceof Missing) return v;
        return !toBoolean(v);
    }

    private static Object evalFilter(Map<String, Object> m, Map<String, Object> inputs) {
        Object from = evalNode(sourceArg(m), inputs);
        if (from instanceof Missing) return from;
        List<?> rows = asRecordList(from, "FILTER");
        Object where = m.get("where");
        if (where == null) return new ArrayList<>(rows);
        List<Object> out = new ArrayList<>();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?>)) continue;
            Map<String, Object> bound = bindRow(inputs, rowObj);
            Object pred = evalNode(where, bound);
            if (pred instanceof Missing) continue;
            if (toBoolean(pred)) out.add(rowObj);
        }
        return out;
    }

    private static Object evalField(Map<String, Object> m, Map<String, Object> inputs) {
        String name = fieldName(m);
        if (name.isBlank()) throw new IllegalArgumentException("FIELD requires field/name");
        Object from = m.get("from");
        Object rowObj;
        if (from != null) {
            rowObj = evalNode(from, inputs);
            if (rowObj instanceof Missing) return rowObj;
        } else {
            rowObj = inputs.get(INPUT_CURRENT_ROW);
            if (rowObj == null) {
                throw new IllegalArgumentException("FIELD '" + name + "' used outside row context");
            }
        }
        if (!(rowObj instanceof Map<?, ?> rowRaw)) {
            throw new IllegalArgumentException("FIELD requires a RECORD");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) rowRaw;
        Object v = firstPresent(row, name);
        // Missing field on a present row → null value (not collection Missing)
        return v;
    }

    private static Object evalProject(Map<String, Object> m, Map<String, Object> inputs) {
        String name = fieldName(m);
        if (name.isBlank()) throw new IllegalArgumentException("PROJECT requires field");
        Object from = evalNode(sourceArg(m), inputs);
        if (from instanceof Missing) return from;
        List<?> rows = asRecordList(from, "PROJECT");
        List<Object> out = new ArrayList<>();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowRaw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            out.add(firstPresent(row, name));
        }
        return out;
    }

    private static Object evalCount(Map<String, Object> m, Map<String, Object> inputs) {
        Object of = evalNode(collectionArg(m), inputs);
        if (of instanceof Missing) return of;
        return (long) asList(of, "COUNT").size();
    }

    private static Object evalNumericAgg(String op, Map<String, Object> m, Map<String, Object> inputs) {
        Object of = evalNode(collectionArg(m), inputs);
        if (of instanceof Missing) return of;
        List<?> values = asList(of, op);
        if ("AVG".equals(op) && values.isEmpty()) {
            throw new IllegalArgumentException("AVG of empty collection");
        }
        if (("MIN".equals(op) || "MAX".equals(op)) && values.isEmpty()) {
            throw new IllegalArgumentException(op + " of empty collection");
        }
        if ("SUM".equals(op) && values.isEmpty()) {
            return 0.0d;
        }
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (Object v : values) {
            if (v == null) continue;
            if (v instanceof Boolean) {
                throw new IllegalArgumentException(op + "(boolean) is invalid");
            }
            if (v instanceof String s && !isParseableNumber(s)) {
                throw new IllegalArgumentException(op + "(string) is invalid");
            }
            double d = toDouble(v);
            sum += d;
            min = Math.min(min, d);
            max = Math.max(max, d);
            n++;
        }
        if (n == 0) {
            if ("SUM".equals(op)) return 0.0d;
            throw new IllegalArgumentException(op + " of empty collection");
        }
        return switch (op) {
            case "SUM" -> sum;
            case "AVG" -> sum / n;
            case "MIN" -> min;
            case "MAX" -> max;
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static Object evalDistinct(Map<String, Object> m, Map<String, Object> inputs) {
        Object of = evalNode(collectionArg(m), inputs);
        if (of instanceof Missing) return of;
        LinkedHashSet<Object> set = new LinkedHashSet<>();
        for (Object v : asList(of, "DISTINCT")) {
            if (v instanceof String s) set.add(s.toLowerCase(Locale.ROOT));
            else set.add(v);
        }
        return new ArrayList<>(set);
    }

    private static Object evalAnyAll(boolean any, Map<String, Object> m, Map<String, Object> inputs) {
        Object of = evalNode(collectionArg(m), inputs);
        if (of instanceof Missing) return of;
        List<?> rows = asList(of, any ? "ANY" : "ALL");
        Object where = m.get("where");
        if (rows.isEmpty()) {
            return !any; // ANY empty=false, ALL empty=true
        }
        for (Object rowObj : rows) {
            boolean ok;
            if (where != null && rowObj instanceof Map<?, ?>) {
                Object pred = evalNode(where, bindRow(inputs, rowObj));
                if (pred instanceof Missing) {
                    ok = false;
                } else {
                    ok = toBoolean(pred);
                }
            } else if (where != null && !(rowObj instanceof Map<?, ?>)) {
                // scalar collection: bind as FIELD "_value" via synthetic row
                Map<String, Object> syn = new LinkedHashMap<>();
                syn.put("_value", rowObj);
                syn.put("value", rowObj);
                Object pred = evalNode(where, bindRow(inputs, syn));
                ok = !(pred instanceof Missing) && toBoolean(pred);
            } else {
                ok = toBoolean(rowObj);
            }
            if (any && ok) return true;
            if (!any && !ok) return false;
        }
        return !any;
    }

    private static Object evalFlatten(Map<String, Object> m, Map<String, Object> inputs) {
        Object of = evalNode(collectionArg(m), inputs);
        if (of instanceof Missing) return of;
        List<Object> out = new ArrayList<>();
        for (Object el : asList(of, "FLATTEN")) {
            if (el instanceof List<?> inner) out.addAll(inner);
            else if (el != null) out.add(el);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object evalTrailingWindow(Map<String, Object> m, Map<String, Object> inputs) {
        Object from = evalNode(sourceArg(m), inputs);
        if (from instanceof Missing) return from;
        Object asOfVal = m.get("asOf") == null ? evalAsOf(inputs) : evalNode(m.get("asOf"), inputs);
        if (asOfVal instanceof Missing) return asOfVal;
        YearMonth asOf = asOfVal instanceof YearMonth ym ? ym : parseYearMonth(asOfVal);
        int windowMonths = m.get("windowMonths") == null ? 6 : (int) toDouble(m.get("windowMonths"));
        if (windowMonths < 1) throw new IllegalArgumentException("windowMonths must be >= 1");
        String dateField = String.valueOf(m.getOrDefault("dateField", "month")).trim();
        YearMonth earliest = asOf.minusMonths(windowMonths - 1L);
        List<?> rows = asRecordList(from, "TRAILING_WINDOW");
        List<Object> out = new ArrayList<>();
        for (Object rowObj : rows) {
            if (!(rowObj instanceof Map<?, ?> rowRaw)) continue;
            Map<String, Object> row = (Map<String, Object>) rowRaw;
            Object dateRaw = firstPresent(row, dateField, "YearMonth", "month", "observationMonth", "period");
            if (dateRaw == null) continue;
            YearMonth ym = parseYearMonth(dateRaw);
            if (ym.isBefore(earliest) || ym.isAfter(asOf)) continue;
            out.add(rowObj);
        }
        return out;
    }

    private static Map<String, Object> bindRow(Map<String, Object> inputs, Object rowObj) {
        Map<String, Object> bound = new LinkedHashMap<>(inputs);
        bound.put(INPUT_CURRENT_ROW, rowObj);
        return bound;
    }

    private static Object sourceArg(Map<String, Object> m) {
        if (m.get("from") != null) return m.get("from");
        if (m.get("source") != null) return m.get("source");
        if (m.get("of") != null) return m.get("of");
        throw new IllegalArgumentException("Collection op requires from/source/of");
    }

    private static Object collectionArg(Map<String, Object> m) {
        if (m.get("of") != null) return m.get("of");
        if (m.get("from") != null) return m.get("from");
        if (m.get("source") != null) return m.get("source");
        throw new IllegalArgumentException("Collection aggregation requires of/from/source");
    }

    private static String fieldName(Map<String, Object> m) {
        Object f = m.get("field");
        if (f == null) f = m.get("name");
        return f == null ? "" : String.valueOf(f).trim();
    }

    private static List<?> asRecordList(Object v, String op) {
        if (v instanceof List<?> list) return list;
        if (v instanceof Map<?, ?> single) return List.of(single);
        throw new IllegalArgumentException(op + " requires COLLECTION<RECORD>");
    }

    private static List<?> asList(Object v, String op) {
        if (v instanceof List<?> list) return list;
        if (v instanceof Map<?, ?> single) return List.of(single);
        throw new IllegalArgumentException(op + " requires a collection");
    }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0.0d;
        if (v instanceof String s) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if ("true".equals(t) || "yes".equals(t) || "y".equals(t)) return true;
            if ("false".equals(t) || "no".equals(t) || "n".equals(t) || t.isEmpty()) return false;
        }
        return v != null;
    }

    private static boolean isNumericValue(Object v) {
        if (v instanceof Number) return true;
        if (v instanceof Boolean) return false;
        if (v instanceof String s) return isParseableNumber(s);
        return false;
    }

    private static boolean isParseableNumber(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String typeLabel(Object v) {
        if (v == null) return "null";
        if (v instanceof Number) return "NUMBER";
        if (v instanceof Boolean) return "BOOLEAN";
        if (v instanceof String) return "STRING";
        if (v instanceof List<?>) return "COLLECTION";
        if (v instanceof Map<?, ?>) return "RECORD";
        return v.getClass().getSimpleName();
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
        return validate(expr, allowedCanonicalIds, null);
    }

    public static List<String> validate(
            Map<String, Object> expr,
            Set<String> allowedCanonicalIds,
            Map<String, SafeDerivedTypeModel.ExprType> rowSchema) {
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
        if (rowSchema != null && !rowSchema.isEmpty()) {
            errors.addAll(SafeDerivedTypeModel.validateTypesWithSchema(expr, rowSchema));
        } else {
            errors.addAll(SafeDerivedTypeModel.validateTypes(expr));
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
        if (!SUPPORTED_OPS.contains(op)) {
            throw new IllegalArgumentException("Unsupported op: " + op);
        }
        switch (op) {
            case "REF" -> {
                if (m.get("id") == null || String.valueOf(m.get("id")).isBlank()) {
                    throw new IllegalArgumentException("REF requires id");
                }
            }
            case "CONST" -> {
                if (!m.containsKey("value")) throw new IllegalArgumentException("CONST requires value");
            }
            case "ADD", "SUB", "MUL", "DIV", "GT", "GTE", "LT", "LTE", "EQ", "AND", "OR" -> {
                evalShape(m.get("left"));
                evalShape(m.get("right"));
            }
            case "NOT" -> evalShape(m.get("of") != null ? m.get("of") : m.get("expr"));
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
            case "FILTER" -> {
                evalShape(sourceArg(m));
                if (m.get("where") != null) evalShape(m.get("where"));
            }
            case "FIELD" -> {
                if (fieldName(m).isBlank()) throw new IllegalArgumentException("FIELD requires field/name");
                if (m.get("from") != null) evalShape(m.get("from"));
            }
            case "PROJECT" -> {
                if (fieldName(m).isBlank()) throw new IllegalArgumentException("PROJECT requires field");
                evalShape(sourceArg(m));
            }
            case "COUNT", "SUM", "AVG", "MIN", "MAX", "DISTINCT", "FLATTEN", "ANY", "ALL" -> {
                evalShape(collectionArg(m));
                if (("ANY".equals(op) || "ALL".equals(op)) && m.get("where") != null) {
                    evalShape(m.get("where"));
                }
            }
            case "TRAILING_WINDOW" -> {
                evalShape(sourceArg(m));
                if (m.get("asOf") != null) evalShape(m.get("asOf"));
            }
            default -> throw new IllegalArgumentException("Unsupported op: " + op);
        }
    }

    private record Missing(String reason) {}
}
