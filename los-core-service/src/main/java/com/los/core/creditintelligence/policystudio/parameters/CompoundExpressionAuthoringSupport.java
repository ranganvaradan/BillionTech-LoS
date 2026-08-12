package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * POLICY-STUDIO-COMPOUND-RULE-AUTHORING-P0 — generic ANY/ALL compound expression
 * authoring over existing PolicyDsl OR/AND trees. Not bureau-specific evaluation.
 */
public final class CompoundExpressionAuthoringSupport {

    public static final String KIND_GROUP = "COMPOUND_GROUP";
    public static final String COMBINATOR_ANY = "ANY";
    public static final String COMBINATOR_ALL = "ALL";

    /** Canonical NTC / thin-file fact used by golden bureau interpretation. */
    public static final String NTC_FACT = "bureau.status_ntc";
    public static final String BUREAU_SCORE = "bureau.score";

    private CompoundExpressionAuthoringSupport() {}

    public static boolean isGroupExpression(Map<String, Object> expr) {
        if (expr == null) return false;
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if (!"OR".equals(op) && !"AND".equals(op)) return false;
        Object args = expr.get("args");
        return args instanceof List<?> list && list.size() >= 2;
    }

    public static boolean looksLikeGroupModel(Map<String, Object> body) {
        if (body == null) return false;
        if (KIND_GROUP.equalsIgnoreCase(String.valueOf(body.getOrDefault("kind", "")))) return true;
        if ("COMPOUND_GROUP".equalsIgnoreCase(String.valueOf(body.getOrDefault("mode", "")))) return true;
        return body.get("conditions") instanceof List<?> list && !list.isEmpty()
                && (COMBINATOR_ANY.equalsIgnoreCase(String.valueOf(body.get("combinator")))
                || COMBINATOR_ALL.equalsIgnoreCase(String.valueOf(body.get("combinator")))
                || "OR".equalsIgnoreCase(String.valueOf(body.get("combinator")))
                || "AND".equalsIgnoreCase(String.valueOf(body.get("combinator"))));
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> toEditableModel(Map<String, Object> expr, Map<String, Object> meta) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", KIND_GROUP);
        String combinator = COMBINATOR_ANY;
        List<Map<String, Object>> conditions = new ArrayList<>();
        if (isGroupExpression(expr)) {
            String op = String.valueOf(expr.get("op")).toUpperCase(Locale.ROOT);
            combinator = "AND".equals(op) ? COMBINATOR_ALL : COMBINATOR_ANY;
            for (Object arg : (List<?>) expr.get("args")) {
                if (arg instanceof Map<?, ?> m) {
                    Map<String, Object> cond = conditionFromExpr((Map<String, Object>) m);
                    if (cond != null) conditions.add(cond);
                }
            }
        }
        out.put("combinator", combinator);
        out.put("conditions", conditions);
        out.put("complete", !conditions.isEmpty()
                && conditions.stream().allMatch(c -> c.get("parameterId") != null
                && c.get("operator") != null && c.get("value") != null));
        out.put("plainEnglish", businessSummary(combinator, conditions));
        out.put("previewLines", previewLines(combinator, conditions));
        out.put("failureTreatment", meta == null ? "Reject"
                : String.valueOf(meta.getOrDefault("failureTreatment",
                meta.getOrDefault("treatment", "Reject"))));
        return out;
    }

    public static Map<String, Object> buildExpression(String combinator, List<Map<String, Object>> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("Compound group requires at least one condition");
        }
        List<Object> args = new ArrayList<>();
        for (Map<String, Object> c : conditions) {
            args.add(conditionToExpr(c));
        }
        boolean all = COMBINATOR_ALL.equalsIgnoreCase(combinator)
                || "AND".equalsIgnoreCase(combinator);
        if (args.size() == 1) {
            @SuppressWarnings("unchecked")
            Map<String, Object> single = (Map<String, Object>) args.get(0);
            return single;
        }
        return all ? PolicyDsl.and(args.toArray()) : PolicyDsl.or(args.toArray());
    }

    public static String businessSummary(String combinator, List<Map<String, Object>> conditions) {
        if (conditions == null || conditions.isEmpty()) return "";
        boolean any = !COMBINATOR_ALL.equalsIgnoreCase(combinator)
                && !"AND".equalsIgnoreCase(combinator);
        String join = any ? " OR " : " AND ";
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> c : conditions) {
            parts.add(conditionDisplay(c));
        }
        return (any ? "Allow when ANY of these is true: " : "Allow when ALL of these are true: ")
                + String.join(join, parts);
    }

    public static List<String> previewLines(String combinator, List<Map<String, Object>> conditions) {
        List<String> lines = new ArrayList<>();
        boolean any = !COMBINATOR_ALL.equalsIgnoreCase(combinator)
                && !"AND".equalsIgnoreCase(combinator);
        lines.add(any
                ? "Allow when ANY of these conditions is true:"
                : "Allow when ALL of these conditions are true:");
        for (int i = 0; i < conditions.size(); i++) {
            String prefix = i == 0 ? "  " : (any ? "  OR " : "  AND ");
            lines.add(prefix + conditionDisplay(conditions.get(i)));
        }
        lines.add("Otherwise:");
        lines.add("  Reject");
        return lines;
    }

    public static String conditionDisplay(Map<String, Object> c) {
        if (c == null) return "?";
        String name = String.valueOf(c.getOrDefault("parameterName",
                friendly(String.valueOf(c.getOrDefault("parameterId", "Parameter")))));
        String op = String.valueOf(c.getOrDefault("operator", "?"));
        Object val = c.get("value");
        if (NTC_FACT.equals(c.get("parameterId")) && Boolean.TRUE.equals(val)) {
            return name + " = NTC";
        }
        if (Boolean.TRUE.equals(val) && ("is".equals(op) || "=".equals(op))) {
            return name + " is Yes";
        }
        return name + " " + op + " " + val;
    }

    public static Map<String, Object> conditionToExpr(Map<String, Object> c) {
        String paramId = String.valueOf(c.get("parameterId"));
        String op = String.valueOf(c.getOrDefault("operator", "="));
        Object value = c.get("value");
        boolean fact = NTC_FACT.equals(paramId)
                || "FACT".equalsIgnoreCase(String.valueOf(c.getOrDefault("leftKind", "")));
        Object left = fact ? PolicyDsl.fact(paramId) : PolicyDsl.metric(paramId);
        Object right = value instanceof Map<?, ?> ? value : Map.of("const", value == null ? false : value);
        return switch (op) {
            case ">", "GT" -> PolicyDsl.gt(left, right);
            case ">=", "GTE" -> PolicyDsl.gte(left, right);
            case "<", "LT" -> PolicyDsl.lt(left, right);
            case "<=", "LTE" -> PolicyDsl.lte(left, right);
            case "!=", "is not", "NE" -> PolicyDsl.ne(left, right);
            default -> PolicyDsl.eq(left, right);
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> conditionFromExpr(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) return null;
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if (List.of("OR", "AND", "IF", "IFF", "NOT").contains(op)) {
            return null; // nested groups handled separately when needed
        }
        Map<String, Object> left = asMap(expr.get("left"));
        Object rightRaw = expr.get("right");
        Object value = rightRaw;
        if (rightRaw instanceof Map<?, ?> rm) {
            Map<String, Object> right = (Map<String, Object>) rm;
            if (right.containsKey("const")) value = right.get("const");
        }
        String paramId;
        String leftKind;
        if (left.containsKey("fact")) {
            paramId = String.valueOf(left.get("fact"));
            leftKind = "FACT";
        } else if (left.containsKey("metric")) {
            paramId = String.valueOf(left.get("metric"));
            leftKind = "METRIC";
        } else {
            return null;
        }
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("parameterId", paramId);
        c.put("parameterName", friendlyParam(paramId));
        c.put("operator", toUiOp(op));
        c.put("value", value);
        c.put("leftKind", leftKind);
        c.put("valueControl", NTC_FACT.equals(paramId) ? AuthoringValueTypes.CONTROL_BOOLEAN
                : AuthoringValueTypes.CONTROL_NUMBER);
        return c;
    }

    public static String friendlyParam(String paramId) {
        if (NTC_FACT.equals(paramId)) return "Bureau status";
        if (BUREAU_SCORE.equals(paramId)) return "Bureau score";
        return friendly(paramId);
    }

    private static String friendly(String id) {
        if (id == null) return "Parameter";
        int i = id.lastIndexOf('.');
        return (i >= 0 ? id.substring(i + 1) : id).replace('_', ' ');
    }

    private static String toUiOp(String op) {
        return switch (op == null ? "" : op.toUpperCase(Locale.ROOT)) {
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "NE" -> "!=";
            default -> "=";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    /** Deep equality of condition lists for round-trip checks (order-sensitive). */
    public static boolean sameConditions(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Map<String, Object> x = a.get(i);
            Map<String, Object> y = b.get(i);
            if (!Objects.equals(String.valueOf(x.get("parameterId")), String.valueOf(y.get("parameterId")))) {
                return false;
            }
            if (!Objects.equals(String.valueOf(x.get("operator")), String.valueOf(y.get("operator")))) {
                return false;
            }
            if (!Objects.equals(normalizeVal(x.get("value")), normalizeVal(y.get("value")))) {
                return false;
            }
        }
        return true;
    }

    private static Object normalizeVal(Object v) {
        if (v instanceof Number n) {
            if (n.doubleValue() == Math.rint(n.doubleValue())) return n.longValue();
            return n.doubleValue();
        }
        return v;
    }
}
