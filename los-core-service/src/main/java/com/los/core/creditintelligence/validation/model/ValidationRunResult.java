package com.los.core.creditintelligence.validation.model;

import com.los.core.creditintelligence.validation.domain.CutoverOutcome;
import com.los.core.creditintelligence.validation.domain.DataOrigin;
import com.los.core.creditintelligence.validation.domain.ValidationCaseCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Full non-authoritative multi-source validation run result (in-memory; optional persist).
 */
public record ValidationRunResult(
        UUID runId,
        String runCode,
        ValidationCaseCode caseCode,
        DataOrigin dataOrigin,
        UUID tenantId,
        UUID applicationId,
        UUID evaluationContextId,
        String configFreezeHash,
        String factSnapshotHash,
        String metricResultSetHash,
        String reconciliationResultSetHash,
        String deterministicEvaluationHash,
        String replayHash,
        boolean replayIdentical,
        List<ProviderStackResult> providerStackResults,
        Map<String, Object> reconciliations,
        Map<String, Object> dualPolicyComparison,
        Map<String, Object> coverage,
        Map<String, Object> evidenceView,
        List<String> investigationQuestions,
        List<Map<String, Object>> obligationMatches,
        Map<String, Object> legacyDefaultExposure,
        CutoverOutcome cutoverOutcome,
        Map<String, Object> cutoverDimensions,
        List<String> blockers,
        Map<String, Long> stageDurationsMs,
        long totalDurationMs,
        Map<String, Object> summary
) {
    public ValidationRunResult {
        providerStackResults = providerStackResults != null ? List.copyOf(providerStackResults) : List.of();
        reconciliations = reconciliations != null ? Map.copyOf(reconciliations) : Map.of();
        dualPolicyComparison = dualPolicyComparison != null ? Map.copyOf(dualPolicyComparison) : Map.of();
        coverage = coverage != null ? Map.copyOf(coverage) : Map.of();
        evidenceView = evidenceView != null ? Map.copyOf(evidenceView) : Map.of();
        investigationQuestions = investigationQuestions != null ? List.copyOf(investigationQuestions) : List.of();
        obligationMatches = obligationMatches != null ? List.copyOf(obligationMatches) : List.of();
        legacyDefaultExposure = legacyDefaultExposure != null ? Map.copyOf(legacyDefaultExposure) : Map.of();
        cutoverDimensions = cutoverDimensions != null ? Map.copyOf(cutoverDimensions) : Map.of();
        blockers = blockers != null ? List.copyOf(blockers) : List.of();
        stageDurationsMs = stageDurationsMs != null ? Map.copyOf(stageDurationsMs) : Map.of();
        summary = summary != null ? Map.copyOf(summary) : Map.of();
    }

    public BigDecimal evidenceStrengthScore() {
        Object s = evidenceView.get("evidenceStrengthScore");
        if (s instanceof BigDecimal bd) {
            return bd;
        }
        if (s instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
