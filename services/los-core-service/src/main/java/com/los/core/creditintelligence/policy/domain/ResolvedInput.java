package com.los.core.creditintelligence.policy.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Resolved input for policy DSL evaluation from a frozen {@code PolicyEvaluationInput}.
 */
public record ResolvedInput(
        String reference,
        Object value,
        String valueType,
        String unit,
        String period,
        String classification,
        DataStatus dataStatus,
        BigDecimal confidence,
        UUID factRef,
        UUID metricResultRef,
        UUID reconciliationResultRef,
        List<String> sourceRefs,
        List<String> evidenceRefs
) {
    public ResolvedInput {
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public boolean isDefaulted() {
        return dataStatus == DataStatus.DEFAULTED;
    }

    public boolean isInsufficient() {
        return dataStatus == DataStatus.DATA_INSUFFICIENT
                || dataStatus == DataStatus.MISSING
                || dataStatus == DataStatus.NULL
                || dataStatus == DataStatus.ERROR;
    }
}
