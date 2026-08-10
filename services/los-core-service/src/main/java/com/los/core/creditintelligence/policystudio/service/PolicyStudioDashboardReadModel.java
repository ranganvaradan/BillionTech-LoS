package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.AuthoringReadinessGrade;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single UI contract read model for Policy Studio dashboard.
 */
@Component
public class PolicyStudioDashboardReadModel {

    private final PolicyStudioReadModel threeColumn;
    private final PolicyAuthoringProgressScorer progressScorer;

    public PolicyStudioDashboardReadModel(
            PolicyStudioReadModel threeColumn,
            PolicyAuthoringProgressScorer progressScorer) {
        this.threeColumn = threeColumn != null ? threeColumn : new PolicyStudioReadModel();
        this.progressScorer = progressScorer != null ? progressScorer : new PolicyAuthoringProgressScorer();
    }

    public PolicyStudioDashboardReadModel() {
        this(new PolicyStudioReadModel(), new PolicyAuthoringProgressScorer());
    }

    public Map<String, Object> dashboard(PolicyStudioSession session) {
        Map<String, Object> readiness = progressScorer.score(session);
        long openAmb = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        long blockingAmb = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .count();
        long blockingConflicts = session.getConflicts().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking"))).count();
        long approvedTests = session.getTestCases().stream()
                .filter(t -> ReviewState.CHECKER_APPROVED.name().equals(t.getReviewStatus())
                        || ReviewState.CREDIT_MANAGER_APPROVED.name().equals(t.getReviewStatus())
                        || "APPROVED".equals(t.getReviewStatus()))
                .count();
        long cmApprovals = session.getReviews().stream()
                .filter(r -> ReviewState.CREDIT_MANAGER_APPROVED.name().equals(r.getReviewState())).count();
        long checkerApprovals = session.getReviews().stream()
                .filter(r -> ReviewState.CHECKER_APPROVED.name().equals(r.getReviewState())).count();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentId", session.getDocument().getId().toString());
        out.put("sessionId", session.sessionId() == null ? null : session.sessionId().toString());
        out.put("readiness", readiness);
        out.put("threeColumn", threeColumn.threeColumn(session));
        out.put("Document", section(
                session.getDocument().getStatus(),
                1,
                0,
                "OPEN".equals(session.getDocument().getStatus()) ? "Upload policy text" : "Review extraction"));
        out.put("Extraction", section(
                session.getClauses().isEmpty() ? "EMPTY" : "COMPLETE",
                session.getClauses().size(),
                session.getClauses().isEmpty() ? 1 : 0,
                session.getClauses().isEmpty() ? "Re-run extraction" : "Review clauses"));
        out.put("Ambiguities", section(
                openAmb == 0 ? "CLEAR" : "OPEN",
                session.getAmbiguities().size(),
                (int) blockingAmb,
                openAmb == 0 ? "None" : "Resolve next ambiguity"));
        out.put("Metrics", section(
                session.getMetricCandidates().isEmpty() ? "EMPTY" : "DRAFTED",
                session.getMetricCandidates().size(),
                0,
                "Approve metric candidates"));
        out.put("Rules", section(
                session.getRuleCandidates().isEmpty() ? "EMPTY" : "DRAFTED",
                session.getRuleCandidates().size(),
                (int) session.getRuleCandidates().stream()
                        .filter(r -> r.getValidationErrors() != null && !r.getValidationErrors().isEmpty()).count(),
                "Validate and approve rules"));
        out.put("Tests", section(
                approvedTests == session.getTestCases().size() && !session.getTestCases().isEmpty()
                        ? "APPROVED" : "PENDING",
                session.getTestCases().size(),
                (int) (session.getTestCases().size() - approvedTests),
                "Approve generated tests"));
        out.put("Conflicts", section(
                blockingConflicts == 0 ? "CLEAR" : "BLOCKING",
                session.getConflicts().size(),
                (int) blockingConflicts,
                blockingConflicts == 0 ? "None" : "Resolve blocking conflicts"));
        out.put("Simulation", section(
                session.getSimulation() == null || session.getSimulation().isEmpty() ? "NOT_RUN" : "RUN",
                session.getSimulationRuns().size(),
                0,
                "Run VALIDATION_FIXTURE_SIMULATION"));
        out.put("Approvals", section(
                checkerApprovals > 0 ? "CHECKER_APPROVED" : (cmApprovals > 0 ? "CM_APPROVED" : "PENDING"),
                session.getReviews().size(),
                checkerApprovals == 0 ? 1 : 0,
                "Submit credit-manager then checker review"));
        out.put("DraftPackage", section(
                session.getDraftPackage() == null ? "NONE"
                        : (Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit())
                        ? "INVALIDATED" : session.getDraftPackage().getPackageStatus()),
                session.getDraftPackage() == null ? 0 : 1,
                AuthoringReadinessGrade.READY_FOR_POLICY_BUILD.name()
                        .equals(String.valueOf(readiness.get("grade"))) ? 0 : 1,
                AuthoringReadinessGrade.READY_FOR_POLICY_BUILD.name()
                        .equals(String.valueOf(readiness.get("grade")))
                        ? "Ready for P1 handoff (still DRAFT_ONLY)"
                        : "Resolve blockers before READY_FOR_POLICY_BUILD"));
        return out;
    }

    private Map<String, Object> section(String status, int count, int blockingCount, String nextAction) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("count", count);
        m.put("blockingCount", blockingCount);
        m.put("nextAction", nextAction);
        return m;
    }
}
