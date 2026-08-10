package com.los.core.creditintelligence.evaluation;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.evaluation.domain.CiEvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves metrics from a pinned {@link com.los.core.creditintelligence.evaluation.domain.CiMetricResultSet}
 * when an {@link CiEvaluationContext} is present; otherwise falls back to latest-by-createdAt
 * (transitional behavior while purity flags are off).
 */
@Service
@RequiredArgsConstructor
public class PinnedMetricLookup {

    private final CiMetricResultRepository metricResultRepository;
    private final MetricResultSetService metricResultSetService;

    @Transactional(readOnly = true)
    public Optional<CiMetricResult> find(UUID applicationId, String metricCode, CiEvaluationContext ctx) {
        if (metricCode == null || metricCode.isBlank()) {
            return Optional.empty();
        }
        if (ctx != null && ctx.getMetricResultSetId() != null) {
            Map<String, CiMetricResult> pinned = metricResultSetService.lookupBySet(ctx.getMetricResultSetId());
            return Optional.ofNullable(pinned.get(metricCode.trim()));
        }
        // Transitional: live latest lookup when EvaluationContext / pinned set absent
        return metricResultRepository
                .findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(applicationId, metricCode.trim());
    }
}
