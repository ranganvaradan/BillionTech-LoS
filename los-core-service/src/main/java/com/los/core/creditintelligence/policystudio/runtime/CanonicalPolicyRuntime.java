package com.los.core.creditintelligence.policystudio.runtime;

import com.los.core.creditintelligence.core.clock.FixedEvaluationClock;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import com.los.core.creditintelligence.policystudio.graph.PolicyDslOperandExtractor;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationMode;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import com.los.core.creditintelligence.policystudio.parameters.semantic.GacatSemanticTaxonomy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wave-5 single target policy runtime facade.
 *
 * <pre>
 * Policy DSL AST → exact GACAT IDs → CPES → ExecutionResult → PolicyDslInterpreterV1 → CanonicalPolicyResult
 * </pre>
 *
 * Does not call BureauMetricService / BankingMetricService / GstMetricService for parameter values.
 * Does not change live UnderwritingRuleEngine / ScorecardPolicyEngine authority.
 */
public final class CanonicalPolicyRuntime {

    public static final String RUNTIME_CLASS = "CanonicalPolicyRuntime";
    public static final String DSL_ENGINE = PolicyDslInterpreterV1.DSL_VERSION;
    public static final String EVALUATION_SEMANTICS = PolicyDslInterpreterV1.EVALUATION_SEMANTICS;

    private final PolicyDslInterpreterV1 interpreter;
    private final CanonicalParameterExecutionService cpes;
    private final CanonicalParameterRegistry gacatRegistry;

    public CanonicalPolicyRuntime(CanonicalParameterExecutionService cpes) {
        this(cpes, PolicyStudioConvergencePresenter.registry(), new PolicyDslInterpreterV1());
    }

    public CanonicalPolicyRuntime(
            CanonicalParameterExecutionService cpes,
            CanonicalParameterRegistry gacatRegistry,
            PolicyDslInterpreterV1 interpreter) {
        this.cpes = Objects.requireNonNull(cpes, "cpes");
        this.gacatRegistry = gacatRegistry == null
                ? PolicyStudioConvergencePresenter.registry() : gacatRegistry;
        this.interpreter = interpreter == null ? new PolicyDslInterpreterV1() : interpreter;
    }

    public record RuleSpec(String ruleId, String ruleVersion, Map<String, Object> expression, String onMissing) {
        public RuleSpec(String ruleId, Map<String, Object> expression) {
            this(ruleId, null, expression, PolicyDslInterpreterV1.DATA_INSUFFICIENT);
        }
    }

    public record PolicyRequest(
            String policyId,
            String policyVersion,
            List<RuleSpec> rules,
            EvaluationContext spineContext,
            LocalDate evaluationAsOf
    ) {}

    /**
     * Evaluate a single DSL expression using CPES for all exact GACAT metric operands.
     * Shared by Policy Test and shadow target path.
     */
    public CanonicalRuleResult evaluateRule(RuleSpec rule, EvaluationContext spineContext, LocalDate evaluationAsOf) {
        LocalDate asOf = requireAsOf(evaluationAsOf, spineContext);
        EvaluationContext ctx = withAsOf(spineContext, asOf);

        Map<String, Object> expr = rule.expression() == null ? Map.of() : rule.expression();
        List<String> operandIds = extractExactCanonicalIds(expr);
        Map<String, ExecutionResult> execById = new LinkedHashMap<>();
        Map<String, Object> metrics = new LinkedHashMap<>();
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("runtime", RUNTIME_CLASS);
        provenance.put("dslEngine", DSL_ENGINE);
        provenance.put("valueAuthority", "CanonicalParameterExecutionService");
        provenance.put("domainMetricServicesInvoked", false);

        boolean anyError = false;
        boolean anyUnavailable = false;
        ExecutionResult primary = null;

        for (String id : operandIds) {
            ExecutionResult er = cpes.resolveAndExecute(id, ctx);
            execById.put(id, er);
            if (primary == null) primary = er;
            provenance.put("exec:" + id, er.toTraceMap());
            if (er.status() == ExecutionStatus.ERROR) {
                anyError = true;
                metrics.put(id, Map.of("outcome", "ERROR", "dataStatus", "ERROR"));
            } else if (!er.valueAvailable()) {
                anyUnavailable = true;
                metrics.put(id, Map.of(
                        "outcome", PolicyDslInterpreterV1.DATA_INSUFFICIENT,
                        "dataStatus", "MISSING",
                        "executionStatus", er.status() == null ? null : er.status().name()));
            } else {
                metrics.put(id, er.value());
            }
        }

        if (anyError && operandIds.isEmpty() == false && metrics.values().stream()
                .allMatch(v -> v instanceof Map<?, ?> m && "ERROR".equals(String.valueOf(m.get("outcome"))))) {
            return new CanonicalRuleResult(
                    rule.ruleId(), rule.ruleVersion(), operandIds, primaryOperator(expr), null,
                    primary, CanonicalRuleResult.RuleOutcome.ERROR, asOf,
                    "CPES ERROR for operand(s)", provenance, List.of());
        }

        // If operands exist and all unavailable → DI without treating as FAIL/false
        if (!operandIds.isEmpty() && anyUnavailable && !anyValueAvailable(execById)) {
            return new CanonicalRuleResult(
                    rule.ruleId(), rule.ruleVersion(), operandIds, primaryOperator(expr), null,
                    primary, CanonicalRuleResult.RuleOutcome.DATA_INSUFFICIENT, asOf,
                    "Canonical parameter value not available via CPES", provenance, List.of());
        }

        FixedEvaluationClock clock = FixedEvaluationClock.atLocalNoon(asOf, ZoneId.of("Asia/Kolkata"));
        String onMissing = rule.onMissing() == null
                ? PolicyDslInterpreterV1.DATA_INSUFFICIENT : rule.onMissing();
        var dslCtx = new PolicyDslInterpreterV1.EvaluationContext(
                metrics,
                Map.copyOf(ctx.facts()),
                Map.copyOf(ctx.inputs()),
                Map.copyOf(ctx.facts()),
                clock,
                onMissing);
        String dslOutcome;
        try {
            dslOutcome = interpreter.evaluate(expr, dslCtx);
        } catch (Exception ex) {
            return new CanonicalRuleResult(
                    rule.ruleId(), rule.ruleVersion(), operandIds, primaryOperator(expr), null,
                    primary, CanonicalRuleResult.RuleOutcome.ERROR, asOf,
                    "DSL evaluation error: " + ex.getMessage(), provenance, List.of());
        }

        CanonicalRuleResult.RuleOutcome outcome = CanonicalRuleResult.fromDslOutcome(dslOutcome);
        return new CanonicalRuleResult(
                rule.ruleId(), rule.ruleVersion(), operandIds, primaryOperator(expr), null,
                primary, outcome, asOf,
                "DSL=" + dslOutcome, provenance, List.of());
    }

    public CanonicalPolicyResult evaluate(PolicyRequest request) {
        LocalDate asOf = requireAsOf(request.evaluationAsOf(), request.spineContext());
        EvaluationContext ctx = withAsOf(request.spineContext(), asOf);
        List<CanonicalRuleResult> rules = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> insufficient = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Map<String, Object> paramProv = new LinkedHashMap<>();

        for (RuleSpec spec : request.rules() == null ? List.<RuleSpec>of() : request.rules()) {
            CanonicalRuleResult rr = evaluateRule(spec, ctx, asOf);
            rules.add(rr);
            paramProv.putAll(rr.provenance());
            switch (rr.result()) {
                case FAIL -> failed.add(rr.ruleId());
                case DATA_INSUFFICIENT -> insufficient.add(rr.ruleId());
                case ERROR -> errors.add(rr.ruleId());
                default -> { }
            }
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("evaluationSemantics", EVALUATION_SEMANTICS);
        meta.put("cpesOnly", true);
        meta.put("explicitAsOf", true);
        meta.put("shadowOrTestOnly", true);
        meta.put("liveDecisionAuthority", "UNCHANGED_UnderwritingRuleEngine_ScorecardPolicyEngine");

        return new CanonicalPolicyResult(
                request.policyId(),
                request.policyVersion(),
                GacatSemanticTaxonomy.SEMANTIC_VERSION,
                asOf,
                CanonicalPolicyResult.aggregate(rules),
                rules,
                failed,
                insufficient,
                errors,
                paramProv,
                meta);
    }

    /** Extract exact GACAT IDs referenced as metric/fact operands. */
    public List<String> extractExactCanonicalIds(Map<String, Object> expression) {
        Set<String> ids = new LinkedHashSet<>();
        for (PolicyDslOperandExtractor.ExtractedOperand op :
                PolicyDslOperandExtractor.extract(expression, gacatRegistry)) {
            if (op.canonicalParameterId() != null
                    && "RESOLVED".equalsIgnoreCase(op.resolutionStatus())) {
                ids.add(op.canonicalParameterId());
            } else if (op.originalToken() != null && op.originalToken().contains(".")) {
                // Exact path token — still request via CPES (may be execution fact key)
                ids.add(op.originalToken().trim());
            }
        }
        // Also collect bare metric paths that may be exact IDs even if not in GACAT (e.g. collections)
        collectMetricPaths(expression, ids);
        return List.copyOf(ids);
    }

    @SuppressWarnings("unchecked")
    private static void collectMetricPaths(Object node, Set<String> ids) {
        if (!(node instanceof Map<?, ?> raw)) return;
        Map<String, Object> m = (Map<String, Object>) raw;
        Object metric = m.get("metric");
        if (metric == null) metric = m.get("METRIC_REF");
        if (metric != null) {
            String s = String.valueOf(metric).trim();
            if (s.contains(".")) ids.add(s);
        }
        for (Object v : m.values()) {
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                if (v instanceof Map<?, ?>) collectMetricPaths(v, ids);
                else for (Object i : (List<?>) v) collectMetricPaths(i, ids);
            }
        }
    }

    private static LocalDate requireAsOf(LocalDate explicit, EvaluationContext spine) {
        if (explicit != null) return explicit;
        if (spine != null && spine.evaluationAsOf() != null) return spine.evaluationAsOf();
        throw new IllegalArgumentException(
                "CanonicalPolicyRuntime requires explicit evaluationAsOf (no LocalDate.now())");
    }

    private static EvaluationContext withAsOf(EvaluationContext spine, LocalDate asOf) {
        EvaluationContext base = spine == null
                ? EvaluationContext.builder().mode(EvaluationMode.POLICY_TEST).evaluationAsOf(asOf).build()
                : spine;
        if (asOf.equals(base.evaluationAsOf())) {
            return base;
        }
        EvaluationContext.Builder b = EvaluationContext.builder()
                .mode(base.mode())
                .tenantId(base.tenantId())
                .applicationId(base.applicationId())
                .evaluationAsOf(asOf);
        base.facts().forEach(b::fact);
        base.inputs().forEach(b::input);
        base.entities().forEach(b::entity);
        return b.build();
    }

    private static boolean anyValueAvailable(Map<String, ExecutionResult> map) {
        for (ExecutionResult er : map.values()) {
            if (er != null && er.valueAvailable()) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String primaryOperator(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) return null;
        Object op = expr.get("op");
        return op == null ? null : String.valueOf(op);
    }
}
