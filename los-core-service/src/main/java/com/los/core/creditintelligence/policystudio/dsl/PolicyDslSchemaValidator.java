package com.los.core.creditintelligence.policystudio.dsl;

import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * POLICY_DSL_V1 schema validation: operators, operand types, units, periods, onMissing.
 */
@Component
public class PolicyDslSchemaValidator {

    public static final String DSL_VERSION = "POLICY_DSL_V1";

    private static final Set<String> COMPARISON = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN");
    private static final Set<String> LOGICAL = Set.of("AND", "OR", "NOT", "IF");
    private static final Set<String> ARITH = Set.of(
            "ADD", "SUB", "SUBTRACT", "MUL", "MULTIPLY", "DIVIDE", "DIV",
            "COUNT", "SUM", "AVERAGE", "AVG", "MIN", "MAX");
    private static final Set<String> OTHER = Set.of("EXISTS", "IN", "NOT_IN", "IS_MISSING");

    private final PolicyAuthoringRegistry registry;

    public PolicyDslSchemaValidator(PolicyAuthoringRegistry registry) {
        this.registry = registry != null ? registry : new PolicyAuthoringRegistry();
    }

    public PolicyDslSchemaValidator() {
        this(new PolicyAuthoringRegistry());
    }

    public ValidationResult validate(Map<String, Object> expression, String ruleType, String onMissing,
                                     String unitLeft, String unitRight, String periodLeft, String periodRight) {
        List<Map<String, Object>> errors = new ArrayList<>();
        if (expression == null || expression.isEmpty()) {
            errors.add(err("EMPTY_EXPRESSION", "Expression required"));
            return new ValidationResult(false, errors);
        }
        if ("HARD".equalsIgnoreCase(ruleType)
                && (onMissing == null || onMissing.isBlank())) {
            errors.add(err("ON_MISSING_REQUIRED", "HARD rules require onMissing"));
        }
        if (unitLeft != null && unitRight != null
                && !unitLeft.equalsIgnoreCase(unitRight)
                && isMoneyVsCount(unitLeft, unitRight)) {
            errors.add(err("UNIT_MISMATCH", "Cannot compare " + unitLeft + " with " + unitRight));
        }
        if (periodLeft != null && periodRight != null && !periodLeft.equalsIgnoreCase(periodRight)) {
            errors.add(err("PERIOD_MISMATCH", "Period mismatch: " + periodLeft + " vs " + periodRight));
        }
        validateNode(expression, errors);
        return new ValidationResult(errors.isEmpty(), errors);
    }

    public ValidationResult validateRuleExpression(Map<String, Object> expression, String ruleType, String onMissing) {
        return validate(expression, ruleType, onMissing, null, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private void validateNode(Object node, List<Map<String, Object>> errors) {
        if (!(node instanceof Map<?, ?> raw)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) raw;
        if (m.containsKey("metric") || m.containsKey("METRIC_REF")) {
            String path = String.valueOf(m.getOrDefault("metric", m.get("METRIC_REF")));
            if (!knownPath(path)) {
                errors.add(err("UNKNOWN_PATH", "Unknown metric path: " + path));
            }
            return;
        }
        if (m.containsKey("fact") || m.containsKey("FACT_REF")) {
            String path = String.valueOf(m.getOrDefault("fact", m.get("FACT_REF")));
            if (!knownPath(path) && !path.startsWith("application.") && !path.startsWith("bureau.")) {
                errors.add(err("UNKNOWN_PATH", "Unknown fact path: " + path));
            }
            return;
        }
        if (m.containsKey("policyParameter") || m.containsKey("POLICY_PARAMETER_REF")) {
            return;
        }
        if (m.containsKey("applicationField") || m.containsKey("APPLICATION_FIELD_REF")) {
            return;
        }
        if (m.containsKey("const") || m.containsKey("CONSTANT") || m.containsKey("value")) {
            return;
        }
        Object opObj = m.get("op");
        if (opObj == null) {
            errors.add(err("MISSING_OP", "Node missing op"));
            return;
        }
        String op = String.valueOf(opObj).toUpperCase(Locale.ROOT);
        if (!(COMPARISON.contains(op) || LOGICAL.contains(op) || ARITH.contains(op) || OTHER.contains(op))) {
            errors.add(err("UNKNOWN_OPERATOR", "Unknown operator: " + op));
        }
        if (m.containsKey("left")) {
            validateNode(m.get("left"), errors);
        }
        if (m.containsKey("right")) {
            validateNode(m.get("right"), errors);
        }
        if (m.containsKey("arg")) {
            validateNode(m.get("arg"), errors);
        }
        if (m.containsKey("args") && m.get("args") instanceof List<?> args) {
            for (Object a : args) {
                validateNode(a, errors);
            }
        }
        if (m.containsKey("condition")) {
            validateNode(m.get("condition"), errors);
            validateNode(m.get("then"), errors);
            validateNode(m.get("else"), errors);
        }
        // Unit mismatch inside comparison when left/right carry unit metadata
        if (COMPARISON.contains(op)) {
            String ul = unitOf(m.get("left"));
            String ur = unitOf(m.get("right"));
            if (ul != null && ur != null && isMoneyVsCount(ul, ur)) {
                errors.add(err("UNIT_MISMATCH", "INR vs COUNT rejected for " + op));
            }
            String pl = periodOf(m.get("left"));
            String pr = periodOf(m.get("right"));
            if (pl != null && pr != null && !pl.equalsIgnoreCase(pr)) {
                errors.add(err("PERIOD_MISMATCH", "Period mismatch under " + op));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String unitOf(Object node) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            if (m.get("unit") != null) {
                return String.valueOf(m.get("unit"));
            }
            Object path = m.getOrDefault("metric", m.get("METRIC_REF"));
            if (path != null) {
                String p = String.valueOf(path).toLowerCase(Locale.ROOT);
                if (p.contains("count") || p.contains("ratio") || p.contains("dpd") || p.contains("inquiry")) {
                    return "COUNT";
                }
                if (p.contains("balance") || p.contains("amount") || p.contains("score") || p.contains("adb")) {
                    return "INR";
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String periodOf(Object node) {
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw;
            if (m.get("period") != null) {
                return String.valueOf(m.get("period"));
            }
        }
        return null;
    }

    private boolean isMoneyVsCount(String a, String b) {
        String x = a.toUpperCase(Locale.ROOT);
        String y = b.toUpperCase(Locale.ROOT);
        boolean moneyA = x.contains("INR") || x.contains("MONEY") || x.contains("AMOUNT");
        boolean moneyB = y.contains("INR") || y.contains("MONEY") || y.contains("AMOUNT");
        boolean countA = x.contains("COUNT") || x.equals("N");
        boolean countB = y.contains("COUNT") || y.equals("N");
        return (moneyA && countB) || (moneyB && countA);
    }

    private boolean knownPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.startsWith("application.") || path.startsWith("BANK_POLICY_")
                || path.contains("PROPOSED_EDI")
                || path.startsWith("banking.") || path.startsWith("bureau.")) {
            return true;
        }
        return registry.hasCanonicalPath(path);
    }

    private Map<String, Object> err(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        return m;
    }

    public record ValidationResult(boolean valid, List<Map<String, Object>> errors) {}
}
