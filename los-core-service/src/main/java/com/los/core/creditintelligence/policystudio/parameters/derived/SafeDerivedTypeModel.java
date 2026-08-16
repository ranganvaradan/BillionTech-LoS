package com.los.core.creditintelligence.policystudio.parameters.derived;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Minimal typed expression model for SafeDerived Wave-2 validation.
 * Does not replace the application/GACAT type model — evaluator-local only.
 */
public final class SafeDerivedTypeModel {

    public enum ExprType {
        NUMBER,
        INTEGER,
        DECIMAL,
        MONEY,
        BOOLEAN,
        STRING,
        ENUM,
        DATE,
        YEAR_MONTH,
        COLLECTION,
        RECORD,
        UNKNOWN
    }

    private SafeDerivedTypeModel() {}

    public static ExprType fromUnitHint(String unit) {
        if (unit == null || unit.isBlank()) return ExprType.UNKNOWN;
        String u = unit.toUpperCase(Locale.ROOT);
        if (u.contains("INR") || u.contains("MONEY") || u.contains("AMOUNT") || u.contains("RUPEE")) {
            return ExprType.MONEY;
        }
        if (u.contains("PERCENT") || u.contains("%")) return ExprType.DECIMAL;
        if (u.contains("MONTH") && !u.contains("AMOUNT")) return ExprType.YEAR_MONTH;
        if (u.contains("DATE") || u.contains("DAY")) return ExprType.DATE;
        if (u.contains("COUNT") || u.contains("INTEGER") || u.equals("N")) return ExprType.INTEGER;
        if (u.contains("HISTORY") || u.contains("COLLECTION") || u.contains("LIST")) return ExprType.COLLECTION;
        if (u.contains("BOOL")) return ExprType.BOOLEAN;
        return ExprType.UNKNOWN;
    }

    public static ExprType ofConst(Object value, Object valueTypeHint) {
        if (valueTypeHint != null) {
            ExprType t = parseHint(String.valueOf(valueTypeHint));
            if (t != ExprType.UNKNOWN) return t;
        }
        if (value instanceof Boolean) return ExprType.BOOLEAN;
        if (value instanceof Integer || value instanceof Long) return ExprType.INTEGER;
        if (value instanceof Number) return ExprType.DECIMAL;
        if (value instanceof String) return ExprType.STRING;
        if (value instanceof List<?>) return ExprType.COLLECTION;
        if (value instanceof Map<?, ?>) return ExprType.RECORD;
        return ExprType.UNKNOWN;
    }

    public static ExprType parseHint(String hint) {
        if (hint == null || hint.isBlank()) return ExprType.UNKNOWN;
        String h = hint.trim().toUpperCase(Locale.ROOT);
        if ("MONTH".equals(h) || "YEARMONTH".equals(h) || "YEAR_MONTH".equals(h)) {
            return ExprType.YEAR_MONTH;
        }
        try {
            return ExprType.valueOf(h);
        } catch (Exception ignored) {
            return fromUnitHint(hint);
        }
    }

    public static boolean isNumeric(ExprType t) {
        return t == ExprType.NUMBER || t == ExprType.INTEGER || t == ExprType.DECIMAL
                || t == ExprType.MONEY || t == ExprType.UNKNOWN;
    }

    public static boolean isTemporal(ExprType t) {
        return t == ExprType.DATE || t == ExprType.YEAR_MONTH;
    }

    /**
     * Static reject rules for obviously invalid ops. Returns human-readable errors.
     */
    @SuppressWarnings("unchecked")
    public static List<String> validateTypes(Map<String, Object> expr) {
        List<String> errors = new ArrayList<>();
        if (expr == null || expr.isEmpty()) return errors;
        try {
            infer(expr, errors, Map.of());
        } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static ExprType infer(Object node, List<String> errors, Map<String, ExprType> rowFields) {
        if (!(node instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Expression node must be an object");
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        return switch (op) {
            case "REF" -> {
                Object vt = m.get("valueType");
                Object unit = m.get("unit");
                if (vt != null) yield parseHint(String.valueOf(vt));
                if (unit != null) yield fromUnitHint(String.valueOf(unit));
                yield ExprType.UNKNOWN;
            }
            case "CONST" -> ofConst(m.get("value"), m.get("valueType") != null ? m.get("valueType") : m.get("type"));
            case "EVAL_AS_OF" -> ExprType.YEAR_MONTH;
            case "ADD", "SUB", "MUL", "DIV" -> {
                ExprType l = infer(m.get("left"), errors, rowFields);
                ExprType r = infer(m.get("right"), errors, rowFields);
                if (isTemporal(l) && isNumeric(r) && r == ExprType.MONEY
                        || isTemporal(r) && isNumeric(l) && l == ExprType.MONEY
                        || (l == ExprType.MONEY && isTemporal(r))
                        || (r == ExprType.MONEY && isTemporal(l))
                        || (l == ExprType.MONEY && r == ExprType.YEAR_MONTH)
                        || (r == ExprType.MONEY && l == ExprType.YEAR_MONTH)) {
                    errors.add("ADD/arithmetic incompatible types: " + l + " and " + r);
                }
                if ((isTemporal(l) && r == ExprType.MONEY) || (isTemporal(r) && l == ExprType.MONEY)) {
                    errors.add("Cannot combine DATE/MONTH with MONEY in arithmetic");
                }
                yield ExprType.NUMBER;
            }
            case "GT", "GTE", "LT", "LTE" -> {
                ExprType l = infer(m.get("left"), errors, rowFields);
                ExprType r = infer(m.get("right"), errors, rowFields);
                if ((isTemporal(l) && r == ExprType.MONEY) || (isTemporal(r) && l == ExprType.MONEY)
                        || (isTemporal(l) && isNumeric(r) && r == ExprType.MONEY)
                        || (l == ExprType.MONEY && isTemporal(r))) {
                    errors.add("Cannot compare DATE/MONTH to MONEY");
                }
                if ((isTemporal(l) && isNumeric(r) && !isTemporal(r) && r != ExprType.UNKNOWN && r != ExprType.INTEGER)
                        || (isTemporal(r) && isNumeric(l) && !isTemporal(l) && l != ExprType.UNKNOWN && l != ExprType.INTEGER)) {
                    // allow YEAR_MONTH vs integer months only when both temporal or both numeric
                }
                if ((isTemporal(l) && r == ExprType.MONEY) || (l == ExprType.DATE && r == ExprType.MONEY)) {
                    errors.add("numeric compare to DATE with MONEY is invalid");
                }
                yield ExprType.BOOLEAN;
            }
            case "EQ" -> {
                infer(m.get("left"), errors, rowFields);
                infer(m.get("right"), errors, rowFields);
                yield ExprType.BOOLEAN;
            }
            case "AND", "OR" -> {
                ExprType l = infer(m.get("left"), errors, rowFields);
                ExprType r = infer(m.get("right"), errors, rowFields);
                if (l == ExprType.MONEY || r == ExprType.MONEY) {
                    errors.add("AND/OR requires boolean operands");
                }
                yield ExprType.BOOLEAN;
            }
            case "NOT" -> {
                infer(m.get("of") != null ? m.get("of") : m.get("expr"), errors, rowFields);
                yield ExprType.BOOLEAN;
            }
            case "IF" -> {
                infer(m.get("when"), errors, rowFields);
                ExprType t = infer(m.get("then"), errors, rowFields);
                infer(m.get("else"), errors, rowFields);
                yield t;
            }
            case "FIELD" -> {
                String name = fieldName(m);
                if (name.isBlank()) errors.add("FIELD requires field name");
                if (!rowFields.isEmpty() && !rowFields.containsKey(name.toLowerCase(Locale.ROOT))
                        && m.get("from") == null) {
                    // unknown field in declared schema context
                    if (Boolean.TRUE.equals(m.get("requireKnownField"))) {
                        errors.add("Unknown field: " + name);
                    }
                }
                if (m.get("from") != null) infer(m.get("from"), errors, rowFields);
                Object vt = m.get("valueType");
                yield vt != null ? parseHint(String.valueOf(vt)) : ExprType.UNKNOWN;
            }
            case "FILTER", "TRAILING_WINDOW", "FLATTEN", "DISTINCT", "PROJECT" -> {
                if ("FILTER".equals(op) || "TRAILING_WINDOW".equals(op) || "PROJECT".equals(op)) {
                    Object from = m.get("from") != null ? m.get("from") : m.get("source");
                    if (from == null) errors.add(op + " requires from/source");
                    else infer(from, errors, rowFields);
                }
                if ("FILTER".equals(op) && m.get("where") != null) {
                    infer(m.get("where"), errors, rowFields);
                }
                if ("PROJECT".equals(op)) {
                    if (fieldName(m).isBlank()) errors.add("PROJECT requires field");
                }
                if ("DISTINCT".equals(op) || "FLATTEN".equals(op)) {
                    Object of = m.get("of") != null ? m.get("of") : m.get("from");
                    if (of == null) errors.add(op + " requires of/from");
                    else infer(of, errors, rowFields);
                }
                yield ExprType.COLLECTION;
            }
            case "COUNT" -> {
                Object of = collectionArg(m);
                if (of == null) errors.add("COUNT requires of/from");
                else infer(of, errors, rowFields);
                yield ExprType.INTEGER;
            }
            case "SUM", "AVG", "MIN", "MAX" -> {
                Object of = collectionArg(m);
                if (of == null) errors.add(op + " requires of/from");
                else {
                    ExprType inner = infer(of, errors, rowFields);
                    if (inner == ExprType.BOOLEAN) {
                        errors.add(op + "(boolean) is invalid");
                    }
                    if (inner == ExprType.STRING || inner == ExprType.ENUM) {
                        errors.add(op + "(" + inner.name().toLowerCase(Locale.ROOT) + ") is invalid");
                    }
                    // CONST list of strings
                    if (of instanceof Map<?, ?> om) {
                        Map<String, Object> om2 = (Map<String, Object>) om;
                        if ("CONST".equalsIgnoreCase(String.valueOf(om2.get("op")))
                                && om2.get("value") instanceof List<?> list) {
                            for (Object el : list) {
                                if (el instanceof String) {
                                    errors.add(op + "(string) is invalid");
                                    break;
                                }
                                if (el instanceof Boolean) {
                                    errors.add(op + "(boolean) is invalid");
                                    break;
                                }
                                if (el instanceof Enum || (el instanceof String s && looksLikeEnum(s)
                                        && "AVG".equals(op) && m.get("valueType") != null
                                        && "ENUM".equalsIgnoreCase(String.valueOf(m.get("valueType"))))) {
                                    errors.add("AVG(enum) is invalid");
                                    break;
                                }
                            }
                        }
                        if ("CONST".equalsIgnoreCase(String.valueOf(om2.get("op")))) {
                            ExprType ct = ofConst(om2.get("value"), om2.get("valueType"));
                            if (ct == ExprType.ENUM && "AVG".equals(op)) {
                                errors.add("AVG(enum) is invalid");
                            }
                        }
                    }
                }
                yield "COUNT".equals(op) ? ExprType.INTEGER : ExprType.NUMBER;
            }
            case "ANY", "ALL" -> {
                Object of = collectionArg(m);
                if (of == null) errors.add(op + " requires of/from");
                else infer(of, errors, rowFields);
                if (m.get("where") != null) infer(m.get("where"), errors, rowFields);
                yield ExprType.BOOLEAN;
            }
            case "MONTHS_SINCE_LAST_MATCH", "COUNT_PERIODS_MATCHING" -> {
                if (m.get("history") != null) infer(m.get("history"), errors, rowFields);
                if (m.get("asOf") != null) infer(m.get("asOf"), errors, rowFields);
                yield ExprType.INTEGER;
            }
            default -> ExprType.UNKNOWN;
        };
    }

    private static boolean looksLikeEnum(String s) {
        return s.equals(s.toUpperCase(Locale.ROOT)) && s.matches("[A-Z][A-Z0-9_]+");
    }

    private static String fieldName(Map<String, Object> m) {
        Object f = m.get("field");
        if (f == null) f = m.get("name");
        return f == null ? "" : String.valueOf(f).trim();
    }

    private static Object collectionArg(Map<String, Object> m) {
        if (m.get("of") != null) return m.get("of");
        if (m.get("from") != null) return m.get("from");
        if (m.get("source") != null) return m.get("source");
        return null;
    }

    /** Build a schema map (lowercase field → type) for tests / validation context. */
    public static Map<String, ExprType> schema(Map<String, String> fieldToType) {
        Map<String, ExprType> out = new LinkedHashMap<>();
        if (fieldToType == null) return out;
        for (Map.Entry<String, String> e : fieldToType.entrySet()) {
            out.put(e.getKey().toLowerCase(Locale.ROOT), parseHint(e.getValue()));
        }
        return out;
    }

    public static List<String> validateTypesWithSchema(Map<String, Object> expr, Map<String, ExprType> rowSchema) {
        List<String> errors = new ArrayList<>();
        if (expr == null) return errors;
        try {
            infer(expr, errors, rowSchema == null ? Map.of() : rowSchema);
            // second pass: walk FILTER/PROJECT fields against schema when requireKnownField or schema present
            if (rowSchema != null && !rowSchema.isEmpty()) {
                walkUnknownFields(expr, rowSchema, errors);
            }
        } catch (IllegalArgumentException ex) {
            errors.add(ex.getMessage());
        }
        return errors;
    }

    @SuppressWarnings("unchecked")
    private static void walkUnknownFields(Object node, Map<String, ExprType> schema, List<String> errors) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        String op = String.valueOf(m.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        if ("FIELD".equals(op) || "PROJECT".equals(op)) {
            String name = fieldName(m).toLowerCase(Locale.ROOT);
            if (!name.isBlank() && !schema.containsKey(name)) {
                errors.add("Unknown field: " + fieldName(m));
            }
        }
        for (Object v : m.values()) {
            if (v instanceof Map || v instanceof List) {
                if (v instanceof List<?> list) {
                    for (Object i : list) walkUnknownFields(i, schema, errors);
                } else {
                    walkUnknownFields(v, schema, errors);
                }
            }
        }
    }

    public static final Set<String> NUMERIC_AGG_OPS = Set.of("SUM", "AVG", "MIN", "MAX");
}
