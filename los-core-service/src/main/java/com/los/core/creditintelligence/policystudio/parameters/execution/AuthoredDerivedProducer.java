package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.derived.BusinessCalculationAssistant;
import com.los.core.creditintelligence.policystudio.parameters.derived.CiGacatDerivedCalculationDefinition;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationDefinitionService;
import com.los.core.creditintelligence.policystudio.parameters.derived.DerivedCalculationSemanticCompatibility;
import com.los.core.creditintelligence.policystudio.parameters.derived.SafeDerivedExpressionEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * AUTHORED_DERIVED producer: active definition → recursive deps via spine →
 * {@link SafeDerivedExpressionEvaluator}. No LLM at runtime.
 *
 * <p>Semantically invalid approved expressions (e.g. MONTHS_SINCE on a COUNT_DPD target)
 * do not claim capability — exact ID remains without an executable producer.
 */
public final class AuthoredDerivedProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "AuthoredDerivedProducer";

    private final DerivedCalculationDefinitionService definitionService;

    public AuthoredDerivedProducer(DerivedCalculationDefinitionService definitionService) {
        this.definitionService = definitionService;
    }

    @Override
    public String producerId() {
        return PRODUCER_ID;
    }

    @Override
    public ProducerType producerType() {
        return ProducerType.AUTHORED_DERIVED;
    }

    @Override
    public boolean claims(String canonicalParameterId) {
        return loadValidDefinition(canonicalParameterId, null).isPresent();
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        Optional<CiGacatDerivedCalculationDefinition> def =
                loadValidDefinition(canonicalParameterId, ctx == null ? null : ctx.tenantId());
        if (def.isEmpty()) {
            return false;
        }
        List<String> deps = dependencyIds(def.get());
        for (String dep : deps) {
            if (!resolver.hasExecutionCapability(dep, ctx)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ExecutionResult execute(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        Optional<CiGacatDerivedCalculationDefinition> opt =
                loadValidDefinition(canonicalParameterId, ctx.tenantId());
        if (opt.isEmpty()) {
            // Distinguish: definition exists but invalid vs absent
            Optional<CiGacatDerivedCalculationDefinition> raw =
                    definitionService.latestFor(canonicalParameterId, ctx.tenantId());
            if (raw.isPresent()) {
                return ExecutionResult.calculationNotDefined(canonicalParameterId,
                        "Authored definition present but not executable (semantic/op mismatch): "
                                + raw.get().getId());
            }
            return ExecutionResult.notExecutable(canonicalParameterId,
                    "No authored derived definition for " + canonicalParameterId);
        }
        CiGacatDerivedCalculationDefinition def = opt.get();
        if (!canonicalParameterId.equals(def.getCanonicalParameterId())) {
            return ExecutionResult.error(canonicalParameterId,
                    "Definition canonical_parameter_id mismatch: " + def.getCanonicalParameterId());
        }

        // Policy-test input overlay for the target itself (simulation)
        Object overlay = ctx.inputs().get(canonicalParameterId);
        if (overlay != null) {
            Map<String, Object> prov = new LinkedHashMap<>();
            prov.put("sourceType", "POLICY_TEST_INPUT_OVERLAY");
            prov.put("definitionId", def.getId() == null ? null : def.getId().toString());
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.VALUE_AVAILABLE)
                    .value(overlay)
                    .producerType(ProducerType.AUTHORED_DERIVED)
                    .producerId(PRODUCER_ID)
                    .dependencies(dependencyIds(def))
                    .capability(true)
                    .provenance(prov)
                    .exactProducerPath(PRODUCER_ID + " ← inputs[" + canonicalParameterId + "]")
                    .build();
        }

        Map<String, Object> expr = def.getExpressionJson() == null
                ? Map.of() : def.getExpressionJson();
        List<String> deps = new ArrayList<>(SafeDerivedExpressionEvaluator.collectDependencies(expr));
        Map<String, Object> evalInputs = new LinkedHashMap<>();
        if (ctx.evaluationAsOf() != null) {
            evalInputs.put(SafeDerivedExpressionEvaluator.INPUT_EVAL_AS_OF, ctx.evaluationAsOf());
            evalInputs.put("evalAsOf", ctx.evaluationAsOf());
            evalInputs.put("evaluationDate", ctx.evaluationAsOf());
        }
        List<Map<String, Object>> depProv = new ArrayList<>();
        for (String dep : deps) {
            ExecutionResult depResult = resolver.resolveAndExecute(dep, ctx);
            depProv.add(depResult.toTraceMap());
            if (!depResult.capability() && depResult.status() == ExecutionStatus.NOT_EXECUTABLE) {
                return ExecutionResult.builder(canonicalParameterId)
                        .status(ExecutionStatus.DEPENDENCY_NOT_AVAILABLE)
                        .producerType(ProducerType.AUTHORED_DERIVED)
                        .producerId(PRODUCER_ID)
                        .dependencies(deps)
                        .capability(false)
                        .putProvenance("definitionId", def.getId() == null ? null : def.getId().toString())
                        .putProvenance("dependencyProvenance", depProv)
                        .reason("Dependency not executable: " + dep)
                        .exactProducerPath(PRODUCER_ID + " → dep:" + dep + " NOT_EXECUTABLE")
                        .build();
            }
            if (depResult.status() == ExecutionStatus.INPUT_REQUIRED) {
                return ExecutionResult.builder(canonicalParameterId)
                        .status(ExecutionStatus.DEPENDENCY_NOT_AVAILABLE)
                        .producerType(ProducerType.AUTHORED_DERIVED)
                        .producerId(PRODUCER_ID)
                        .dependencies(deps)
                        .capability(true)
                        .putProvenance("dependencyProvenance", depProv)
                        .reason("Dependency input required: " + dep)
                        .exactProducerPath(PRODUCER_ID + " → dep:" + dep + " INPUT_REQUIRED")
                        .build();
            }
            if (!depResult.valueAvailable()) {
                return ExecutionResult.builder(canonicalParameterId)
                        .status(ExecutionStatus.DEPENDENCY_NOT_AVAILABLE)
                        .producerType(ProducerType.AUTHORED_DERIVED)
                        .producerId(PRODUCER_ID)
                        .dependencies(deps)
                        .capability(true)
                        .putProvenance("definitionId", def.getId() == null ? null : def.getId().toString())
                        .putProvenance("dependencyProvenance", depProv)
                        .reason("Dependency value unavailable: " + dep + " (" + depResult.status() + ")")
                        .exactProducerPath(PRODUCER_ID + " → dep:" + dep + " " + depResult.status())
                        .build();
            }
            evalInputs.put(dep, depResult.value());
        }

        SafeDerivedExpressionEvaluator.EvalResult eval =
                SafeDerivedExpressionEvaluator.evaluate(expr, evalInputs);
        Map<String, Object> prov = new LinkedHashMap<>();
        prov.put("sourceType", "AUTHORED_DERIVED");
        prov.put("definitionId", def.getId() == null ? null : def.getId().toString());
        prov.put("definitionStatus", def.getStatus());
        prov.put("definitionVersion", def.getVersionNo());
        prov.put("dependencyProvenance", depProv);
        if (ctx.evaluationAsOf() != null) {
            prov.put("asOf", ctx.evaluationAsOf().toString());
        }

        if (SafeDerivedExpressionEvaluator.STATUS_OK.equals(eval.status())) {
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.VALUE_AVAILABLE)
                    .value(eval.value())
                    .producerType(ProducerType.AUTHORED_DERIVED)
                    .producerId(PRODUCER_ID)
                    .dependencies(deps)
                    .capability(true)
                    .provenance(prov)
                    .exactProducerPath(PRODUCER_ID + " → SafeDerivedExpressionEvaluator ["
                            + canonicalParameterId + "] def=" + def.getId())
                    .build();
        }
        if (SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT.equals(eval.status())) {
            return ExecutionResult.builder(canonicalParameterId)
                    .status(ExecutionStatus.DATA_NOT_AVAILABLE)
                    .producerType(ProducerType.AUTHORED_DERIVED)
                    .producerId(PRODUCER_ID)
                    .dependencies(deps)
                    .capability(true)
                    .provenance(prov)
                    .reason(eval.reason())
                    .exactProducerPath(PRODUCER_ID + " → SafeDerivedExpressionEvaluator DATA_INSUFFICIENT")
                    .build();
        }
        return ExecutionResult.builder(canonicalParameterId)
                .status(ExecutionStatus.ERROR)
                .producerType(ProducerType.AUTHORED_DERIVED)
                .producerId(PRODUCER_ID)
                .dependencies(deps)
                .capability(false)
                .provenance(prov)
                .reason(eval.reason())
                .exactProducerPath(PRODUCER_ID + " → SafeDerivedExpressionEvaluator INVALID")
                .build();
    }

    private Optional<CiGacatDerivedCalculationDefinition> loadValidDefinition(
            String canonicalParameterId, java.util.UUID tenantId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        Optional<CiGacatDerivedCalculationDefinition> opt =
                definitionService.latestFor(canonicalParameterId, tenantId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        CiGacatDerivedCalculationDefinition def = opt.get();
        String st = def.getStatus() == null ? "" : def.getStatus().toUpperCase(Locale.ROOT);
        if (DerivedCalculationDefinitionService.STATUS_RETIRED.equals(st)) {
            return Optional.empty();
        }
        if (!canonicalParameterId.equals(def.getCanonicalParameterId())) {
            return Optional.empty();
        }
        Map<String, Object> expr = def.getExpressionJson();
        if (expr == null || expr.isEmpty()) {
            return Optional.empty();
        }
        if (!expressionMatchesTargetIntent(canonicalParameterId, expr)) {
            return Optional.empty();
        }
        return Optional.of(def);
    }

    /**
     * Refuse capability when approved expression op cannot satisfy target business intent
     * (e.g. MONTHS_SINCE_LAST_MATCH on COUNT_DPD_MONTHS target).
     */
    static boolean expressionMatchesTargetIntent(String canonicalParameterId, Map<String, Object> expr) {
        CanonicalParameterRegistry registry = PolicyStudioConvergencePresenter.registry();
        CanonicalParameterDefinition target = registry.findById(canonicalParameterId).orElse(null);
        if (target == null) {
            // Unknown to catalogue — still allow if expression is structurally evaluable
            return expr.get("op") != null;
        }
        BusinessCalculationAssistant.TargetSemantics semantics =
                BusinessCalculationAssistant.extractTargetSemantics(target, null, Map.of());
        String intent = semantics.intent();
        if ("COUNT_DPD_MONTHS".equals(intent)) {
            return isOp(expr, "COUNT_PERIODS_MATCHING");
        }
        if ("MONTHS_SINCE_OVERDUE".equals(intent)) {
            return isOp(expr, "MONTHS_SINCE_LAST_MATCH");
        }
        if ("SUM_CC_OVERDUE".equals(intent) || "bureau.cc_overdue_amount".equals(canonicalParameterId)) {
            // No SafeDerived SUM producer registered via this path without a real sum expression
            String op = String.valueOf(expr.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
            return "ADD".equals(op) || "SUM".equals(op) || "REF".equals(op);
        }
        DerivedCalculationSemanticCompatibility.Result compat =
                DerivedCalculationSemanticCompatibility.assessExpression(
                        target, expr, id -> registry.findById(id).orElse(null));
        // Soft: if assess fails solely on catalogue implemented flags of deps, still allow
        // structural ops that SafeDerived can evaluate — but refuse hard op/unit failures.
        if (!compat.compatible()) {
            boolean onlyImplFlags = compat.failures().stream()
                    .allMatch(f -> f != null && f.contains("not implemented in catalogue"));
            return onlyImplFlags;
        }
        return true;
    }

    private static boolean isOp(Map<String, Object> expression, String expectedOp) {
        if (expression == null || expression.isEmpty()) return false;
        String op = String.valueOf(expression.getOrDefault("op", "")).trim().toUpperCase(Locale.ROOT);
        return expectedOp.equals(op);
    }

    private static List<String> dependencyIds(CiGacatDerivedCalculationDefinition def) {
        if (def.getDependencyIds() != null && !def.getDependencyIds().isEmpty()) {
            return List.copyOf(def.getDependencyIds());
        }
        return List.copyOf(SafeDerivedExpressionEvaluator.collectDependencies(
                def.getExpressionJson() == null ? Map.of() : def.getExpressionJson()));
    }
}
