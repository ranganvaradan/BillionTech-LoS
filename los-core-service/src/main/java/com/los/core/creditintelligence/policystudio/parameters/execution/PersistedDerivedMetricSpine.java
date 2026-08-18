package com.los.core.creditintelligence.policystudio.parameters.execution;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binds persisted canonical derived metrics ({@code ci_metric_result}) into CPES
 * {@link EvaluationContext} by exact bureau report id.
 *
 * <p>Does not recompute, does not alias, does not look up the latest report,
 * and does not coerce {@code DATA_INSUFFICIENT} to zero.
 */
@Component
@RequiredArgsConstructor
public class PersistedDerivedMetricSpine {

    public static final String PRECOMPUTED_METRICS = "precomputedMetrics";
    public static final String PRECOMPUTED_METRIC_STATUSES = "precomputedMetricStatuses";
    public static final String PRECOMPUTED_METRIC_PROVENANCE = "precomputedMetricProvenance";
    public static final String SOURCE_PERSISTED_CANONICAL_DERIVED = "PERSISTED_CANONICAL_DERIVED";

    public static final String STATUS_VALUE_PRESENT = "VALUE_PRESENT";
    public static final String STATUS_DATA_INSUFFICIENT = "DATA_INSUFFICIENT";
    public static final String STATUS_ERROR = "ERROR";

    private final CiMetricResultRepository metricResultRepository;

    public void bindExactReport(EvaluationContext.Builder builder, UUID bureauReportId) {
        if (builder == null || bureauReportId == null) {
            return;
        }
        List<CiMetricResult> rows = metricResultRepository.findByBureauReportId(bureauReportId);
        Map<String, CiMetricResult> latestByCode = new LinkedHashMap<>();
        rows.stream()
                .filter(r -> r.getMetricCode() != null && !r.getMetricCode().isBlank())
                .sorted(Comparator.comparing(CiMetricResult::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(r -> latestByCode.put(r.getMetricCode(), r));

        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> statuses = new LinkedHashMap<>();
        Map<String, Object> provenance = new LinkedHashMap<>();
        for (Map.Entry<String, CiMetricResult> e : latestByCode.entrySet()) {
            String id = e.getKey();
            CiMetricResult row = e.getValue();
            if (row.getBureauReportId() == null || !bureauReportId.equals(row.getBureauReportId())) {
                continue;
            }
            String outcome = row.getOutcome() == null ? "" : row.getOutcome().trim().toUpperCase();
            Map<String, Object> rowProv = new LinkedHashMap<>();
            rowProv.put("sourceType", SOURCE_PERSISTED_CANONICAL_DERIVED);
            rowProv.put("authority", "ci_metric_result");
            rowProv.put("metricCode", id);
            rowProv.put("bureauReportId", bureauReportId.toString());
            rowProv.put("metricResultId", row.getId() == null ? null : row.getId().toString());
            rowProv.put("persistedOutcome", outcome);
            rowProv.put("dataQualityStatus", row.getDataQualityStatus());
            provenance.put(id, rowProv);

            if (isInsufficient(outcome, row.getDataQualityStatus())) {
                statuses.put(id, STATUS_DATA_INSUFFICIENT);
                continue;
            }
            if ("ERROR".equals(outcome)) {
                statuses.put(id, STATUS_ERROR);
                continue;
            }
            Object unwrapped = unwrap(row.getValue());
            if (unwrapped == null) {
                statuses.put(id, STATUS_DATA_INSUFFICIENT);
                continue;
            }
            values.put(id, unwrapped);
            statuses.put(id, STATUS_VALUE_PRESENT);
        }
        builder.entity(PRECOMPUTED_METRICS, values);
        builder.entity(PRECOMPUTED_METRIC_STATUSES, statuses);
        builder.entity(PRECOMPUTED_METRIC_PROVENANCE, provenance);
    }

    static boolean isInsufficient(String outcome, String quality) {
        String o = outcome == null ? "" : outcome.trim().toUpperCase();
        String q = quality == null ? "" : quality.trim().toUpperCase();
        return "DATA_INSUFFICIENT".equals(o)
                || "INSUFFICIENT_DATA".equals(o)
                || "DATA_INSUFFICIENT".equals(q);
    }

    static Object unwrap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.containsKey("v")) {
            return value.get("v");
        }
        return value;
    }
}
