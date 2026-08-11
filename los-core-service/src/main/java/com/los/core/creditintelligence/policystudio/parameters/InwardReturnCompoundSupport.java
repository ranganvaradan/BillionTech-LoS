package com.los.core.creditintelligence.policystudio.parameters;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * POLICY-RULE-EDITOR-ROUNDTRIP-P0 — compound inward-return IF rule helpers.
 * Surgical operator / branch edits without rebuilding from free-text PE.
 */
public final class InwardReturnCompoundSupport {

    public static final String TXN_METRIC = "banking.transaction_count.total_3m";
    public static final String RATIO_METRIC = "banking.inward_return.ratio_3m";
    public static final String COUNT_METRIC = "banking.inward_return.count_3m";
    public static final String OPTION_RATIO = "treat_100_as_ratio_branch";
    public static final String OPTION_COUNT = "treat_100_as_count_branch";

    private InwardReturnCompoundSupport() {}

    public static boolean isIfExpression(Map<String, Object> expr) {
        if (expr == null) return false;
        String op = String.valueOf(expr.getOrDefault("op", "")).toUpperCase(Locale.ROOT);
        return "IF".equals(op) || "IFF".equals(op);
    }

    public static boolean looksLikeInwardReturnCompound(CiPolicyRuleCandidate r) {
        if (r == null) return false;
        String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
        if (sys.contains("INWARD_RETURN") || sys.contains("BANK_INWARD")) return true;
        Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
        String title = String.valueOf(meta.getOrDefault("businessTitle", "")).toLowerCase(Locale.ROOT);
        if (title.contains("inward") && title.contains("return")) return true;
        return isIfExpression(r.getExpression()) && expressionMentions(r.getExpression(), "inward_return");
    }

    public static int branchOutcomeCount(Map<String, Object> expr) {
        if (!isIfExpression(expr)) return 0;
        int n = 0;
        if (expr.get("then") != null) n++;
        if (expr.get("else") != null || expr.get("elseExpr") != null) n++;
        return n;
    }

    /** Business-facing structured model for Build / Edit (no JSON required). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toEditableModel(Map<String, Object> expr, Map<String, Object> meta) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "COMPOUND_IF");
        out.put("periodMonths", 3);
        out.put("periodLabel", "Last 3 months");
        out.put("txnMetricId", TXN_METRIC);
        out.put("txnMetricName", "Transaction count");
        out.put("failureTreatment", meta == null ? "Reject"
                : String.valueOf(meta.getOrDefault("failureTreatment", "REJECT")));
        List<Map<String, Object>> branches = new ArrayList<>();
        if (!isIfExpression(expr)) {
            out.put("branches", branches);
            out.put("complete", false);
            return out;
        }
        Map<String, Object> cond = asMap(expr.get("condition") != null ? expr.get("condition") : expr.get("when"));
        Map<String, Object> thenN = asMap(expr.get("then"));
        Map<String, Object> elseN = asMap(expr.get("else") != null ? expr.get("else") : expr.get("elseExpr"));

        String condOp = opOf(cond);
        Object condVal = cond.get("right");
        Map<String, Object> b1 = new LinkedHashMap<>();
        b1.put("label", "Branch 1");
        b1.put("whenParameterId", TXN_METRIC);
        b1.put("whenParameterName", "Transaction count");
        b1.put("whenOperator", toUiOp(condOp));
        b1.put("whenValue", condVal);
        b1.put("thenParameterId", metricOf(thenN));
        b1.put("thenParameterName", friendlyMetric(metricOf(thenN)));
        b1.put("thenOperator", toUiOp(opOf(thenN)));
        b1.put("thenValue", thenN.get("right"));
        b1.put("thenUnit", RATIO_METRIC.equals(metricOf(thenN)) ? "%" : "count");
        branches.add(b1);

        // Complementary branch for the ELSE outcome
        String elseWhenOp = complementaryUiOp(toUiOp(condOp));
        Map<String, Object> b2 = new LinkedHashMap<>();
        b2.put("label", "Branch 2");
        b2.put("whenParameterId", TXN_METRIC);
        b2.put("whenParameterName", "Transaction count");
        b2.put("whenOperator", elseWhenOp);
        b2.put("whenValue", condVal);
        b2.put("thenParameterId", metricOf(elseN));
        b2.put("thenParameterName", friendlyMetric(metricOf(elseN)));
        b2.put("thenOperator", toUiOp(opOf(elseN)));
        b2.put("thenValue", elseN.get("right"));
        b2.put("thenUnit", RATIO_METRIC.equals(metricOf(elseN)) ? "%" : "count");
        branches.add(b2);

        out.put("branches", branches);
        out.put("complete", branches.size() == 2
                && metricOf(thenN) != null && metricOf(elseN) != null);
        out.put("plainEnglish", businessSummaryFromBranches(branches));
        out.put("boundaryOpen", ">".equals(toUiOp(condOp)) || "<".equals(toUiOp(condOp)));
        out.put("boundaryValue", condVal);
        return out;
    }

    /** Rebuild IF expression from editable branches (branch1 = IF condition/then, branch2 = else). */
    public static Map<String, Object> buildExpressionFromBranches(List<Map<String, Object>> branches) {
        if (branches == null || branches.size() < 2) {
            throw new IllegalArgumentException("Compound rule requires two branches");
        }
        Map<String, Object> b1 = branches.get(0);
        Map<String, Object> b2 = branches.get(1);
        Object whenVal = b1.get("whenValue") == null ? 100 : b1.get("whenValue");
        String whenOp = String.valueOf(b1.getOrDefault("whenOperator", ">"));
        Map<String, Object> condition = cmp(whenOp, PolicyDsl.metric(TXN_METRIC), whenVal);
        Map<String, Object> thenExpr = cmp(
                String.valueOf(b1.getOrDefault("thenOperator", "<=")),
                PolicyDsl.metric(String.valueOf(b1.getOrDefault("thenParameterId", RATIO_METRIC))),
                b1.get("thenValue") == null ? 5 : b1.get("thenValue"));
        Map<String, Object> elseExpr = cmp(
                String.valueOf(b2.getOrDefault("thenOperator", "<=")),
                PolicyDsl.metric(String.valueOf(b2.getOrDefault("thenParameterId", COUNT_METRIC))),
                b2.get("thenValue") == null ? 5 : b2.get("thenValue"));
        return PolicyDsl.iff(condition, thenExpr, elseExpr);
    }

    /**
     * Apply boundary choice — only changes the relevant comparison operator(s).
     * A ratio: txn &gt;= 100 → ratio; else count (covers &lt;100)
     * B count: txn &gt; 100 → ratio; else count (covers &lt;=100)
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> patchBoundary(Map<String, Object> expr, String option) {
        if (!isIfExpression(expr)) {
            throw new IllegalArgumentException("Not a compound IF rule");
        }
        Map<String, Object> copy = deepCopy(expr);
        Map<String, Object> cond = asMap(copy.get("condition"));
        if (OPTION_RATIO.equals(option) || "INCLUDE_100_IN_RATIO_BRANCH".equalsIgnoreCase(option)) {
            cond.put("op", "GTE");
            copy.put("condition", cond);
        } else if (OPTION_COUNT.equals(option) || "INCLUDE_100_IN_COUNT_BRANCH".equalsIgnoreCase(option)) {
            // Keep GT so =100 falls into else (count branch)
            cond.put("op", "GT");
            copy.put("condition", cond);
        } else {
            throw new IllegalArgumentException("Unknown boundary option: " + option);
        }
        return copy;
    }

    public static String businessSummaryAfterBoundary(String option) {
        if (OPTION_RATIO.equals(option) || "INCLUDE_100_IN_RATIO_BRANCH".equalsIgnoreCase(option)) {
            return "If transactions ≥ 100 in last 3 months: return ratio ≤ 5%; if < 100: return count ≤ 5";
        }
        return "If transactions > 100 in last 3 months: return ratio ≤ 5%; if ≤ 100: return count ≤ 5";
    }

    public static String businessSummaryFromBranches(List<Map<String, Object>> branches) {
        if (branches == null || branches.size() < 2) return "";
        Map<String, Object> b1 = branches.get(0);
        Map<String, Object> b2 = branches.get(1);
        return "If transactions " + b1.get("whenOperator") + " " + b1.get("whenValue")
                + " in last 3 months: " + friendlyMetric(String.valueOf(b1.get("thenParameterId")))
                + " " + b1.get("thenOperator") + " " + b1.get("thenValue")
                + (RATIO_METRIC.equals(String.valueOf(b1.get("thenParameterId"))) ? "%" : "")
                + "; if " + b2.get("whenOperator") + " " + b2.get("whenValue") + ": "
                + friendlyMetric(String.valueOf(b2.get("thenParameterId")))
                + " " + b2.get("thenOperator") + " " + b2.get("thenValue");
    }

    /** Reject destructive replacements of compound IF rules. */
    public static void assertNonDestructiveReplace(
            Map<String, Object> prior, Map<String, Object> next) {
        if (!isIfExpression(prior)) return;
        int priorBranches = branchOutcomeCount(prior);
        if (priorBranches < 2) return;
        if (!isIfExpression(next)) {
            throw new IllegalArgumentException(
                    "Destructive edit blocked: compound rule would lose its IF/THEN/ELSE branches. "
                            + "Use Build (compound) or Define boundary instead of free-text rewrite.");
        }
        if (branchOutcomeCount(next) < priorBranches) {
            throw new IllegalArgumentException(
                    "Destructive edit blocked: one or more compound branches would disappear.");
        }
        // Mapped metrics must remain present
        List<String> priorMetrics = metricPaths(prior);
        List<String> nextMetrics = metricPaths(next);
        for (String m : priorMetrics) {
            if (!nextMetrics.contains(m)) {
                throw new IllegalArgumentException(
                        "Destructive edit blocked: mapped parameter " + m + " would be lost.");
            }
        }
    }

    public static Map<String, Object> boundaryResolverPayload(String ambiguityId, String ruleId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("defineBoundary", true);
        out.put("ambiguityId", ambiguityId);
        out.put("ruleId", ruleId);
        out.put("question", "What should happen when transaction count is exactly 100?");
        out.put("boundaryValue", 100);
        List<Map<String, Object>> choices = new ArrayList<>();
        choices.add(Map.of(
                "option", OPTION_RATIO,
                "label", "Include 100 in the ratio branch",
                "summary", "transactions ≥ 100 → return ratio ≤ 5%; transactions < 100 → return count ≤ 5"));
        choices.add(Map.of(
                "option", OPTION_COUNT,
                "label", "Include 100 in the count branch",
                "summary", "transactions > 100 → return ratio ≤ 5%; transactions ≤ 100 → return count ≤ 5"));
        out.put("choices", choices);
        out.put("action", "Define boundary");
        return out;
    }

    public static boolean coversExact100(Map<String, Object> expr) {
        if (!isIfExpression(expr)) return false;
        Map<String, Object> cond = asMap(expr.get("condition"));
        String op = opOf(cond);
        // GTE includes 100 in then; GT leaves 100 in else (count). Either covers execution.
        // Boundary wording is closed only when operator is inclusive on one side deliberately.
        return "GTE".equals(op) || "LTE".equals(op) || "GT".equals(op) || "LT".equals(op);
    }

    public static boolean boundaryClosedForReadiness(Map<String, Object> expr, String resolvedOption) {
        if (resolvedOption == null) return false;
        return OPTION_RATIO.equals(resolvedOption) || OPTION_COUNT.equals(resolvedOption)
                || "INCLUDE_100_IN_RATIO_BRANCH".equalsIgnoreCase(resolvedOption)
                || "INCLUDE_100_IN_COUNT_BRANCH".equalsIgnoreCase(resolvedOption);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    static List<String> metricPaths(Map<String, Object> node) {
        List<String> out = new ArrayList<>();
        collectMetrics(node, out);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void collectMetrics(Object node, List<String> out) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        if (m.get("metric") != null) out.add(String.valueOf(m.get("metric")));
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?>) collectMetrics(v, out);
            else if (v instanceof List<?> list) {
                for (Object i : list) collectMetrics(i, out);
            }
        }
    }

    private static boolean expressionMentions(Map<String, Object> expr, String needle) {
        return String.valueOf(expr).toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static Map<String, Object> cmp(String uiOp, Object left, Object right) {
        String op = switch (uiOp == null ? "" : uiOp.trim()) {
            case ">=", "≥", "GTE" -> "GTE";
            case ">", "GT" -> "GT";
            case "<=", "≤", "LTE" -> "LTE";
            case "<", "LT" -> "LT";
            case "=", "==", "EQ" -> "EQ";
            default -> "GT";
        };
        return PolicyDsl.op(op, left, right);
    }

    private static String toUiOp(String dslOp) {
        return switch (dslOp == null ? "" : dslOp.toUpperCase(Locale.ROOT)) {
            case "GTE" -> ">=";
            case "GT" -> ">";
            case "LTE" -> "<=";
            case "LT" -> "<";
            case "EQ" -> "=";
            default -> dslOp == null ? "" : dslOp;
        };
    }

    private static String complementaryUiOp(String uiOp) {
        return switch (uiOp == null ? "" : uiOp) {
            case ">" -> "<=";
            case ">=" -> "<";
            case "<" -> ">=";
            case "<=" -> ">";
            default -> "<=";
        };
    }

    private static String opOf(Map<String, Object> n) {
        return n == null || n.get("op") == null ? "" : String.valueOf(n.get("op")).toUpperCase(Locale.ROOT);
    }

    private static String metricOf(Map<String, Object> n) {
        if (n == null) return null;
        Object left = n.get("left");
        if (left instanceof Map<?, ?> m && m.get("metric") != null) return String.valueOf(m.get("metric"));
        if (n.get("metric") != null) return String.valueOf(n.get("metric"));
        return null;
    }

    private static String friendlyMetric(String id) {
        if (id == null) return "Parameter";
        if (id.contains("ratio")) return "Inward return ratio";
        if (id.contains("count") && id.contains("return")) return "Inward return count";
        if (id.contains("transaction_count")) return "Transaction count";
        return id;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o instanceof Map<?, ?> m) return new LinkedHashMap<>((Map<String, Object>) m);
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> m) {
                out.put(e.getKey(), deepCopy(new LinkedHashMap<>((Map<String, Object>) m)));
            } else {
                out.put(e.getKey(), v);
            }
        }
        return out;
    }

    public static boolean sameSemantics(Map<String, Object> a, Map<String, Object> b) {
        return Objects.equals(String.valueOf(a), String.valueOf(b));
    }
}
