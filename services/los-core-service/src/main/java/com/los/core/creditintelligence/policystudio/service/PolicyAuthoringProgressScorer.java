package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.AuthoringReadinessGrade;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic authoring readiness 0–100 — not a credit score.
 */
@Component
public class PolicyAuthoringProgressScorer {

    public Map<String, Object> score(PolicyStudioSession session) {
        int extraction = session.getClauses().isEmpty() ? 0 : 100;
        int mapping = session.getClauses().isEmpty() ? 0
                : (int) Math.min(100, session.getMappings().size() * 100.0 / Math.max(1, session.getClauses().size()));
        int metrics = session.getMetricCandidates().isEmpty() ? 40 : 100;
        long openMaterial = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .count();
        int ambiguity = openMaterial == 0 ? 100 : (int) Math.max(0, 100 - openMaterial * 15);
        long invalidRules = session.getRuleCandidates().stream()
                .filter(r -> r.getValidationErrors() != null && !r.getValidationErrors().isEmpty())
                .count();
        int ruleValidation = session.getRuleCandidates().isEmpty() ? 0
                : (int) Math.max(0, 100 - invalidRules * 20);
        long approvedTests = session.getTestCases().stream().filter(this::testApproved).count();
        int testApproval = session.getTestCases().isEmpty() ? 0
                : (int) (approvedTests * 100 / session.getTestCases().size());
        long blockingConflicts = session.getConflicts().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking"))).count();
        int conflictResolution = blockingConflicts == 0 ? 100 : Math.max(0, 100 - (int) blockingConflicts * 25);
        boolean checkerOk = session.getReviews().stream()
                .anyMatch(r -> ReviewState.CHECKER_APPROVED.name().equals(r.getReviewState()));
        boolean draftInvalidated = session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit());
        int reviewApproval = checkerOk && !draftInvalidated ? 100 : (session.getReviews().isEmpty() ? 20 : 60);

        double avg = (extraction + mapping + metrics + ambiguity + ruleValidation
                + testApproval + conflictResolution + reviewApproval) / 8.0;
        int score = BigDecimal.valueOf(avg).setScale(0, RoundingMode.HALF_UP).intValue();

        AuthoringReadinessGrade grade;
        if (openMaterial > 0 || blockingConflicts > 0) {
            grade = AuthoringReadinessGrade.BLOCKED;
        } else if (score >= 85 && approvedTests == session.getTestCases().size()
                && !session.getTestCases().isEmpty()
                && checkerOk && !draftInvalidated) {
            grade = AuthoringReadinessGrade.READY_FOR_POLICY_BUILD;
        } else if (openMaterial == 0 && !session.getRuleCandidates().isEmpty()) {
            grade = AuthoringReadinessGrade.NEEDS_REVIEW;
        } else {
            grade = AuthoringReadinessGrade.DRAFT;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("score", score);
        out.put("grade", grade.name());
        out.put("dimensions", Map.of(
                "clause_extraction_completeness", extraction,
                "mapping_completeness", mapping,
                "metric_availability", metrics,
                "ambiguity_resolution", ambiguity,
                "rule_validation", ruleValidation,
                "test_approval", testApproval,
                "conflict_resolution", conflictResolution,
                "review_approval", reviewApproval
        ));
        out.put("openMaterialAmbiguities", openMaterial);
        out.put("blockingConflicts", blockingConflicts);
        out.put("note", "Authoring readiness only — not a credit score");
        return out;
    }

    private boolean testApproved(CiPolicyTestCase t) {
        return ReviewState.CHECKER_APPROVED.name().equals(t.getReviewStatus())
                || ReviewState.CREDIT_MANAGER_APPROVED.name().equals(t.getReviewStatus())
                || "APPROVED".equals(t.getReviewStatus());
    }
}
