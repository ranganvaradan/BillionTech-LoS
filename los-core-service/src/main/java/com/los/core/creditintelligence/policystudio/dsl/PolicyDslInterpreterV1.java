package com.los.core.creditintelligence.policystudio.dsl;

import com.los.core.creditintelligence.core.clock.EvaluationClock;
import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POLICY_DSL_V1 interpreter — shadow/studio only. Never production-active.
 * Semantics: {@value #EVALUATION_SEMANTICS}.
 *
 * <p>POLICY_DSL_EVALUATION_SEMANTICS_V1 truth tables (logical):
 * <pre>
 * AND priority: FAIL > ERROR > DATA_INSUFFICIENT > REFER > PASS; NOT_APPLICABLE ignored when any PASS;
 *   AND(all NA) = NOT_APPLICABLE; AND(PASS, NA) = PASS; AND(FAIL, *) = FAIL; AND(ERROR, no FAIL) = ERROR;
 *   AND(PASS, DI) = DI; AND(DI, no FAIL/ERROR) = DI.
 * OR priority: PASS > ERROR > DATA_INSUFFICIENT > REFER > FAIL; NOT_APPLICABLE ignored when any decisive;
 *   OR(PASS, *) = PASS; OR(FAIL, DI) = DI; OR(all NA) = NOT_APPLICABLE; OR(FAIL, NA) = FAIL.
 * Studio PASS/FAIL/DI behaviour for boolean expressions is preserved.
 * </pre>
 */
public class PolicyDslInterpreterV1 {

    public static final String EVALUATION_SEMANTICS = "POLICY_DSL_EVALUATION_SEMANTICS_V1";
    public static final String DSL_VERSION = "POLICY_DSL_V1";

    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";
    public static final String REFER = "REFER";
    public static final String DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    public static final String ERROR = "ERROR";

    /**
     * Evaluation context. {@code reconciliations} defaults to empty for backward compatibility.
     */
    public record EvaluationContext(
            Map<String, Object> metrics,
            Map<String, Object> facts,
            Map<String, Object> policyParameters,
            Map<String, Object> applicationFields,
            EvaluationClock clock,
            String onMissing,
            Map<String, Object> reconciliations
    ) {
        public EvaluationContext {
            metrics = metrics == null ? Map.of() : metrics;
            facts = facts == null ? Map.of() : facts;
            policyParameters = policyParameters == null ? Map.of() : policyParameters;
            applicationFields = applicationFields == null ? Map.of() : applicationFields;
            reconciliations = reconciliations == null ? Map.of() : reconciliations;
            clock = clock != null ? clock
                    : new FixedEvaluationClock(Instant.parse("2024-06-15T00:00:00Z"), ZoneId.of("Asia/Kolkata"));
            onMissing = onMissing == null ? DATA_INSUFFICIENT : onMissing;
        }

        /** Backward-compatible 6-arg construction (empty reconciliations). */
        public EvaluationContext(
                Map<String, Object> metrics,
                Map<String, Object> facts,
                Map<String, Object> policyParameters,
                Map<String, Object> applicationFields,
                EvaluationClock clock,
                String onMissing) {
            this(metrics, facts, policyParameters, applicationFields, clock, onMissing, Map.of());
        }

        public static EvaluationContext of(
                Map<String, Object> metrics,
                Map<String, Object> facts,
                EvaluationClock clock) {
            return new EvaluationContext(
                    metrics,
                    facts,
                    Map.of(),
                    Map.of(),
                    clock,
                    DATA_INSUFFICIENT,
                    Map.of());
        }

        public static EvaluationContext of(
                Map<String, Object> metrics,
                Map<String, Object> facts,
                Map<String, Object> policyParameters,
                Map<String, Object> applicationFields,
                EvaluationClock clock,
                String onMissing) {
            return new EvaluationContext(
                    metrics, facts, policyParameters, applicationFields, clock, onMissing, Map.of());
        }

        public static EvaluationContext of(
                Map<String, Object> metrics,
                Map<String, Object> facts,
                Map<String, Object> policyParameters,
                Map<String, Object> applicationFields,
                EvaluationClock clock,
                String onMissing,
                Map<String, Object> reconciliations) {
            return new EvaluationContext(
                    metrics, facts, policyParameters, applicationFields, clock, onMissing, reconciliations);
        }
    }

    public String evaluate(Map<String, Object> expression, EvaluationContext ctx) {
        if (expression == null || expression.isEmpty()) {
            return ctx.onMissing() == null ? DATA_INSUFFICIENT : ctx.onMissing();
        }
        EvalResult r = eval(expression, ctx);
        if (r.kind == Kind.DI) {
            // Wave-6: honor explicit rule onMissing (PASS/FAIL/REFER/DI); default remains DI
            return ctx.onMissing() == null ? DATA_INSUFFICIENT : ctx.onMissing();
        }
        if (r.kind == Kind.BOOL) {
            return Boolean.TRUE.equals(r.value) ? PASS : FAIL;
        }
        if (r.kind == Kind.OUTCOME) {
            return String.valueOf(r.value);
        }
        return DATA_INSUFFICIENT;
    }

    @SuppressWarnings("unchecked")
    private EvalResult eval(Object node, EvaluationContext ctx) {
        if (node == null) {
            return EvalResult.di();
        }
        if (node instanceof Number n) {
            return EvalResult.num(n.doubleValue());
        }
        if (node instanceof Boolean b) {
            return EvalResult.bool(b);
        }
        if (node instanceof String s) {
            return EvalResult.str(s);
        }
        if (!(node instanceof Map<?, ?> raw)) {
            return EvalResult.di();
        }
        Map<String, Object> m = (Map<String, Object>) raw;

        if (m.containsKey("metric") || m.containsKey("METRIC_REF")) {
            return resolveMetric(String.valueOf(m.getOrDefault("metric", m.get("METRIC_REF"))), ctx);
        }
        if (m.containsKey("fact") || m.containsKey("FACT_REF")) {
            return resolveMap(ctx.facts(), String.valueOf(m.getOrDefault("fact", m.get("FACT_REF"))));
        }
        if (m.containsKey("reconciliation") || m.containsKey("RECON_REF") || m.containsKey("RECONCILIATION_REF")) {
            Object key = m.getOrDefault("reconciliation",
                    m.getOrDefault("RECON_REF", m.get("RECONCILIATION_REF")));
            return resolveMap(ctx.reconciliations(), String.valueOf(key));
        }
        if (m.containsKey("policyParameter") || m.containsKey("POLICY_PARAMETER_REF")) {
            String code = String.valueOf(m.getOrDefault("policyParameter", m.get("POLICY_PARAMETER_REF")));
            return resolveMap(ctx.policyParameters(), code);
        }
        if (m.containsKey("applicationField") || m.containsKey("APPLICATION_FIELD_REF")) {
            String code = String.valueOf(m.getOrDefault("applicationField", m.get("APPLICATION_FIELD_REF")));
            EvalResult fromApp = resolveMap(ctx.applicationFields(), code);
            if (fromApp.kind != Kind.DI) {
                return fromApp;
            }
            return resolveMap(ctx.facts(), code.startsWith("application.") ? code : "application." + code);
        }
        if (m.containsKey("const") || m.containsKey("CONSTANT")) {
            Object v = m.getOrDefault("const", m.get("CONSTANT"));
            return coerce(v);
        }
        if (m.containsKey("value") && !m.containsKey("op")) {
            return coerce(m.get("value"));
        }

        String op = m.get("op") == null ? null : String.valueOf(m.get("op")).toUpperCase(Locale.ROOT);
        if (op == null) {
            return EvalResult.di();
        }
        return switch (op) {
            case "AND" -> evalAnd(args(m), ctx);
            case "OR" -> evalOr(args(m), ctx);
            case "NOT" -> {
                EvalResult a = eval(m.get("arg"), ctx);
                if (a.kind == Kind.DI) yield EvalResult.di();
                if (a.kind == Kind.OUTCOME && ERROR.equals(a.value)) yield EvalResult.outcome(ERROR);
                if (a.kind == Kind.OUTCOME && NOT_APPLICABLE.equals(a.value)) yield EvalResult.outcome(NOT_APPLICABLE);
                if (a.kind == Kind.BOOL) yield EvalResult.bool(!Boolean.TRUE.equals(a.value));
                if (a.kind == Kind.OUTCOME && PASS.equals(a.value)) yield EvalResult.bool(false);
                if (a.kind == Kind.OUTCOME && FAIL.equals(a.value)) yield EvalResult.bool(true);
                yield EvalResult.di();
            }
            case "IF" -> {
                EvalResult c = eval(m.get("condition"), ctx);
                if (c.kind == Kind.DI) yield EvalResult.di();
                if (c.kind == Kind.OUTCOME && ERROR.equals(c.value)) yield EvalResult.outcome(ERROR);
                if (c.kind == Kind.OUTCOME && NOT_APPLICABLE.equals(c.value)) yield EvalResult.outcome(NOT_APPLICABLE);
                if (Boolean.TRUE.equals(c.value) || (c.kind == Kind.OUTCOME && PASS.equals(c.value))) {
                    yield eval(m.get("then"), ctx);
                }
                yield eval(m.get("else"), ctx);
            }
            case "EXISTS" -> {
                EvalResult a = eval(m.get("arg") != null ? m.get("arg") : m.get("left"), ctx);
                yield a.kind == Kind.DI ? EvalResult.bool(false) : EvalResult.bool(true);
            }
            case "IS_MISSING" -> {
                EvalResult a = eval(m.get("arg") != null ? m.get("arg") : m.get("left"), ctx);
                yield EvalResult.bool(a.kind == Kind.DI);
            }
            case "EQ", "NE", "GT", "GTE", "LT", "LTE" -> compare(op, eval(m.get("left"), ctx), eval(m.get("right"), ctx));
            case "BETWEEN" -> evalBetween(m, ctx);
            case "IN" -> evalIn(m, ctx, true);
            case "NOT_IN" -> evalIn(m, ctx, false);
            case "ADD", "SUB", "SUBTRACT", "MUL", "MULTIPLY", "DIVIDE", "DIV" ->
                    arith(normalizeArith(op), eval(m.get("left"), ctx), eval(m.get("right"), ctx));
            case "COUNT", "SUM", "AVERAGE", "AVG", "MIN", "MAX" -> aggregate(op, args(m), ctx);
            default -> EvalResult.di();
        };
    }

    private String normalizeArith(String op) {
        return switch (op) {
            case "SUBTRACT" -> "SUB";
            case "MULTIPLY" -> "MUL";
            default -> op;
        };
    }

    private EvalResult evalBetween(Map<String, Object> m, EvaluationContext ctx) {
        EvalResult v = eval(m.get("left") != null ? m.get("left") : m.get("arg"), ctx);
        EvalResult lo = eval(m.get("low") != null ? m.get("low") : m.get("min"), ctx);
        EvalResult hi = eval(m.get("high") != null ? m.get("high") : m.get("max"), ctx);
        if (v.kind == Kind.DI || lo.kind == Kind.DI || hi.kind == Kind.DI) {
            return EvalResult.di();
        }
        Double vn = toNum(v);
        Double lon = toNum(lo);
        Double hin = toNum(hi);
        if (vn == null || lon == null || hin == null) {
            return EvalResult.di();
        }
        boolean inclusive = m.get("inclusive") == null || Boolean.TRUE.equals(m.get("inclusive"));
        if (inclusive) {
            return EvalResult.bool(vn >= lon && vn <= hin);
        }
        return EvalResult.bool(vn > lon && vn < hin);
    }

    @SuppressWarnings("unchecked")
    private EvalResult evalIn(Map<String, Object> m, EvaluationContext ctx, boolean positive) {
        EvalResult left = eval(m.get("left") != null ? m.get("left") : m.get("arg"), ctx);
        if (left.kind == Kind.DI) {
            return EvalResult.di();
        }
        Object rawSet = m.get("set") != null ? m.get("set") : m.get("right");
        List<Object> values = new ArrayList<>();
        if (rawSet instanceof List<?> list) {
            values.addAll(list);
        } else if (rawSet instanceof Map<?, ?>) {
            EvalResult r = eval(rawSet, ctx);
            if (r.kind == Kind.DI) {
                return EvalResult.di();
            }
            if (r.value instanceof Collection<?> c) {
                values.addAll(c);
            } else {
                values.add(r.value);
            }
        } else if (rawSet != null) {
            values.add(rawSet);
        }
        String leftStr = String.valueOf(left.value);
        Double leftNum = toNum(left);
        boolean found = false;
        for (Object item : values) {
            EvalResult ir = item instanceof Map<?, ?> ? eval(item, ctx) : coerce(item);
            if (ir.kind == Kind.DI) {
                continue;
            }
            Double in = toNum(ir);
            if (leftNum != null && in != null) {
                if (leftNum.equals(in)) {
                    found = true;
                    break;
                }
            } else if (leftStr.equals(String.valueOf(ir.value))) {
                found = true;
                break;
            }
        }
        return EvalResult.bool(positive == found);
    }

    private EvalResult aggregate(String op, List<Object> args, EvaluationContext ctx) {
        List<Double> nums = new ArrayList<>();
        boolean anyDi = false;
        for (Object a : args) {
            EvalResult r = eval(a, ctx);
            if (r.kind == Kind.DI) {
                anyDi = true;
                continue;
            }
            Double n = toNum(r);
            if (n != null) {
                nums.add(n);
            } else {
                anyDi = true;
            }
        }
        String norm = "AVG".equals(op) ? "AVERAGE" : op;
        if ("COUNT".equals(norm)) {
            return EvalResult.num(nums.size());
        }
        if (nums.isEmpty()) {
            return anyDi ? EvalResult.di() : EvalResult.num(0);
        }
        return switch (norm) {
            case "SUM" -> EvalResult.num(nums.stream().mapToDouble(Double::doubleValue).sum());
            case "AVERAGE" -> EvalResult.num(nums.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            case "MIN" -> EvalResult.num(nums.stream().mapToDouble(Double::doubleValue).min().orElse(0));
            case "MAX" -> EvalResult.num(nums.stream().mapToDouble(Double::doubleValue).max().orElse(0));
            default -> EvalResult.di();
        };
    }

    private EvalResult evalAnd(List<Object> args, EvaluationContext ctx) {
        boolean anyDi = false;
        boolean anyError = false;
        boolean anyRefer = false;
        boolean anyPass = false;
        boolean anyNa = false;
        for (Object a : args) {
            EvalResult r = eval(a, ctx);
            if (r.kind == Kind.DI) {
                anyDi = true;
                continue;
            }
            if (r.kind == Kind.BOOL && !Boolean.TRUE.equals(r.value)) {
                return EvalResult.bool(false); // FAIL short-circuit
            }
            if (r.kind == Kind.OUTCOME && FAIL.equals(r.value)) {
                return EvalResult.bool(false);
            }
            if (r.kind == Kind.OUTCOME && ERROR.equals(r.value)) {
                anyError = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && DATA_INSUFFICIENT.equals(r.value)) {
                anyDi = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && REFER.equals(r.value)) {
                anyRefer = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && NOT_APPLICABLE.equals(r.value)) {
                anyNa = true;
                continue;
            }
            if (r.kind == Kind.BOOL && Boolean.TRUE.equals(r.value)) {
                anyPass = true;
            }
            if (r.kind == Kind.OUTCOME && PASS.equals(r.value)) {
                anyPass = true;
            }
        }
        // AND(FAIL,*) already returned; AND with ERROR = ERROR; AND with DI (no fail) = DI
        if (anyError) {
            return EvalResult.outcome(ERROR);
        }
        if (anyDi) {
            return EvalResult.di();
        }
        if (anyRefer) {
            return EvalResult.outcome(REFER);
        }
        if (anyPass) {
            return EvalResult.bool(true); // AND(PASS, NA) = PASS
        }
        if (anyNa) {
            return EvalResult.outcome(NOT_APPLICABLE);
        }
        return EvalResult.bool(true);
    }

    private EvalResult evalOr(List<Object> args, EvaluationContext ctx) {
        boolean anyDi = false;
        boolean anyError = false;
        boolean anyRefer = false;
        boolean anyFail = false;
        boolean anyNa = false;
        for (Object a : args) {
            EvalResult r = eval(a, ctx);
            if (r.kind == Kind.DI) {
                anyDi = true;
                continue;
            }
            if (r.kind == Kind.BOOL && Boolean.TRUE.equals(r.value)) {
                return EvalResult.bool(true); // OR(PASS, DI)=PASS
            }
            if (r.kind == Kind.OUTCOME && PASS.equals(r.value)) {
                return EvalResult.bool(true);
            }
            if (r.kind == Kind.OUTCOME && ERROR.equals(r.value)) {
                anyError = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && DATA_INSUFFICIENT.equals(r.value)) {
                anyDi = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && REFER.equals(r.value)) {
                anyRefer = true;
                continue;
            }
            if (r.kind == Kind.OUTCOME && NOT_APPLICABLE.equals(r.value)) {
                anyNa = true;
                continue;
            }
            if (r.kind == Kind.BOOL && !Boolean.TRUE.equals(r.value)) {
                anyFail = true;
            }
            if (r.kind == Kind.OUTCOME && FAIL.equals(r.value)) {
                anyFail = true;
            }
        }
        if (anyError) {
            return EvalResult.outcome(ERROR);
        }
        // OR(FAIL, DI)=DI
        if (anyDi) {
            return EvalResult.di();
        }
        if (anyRefer) {
            return EvalResult.outcome(REFER);
        }
        if (anyFail) {
            return EvalResult.bool(false);
        }
        if (anyNa) {
            return EvalResult.outcome(NOT_APPLICABLE);
        }
        return EvalResult.bool(!anyFail && args.isEmpty());
    }

    private EvalResult compare(String op, EvalResult left, EvalResult right) {
        if (left.kind == Kind.DI || right.kind == Kind.DI) {
            return EvalResult.di();
        }
        Double ln = toNum(left);
        Double rn = toNum(right);
        if (ln == null || rn == null) {
            if ("EQ".equals(op)) {
                return EvalResult.bool(String.valueOf(left.value).equals(String.valueOf(right.value)));
            }
            if ("NE".equals(op)) {
                return EvalResult.bool(!String.valueOf(left.value).equals(String.valueOf(right.value)));
            }
            return EvalResult.di();
        }
        return EvalResult.bool(switch (op) {
            case "EQ" -> ln.equals(rn);
            case "NE" -> !ln.equals(rn);
            case "GT" -> ln > rn;
            case "GTE" -> ln >= rn;
            case "LT" -> ln < rn;
            case "LTE" -> ln <= rn;
            default -> false;
        });
    }

    private EvalResult arith(String op, EvalResult left, EvalResult right) {
        if (left.kind == Kind.DI || right.kind == Kind.DI) {
            return EvalResult.di();
        }
        Double ln = toNum(left);
        Double rn = toNum(right);
        if (ln == null || rn == null) {
            return EvalResult.di();
        }
        return EvalResult.num(switch (op) {
            case "ADD" -> ln + rn;
            case "SUB" -> ln - rn;
            case "MUL" -> ln * rn;
            case "DIVIDE", "DIV" -> rn == 0 ? Double.NaN : ln / rn;
            default -> Double.NaN;
        });
    }

    private EvalResult resolveMetric(String path, EvaluationContext ctx) {
        Object raw = ctx.metrics().get(path);
        if (raw == null) {
            return EvalResult.di();
        }
        if (raw instanceof Map<?, ?> mm) {
            Object outcome = mm.get("outcome");
            if (outcome != null && DATA_INSUFFICIENT.equalsIgnoreCase(String.valueOf(outcome))) {
                return EvalResult.di();
            }
            Object status = mm.get("dataStatus");
            if (status != null && ("MISSING".equalsIgnoreCase(String.valueOf(status))
                    || "DATA_INSUFFICIENT".equalsIgnoreCase(String.valueOf(status)))) {
                return EvalResult.di();
            }
            Object v = mm.get("v");
            if (v == null && mm.get("value") instanceof Map<?, ?> vm) {
                v = vm.get("v");
            }
            if (v == null) {
                v = mm.get("value");
            }
            if (v == null) {
                return EvalResult.di();
            }
            return coerce(v);
        }
        return coerce(raw);
    }

    private EvalResult resolveMap(Map<String, Object> map, String key) {
        if (map == null || key == null || !map.containsKey(key) || map.get(key) == null) {
            return EvalResult.di();
        }
        Object raw = map.get(key);
        if (raw instanceof Map<?, ?> mm) {
            Object outcome = mm.get("outcome");
            if (outcome != null && DATA_INSUFFICIENT.equalsIgnoreCase(String.valueOf(outcome))) {
                return EvalResult.di();
            }
            Object status = mm.get("dataStatus");
            if (status != null && ("MISSING".equalsIgnoreCase(String.valueOf(status))
                    || "DATA_INSUFFICIENT".equalsIgnoreCase(String.valueOf(status)))) {
                return EvalResult.di();
            }
            Object v = mm.get("v");
            if (v == null) {
                v = mm.get("value");
            }
            if (v == null) {
                return EvalResult.di();
            }
            return coerce(v);
        }
        return coerce(raw);
    }

    private EvalResult coerce(Object v) {
        if (v == null) {
            return EvalResult.di();
        }
        if (v instanceof Boolean b) {
            return EvalResult.bool(b);
        }
        if (v instanceof Number n) {
            return EvalResult.num(n.doubleValue());
        }
        if (v instanceof String s) {
            if (PASS.equals(s) || FAIL.equals(s) || REFER.equals(s) || DATA_INSUFFICIENT.equals(s)
                    || NOT_APPLICABLE.equals(s) || ERROR.equals(s)) {
                if (DATA_INSUFFICIENT.equals(s)) {
                    return EvalResult.di();
                }
                return EvalResult.outcome(s);
            }
            try {
                return EvalResult.num(Double.parseDouble(s));
            } catch (NumberFormatException e) {
                return EvalResult.str(s);
            }
        }
        return EvalResult.str(String.valueOf(v));
    }

    private Double toNum(EvalResult r) {
        if (r.kind == Kind.NUM) {
            return (Double) r.value;
        }
        if (r.kind == Kind.STR) {
            try {
                return Double.parseDouble(String.valueOf(r.value));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> args(Map<String, Object> m) {
        Object a = m.get("args");
        if (a instanceof List<?> list) {
            return (List<Object>) list;
        }
        List<Object> out = new ArrayList<>();
        if (m.get("left") != null) {
            out.add(m.get("left"));
        }
        if (m.get("right") != null) {
            out.add(m.get("right"));
        }
        return out;
    }

    /** Current calendar month from EvaluationClock — never wall clock. */
    public LocalDate currentMonthStart(EvaluationContext ctx) {
        LocalDate today = ctx.clock().today();
        return today.withDayOfMonth(1);
    }

    private enum Kind { BOOL, NUM, STR, DI, OUTCOME }

    private record EvalResult(Kind kind, Object value) {
        static EvalResult bool(boolean b) { return new EvalResult(Kind.BOOL, b); }
        static EvalResult num(double d) { return new EvalResult(Kind.NUM, d); }
        static EvalResult str(String s) { return new EvalResult(Kind.STR, s); }
        static EvalResult di() { return new EvalResult(Kind.DI, null); }
        static EvalResult outcome(String o) { return new EvalResult(Kind.OUTCOME, o); }
    }
}
