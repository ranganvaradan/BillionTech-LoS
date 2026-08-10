package com.los.core.creditintelligence.reconciliation.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationDefinition;
import com.los.core.creditintelligence.reconciliation.domain.CiReconciliationResult;
import com.los.core.creditintelligence.reconciliation.domain.DiscrepancySeverity;
import com.los.core.creditintelligence.reconciliation.domain.ExplanationCode;
import com.los.core.creditintelligence.reconciliation.domain.PeriodAlignmentStrategy;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationDataStatus;
import com.los.core.creditintelligence.reconciliation.domain.ReconciliationOutcome;
import com.los.core.creditintelligence.reconciliation.domain.SubjectMatchStatus;
import com.los.core.creditintelligence.reconciliation.domain.VarianceMethod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Evaluates a single reconciliation definition against resolved operands + period alignment.
 */
@Component
@RequiredArgsConstructor
public class ReconciliationEvaluator {

    private final PeriodAlignmentService periodAlignmentService;
    private final VarianceCalculator varianceCalculator;
    private final ReconciliationConfidenceCalculator confidenceCalculator;
    private final ReconciliationExplanationBuilder explanationBuilder;
    private final DiscrepancySeverityClassifier severityClassifier;
    private final CreditIntelligenceProperties properties;

    public CiReconciliationResult evaluate(
            UUID tenantId,
            UUID applicationId,
            UUID factSnapshotId,
            UUID evaluationId,
            CiReconciliationDefinition def,
            OperandResolver.ResolvedOperand left,
            OperandResolver.ResolvedOperand right,
            SubjectMatchStatus subjectMatch,
            LocalDate asOf) {

        long start = System.currentTimeMillis();
        Instant executedAt = Instant.now();

        try {
            if (subjectMatch == SubjectMatchStatus.MISMATCH) {
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.CONFLICTED_SOURCE,
                        List.of(ExplanationCode.SUBJECT_MISMATCH),
                        subjectMatch, executedAt, start, Map.of("reason", "SUBJECT_MISMATCH"));
            }

            if ((left != null && left.presumptiveItr() || right != null && right.presumptiveItr())
                    && def.getCategory() != null
                    && "TURNOVER".equalsIgnoreCase(def.getCategory())) {
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.PARTIAL,
                        List.of(ExplanationCode.PRESUMPTIVE_ITR),
                        subjectMatch != null ? subjectMatch : SubjectMatchStatus.UNKNOWN,
                        executedAt, start, Map.of("reason", "PRESUMPTIVE_ITR_NOT_COMPARABLE"));
            }

            boolean leftMissing = left == null || !left.present() || left.value() == null;
            boolean rightMissing = right == null || !right.present() || right.value() == null;
            if (leftMissing && rightMissing) {
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.MISSING_BOTH,
                        List.of(ExplanationCode.MISSING_LEFT_OPERAND, ExplanationCode.MISSING_RIGHT_OPERAND),
                        subjectMatch != null ? subjectMatch : SubjectMatchStatus.UNKNOWN,
                        executedAt, start, Map.of());
            }
            if (leftMissing) {
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.MISSING_LEFT,
                        List.of(ExplanationCode.MISSING_LEFT_OPERAND),
                        subjectMatch != null ? subjectMatch : SubjectMatchStatus.UNKNOWN,
                        executedAt, start, Map.of());
            }
            if (rightMissing) {
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.MISSING_RIGHT,
                        List.of(ExplanationCode.MISSING_RIGHT_OPERAND),
                        subjectMatch != null ? subjectMatch : SubjectMatchStatus.UNKNOWN,
                        executedAt, start, Map.of());
            }

            PeriodAlignmentStrategy strategy = parseStrategy(def.getPeriodAlignmentStrategy());
            PeriodAlignmentService.PeriodWindow leftWindow =
                    new PeriodAlignmentService.PeriodWindow(left.periodFrom(), left.periodTo());
            PeriodAlignmentService.PeriodWindow rightWindow =
                    new PeriodAlignmentService.PeriodWindow(right.periodFrom(), right.periodTo());
            PeriodAlignmentService.PeriodWindow requested = PeriodAlignmentService.trailing12(asOf);

            PeriodAlignmentService.AlignmentResult alignment =
                    periodAlignmentService.align(strategy, leftWindow, rightWindow, requested);

            if (!alignment.comparable()) {
                List<ExplanationCode> codes = new ArrayList<>();
                codes.add(ExplanationCode.PERIOD_MISMATCH);
                if (alignment.excludedMonths() != null
                        && alignment.excludedMonths().stream().anyMatch(s -> s.contains("PARTIAL"))) {
                    codes.add(ExplanationCode.PARTIAL_GST);
                }
                return diResult(tenantId, applicationId, factSnapshotId, evaluationId, def,
                        left, right, ReconciliationDataStatus.PERIOD_MISMATCH, codes,
                        subjectMatch != null ? subjectMatch : SubjectMatchStatus.UNKNOWN,
                        executedAt, start, alignment.trace());
            }

            VarianceMethod method = parseMethod(def.getVarianceMethod());
            VarianceCalculator.VarianceResult variance =
                    varianceCalculator.calculate(left.value(), right.value(), method);

            Tolerances tol = resolveTolerances(def);
            ReconciliationOutcome outcome = classifyOutcome(
                    variance.percentageVariance(), tol.matchPct, tol.warningPct, tol.materialPct);

            List<ExplanationCode> codes = new ArrayList<>();
            if (outcome == ReconciliationOutcome.MATCH) {
                codes.add(ExplanationCode.VALUES_ALIGNED);
            } else if (outcome == ReconciliationOutcome.ACCEPTABLE_VARIANCE) {
                codes.add(ExplanationCode.WITHIN_WARNING_TOLERANCE);
                if (left.value().compareTo(right.value()) > 0) {
                    codes.add(ExplanationCode.RECEIVABLE_COLLECTION_LAG);
                }
            } else if (outcome == ReconciliationOutcome.MATERIAL_VARIANCE) {
                codes.add(ExplanationCode.MATERIAL_DIFFERENCE);
            } else if (outcome == ReconciliationOutcome.CONFLICT) {
                codes.add(ExplanationCode.CONFLICTING_SOURCES);
            }
            if (alignment.coverage() != null && alignment.coverage().compareTo(new BigDecimal("0.85")) < 0) {
                codes.add(ExplanationCode.PARTIAL_BANK_STATEMENT);
            }

            BigDecimal confidence = confidenceCalculator.calculate(
                    new ReconciliationConfidenceCalculator.ConfidenceInput(
                            left.confidence(),
                            right.confidence(),
                            alignment.coverage() != null ? alignment.coverage() : left.completeness(),
                            BigDecimal.valueOf(0.9)));

            BigDecimal minConf = def.getMinimumConfidence() != null
                    ? def.getMinimumConfidence() : BigDecimal.valueOf(0.4);
            ReconciliationDataStatus dataStatus = ReconciliationDataStatus.COMPLETE;
            if (confidence.compareTo(minConf) < 0) {
                dataStatus = ReconciliationDataStatus.LOW_CONFIDENCE;
                codes.add(ExplanationCode.SOURCE_LOW_CONFIDENCE);
            }
            if (alignment.coverage() != null && alignment.coverage().compareTo(new BigDecimal("0.99")) < 0) {
                dataStatus = ReconciliationDataStatus.PARTIAL;
            }

            DiscrepancySeverity severity = severityClassifier.classify(
                    outcome, variance.percentageVariance(), variance.absoluteVariance());

            var explanation = explanationBuilder.build(
                    def.getReconciliationCode(), outcome, left.value(), right.value(),
                    variance.percentageVariance(), codes);

            boolean review = outcome == ReconciliationOutcome.MATERIAL_VARIANCE
                    || outcome == ReconciliationOutcome.CONFLICT
                    || severity == DiscrepancySeverity.HIGH
                    || severity == DiscrepancySeverity.CRITICAL;

            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("alignment", alignment.trace());
            trace.put("varianceMethod", method.name());
            trace.put("denominatorNote", variance.denominatorNote());
            trace.put("confidenceMethod", confidenceCalculator.methodVersion());
            trace.put("explanationMethod", explanationBuilder.methodVersion());
            trace.put("facts", explanation.facts());
            trace.put("deterministicExplanations", explanation.deterministicExplanations());

            return CiReconciliationResult.builder()
                    .tenantId(tenantId)
                    .applicationId(applicationId)
                    .factSnapshotId(factSnapshotId)
                    .evaluationId(evaluationId)
                    .reconciliationDefinitionId(def.getId())
                    .reconciliationCode(def.getReconciliationCode())
                    .definitionVersion(def.getVersion())
                    .leftMetricCode(left.metricCode() != null ? left.metricCode() : left.factPath())
                    .leftMetricVersion(left.metricVersion())
                    .leftValue(left.value())
                    .leftPeriodFrom(left.periodFrom())
                    .leftPeriodTo(left.periodTo())
                    .leftCompleteness(left.completeness())
                    .leftConfidence(left.confidence())
                    .leftSourceRefs(left.sourceRefs() != null ? left.sourceRefs() : List.of())
                    .rightMetricCode(right.metricCode() != null ? right.metricCode() : right.factPath())
                    .rightMetricVersion(right.metricVersion())
                    .rightValue(right.value())
                    .rightPeriodFrom(right.periodFrom())
                    .rightPeriodTo(right.periodTo())
                    .rightCompleteness(right.completeness())
                    .rightConfidence(right.confidence())
                    .rightSourceRefs(right.sourceRefs() != null ? right.sourceRefs() : List.of())
                    .normalizedLeftValue(left.value())
                    .normalizedRightValue(right.value())
                    .absoluteVariance(variance.absoluteVariance())
                    .percentageVariance(variance.percentageVariance())
                    .varianceDenominatorMethod(method.name())
                    .outcome(outcome.name())
                    .severity(severity.name())
                    .explanation(explanation.freeText())
                    .explanationCodes(new ArrayList<>(explanation.explanationCodes()))
                    .probableCauses(new ArrayList<>(explanation.probableCauses()))
                    .evidenceRefs(List.of())
                    .evidenceGroupIds(List.of())
                    .humanReviewRequired(review)
                    .dataStatus(dataStatus.name())
                    .subjectMatchStatus(subjectMatch != null ? subjectMatch.name() : SubjectMatchStatus.UNKNOWN.name())
                    .confidence(confidence)
                    .toleranceVersion(tol.version)
                    .executedAt(executedAt)
                    .executionDurationMs(System.currentTimeMillis() - start)
                    .trace(trace)
                    .metadata(Map.of("category", def.getCategory() != null ? def.getCategory() : ""))
                    .build();
        } catch (Exception e) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return CiReconciliationResult.builder()
                    .tenantId(tenantId)
                    .applicationId(applicationId)
                    .factSnapshotId(factSnapshotId)
                    .evaluationId(evaluationId)
                    .reconciliationDefinitionId(def.getId())
                    .reconciliationCode(def.getReconciliationCode())
                    .definitionVersion(def.getVersion() != null ? def.getVersion() : "V1")
                    .outcome(ReconciliationOutcome.ERROR.name())
                    .severity(DiscrepancySeverity.LOW.name())
                    .explanation("Reconciliation evaluation error")
                    .explanationCodes(List.of())
                    .probableCauses(List.of())
                    .evidenceRefs(List.of())
                    .evidenceGroupIds(List.of())
                    .humanReviewRequired(false)
                    .dataStatus(ReconciliationDataStatus.COMPLETE.name())
                    .subjectMatchStatus(SubjectMatchStatus.UNKNOWN.name())
                    .leftSourceRefs(List.of())
                    .rightSourceRefs(List.of())
                    .executedAt(executedAt)
                    .executionDurationMs(System.currentTimeMillis() - start)
                    .trace(trace)
                    .metadata(Map.of())
                    .build();
        }
    }

    public static ReconciliationOutcome classifyOutcome(
            BigDecimal percentageVariance, double matchPct, double warningPct, double materialPct) {
        double pct = percentageVariance != null ? percentageVariance.doubleValue() : 0.0;
        if (pct <= matchPct) {
            return ReconciliationOutcome.MATCH;
        }
        if (pct <= warningPct) {
            return ReconciliationOutcome.ACCEPTABLE_VARIANCE;
        }
        if (pct <= materialPct) {
            return ReconciliationOutcome.MATERIAL_VARIANCE;
        }
        return ReconciliationOutcome.CONFLICT;
    }

    private CiReconciliationResult diResult(
            UUID tenantId, UUID applicationId, UUID factSnapshotId, UUID evaluationId,
            CiReconciliationDefinition def,
            OperandResolver.ResolvedOperand left,
            OperandResolver.ResolvedOperand right,
            ReconciliationDataStatus dataStatus,
            List<ExplanationCode> codes,
            SubjectMatchStatus subjectMatch,
            Instant executedAt,
            long start,
            Map<String, Object> extraTrace) {

        var explanation = explanationBuilder.build(
                def.getReconciliationCode(),
                ReconciliationOutcome.DATA_INSUFFICIENT,
                left != null ? left.value() : null,
                right != null ? right.value() : null,
                null,
                codes);

        Map<String, Object> trace = new LinkedHashMap<>();
        if (extraTrace != null) {
            trace.putAll(extraTrace);
        }
        trace.put("facts", explanation.facts());
        trace.put("deterministicExplanations", explanation.deterministicExplanations());

        return CiReconciliationResult.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .factSnapshotId(factSnapshotId)
                .evaluationId(evaluationId)
                .reconciliationDefinitionId(def.getId())
                .reconciliationCode(def.getReconciliationCode())
                .definitionVersion(def.getVersion())
                .leftMetricCode(left != null ? (left.metricCode() != null ? left.metricCode() : left.factPath()) : null)
                .leftMetricVersion(left != null ? left.metricVersion() : null)
                .leftValue(left != null ? left.value() : null)
                .leftPeriodFrom(left != null ? left.periodFrom() : null)
                .leftPeriodTo(left != null ? left.periodTo() : null)
                .leftCompleteness(left != null ? left.completeness() : null)
                .leftConfidence(left != null ? left.confidence() : null)
                .leftSourceRefs(left != null && left.sourceRefs() != null ? left.sourceRefs() : List.of())
                .rightMetricCode(right != null ? (right.metricCode() != null ? right.metricCode() : right.factPath()) : null)
                .rightMetricVersion(right != null ? right.metricVersion() : null)
                .rightValue(right != null ? right.value() : null)
                .rightPeriodFrom(right != null ? right.periodFrom() : null)
                .rightPeriodTo(right != null ? right.periodTo() : null)
                .rightCompleteness(right != null ? right.completeness() : null)
                .rightConfidence(right != null ? right.confidence() : null)
                .rightSourceRefs(right != null && right.sourceRefs() != null ? right.sourceRefs() : List.of())
                .outcome(ReconciliationOutcome.DATA_INSUFFICIENT.name())
                .severity(DiscrepancySeverity.LOW.name())
                .explanation(explanation.freeText())
                .explanationCodes(new ArrayList<>(explanation.explanationCodes()))
                .probableCauses(new ArrayList<>(explanation.probableCauses()))
                .evidenceRefs(List.of())
                .evidenceGroupIds(List.of())
                .humanReviewRequired(false)
                .dataStatus(dataStatus.name())
                .subjectMatchStatus(subjectMatch.name())
                .confidence(BigDecimal.ZERO)
                .toleranceVersion(resolveTolerances(def).version)
                .executedAt(executedAt)
                .executionDurationMs(System.currentTimeMillis() - start)
                .trace(trace)
                .metadata(Map.of())
                .build();
    }

    private Tolerances resolveTolerances(CiReconciliationDefinition def) {
        var recon = properties.getReconciliation();
        String profile = null;
        if (def.getMetadata() != null && def.getMetadata().get("toleranceProfile") != null) {
            profile = String.valueOf(def.getMetadata().get("toleranceProfile"));
        }
        double match;
        double warn;
        double material;
        String version;
        if ("obligation".equalsIgnoreCase(profile)) {
            var t = recon.getObligation();
            match = t.getMatchPct();
            warn = def.getWarningTolerance() != null ? def.getWarningTolerance().doubleValue() : t.getWarningPct();
            material = def.getMaterialTolerance() != null ? def.getMaterialTolerance().doubleValue() : t.getMaterialPct();
            version = t.getToleranceVersion();
        } else if ("tax".equalsIgnoreCase(profile)) {
            var t = recon.getTax();
            match = t.getMatchPct();
            warn = def.getWarningTolerance() != null ? def.getWarningTolerance().doubleValue() : t.getWarningPct();
            material = def.getMaterialTolerance() != null ? def.getMaterialTolerance().doubleValue() : t.getMaterialPct();
            version = t.getToleranceVersion();
        } else {
            var t = recon.getTurnover();
            match = t.getMatchPct();
            warn = def.getWarningTolerance() != null ? def.getWarningTolerance().doubleValue() : t.getWarningPct();
            material = def.getMaterialTolerance() != null ? def.getMaterialTolerance().doubleValue() : t.getMaterialPct();
            version = t.getToleranceVersion();
        }
        return new Tolerances(match, warn, material, version);
    }

    private static PeriodAlignmentStrategy parseStrategy(String raw) {
        if (raw == null || raw.isBlank()) {
            return PeriodAlignmentStrategy.COMMON_OVERLAP;
        }
        try {
            return PeriodAlignmentStrategy.valueOf(raw.trim());
        } catch (Exception e) {
            return PeriodAlignmentStrategy.COMMON_OVERLAP;
        }
    }

    private static VarianceMethod parseMethod(String raw) {
        if (raw == null || raw.isBlank()) {
            return VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE;
        }
        try {
            return VarianceMethod.valueOf(raw.trim());
        } catch (Exception e) {
            return VarianceMethod.SYMMETRIC_PERCENT_DIFFERENCE;
        }
    }

    private record Tolerances(double matchPct, double warningPct, double materialPct, String version) {
    }
}
