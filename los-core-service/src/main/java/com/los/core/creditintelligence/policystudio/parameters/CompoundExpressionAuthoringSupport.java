package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * POLICY-STUDIO-AUTHORING-COMPLETENESS-GATE-1 — recursively composable ANY/ALL
 * groups over PolicyDsl AND/OR (+ leaf EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN).
 * Not bureau-specific evaluation logic.
 */
public final class CompoundExpressionAuthoringSupport {

    public static final String KIND_GROUP = "COMPOUND_GROUP";
    public static final String KIND_CONDITION = "CONDITION";
    public static final String COMBINATOR_ANY = "ANY";
    public static final String COMBINATOR_ALL = "ALL";

    public static final String NTC_FACT = "bureau.status_ntc";
    public static final String BUREAU_SCORE = "bureau.score";
    public static final String FOIR = "obligation.ratio";
    public static final String LTV = "collateral.ltv";
    public static final String BORROWER_TYPE = "application.borrower_type";
    public static final String INDUSTRY = "bureau.commercial.industry_type";

    private CompoundExpressionAuthoringSupport() {}

    public static boolean isGroupExpression(Map<String, Object> expr) {
        if (expr == null) return false;
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if (!"OR".equals(op) && !"AND".equals(op)) return false;
        Object args = expr.get("args");
        return args instanceof List<?> list && !list.isEmpty();
    }

    public static boolean looksLikeGroupModel(Map<String, Object> body) {
        if (body == null) return false;
        if (KIND_GROUP.equalsIgnoreCase(String.valueOf(body.getOrDefault("kind", "")))) return true;
        if ("COMPOUND_GROUP".equalsIgnoreCase(String.valueOf(body.getOrDefault("mode", "")))) return true;
        if (body.get("children") instanceof List<?> ch && !ch.isEmpty()) return true;
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
        if (expr == null || expr.isEmpty()) {
            out.put("combinator", COMBINATOR_ANY);
            out.put("children", List.of());
            out.put("conditions", List.of());
            out.put("complete", false);
            return out;
        }
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if ("AND".equals(op) || "OR".equals(op)) {
            String combinator = "AND".equals(op) ? COMBINATOR_ALL : COMBINATOR_ANY;
            List<Map<String, Object>> children = new ArrayList<>();
            Object args = expr.get("args");
            if (args instanceof List<?> list) {
                for (Object arg : list) {
                    if (arg instanceof Map<?, ?> m) {
                        children.add(nodeFromExpr((Map<String, Object>) m));
                    }
                }
            }
            out.put("combinator", combinator);
            out.put("children", children);
            out.put("conditions", flattenLeaves(children)); // backward-compat flat view
            out.put("complete", isCompleteNode(out));
            out.put("plainEnglish", businessSummary(out));
            out.put("previewLines", previewLines(out));
        } else {
            // Single leaf comparison — wrap as one-child group for Build
            Map<String, Object> leaf = nodeFromExpr(expr);
            out.put("combinator", COMBINATOR_ALL);
            out.put("children", List.of(leaf));
            out.put("conditions", List.of(leaf));
            out.put("complete", isCompleteNode(out));
            out.put("plainEnglish", businessSummary(out));
            out.put("previewLines", previewLines(out));
        }
        out.put("failureTreatment", meta == null ? "Reject"
                : String.valueOf(meta.getOrDefault("failureTreatment",
                meta.getOrDefault("treatment", "Reject"))));
        return out;
    }

    /** Build PolicyDsl expression from editable group model (children or flat conditions). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> buildExpression(Map<String, Object> model) {
        if (model == null) throw new IllegalArgumentException("Compound group model required");
        List<Map<String, Object>> children = childrenOf(model);
        String combinator = String.valueOf(model.getOrDefault("combinator", COMBINATOR_ANY));
        return buildExpression(combinator, children);
    }

    public static Map<String, Object> buildExpression(String combinator, List<Map<String, Object>> children) {
        if (children == null || children.isEmpty()) {
            throw new IllegalArgumentException("Compound group requires at least one condition");
        }
        List<Object> args = new ArrayList<>();
        for (Map<String, Object> child : children) {
            args.add(nodeToExpr(child));
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

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> childrenOf(Map<String, Object> model) {
        if (model == null) return List.of();
        if (model.get("children") instanceof List<?> ch && !ch.isEmpty()) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : ch) {
                if (o instanceof Map<?, ?> m) out.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
            return out;
        }
        if (model.get("conditions") instanceof List<?> conds) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : conds) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> c = new LinkedHashMap<>((Map<String, Object>) m);
                    if (c.get("kind") == null) c.put("kind", KIND_CONDITION);
                    out.add(c);
                }
            }
            return out;
        }
        return List.of();
    }

    public static Map<String, Object> nodeToExpr(Map<String, Object> node) {
        if (node == null) throw new IllegalArgumentException("Empty expression node");
        String kind = String.valueOf(node.getOrDefault("kind", KIND_CONDITION));
        if (KIND_GROUP.equalsIgnoreCase(kind)
                || node.get("children") instanceof List<?>
                || COMBINATOR_ANY.equalsIgnoreCase(String.valueOf(node.get("combinator")))
                || COMBINATOR_ALL.equalsIgnoreCase(String.valueOf(node.get("combinator")))) {
            return buildExpression(
                    String.valueOf(node.getOrDefault("combinator", COMBINATOR_ANY)),
                    childrenOf(node));
        }
        return conditionToExpr(node);
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> nodeFromExpr(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("kind", KIND_CONDITION);
            return empty;
        }
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if ("AND".equals(op) || "OR".equals(op)) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("kind", KIND_GROUP);
            group.put("combinator", "AND".equals(op) ? COMBINATOR_ALL : COMBINATOR_ANY);
            List<Map<String, Object>> children = new ArrayList<>();
            if (expr.get("args") instanceof List<?> args) {
                for (Object arg : args) {
                    if (arg instanceof Map<?, ?> m) {
                        children.add(nodeFromExpr((Map<String, Object>) m));
                    }
                }
            }
            group.put("children", children);
            return group;
        }
        Map<String, Object> leaf = conditionFromExpr(expr);
        if (leaf == null) {
            Map<String, Object> unknown = new LinkedHashMap<>();
            unknown.put("kind", KIND_CONDITION);
            unknown.put("unresolved", true);
            return unknown;
        }
        leaf.put("kind", KIND_CONDITION);
        return leaf;
    }

    public static Map<String, Object> conditionToExpr(Map<String, Object> c) {
        String paramId = String.valueOf(c.get("parameterId"));
        String op = String.valueOf(c.getOrDefault("operator", "="));
        boolean fact = NTC_FACT.equals(paramId)
                || "FACT".equalsIgnoreCase(String.valueOf(c.getOrDefault("leftKind", "")));
        Object left = fact ? PolicyDsl.fact(paramId) : PolicyDsl.metric(paramId);
        if ("in".equalsIgnoreCase(op) || "IN".equals(op)) {
            return PolicyDsl.in(left, listValues(c));
        }
        if ("not in".equalsIgnoreCase(op) || "NOT_IN".equalsIgnoreCase(op) || "notin".equalsIgnoreCase(op)) {
            return PolicyDsl.notIn(left, listValues(c));
        }
        Object value = c.get("value");
        Object right = value instanceof Map<?, ?> ? value : Map.of("const", value == null ? false : value);
        return switch (op) {
            case ">", "GT" -> PolicyDsl.gt(left, right);
            case ">=", "GTE" -> PolicyDsl.gte(left, right);
            case "<", "LT" -> PolicyDsl.lt(left, right);
            case "<=", "LTE" -> PolicyDsl.lte(left, right);
            case "!=", "is not", "NE" -> PolicyDsl.ne(left, right);
            case "is" -> PolicyDsl.eq(left, right);
            default -> PolicyDsl.eq(left, right);
        };
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> conditionFromExpr(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) return null;
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        if (List.of("OR", "AND", "IF", "IFF", "NOT").contains(op)) {
            return null;
        }
        Map<String, Object> left = asMap(expr.get("left") != null ? expr.get("left") : expr.get("arg"));
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
        c.put("kind", KIND_CONDITION);
        c.put("parameterId", paramId);
        c.put("parameterName", friendlyParam(paramId));
        c.put("leftKind", leftKind);
        c.put("valueControl", valueControlFor(paramId));
        if ("IN".equals(op) || "NOT_IN".equals(op)) {
            c.put("operator", "IN".equals(op) ? "in" : "not in");
            Object rawSet = expr.get("set") != null ? expr.get("set") : expr.get("right");
            List<Object> values = new ArrayList<>();
            if (rawSet instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> im && ((Map<?, ?>) im).containsKey("const")) {
                        values.add(((Map<?, ?>) im).get("const"));
                    } else {
                        values.add(item);
                    }
                }
            }
            c.put("values", values);
            c.put("value", values);
            return c;
        }
        Object rightRaw = expr.get("right");
        Object value = rightRaw;
        if (rightRaw instanceof Map<?, ?> rm) {
            Map<String, Object> right = (Map<String, Object>) rm;
            if (right.containsKey("const")) value = right.get("const");
        }
        c.put("operator", toUiOp(op));
        c.put("value", value);
        return c;
    }

    private static List<Object> listValues(Map<String, Object> c) {
        Object raw = c.get("values") != null ? c.get("values") : c.get("value");
        List<Object> out = new ArrayList<>();
        if (raw instanceof Collection<?> col) {
            out.addAll(col);
        } else if (raw != null) {
            String s = String.valueOf(raw);
            if (s.contains(",")) {
                for (String part : s.split(",")) {
                    if (!part.isBlank()) out.add(part.trim());
                }
            } else {
                out.add(raw);
            }
        }
        return out;
    }

    public static String businessSummary(Map<String, Object> model) {
        return summarizeNode(model, 0);
    }

    public static String businessSummary(String combinator, List<Map<String, Object>> conditions) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("kind", KIND_GROUP);
        model.put("combinator", combinator);
        model.put("children", conditions);
        return businessSummary(model);
    }

    public static List<String> previewLines(Map<String, Object> model) {
        List<String> lines = new ArrayList<>();
        lines.add("Allow when:");
        appendPreviewLines(model, lines, 1);
        lines.add("Otherwise:");
        lines.add("  Reject");
        return lines;
    }

    public static List<String> previewLines(String combinator, List<Map<String, Object>> conditions) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("kind", KIND_GROUP);
        model.put("combinator", combinator);
        model.put("children", conditions);
        return previewLines(model);
    }

    private static void appendPreviewLines(Map<String, Object> node, List<String> lines, int indent) {
        String pad = "  ".repeat(Math.max(0, indent));
        String kind = String.valueOf(node.getOrDefault("kind", KIND_CONDITION));
        if (KIND_GROUP.equalsIgnoreCase(kind) || node.get("children") instanceof List<?>) {
            boolean any = !COMBINATOR_ALL.equalsIgnoreCase(String.valueOf(node.get("combinator")))
                    && !"AND".equalsIgnoreCase(String.valueOf(node.get("combinator")));
            lines.add(pad + (any ? "ANY of:" : "ALL of:"));
            List<Map<String, Object>> children = childrenOf(node);
            for (int i = 0; i < children.size(); i++) {
                Map<String, Object> child = children.get(i);
                if (KIND_GROUP.equalsIgnoreCase(String.valueOf(child.get("kind")))
                        || child.get("children") instanceof List<?>) {
                    if (i > 0) lines.add(pad + "  " + (any ? "OR" : "AND"));
                    appendPreviewLines(child, lines, indent + 1);
                } else {
                    String prefix = i == 0 ? pad + "  " : pad + (any ? "  OR " : "  AND ");
                    lines.add(prefix + conditionDisplay(child));
                }
            }
        } else {
            lines.add(pad + conditionDisplay(node));
        }
    }

    private static String summarizeNode(Map<String, Object> node, int depth) {
        String kind = String.valueOf(node.getOrDefault("kind", KIND_CONDITION));
        if (KIND_GROUP.equalsIgnoreCase(kind) || node.get("children") instanceof List<?>) {
            boolean any = !COMBINATOR_ALL.equalsIgnoreCase(String.valueOf(node.get("combinator")))
                    && !"AND".equalsIgnoreCase(String.valueOf(node.get("combinator")));
            List<String> parts = new ArrayList<>();
            for (Map<String, Object> child : childrenOf(node)) {
                String inner = summarizeNode(child, depth + 1);
                if (KIND_GROUP.equalsIgnoreCase(String.valueOf(child.get("kind")))) {
                    parts.add("(" + inner + ")");
                } else {
                    parts.add(inner);
                }
            }
            return String.join(any ? " OR " : " AND ", parts);
        }
        return conditionDisplay(node);
    }

    public static String conditionDisplay(Map<String, Object> c) {
        if (c == null) return "?";
        String name = String.valueOf(c.getOrDefault("parameterName",
                friendlyParam(String.valueOf(c.getOrDefault("parameterId", "Parameter")))));
        String op = String.valueOf(c.getOrDefault("operator", "?"));
        if ("in".equalsIgnoreCase(op) || "not in".equalsIgnoreCase(op)) {
            Object vals = c.get("values") != null ? c.get("values") : c.get("value");
            return name + " " + op.toLowerCase(Locale.ROOT) + " " + vals;
        }
        Object val = c.get("value");
        if (NTC_FACT.equals(c.get("parameterId")) && Boolean.TRUE.equals(val)) {
            return name + " = NTC";
        }
        if (Boolean.TRUE.equals(val) && ("is".equals(op) || "=".equals(op))) {
            return name + " is Yes";
        }
        return name + " " + op + " " + val;
    }

    public static String friendlyParam(String paramId) {
        if (NTC_FACT.equals(paramId)) return "Bureau status";
        if (BUREAU_SCORE.equals(paramId)) return "Bureau score";
        if (FOIR.equals(paramId)) return "FOIR";
        if (LTV.equals(paramId)) return "LTV";
        if (BORROWER_TYPE.equals(paramId)) return "Borrower type";
        if (INDUSTRY.equals(paramId)) return "Industry type";
        if (paramId == null) return "Parameter";
        int i = paramId.lastIndexOf('.');
        return (i >= 0 ? paramId.substring(i + 1) : paramId).replace('_', ' ');
    }

    public static String valueControlFor(String paramId) {
        if (NTC_FACT.equals(paramId)) return AuthoringValueTypes.CONTROL_BOOLEAN;
        if (BORROWER_TYPE.equals(paramId)) return AuthoringValueTypes.CONTROL_ENUM;
        if (FOIR.equals(paramId) || LTV.equals(paramId)) return AuthoringValueTypes.CONTROL_PERCENTAGE;
        if (INDUSTRY.equals(paramId)) return AuthoringValueTypes.CONTROL_STRING;
        return AuthoringValueTypes.CONTROL_NUMBER;
    }

    /** Operators allowed for a parameter (type-safe). */
    public static List<String> operatorsForParameter(String paramId) {
        String control = valueControlFor(paramId);
        if (AuthoringValueTypes.CONTROL_BOOLEAN.equals(control) || NTC_FACT.equals(paramId)) {
            return List.of("is", "is not", "=", "!=");
        }
        if (AuthoringValueTypes.CONTROL_ENUM.equals(control)
                || AuthoringValueTypes.CONTROL_STRING.equals(control)) {
            return List.of("=", "!=", "in", "not in");
        }
        return List.of(">", ">=", "<", "<=", "=", "!=", "in", "not in");
    }

    public static boolean operatorAllowed(String paramId, String operator) {
        if (operator == null) return false;
        String op = operator.trim().toLowerCase(Locale.ROOT);
        if ("is true".equals(op) || "is false".equals(op)) {
            return AuthoringValueTypes.CONTROL_BOOLEAN.equals(valueControlFor(paramId))
                    || NTC_FACT.equals(paramId);
        }
        return operatorsForParameter(paramId).stream()
                .anyMatch(a -> a.equalsIgnoreCase(op));
    }

    public static boolean isCompleteNode(Map<String, Object> node) {
        if (node == null) return false;
        String kind = String.valueOf(node.getOrDefault("kind", KIND_CONDITION));
        if (KIND_GROUP.equalsIgnoreCase(kind) || node.get("children") instanceof List<?>) {
            List<Map<String, Object>> children = childrenOf(node);
            return !children.isEmpty() && children.stream().allMatch(CompoundExpressionAuthoringSupport::isCompleteNode);
        }
        if (Boolean.TRUE.equals(node.get("unresolved"))) return false;
        if (node.get("parameterId") == null || node.get("operator") == null) return false;
        String op = String.valueOf(node.get("operator"));
        if ("in".equalsIgnoreCase(op) || "not in".equalsIgnoreCase(op)) {
            Object vals = node.get("values") != null ? node.get("values") : node.get("value");
            return vals instanceof Collection<?> col ? !col.isEmpty() : vals != null;
        }
        return node.get("value") != null;
    }

    public static boolean sameTree(Map<String, Object> a, Map<String, Object> b) {
        if (a == null || b == null) return a == b;
        Map<String, Object> ea = normalizeExpr(a);
        Map<String, Object> eb = normalizeExpr(b);
        return Objects.equals(ea, eb);
    }

    /** Normalize for structural compare (build from editable if needed). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> normalizeExpr(Map<String, Object> exprOrModel) {
        if (exprOrModel == null) return Map.of();
        if (looksLikeGroupModel(exprOrModel) && !(exprOrModel.get("op") instanceof String)) {
            return buildExpression(exprOrModel);
        }
        if (exprOrModel.get("op") != null) {
            return deepCopy(exprOrModel);
        }
        return buildExpression(exprOrModel);
    }

    /** Collect metric/fact paths referenced by an expression. */
    public static List<String> referencedPaths(Map<String, Object> expr) {
        List<String> out = new ArrayList<>();
        collectPaths(expr, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectPaths(Object node, List<String> out) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        if (m.get("metric") != null) out.add(String.valueOf(m.get("metric")));
        if (m.get("fact") != null) out.add(String.valueOf(m.get("fact")));
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) collectPaths(v, out);
        }
        if (m.get("args") instanceof List<?> args) {
            for (Object a : args) collectPaths(a, out);
        }
    }

    private static List<Map<String, Object>> flattenLeaves(List<Map<String, Object>> children) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : children) {
            if (KIND_GROUP.equalsIgnoreCase(String.valueOf(c.get("kind")))
                    || c.get("children") instanceof List<?>) {
                out.addAll(flattenLeaves(childrenOf(c)));
            } else {
                out.add(c);
            }
        }
        return out;
    }

    private static String toUiOp(String op) {
        return switch (op == null ? "" : op.toUpperCase(Locale.ROOT)) {
            case "GT" -> ">";
            case "GTE" -> ">=";
            case "LT" -> "<";
            case "LTE" -> "<=";
            case "NE" -> "!=";
            case "IN" -> "in";
            case "NOT_IN" -> "not in";
            default -> "=";
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> in) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) {
                out.put(e.getKey(), deepCopy((Map<String, Object>) m));
            } else if (v instanceof List<?> list) {
                List<Object> copy = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> im) copy.add(deepCopy((Map<String, Object>) im));
                    else copy.add(item);
                }
                out.put(e.getKey(), copy);
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    /** Deep equality of condition lists for round-trip checks (order-sensitive). */
    public static boolean sameConditions(List<Map<String, Object>> a, List<Map<String, Object>> b) {
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Map<String, Object> x = a.get(i);
            Map<String, Object> y = b.get(i);
            if (KIND_GROUP.equalsIgnoreCase(String.valueOf(x.get("kind")))
                    || KIND_GROUP.equalsIgnoreCase(String.valueOf(y.get("kind")))) {
                if (!sameTree(
                        Map.of("kind", KIND_GROUP, "combinator", x.get("combinator"), "children", childrenOf(x)),
                        Map.of("kind", KIND_GROUP, "combinator", y.get("combinator"), "children", childrenOf(y)))) {
                    return false;
                }
                continue;
            }
            if (!Objects.equals(String.valueOf(x.get("parameterId")), String.valueOf(y.get("parameterId")))) {
                return false;
            }
            if (!Objects.equals(String.valueOf(x.get("operator")), String.valueOf(y.get("operator")))) {
                return false;
            }
            Object xv = x.get("values") != null ? x.get("values") : x.get("value");
            Object yv = y.get("values") != null ? y.get("values") : y.get("value");
            if (!Objects.equals(normalizeVal(xv), normalizeVal(yv))) {
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
        if (v instanceof Collection<?> col) {
            List<Object> out = new ArrayList<>();
            for (Object o : col) out.add(normalizeVal(o));
            return out;
        }
        return v;
    }
}
