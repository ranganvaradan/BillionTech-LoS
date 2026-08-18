package com.los.core.creditintelligence.policystudio.runtime.canonicalshadow;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalPolicyResult;
import com.los.core.creditintelligence.policystudio.runtime.CanonicalRuleResult;
import com.los.core.creditintelligence.policystudio.runtime.canonicalconfig.CanonicalApplicationConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One CPES execution row per participating exact canonical parameter ID.
 * Does not collapse a multi-operand rule onto the first operand only.
 */
public final class CanonicalShadowParameterEvidence {

    private CanonicalShadowParameterEvidence() {}

    public static List<Map<String, Object>> rows(
            CanonicalPolicyResult policy,
            CanonicalApplicationConfiguration freeze,
            EvaluationContext spine,
            CanonicalParameterExecutionService cpes) {
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        if (policy == null || cpes == null || spine == null) {
            return List.of();
        }
        for (CanonicalRuleResult rr : policy.ruleResults()) {
            if (rr == null || rr.canonicalParameterIds() == null) {
                continue;
            }
            for (String id : rr.canonicalParameterIds()) {
                if (id == null || id.isBlank() || byId.containsKey(id)) {
                    continue;
                }
                ExecutionResult er = cpes.resolveAndExecute(id, spine);
                byId.put(id, toRow(id, er, freeze, spine));
            }
        }
        return new ArrayList<>(byId.values());
    }

    static Map<String, Object> toRow(
            String id,
            ExecutionResult er,
            CanonicalApplicationConfiguration freeze,
            EvaluationContext spine) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parameterId", id);
        if (er != null) {
            row.put("canonicalStatus", er.status() == null ? null : er.status().name());
            row.put("canonicalValue", er.value());
            row.put("valueAvailable", er.valueAvailable());
            row.put("calculationAuthority", er.producerId());
            row.put("exactProducerPath", er.exactProducerPath());
            row.put("reason", er.reason());
            Map<String, Object> prov = er.provenance() == null ? Map.of() : er.provenance();
            row.put("provenanceKeys", new ArrayList<>(prov.keySet()));
        }
        if (freeze != null) {
            row.put("sourceReportId", freeze.bureauReportId() == null ? null : freeze.bureauReportId().toString());
            row.put("evaluationAsOf", freeze.evaluationAsOf() == null ? null : freeze.evaluationAsOf().toString());
        }
        if (spine != null) {
            Object defId = spine.entities().get("pinnedCalculationDefinitionIds");
            if (defId instanceof Map<?, ?> m) {
                row.put("calculationDefinitionId", m.get(id));
            }
        }
        return row;
    }
}
