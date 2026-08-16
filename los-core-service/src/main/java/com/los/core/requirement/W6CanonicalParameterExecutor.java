package com.los.core.requirement;

import com.los.core.creditintelligence.policystudio.parameters.execution.CanonicalParameterExecutionService;
import com.los.core.creditintelligence.policystudio.parameters.execution.EvaluationContext;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionCapabilityAuthority;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionResult;
import com.los.core.creditintelligence.policystudio.parameters.execution.ExecutionStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * W6 post-acquisition canonical execution — spine only.
 * Source acquisition success does not imply parameter available.
 */
@Service
public class W6CanonicalParameterExecutor {

    public record ParameterExecutionView(
            String canonicalParameterId,
            ExecutionStatus status,
            Object value,
            String producerId,
            String producerType,
            String exactProducerPath,
            Map<String, Object> provenance,
            String reason,
            boolean requirementSatisfied) {

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("canonicalParameterId", canonicalParameterId);
            m.put("executionStatus", status == null ? null : status.name());
            m.put("value", value);
            m.put("producerId", producerId);
            m.put("producerType", producerType);
            m.put("exactProducerPath", exactProducerPath);
            m.put("provenance", provenance);
            m.put("reason", reason);
            m.put("requirementSatisfied", requirementSatisfied);
            m.put("executionAuthority", "CanonicalParameterExecutionService");
            return m;
        }
    }

    public ParameterExecutionView execute(String canonicalParameterId, EvaluationContext ctx) {
        CanonicalParameterExecutionService spine = ExecutionCapabilityAuthority.require();
        ExecutionResult er = spine.resolveAndExecute(canonicalParameterId, ctx);
        boolean ok = er.valueAvailable();
        return new ParameterExecutionView(
                er.canonicalParameterId(),
                er.status(),
                er.value(),
                er.producerId(),
                er.producerType() == null ? null : er.producerType().name(),
                er.exactProducerPath(),
                er.provenance(),
                er.reason(),
                ok);
    }
}
