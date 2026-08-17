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
import java.util.Set;

/**
 * AUTHORED_DERIVED producer: active definition → recursive deps via spine →
 * {@link SafeDerivedExpressionEvaluator}. No LLM at runtime.
 *
 * <p>Semantically invalid approved expressions (e.g. MONTHS_SINCE on a COUNT_DPD target)
 * do not claim capability — exact ID remains without an executable producer.
 */
public final class AuthoredDerivedProducer implements ParameterProducer {

    public static final String PRODUCER_ID = "AuthoredDerivedProducer";

    private final AuthoredDefinitionSource definitions;

    public AuthoredDerivedProducer(DerivedCalculationDefinitionService definitionService) {
        this(new AuthoredDefinitionSource() {
            @Override
            public Optional<CiGacatDerivedCalculationDefinition> latestFor(String id, java.util.UUID tenantId) {
                return definitionService.latestFor(id, tenantId);
            }

            @Override
            public Optional<CiGacatDerivedCalculationDefinition> forVersion(
                    String id, java.util.UUID tenantId, int versionNo) {
                return definitionService.forVersion(id, tenantId, versionNo);
            }
        });
    }

    public AuthoredDerivedProducer(AuthoredDefinitionSource definitions) {
        this.definitions = definitions == null ? (id, t) -> Optional.empty() : definitions;
    }

    @FunctionalInterface
    public interface AuthoredDefinitionSource {
        Optional<CiGacatDerivedCalculationDefinition> latestFor(String canonicalParameterId, java.util.UUID tenantId);

        /** Wave-10 pinned lookup; default filters latest by versionNo when present. */
        default Optional<CiGacatDerivedCalculationDefinition> forVersion(
                String canonicalParameterId, java.util.UUID tenantId, int versionNo) {
            return latestFor(canonicalParameterId, tenantId)
                    .filter(d -> d.getVersionNo() != null && d.getVersionNo() == versionNo);
        }
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
        return loadValidDefinition(canonicalParameterId, EvaluationContext.builder().build()).isPresent();
    }

    @Override
    public boolean hasCapability(String canonicalParameterId, EvaluationContext ctx, DependencyResolver resolver) {
        Optional<CiGacatDerivedCalculationDefinition> def =
                loadValidDefinition(canonicalParameterId, ctx);
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
                loadValidDefinition(canonicalParameterId, ctx);
        if (opt.isEmpty()) {
            // Distinguish: definition exists but invalid vs absent / unpinned under forbidLatestFor
            if (ctx != null && Boolean.TRUE.equals(ctx.entities().get("forbidLatestFor"))) {
                return ExecutionResult.notExecutable(canonicalParameterId,
                        "Pinned definition required (forbidLatestFor) for " + canonicalParameterId);
            }
            Optional<CiGacatDerivedCalculationDefinition> raw =
                    definitions.latestFor(canonicalParameterId, ctx.tenantId());
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
        prov.put("operatorChain", SafeDerivedExpressionEvaluator.operatorChain(expr));
        prov.put("dependencyProvenance", depProv);
        if (ctx.evaluationAsOf() != null) {
            prov.put("asOf", ctx.evaluationAsOf().toString());
        }
        // Wave-3: collection op provenance (identity + row counts; no raw-row dump)
        List<Map<String, Object>> collectionInputs = new ArrayList<>();
        for (String dep : deps) {
            Object v = evalInputs.get(dep);
            if (v instanceof java.util.Collection<?> c) {
                Map<String, Object> ci = new LinkedHashMap<>();
                ci.put("inputCollectionIdentity", dep);
                ci.put("inputRowCount", c.size());
                collectionInputs.add(ci);
            }
        }
        if (!collectionInputs.isEmpty()) {
            prov.put("collectionInputs", collectionInputs);
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
            String canonicalParameterId, EvaluationContext ctx) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) {
            return Optional.empty();
        }
        java.util.UUID tenantId = ctx == null ? null : ctx.tenantId();
        Optional<CiGacatDerivedCalculationDefinition> opt;
        if (ctx != null && Boolean.TRUE.equals(ctx.entities().get("forbidLatestFor"))) {
            Object pins = ctx.entities().get("pinnedCalculationDefinitions");
            if (pins instanceof Map<?, ?> m && m.get(canonicalParameterId) != null) {
                int ver;
                try {
                    ver = Integer.parseInt(String.valueOf(m.get(canonicalParameterId)).trim());
                } catch (NumberFormatException e) {
                    return Optional.empty();
                }
                opt = definitions.forVersion(canonicalParameterId, tenantId, ver);
            } else {
                // Target-live pin required for this id — do not silently use latest
                return Optional.empty();
            }
        } else {
            opt = definitions.latestFor(canonicalParameterId, tenantId);
        }
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
        if (!isSpineExecutableDefinition(canonicalParameterId, expr)) {
            return Optional.empty();
        }
        return Optional.of(def);
    }

    private Optional<CiGacatDerivedCalculationDefinition> loadValidDefinition(
            String canonicalParameterId, java.util.UUID tenantId) {
        return loadValidDefinition(canonicalParameterId,
                EvaluationContext.builder().tenantId(tenantId).build());
    }

    /**
     * Shared with surface overlays: a stored definition is spine-executable only when
     * expression op matches target intent (e.g. COUNT_PERIODS_MATCHING for COUNT_DPD_MONTHS).
     */
    public static boolean isSpineExecutableDefinition(
            String canonicalParameterId, Map<String, Object> expr) {
        if (isBuiltInCodeExpression(expr)) {
            return false;
        }
        return expressionMatchesTargetIntent(canonicalParameterId, expr);
    }

    /** Platform BUILT_IN_CODE rows are definition records, not spine formulas. */
    public static boolean isBuiltInCodeExpression(Map<String, Object> expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String type = String.valueOf(expr.getOrDefault("type", "")).trim();
        String calcType = String.valueOf(expr.getOrDefault("calculationType", "")).trim();
        return "BUILT_IN_CODE".equalsIgnoreCase(type) || "BUILT_IN_CODE".equalsIgnoreCase(calcType);
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
            return isOp(expr, "COUNT_PERIODS_MATCHING")
                    || (containsAnyOp(expr, Set.of("COUNT"))
                    && containsAnyOp(expr, Set.of("FILTER", "TRAILING_WINDOW")));
        }
        if ("MONTHS_SINCE_OVERDUE".equals(intent)) {
            return isOp(expr, "MONTHS_SINCE_LAST_MATCH");
        }
        if ("SUM_CC_OVERDUE".equals(intent) || "bureau.cc_overdue_amount".equals(canonicalParameterId)) {
            // Wave-2: generic FILTER/SUM/PROJECT/ADD/REF — no evaluator ID branch
            return containsAnyOp(expr, Set.of("SUM", "ADD", "FILTER", "PROJECT", "REF"));
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

    private static boolean containsAnyOp(Map<String, Object> expression, Set<String> ops) {
        if (expression == null || ops == null || ops.isEmpty()) return false;
        return SafeDerivedExpressionEvaluator.operatorChain(expression).stream().anyMatch(ops::contains);
    }

    private static List<String> dependencyIds(CiGacatDerivedCalculationDefinition def) {
        if (def.getDependencyIds() != null && !def.getDependencyIds().isEmpty()) {
            return List.copyOf(def.getDependencyIds());
        }
        return List.copyOf(SafeDerivedExpressionEvaluator.collectDependencies(
                def.getExpressionJson() == null ? Map.of() : def.getExpressionJson()));
    }
}
