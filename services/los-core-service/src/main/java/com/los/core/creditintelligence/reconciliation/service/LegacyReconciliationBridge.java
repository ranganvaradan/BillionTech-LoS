package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.core.domain.CiMetricResult;
import com.los.core.creditintelligence.core.repository.CiMetricResultRepository;
import com.los.core.creditintelligence.gst.service.GstMetricService;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.DiscrepancySeverity;
import com.los.core.creditintelligence.reconciliation.domain.ExplanationCode;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationDataStatus;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.SubjectMatchStatus;
import com.los.core.creditintelligence.tax.service.TaxMetricService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Wraps C2 GSTR1/GSTR3B and C4 ITR AIS/26AS variance metrics into CiReconciliationResult with outcome parity.
 */
@Component
@RequiredArgsConstructor
public class LegacyReconciliationBridge {

    private final CiMetricResultRepository metricResultRepository;
    private final ReconciliationExplanationBuilder explanationBuilder;
    private final DiscrepancySeverityClassifier severityClassifier;

    public Optional<CiReconciliationResult> wrapIfLegacy(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            CiReconciliationDefinition def) {

        if (def == null || def.getMetadata() == null) {
            return Optional.empty();
        }
        Object legacyCode = def.getMetadata().get("legacyMetricCode");
        if (legacyCode == null) {
            return Optional.empty();
        }
        String code = String.valueOf(legacyCode);
        Optional<CiMetricResult> metric =
                metricResultRepository.findFirstByApplicationIdAndMetricCodeOrderByCreatedAtDesc(
                        applicationId, code);
        if (metric.isEmpty()) {
            return Optional.of(diWrap(tenantId, applicationId, factSnapshotId, evaluationId, def, code));
        }
        return Optional.of(wrapMetric(tenantId, applicationId, factSnapshotId, evaluationId, def, metric.get()));
    }

    public CiReconciliationResult wrapMetric(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            CiReconciliationDefinition def,
            CiMetricResult metric) {

        Instant now = Instant.now();
        String outcome = mapOutcome(metric.getOutcome());
        BigDecimal pct = MetricValueExtractor.extract(metric.getValue());
        Map<String, Object> value = metric.getValue() != null ? metric.getValue() : Map.of();
        Map<String, Object> evidence = metric.getEvidence() != null ? metric.getEvidence() : Map.of();

        BigDecimal left = firstBd(value, evidence, "gstr1Sum", "left");
        BigDecimal right = firstBd(value, evidence, "gstr3bSum", "right");

        List<ExplanationCode> codes = new ArrayList<>();
        codes.add(ExplanationCode.LEGACY_METRIC_WRAP);
        ReconciliationOutcome reconOutcome;
        try {
            reconOutcome = ReconciliationOutcome.valueOf(outcome);
        } catch (Exception e) {
            reconOutcome = ReconciliationOutcome.DATA_INSUFFICIENT;
        }
        if (reconOutcome == ReconciliationOutcome.MATCH) {
            codes.add(ExplanationCode.VALUES_ALIGNED);
        } else if (reconOutcome == ReconciliationOutcome.ACCEPTABLE_VARIANCE) {
            codes.add(ExplanationCode.WITHIN_WARNING_TOLERANCE);
        } else if (reconOutcome == ReconciliationOutcome.MATERIAL_VARIANCE) {
            codes.add(ExplanationCode.MATERIAL_DIFFERENCE);
        } else if (reconOutcome == ReconciliationOutcome.CONFLICT) {
            codes.add(ExplanationCode.CONFLICTING_SOURCES);
        }

        var explanation = explanationBuilder.build(
                def.getReconciliationCode(), reconOutcome, left, right, pct, codes);
        DiscrepancySeverity severity = severityClassifier.classify(
                reconOutcome, pct, left != null && right != null ? left.subtract(right).abs() : null);

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("legacyMetricCode", metric.getMetricCode());
        trace.put("legacyOutcome", metric.getOutcome());
        trace.put("legacyValue", value);
        trace.put("parity", true);

        boolean di = reconOutcome == ReconciliationOutcome.DATA_INSUFFICIENT;
        return CiReconciliationResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .evaluationId(evaluationId)
                .reconciliationDefinitionId(def.getId())
                .reconciliationCode(def.getReconciliationCode())
                .definitionVersion(def.getVersion())
                .leftMetricCode(metric.getMetricCode())
                .leftMetricVersion(metric.getMetricVersion())
                .leftValue(left)
                .rightMetricCode(metric.getMetricCode())
                .rightMetricVersion(metric.getMetricVersion())
                .rightValue(right)
                .normalizedLeftValue(left)
                .normalizedRightValue(right)
                .absoluteVariance(left != null && right != null ? left.subtract(right).abs() : null)
                .percentageVariance(pct)
                .varianceDenominatorMethod(def.getVarianceMethod())
                .outcome(reconOutcome.name())
                .severity(severity.name())
                .explanation(explanation.freeText())
                .explanationCodes(new ArrayList<>(explanation.explanationCodes()))
                .probableCauses(new ArrayList<>(explanation.probableCauses()))
                .evidenceRefs(List.of(Map.of("metricResultId", metric.getId() != null ? metric.getId().toString() : "")))
                .evidenceGroupIds(List.of())
                .humanReviewRequired(reconOutcome == ReconciliationOutcome.MATERIAL_VARIANCE
                        || reconOutcome == ReconciliationOutcome.CONFLICT)
                .dataStatus(di ? ReconciliationDataStatus.MISSING_BOTH.name()
                        : ReconciliationDataStatus.COMPLETE.name())
                .subjectMatchStatus(SubjectMatchStatus.UNKNOWN.name())
                .confidence(di ? BigDecimal.ZERO : BigDecimal.valueOf(0.9))
                .toleranceVersion("LEGACY_PARITY_V1")
                .executedAt(now)
                .executionDurationMs(0L)
                .trace(trace)
                .metadata(Map.of(
                        "migratedFrom", String.valueOf(def.getMetadata().getOrDefault("migratedFrom", "")),
                        "legacyMetricCode", metric.getMetricCode()))
                .leftSourceRefs(List.of())
                .rightSourceRefs(List.of())
                .build();
    }

    /**
     * Maps legacy GST/Tax metric outcomes to reconciliation outcomes (identity for shared names).
     */
    public static String mapOutcome(String legacyOutcome) {
        if (legacyOutcome == null || legacyOutcome.isBlank()) {
            return ReconciliationOutcome.DATA_INSUFFICIENT.name();
        }
        return switch (legacyOutcome) {
            case "MATCH", "ACCEPTABLE_VARIANCE", "MATERIAL_VARIANCE", "CONFLICT",
                 "DATA_INSUFFICIENT", "NOT_APPLICABLE" -> legacyOutcome;
            case "PASS" -> ReconciliationOutcome.MATCH.name();
            case "WARN" -> ReconciliationOutcome.ACCEPTABLE_VARIANCE.name();
            case "FAIL" -> ReconciliationOutcome.CONFLICT.name();
            case "REFER" -> ReconciliationOutcome.MATERIAL_VARIANCE.name();
            default -> ReconciliationOutcome.DATA_INSUFFICIENT.name();
        };
    }

    public static boolean isLegacyCode(String reconciliationCode) {
        return ReconciliationConstants.XSRC_GSTR1_GSTR3B_TURNOVER.equals(reconciliationCode)
                || ReconciliationConstants.XSRC_ITR_AIS_INCOME.equals(reconciliationCode)
                || ReconciliationConstants.XSRC_ITR_26AS_TDS.equals(reconciliationCode);
    }

    public static String legacyMetricFor(String reconciliationCode) {
        if (ReconciliationConstants.XSRC_GSTR1_GSTR3B_TURNOVER.equals(reconciliationCode)) {
            return GstMetricService.VARIANCE;
        }
        if (ReconciliationConstants.XSRC_ITR_AIS_INCOME.equals(reconciliationCode)) {
            return TaxMetricService.XSRC_AIS_INCOME;
        }
        if (ReconciliationConstants.XSRC_ITR_26AS_TDS.equals(reconciliationCode)) {
            return TaxMetricService.XSRC_26AS_TDS;
        }
        return null;
    }

    private CiReconciliationResult diWrap(
            UUID tenantId, UUID applicationId, UUID factSnapshotId, UUID evaluationId,
            CiReconciliationDefinition def, String legacyCode) {
        Instant now = Instant.now();
        var explanation = explanationBuilder.build(
                def.getReconciliationCode(),
                ReconciliationOutcome.DATA_INSUFFICIENT,
                null, null, null,
                List.of(ExplanationCode.LEGACY_METRIC_WRAP, ExplanationCode.MISSING_LEFT_OPERAND));
        return CiReconciliationResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .evaluationId(evaluationId)
                .reconciliationDefinitionId(def.getId())
                .reconciliationCode(def.getReconciliationCode())
                .definitionVersion(def.getVersion())
                .outcome(ReconciliationOutcome.DATA_INSUFFICIENT.name())
                .severity(DiscrepancySeverity.LOW.name())
                .explanation(explanation.freeText())
                .explanationCodes(new ArrayList<>(explanation.explanationCodes()))
                .probableCauses(List.of())
                .evidenceRefs(List.of())
                .evidenceGroupIds(List.of())
                .humanReviewRequired(false)
                .dataStatus(ReconciliationDataStatus.MISSING_BOTH.name())
                .subjectMatchStatus(SubjectMatchStatus.UNKNOWN.name())
                .confidence(BigDecimal.ZERO)
                .toleranceVersion("LEGACY_PARITY_V1")
                .executedAt(now)
                .executionDurationMs(0L)
                .trace(Map.of("legacyMetricCode", legacyCode, "missing", true))
                .metadata(Map.of("legacyMetricCode", legacyCode))
                .leftSourceRefs(List.of())
                .rightSourceRefs(List.of())
                .build();
    }

    private static BigDecimal firstBd(Map<String, Object> value, Map<String, Object> evidence, String... keys) {
        for (String k : keys) {
            BigDecimal v = MetricValueExtractor.extractOrNull(value.get(k));
            if (v != null) {
                return v;
            }
            v = MetricValueExtractor.extractOrNull(evidence.get(k));
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
