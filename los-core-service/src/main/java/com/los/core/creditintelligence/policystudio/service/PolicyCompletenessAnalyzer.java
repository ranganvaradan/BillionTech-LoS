package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.AuthoringReadinessGrade;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CompletenessStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PolicyCompletenessAnalyzer {

    public Map<String, Object> analyze(
            List<CiPolicyClause> clauses,
            List<CiPolicyClause> interpreted,
            List<?> exactMappings,
            List<CiPolicyAmbiguity> ambiguities,
            List<?> metrics,
            List<?> rules,
            List<?> tests,
            List<Map<String, Object>> conflicts) {
        long openMaterial = ambiguities.stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .count();
        long missingMetrics = ambiguities.stream()
                .filter(a -> "MISSING_METRIC".equals(a.getAmbiguityType()) && "OPEN".equals(a.getResolutionStatus()))
                .count();
        long blockingConflicts = conflicts.stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("clauses_total", clauses.size());
        summary.put("clauses_interpreted", interpreted.size());
        summary.put("exact_mappings", exactMappings.size());
        summary.put("ambiguous_mappings", openMaterial);
        summary.put("missing_metrics", missingMetrics);
        summary.put("missing_facts", ambiguities.stream()
                .filter(a -> a.getAmbiguityType() != null && a.getAmbiguityType().contains("UNKNOWN")).count());
        summary.put("unsupported_rules", ambiguities.stream()
                .filter(a -> "UNSUPPORTED_DATA".equals(a.getAmbiguityType())).count());
        summary.put("conflicts", conflicts.size());
        summary.put("blocking_conflicts", blockingConflicts);
        summary.put("test_cases_generated", tests.size());
        summary.put("review_required_count", openMaterial + blockingConflicts);
        summary.put("metric_candidates", metrics.size());
        summary.put("rule_candidates", rules.size());

        CompletenessStatus status;
        AuthoringReadinessGrade grade;
        if (openMaterial > 0 || missingMetrics > 0 || blockingConflicts > 0) {
            status = CompletenessStatus.BLOCKED;
            grade = AuthoringReadinessGrade.BLOCKED;
        } else if (tests.isEmpty() || rules.isEmpty()) {
            status = CompletenessStatus.INCOMPLETE;
            grade = AuthoringReadinessGrade.DRAFT;
        } else {
            status = CompletenessStatus.READY_FOR_REVIEW;
            grade = AuthoringReadinessGrade.NEEDS_REVIEW;
        }
        summary.put("status", status.name());
        summary.put("readinessGrade", grade.name());
        return summary;
    }
}
