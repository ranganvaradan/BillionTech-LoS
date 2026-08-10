package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.reconciliation.domain.EvidenceStrengthGrade;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationConstants;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.TriangulationStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evidence strength 0–100 + grade. NOT a credit/risk score.
 * Components: source availability, freshness, period completeness, subject match,
 * metric completeness, cross-source alignment, conflicts.
 */
@Component
public class EvidenceStrengthScorer {

    public record StrengthInput(
            boolean bureauAvailable,
            boolean gstAvailable,
            boolean bankAvailable,
            boolean itrAvailable,
            boolean aisAvailable,
            boolean form26AsAvailable,
            BigDecimal freshnessProxy,
            BigDecimal periodCompleteness,
            BigDecimal subjectMatchScore,
            BigDecimal metricCompleteness,
            TriangulationStatus triangulationStatus,
            int materialConflictCount,
            int dataGapCount) {
    }

    public record StrengthResult(
            BigDecimal score,
            EvidenceStrengthGrade grade,
            Map<String, Object> components,
            String methodVersion) {
    }

    public StrengthResult score(StrengthInput input) {
        Map<String, Object> components = new LinkedHashMap<>();
        if (input == null) {
            components.put("reason", "NULL_INPUT");
            return new StrengthResult(BigDecimal.ZERO, EvidenceStrengthGrade.INSUFFICIENT, components,
                    ReconciliationConstants.EVIDENCE_STRENGTH_V1);
        }

        double sourceAvail = 0;
        int sources = 0;
        if (input.bureauAvailable()) {
            sourceAvail += 1;
            sources++;
        }
        if (input.gstAvailable()) {
            sourceAvail += 1;
            sources++;
        }
        if (input.bankAvailable()) {
            sourceAvail += 1;
            sources++;
        }
        if (input.itrAvailable()) {
            sourceAvail += 1;
            sources++;
        }
        if (input.aisAvailable()) {
            sourceAvail += 0.5;
            sources++;
        }
        if (input.form26AsAvailable()) {
            sourceAvail += 0.5;
            sources++;
        }
        // Max weighted sources ≈ 5.0 (4 core + AIS + 26AS half each)
        double sourceScore = Math.min(1.0, sourceAvail / 5.0) * 25.0;
        components.put("sourceAvailability", round(sourceScore));

        double freshness = clamp01(input.freshnessProxy()) * 10.0;
        components.put("freshness", round(freshness));

        double period = clamp01(input.periodCompleteness()) * 15.0;
        components.put("periodCompleteness", round(period));

        double subject = clamp01(input.subjectMatchScore()) * 10.0;
        components.put("subjectMatch", round(subject));

        double metrics = clamp01(input.metricCompleteness()) * 15.0;
        components.put("metricCompleteness", round(metrics));

        double alignment = alignmentScore(input.triangulationStatus()) * 15.0;
        components.put("crossSourceAlignment", round(alignment));

        double conflictPenalty = Math.min(20.0, input.materialConflictCount() * 5.0
                + input.dataGapCount() * 2.0);
        components.put("conflictPenalty", round(conflictPenalty));

        double raw = sourceScore + freshness + period + subject + metrics + alignment - conflictPenalty;
        raw = Math.max(0, Math.min(100, raw));
        BigDecimal score = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
        EvidenceStrengthGrade grade = gradeFor(score);
        components.put("note", "NOT_A_CREDIT_OR_RISK_SCORE");
        components.put("coreSourceCount", sources);
        return new StrengthResult(score, grade, components, ReconciliationConstants.EVIDENCE_STRENGTH_V1);
    }

    public StrengthResult scoreFromResults(
            boolean bureau, boolean gst, boolean bank, boolean itr, boolean ais, boolean form26,
            List<com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult> results,
            TriangulationStatus triangulationStatus) {

        int conflicts = 0;
        int gaps = 0;
        if (results != null) {
            for (var r : results) {
                if (ReconciliationOutcome.CONFLICT.name().equals(r.getOutcome())
                        || ReconciliationOutcome.MATERIAL_VARIANCE.name().equals(r.getOutcome())) {
                    conflicts++;
                }
                if (ReconciliationOutcome.DATA_INSUFFICIENT.name().equals(r.getOutcome())) {
                    gaps++;
                }
            }
        }
        double metricCompleteness = results == null || results.isEmpty()
                ? 0.3
                : Math.min(1.0, results.stream()
                .filter(r -> !ReconciliationOutcome.DATA_INSUFFICIENT.name().equals(r.getOutcome())
                        && !ReconciliationOutcome.ERROR.name().equals(r.getOutcome()))
                .count() / (double) Math.max(results.size(), 1));

        return score(new StrengthInput(
                bureau, gst, bank, itr, ais, form26,
                BigDecimal.valueOf(0.85),
                BigDecimal.valueOf(gst && bank ? 0.9 : 0.6),
                BigDecimal.valueOf(0.9),
                BigDecimal.valueOf(metricCompleteness),
                triangulationStatus,
                conflicts,
                gaps));
    }

    public static EvidenceStrengthGrade gradeFor(BigDecimal score) {
        double s = score != null ? score.doubleValue() : 0;
        if (s >= 80) {
            return EvidenceStrengthGrade.STRONG;
        }
        if (s >= 60) {
            return EvidenceStrengthGrade.ADEQUATE;
        }
        if (s >= 35) {
            return EvidenceStrengthGrade.WEAK;
        }
        return EvidenceStrengthGrade.INSUFFICIENT;
    }

    private static double alignmentScore(TriangulationStatus status) {
        if (status == null) {
            return 0.4;
        }
        return switch (status) {
            case STRONG_ALIGNMENT -> 1.0;
            case REASONABLE_ALIGNMENT -> 0.8;
            case REVIEW_REQUIRED -> 0.45;
            case MATERIAL_CONFLICT -> 0.15;
            case DATA_INSUFFICIENT -> 0.25;
        };
    }

    private static double clamp01(BigDecimal v) {
        if (v == null) {
            return 0.5;
        }
        double d = v.doubleValue();
        if (d < 0) {
            return 0;
        }
        if (d > 1) {
            return 1;
        }
        return d;
    }

    private static double round(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
