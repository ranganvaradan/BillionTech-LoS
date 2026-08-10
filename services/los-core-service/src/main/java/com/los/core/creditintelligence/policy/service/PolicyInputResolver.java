package com.los.core.creditintelligence.policy.service;

import com.los.core.creditintelligence.policy.domain.DataStatus;
import com.los.core.creditintelligence.policy.domain.PolicyEvaluationInput;
import com.los.core.creditintelligence.policy.domain.PolicyRuleType;
import com.los.core.creditintelligence.policy.domain.ResolvedInput;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslInterpreterV1;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves FACT/METRIC/RECONCILIATION/POLICY_PARAMETER/APPLICATION_FIELD exclusively
 * from a frozen {@link PolicyEvaluationInput} — never live DB on the P1 core path.
 */
@Service
public class PolicyInputResolver {

    public ResolvedInput resolve(String kind, String reference, PolicyEvaluationInput input,
                                 PolicyRuleType ruleType, boolean allowsDefaulted) {
        String k = kind == null ? "" : kind.toUpperCase(Locale.ROOT);
        Map<String, Object> source = switch (k) {
            case "FACT", "FACT_REF" -> input.facts();
            case "METRIC", "METRIC_REF" -> input.metrics();
            case "RECONCILIATION", "RECON_REF", "RECONCILIATION_REF" -> input.reconciliations();
            case "POLICY_PARAMETER", "POLICY_PARAMETER_REF" -> input.policyParameters();
            case "APPLICATION_FIELD", "APPLICATION_FIELD_REF" -> input.applicationFields();
            default -> Map.of();
        };
        Object raw = source.get(reference);
        if (raw == null && "APPLICATION_FIELD".equals(k)) {
            raw = input.facts().get(reference.startsWith("application.") ? reference : "application." + reference);
        }
        if (raw == null) {
            return missing(reference);
        }
        return fromRaw(reference, raw, ruleType, allowsDefaulted, k);
    }

    /**
     * Build DSL EvaluationContext maps from resolved inputs (value or DI marker map).
     */
    public PolicyDslInterpreterV1.EvaluationContext toDslContext(
            PolicyEvaluationInput input,
            Map<String, ResolvedInput> resolved,
            String onMissing) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        Map<String, Object> facts = new LinkedHashMap<>();
        Map<String, Object> recons = new LinkedHashMap<>();
        Map<String, Object> params = new LinkedHashMap<>();
        Map<String, Object> app = new LinkedHashMap<>();

        // Seed from frozen maps first
        metrics.putAll(toDslMap(input.metrics()));
        facts.putAll(toDslMap(input.facts()));
        recons.putAll(toDslMap(input.reconciliations()));
        params.putAll(toDslMap(input.policyParameters()));
        app.putAll(toDslMap(input.applicationFields()));

        if (resolved != null) {
            for (ResolvedInput ri : resolved.values()) {
                Object dslVal = toDslValue(ri);
                String ref = ri.reference();
                // Heuristic placement by ref prefix / known maps
                if (input.metrics().containsKey(ref)) {
                    metrics.put(ref, dslVal);
                } else if (input.reconciliations().containsKey(ref)) {
                    recons.put(ref, dslVal);
                } else if (input.policyParameters().containsKey(ref)) {
                    params.put(ref, dslVal);
                } else if (input.applicationFields().containsKey(ref) || ref.startsWith("application.")) {
                    app.put(ref, dslVal);
                } else {
                    facts.put(ref, dslVal);
                }
            }
        }

        return PolicyDslInterpreterV1.EvaluationContext.of(
                metrics, facts, params, app, input.clock(),
                onMissing == null ? PolicyDslInterpreterV1.DATA_INSUFFICIENT : onMissing,
                recons);
    }

    public Map<String, Object> toDslMap(Map<String, Object> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (source == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : source.entrySet()) {
            out.put(e.getKey(), unwrapForDsl(e.getValue()));
        }
        return out;
    }

    private Object unwrapForDsl(Object raw) {
        if (!(raw instanceof Map<?, ?> mm)) {
            return raw;
        }
        Object status = mm.get("dataStatus");
        if (status != null && DataStatus.DEFAULTED.name().equalsIgnoreCase(String.valueOf(status))) {
            // leave as structured — hard-rule DI handled by resolve(); soft may use value
            Object v = mm.get("value") != null ? mm.get("value") : mm.get("v");
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("value", v);
            marker.put("dataStatus", DataStatus.DEFAULTED.name());
            return marker;
        }
        if (status != null && (DataStatus.DATA_INSUFFICIENT.name().equalsIgnoreCase(String.valueOf(status))
                || DataStatus.MISSING.name().equalsIgnoreCase(String.valueOf(status)))) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("outcome", PolicyDslInterpreterV1.DATA_INSUFFICIENT);
            marker.put("dataStatus", String.valueOf(status));
            return marker;
        }
        Object v = mm.get("v");
        if (v == null) {
            v = mm.get("value");
        }
        return v != null ? v : raw;
    }

    private Object toDslValue(ResolvedInput ri) {
        if (ri.dataStatus() == DataStatus.DATA_INSUFFICIENT
                || ri.dataStatus() == DataStatus.MISSING
                || ri.dataStatus() == DataStatus.NULL
                || ri.dataStatus() == DataStatus.ERROR) {
            return Map.of("outcome", PolicyDslInterpreterV1.DATA_INSUFFICIENT, "dataStatus", ri.dataStatus().name());
        }
        if (ri.dataStatus() == DataStatus.DEFAULTED) {
            return Map.of("value", ri.value(), "dataStatus", DataStatus.DEFAULTED.name());
        }
        return ri.value();
    }

    private ResolvedInput fromRaw(String reference, Object raw, PolicyRuleType ruleType,
                                  boolean allowsDefaulted, String kind) {
        if (raw instanceof Map<?, ?> mm) {
            Object statusObj = mm.get("dataStatus");
            DataStatus status = parseStatus(statusObj);
            Object value = mm.get("value") != null ? mm.get("value") : mm.get("v");
            String classification = mm.get("classification") == null ? null : String.valueOf(mm.get("classification"));
            BigDecimal confidence = toBd(mm.get("confidence"));
            UUID factRef = toUuid(mm.get("factRef"));
            UUID metricRef = toUuid(mm.get("metricResultRef"));
            UUID reconRef = toUuid(mm.get("reconciliationResultRef"));
            List<String> sources = toStrList(mm.get("sourceRefs"));
            List<String> evidence = toStrList(mm.get("evidenceRefs"));

            // DEFAULTED → for hard rules treat as DATA_INSUFFICIENT unless allowsDefaulted
            if (status == DataStatus.DEFAULTED && isHardLike(ruleType) && !allowsDefaulted) {
                status = DataStatus.DATA_INSUFFICIENT;
                value = null;
            }
            return new ResolvedInput(
                    reference, value,
                    mm.get("valueType") == null ? null : String.valueOf(mm.get("valueType")),
                    mm.get("unit") == null ? null : String.valueOf(mm.get("unit")),
                    mm.get("period") == null ? null : String.valueOf(mm.get("period")),
                    classification, status, confidence, factRef, metricRef, reconRef, sources, evidence);
        }
        return new ResolvedInput(
                reference, raw, raw == null ? null : raw.getClass().getSimpleName(),
                null, null, "VERIFIED", DataStatus.AVAILABLE, BigDecimal.ONE,
                null, null, null, List.of(), List.of());
    }

    private boolean isHardLike(PolicyRuleType ruleType) {
        return ruleType == PolicyRuleType.HARD
                || ruleType == PolicyRuleType.KNOCKOUT
                || ruleType == PolicyRuleType.DATA_QUALITY;
    }

    private ResolvedInput missing(String reference) {
        return new ResolvedInput(
                reference, null, null, null, null, null,
                DataStatus.MISSING, null, null, null, null, List.of(), List.of());
    }

    private DataStatus parseStatus(Object statusObj) {
        if (statusObj == null) {
            return DataStatus.AVAILABLE;
        }
        try {
            return DataStatus.valueOf(String.valueOf(statusObj).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DataStatus.AVAILABLE;
        }
    }

    private BigDecimal toBd(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private UUID toUuid(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof UUID u) {
            return u;
        }
        try {
            return UUID.fromString(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStrList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of(String.valueOf(o));
    }
}
