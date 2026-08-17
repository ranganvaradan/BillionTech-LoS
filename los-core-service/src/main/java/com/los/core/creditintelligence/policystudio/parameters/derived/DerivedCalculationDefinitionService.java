package com.los.core.creditintelligence.policystudio.parameters.derived;

import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterDefinition;
import com.los.core.creditintelligence.policystudio.parameters.CanonicalParameterRegistry;
import com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter;
import com.los.core.creditintelligence.policystudio.parameters.execution.AuthoredDerivedProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Author / validate / activate safe derived calculations attached to exact GACAT IDs.
 * Lender scope uses tenant_id; never overwrites PLATFORM canonical semantics by mutating GACAT rows.
 */
@Service
@RequiredArgsConstructor
public class DerivedCalculationDefinitionService {

    public static final String SCOPE_PLATFORM = "PLATFORM";
    public static final String SCOPE_LENDER = "LENDER";
    public static final String STATUS_DEFINED = "DEFINED";
    public static final String STATUS_TESTED = "TESTED";
    public static final String STATUS_PRODUCTION_READY = "PRODUCTION_READY";
    public static final String STATUS_RETIRED = "RETIRED";
    public static final String CALCULATION_TYPE_AUTHORED_EXPRESSION = "AUTHORED_EXPRESSION";
    public static final String CALCULATION_TYPE_BUILT_IN_CODE = "BUILT_IN_CODE";

    private final CiGacatDerivedCalculationDefinitionRepository repository;

    private CanonicalParameterRegistry registry() {
        return PolicyStudioConvergencePresenter.registry();
    }

    @Transactional(readOnly = true)
    public Optional<CiGacatDerivedCalculationDefinition> latestFor(
            String canonicalParameterId, UUID tenantId) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) return Optional.empty();
        if (tenantId != null) {
            Optional<CiGacatDerivedCalculationDefinition> lender =
                    repository.findFirstByCanonicalParameterIdAndTenantIdAndStatusNotOrderByVersionNoDesc(
                            canonicalParameterId, tenantId, STATUS_RETIRED);
            if (lender.isPresent()) return lender;
        }
        return repository.findFirstByCanonicalParameterIdAndTenantIdIsNullAndStatusNotOrderByVersionNoDesc(
                canonicalParameterId, STATUS_RETIRED);
    }

    /** Wave-10: exact version pin for target-live — never silent latest. */
    @Transactional(readOnly = true)
    public Optional<CiGacatDerivedCalculationDefinition> forVersion(
            String canonicalParameterId, UUID tenantId, int versionNo) {
        if (canonicalParameterId == null || canonicalParameterId.isBlank()) return Optional.empty();
        return repository.findByCanonicalParameterIdOrderByVersionNoDesc(canonicalParameterId.trim()).stream()
                .filter(d -> d.getVersionNo() != null && d.getVersionNo() == versionNo)
                .filter(d -> !STATUS_RETIRED.equalsIgnoreCase(d.getStatus() == null ? "" : d.getStatus()))
                .filter(d -> tenantId == null
                        ? d.getTenantId() == null
                        : (tenantId.equals(d.getTenantId()) || d.getTenantId() == null))
                .findFirst();
    }

    @Transactional
    public Map<String, Object> saveDraft(Map<String, Object> body, UUID tenantId, String actor) {
        String canonicalId = str(body.get("canonicalParameterId"));
        if (canonicalId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "canonicalParameterId required");
        }
        String scope = str(body.getOrDefault("scope", SCOPE_PLATFORM)).toUpperCase();
        if (!SCOPE_PLATFORM.equals(scope) && !SCOPE_LENDER.equals(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scope must be PLATFORM or LENDER");
        }
        if (SCOPE_LENDER.equals(scope) && tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tenantId required for LENDER scope");
        }
        if (SCOPE_PLATFORM.equals(scope)) {
            // Attach to existing canonical — do not invent a second global id.
            if (registry().findById(canonicalId).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown GACAT parameter: " + canonicalId);
            }
        } else {
            // Lender metrics: require tenant-prefixed identity to avoid polluting global namespace.
            if (!canonicalId.startsWith("lender.") && !canonicalId.contains(".lender.")) {
                // Allow vikasam.* style tenant codes
                if (!canonicalId.matches("^[a-z][a-z0-9_]*\\.[a-z0-9_.]+$")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Lender derived parameter id must be namespaced (e.g. vikasam.adjusted_monthly_income)");
                }
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> expression = body.get("expression") instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m)
                : new LinkedHashMap<>();
        String calcType = str(body.get("calculationType"));
        if (calcType.isBlank() && AuthoredDerivedProducer.isBuiltInCodeExpression(expression)) {
            calcType = CALCULATION_TYPE_BUILT_IN_CODE;
        }
        if (calcType.isBlank()) {
            calcType = CALCULATION_TYPE_AUTHORED_EXPRESSION;
        }
        boolean builtInCode = CALCULATION_TYPE_BUILT_IN_CODE.equalsIgnoreCase(calcType)
                || AuthoredDerivedProducer.isBuiltInCodeExpression(expression);
        if (builtInCode) {
            calcType = CALCULATION_TYPE_BUILT_IN_CODE;
            if (!SCOPE_PLATFORM.equals(scope)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "BUILT_IN_CODE definitions are platform seed only");
            }
            if (expression.isEmpty()) {
                expression.put("type", CALCULATION_TYPE_BUILT_IN_CODE);
                expression.put("executor", "BureauMetricService");
                expression.put("calculationType", CALCULATION_TYPE_BUILT_IN_CODE);
                expression.put("metricCode", canonicalId);
            }
        }

        Set<String> deps;
        if (builtInCode) {
            deps = new LinkedHashSet<>();
            Object rawDeps = body.get("dependencies");
            if (rawDeps instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        deps.add(String.valueOf(o).trim());
                    }
                }
            }
            if (deps.isEmpty() && body.get("dependencyIds") instanceof List<?> list) {
                for (Object o : list) {
                    if (o != null && !String.valueOf(o).isBlank()) {
                        deps.add(String.valueOf(o).trim());
                    }
                }
            }
        } else {
            Set<String> allowed = allKnownCanonicalIds();
            List<String> errors = SafeDerivedExpressionEvaluator.validate(expression, allowed);
            if (!errors.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", errors));
            }
            if (SCOPE_PLATFORM.equals(scope)) {
                CanonicalParameterDefinition target = registry().findById(canonicalId).orElse(null);
                if (target != null) {
                    DerivedCalculationSemanticCompatibility.Result compat =
                            DerivedCalculationSemanticCompatibility.assessExpression(
                                    target, expression, id -> registry().findById(id).orElse(null));
                    if (!compat.compatible()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Semantic/dimensional incompatibility: " + String.join("; ", compat.failures()));
                    }
                }
            }
            deps = SafeDerivedExpressionEvaluator.collectDependencies(expression);
        }
        detectCycle(canonicalId, deps, tenantId, scope);

        int nextVer = repository.findByCanonicalParameterIdOrderByVersionNoDesc(canonicalId).stream()
                .filter(d -> scopeEquals(d, scope, tenantId))
                .map(CiGacatDerivedCalculationDefinition::getVersionNo)
                .findFirst()
                .orElse(0) + 1;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("arbitraryCodeAllowed", false);
        if (builtInCode) {
            metadata.put("executionAuthority", "BureauMetricService");
            metadata.put("producer", "BuiltInBureauMetricProducer");
        }

        CiGacatDerivedCalculationDefinition row = CiGacatDerivedCalculationDefinition.builder()
                .tenantId(SCOPE_LENDER.equals(scope) ? tenantId : null)
                .canonicalParameterId(canonicalId)
                .scope(scope)
                .status(STATUS_DEFINED)
                .calculationType(calcType)
                .resultType(str(body.getOrDefault("resultType", "NUMBER")))
                .unit(blankToNull(str(body.get("unit"))))
                .description(blankToNull(str(body.get("description"))))
                .expressionJson(expression)
                .dependencyIds(new ArrayList<>(deps))
                .versionNo(nextVer)
                .createdBy(actor)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .metadata(metadata)
                .build();
        row = repository.save(row);
        return toView(row);
    }

    @Transactional
    public Map<String, Object> testWithSample(UUID id, Map<String, Object> sampleInputs) {
        CiGacatDerivedCalculationDefinition row = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "definition not found"));
        if (isBuiltInCodeRow(row)) {
            Map<String, Object> out = toView(row);
            out.put("evaluation", Map.of(
                    "status", "NOT_APPLICABLE",
                    "reason", "BUILT_IN_CODE is executed by BureauMetricService, not as a spine formula"));
            return out;
        }
        SafeDerivedExpressionEvaluator.EvalResult result =
                SafeDerivedExpressionEvaluator.evaluate(row.getExpressionJson(), sampleInputs);
        if (SafeDerivedExpressionEvaluator.STATUS_OK.equals(result.status())
                && STATUS_DEFINED.equals(row.getStatus())) {
            row.setStatus(STATUS_TESTED);
            row.setUpdatedAt(Instant.now());
            repository.save(row);
        }
        Map<String, Object> out = toView(row);
        out.put("evaluation", result.toMap());
        return out;
    }

    @Transactional
    public Map<String, Object> markProductionReady(UUID id) {
        CiGacatDerivedCalculationDefinition row = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "definition not found"));
        if (!STATUS_TESTED.equals(row.getStatus()) && !STATUS_PRODUCTION_READY.equals(row.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Definition must be TESTED before PRODUCTION_READY");
        }
        row.setStatus(STATUS_PRODUCTION_READY);
        row.setUpdatedAt(Instant.now());
        repository.save(row);
        return toView(row);
    }

    /** Retire an active definition so it is no longer executable via W6/latestFor. */
    @Transactional
    public Map<String, Object> retire(UUID id, String actor) {
        CiGacatDerivedCalculationDefinition row = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "definition not found"));
        if (STATUS_RETIRED.equals(row.getStatus())) {
            return toView(row);
        }
        row.setStatus(STATUS_RETIRED);
        row.setUpdatedAt(Instant.now());
        Map<String, Object> meta = row.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(row.getMetadata());
        meta.put("retiredBy", actor);
        meta.put("retiredAt", Instant.now().toString());
        meta.put("retireReason", "semantic_incompatibility");
        row.setMetadata(meta);
        repository.save(row);
        return toView(row);
    }

    @Transactional(readOnly = true)
    public SafeDerivedExpressionEvaluator.EvalResult evaluateCanonical(
            String canonicalParameterId, UUID tenantId, Map<String, Object> inputs) {
        return latestFor(canonicalParameterId, tenantId)
                .filter(d -> STATUS_TESTED.equals(d.getStatus()) || STATUS_PRODUCTION_READY.equals(d.getStatus()))
                .map(d -> {
                    if (isBuiltInCodeRow(d)) {
                        return new SafeDerivedExpressionEvaluator.EvalResult(
                                SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT,
                                null,
                                "BUILT_IN_CODE is executed by BureauMetricService, not as a spine formula");
                    }
                    return SafeDerivedExpressionEvaluator.evaluate(d.getExpressionJson(), inputs);
                })
                .orElseGet(() -> new SafeDerivedExpressionEvaluator.EvalResult(
                        SafeDerivedExpressionEvaluator.STATUS_DATA_INSUFFICIENT,
                        null,
                        "No tested/production derived calculation for " + canonicalParameterId));
    }

    private void detectCycle(
            String targetId, Set<String> deps, UUID tenantId, String scope) {
        Set<String> visiting = new HashSet<>();
        visiting.add(targetId);
        for (String dep : deps) {
            walkCycle(dep, tenantId, visiting, new HashSet<>());
        }
    }

    private void walkCycle(String id, UUID tenantId, Set<String> stack, Set<String> seen) {
        if (!stack.add(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cycle detected involving " + id);
        }
        if (!seen.add(id)) {
            stack.remove(id);
            return;
        }
        latestFor(id, tenantId).ifPresent(def -> {
            for (String dep : def.getDependencyIds() == null ? List.<String>of() : def.getDependencyIds()) {
                walkCycle(dep, tenantId, stack, seen);
            }
        });
        stack.remove(id);
    }

    private Set<String> allKnownCanonicalIds() {
        Set<String> ids = new HashSet<>();
        for (CanonicalParameterDefinition d : registry().all()) {
            ids.add(d.id());
        }
        return ids;
    }

    private static boolean scopeEquals(CiGacatDerivedCalculationDefinition d, String scope, UUID tenantId) {
        if (!scope.equalsIgnoreCase(d.getScope())) return false;
        if (SCOPE_PLATFORM.equals(scope)) return d.getTenantId() == null;
        return tenantId != null && tenantId.equals(d.getTenantId());
    }

    public Map<String, Object> toView(CiGacatDerivedCalculationDefinition row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("canonicalParameterId", row.getCanonicalParameterId());
        m.put("scope", row.getScope());
        m.put("status", row.getStatus());
        m.put("resultType", row.getResultType());
        m.put("unit", row.getUnit());
        m.put("description", row.getDescription());
        m.put("expression", row.getExpressionJson());
        m.put("dependencies", row.getDependencyIds());
        m.put("versionNo", row.getVersionNo());
        m.put("calculationType", row.getCalculationType() == null
                ? CALCULATION_TYPE_AUTHORED_EXPRESSION : row.getCalculationType());
        m.put("arbitraryCodeAllowed", false);
        m.put("productionReadyImpliesTested", true);
        return m;
    }

    private static boolean isBuiltInCodeRow(CiGacatDerivedCalculationDefinition d) {
        if (d == null) return false;
        if (CALCULATION_TYPE_BUILT_IN_CODE.equalsIgnoreCase(
                d.getCalculationType() == null ? "" : d.getCalculationType())) {
            return true;
        }
        return AuthoredDerivedProducer.isBuiltInCodeExpression(d.getExpressionJson());
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
