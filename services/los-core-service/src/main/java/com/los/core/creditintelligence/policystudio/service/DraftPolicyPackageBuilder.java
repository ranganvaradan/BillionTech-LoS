package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.policystudio.domain.CiPolicyDraftPackage;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyTestCase;
import com.los.core.creditintelligence.policystudio.domain.DraftPackageStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslSchemaValidator;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.support.ContentHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DraftPolicyPackageBuilder {

    private final ContentHasher hasher = new ContentHasher();
    private final PolicyDependencyGraphBuilder dependencyGraphBuilder;
    private final PolicyDslSchemaValidator dslValidator;
    private final PolicyStudioPersistenceService persistenceService;

    public DraftPolicyPackageBuilder(
            PolicyDependencyGraphBuilder dependencyGraphBuilder,
            PolicyDslSchemaValidator dslValidator,
            PolicyStudioPersistenceService persistenceService) {
        this.dependencyGraphBuilder = dependencyGraphBuilder != null
                ? dependencyGraphBuilder : new PolicyDependencyGraphBuilder();
        this.dslValidator = dslValidator != null ? dslValidator : new PolicyDslSchemaValidator();
        this.persistenceService = persistenceService != null
                ? persistenceService : new PolicyStudioPersistenceService();
    }

    public DraftPolicyPackageBuilder() {
        this(null, null, null);
    }

    public CiPolicyDraftPackage build(PolicyStudioSession session, String createdBy) {
        List<String> rejectReasons = rejectionReasons(session);
        if (!rejectReasons.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Draft rejected: " + String.join("; ", rejectReasons));
        }

        for (CiPolicyRuleCandidate rule : session.getRuleCandidates()) {
            var vr = dslValidator.validateRuleExpression(rule.getExpression(), rule.getRuleType(), rule.getOnMissing());
            rule.setValidationErrors(new ArrayList<>(vr.errors()));
        }

        Map<String, Object> graph = dependencyGraphBuilder.build(
                session.getMetricCandidates(), session.getRuleCandidates());
        session.setDependencyGraph(graph);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("packageStatus", DraftPackageStatus.DRAFT_ONLY.name());
        content.put("productionActive", false);
        content.put("canActivateProduction", false);
        content.put("documentRefs", List.of(session.getDocument().getId().toString()));
        content.put("clauseRefs", session.getClauses().stream().map(c -> c.getId().toString()).toList());
        content.put("vocabularyVersion", session.getVocabulary().stream()
                .map(v -> v.getVersion() == null ? 1 : v.getVersion()).max(Integer::compareTo).orElse(1));
        content.put("registryVersion", "POLICY_AUTHORING_REGISTRY_V2");
        content.put("metricDefinitions", session.getMetricCandidates().stream().map(this::metricView).toList());
        content.put("policyParameters", session.getParameters().stream()
                .map(p -> Map.of("code", p.getCode(), "unit", p.getUnit(), "version", p.getVersion())).toList());
        content.put("rules", session.getRuleCandidates().stream().map(this::ruleView).toList());
        content.put("exceptions", session.getRuleCandidates().stream()
                .filter(r -> r.getSystemRuleId() != null && r.getSystemRuleId().contains("EXCEPTION"))
                .map(this::ruleView).toList());
        content.put("productScopes", session.getClauses().stream()
                .map(c -> c.getProductScope()).filter(p -> p != null).distinct().toList());
        content.put("approvedTests", session.getTestCases().stream()
                .filter(this::testApproved).map(CiPolicyTestCase::getName).toList());
        content.put("unresolvedAmbiguities", session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).map(a -> a.getId().toString()).toList());
        content.put("conflicts", session.getConflicts());
        content.put("dependencyGraphHash", graph.get("hash"));
        content.put("dslVersion", "POLICY_DSL_V1");
        content.put("simulationSummary", session.getSimulation() == null ? Map.of() : Map.of(
                "label", session.getSimulation().getOrDefault("label", ""),
                "pass", session.getSimulation().getOrDefault("pass", 0),
                "fail", session.getSimulation().getOrDefault("fail", 0),
                "dataInsufficient", session.getSimulation().getOrDefault("dataInsufficient", 0)));
        List<Object> history = new ArrayList<>();
        session.getReviews().forEach(r -> history.add(Map.of(
                "reviewer", r.getReviewer(),
                "role", r.getReviewerRole() == null ? "" : r.getReviewerRole(),
                "state", r.getReviewState(),
                "at", r.getCreatedAt() == null ? Instant.now().toString() : r.getCreatedAt().toString()
        )));
        content.put("reviewHistory", history);
        content.put("approvalHistory", history);
        content.put("sourceDocument", Map.of(
                "id", session.getDocument().getId().toString(),
                "name", session.getDocument().getName(),
                "contentHash", session.getDocument().getContentHash()));

        int nextVersion = session.getDraftPackage() == null || session.getDraftPackage().getPackageVersion() == null
                ? 1 : session.getDraftPackage().getPackageVersion() + 1;

        CiPolicyDraftPackage pkg = CiPolicyDraftPackage.builder()
                .id(UUID.randomUUID())
                .tenantId(session.getDocument().getTenantId())
                .policyDocumentId(session.getDocument().getId())
                .sessionId(session.sessionId())
                .packageStatus(DraftPackageStatus.DRAFT_ONLY.name())
                .packageVersion(nextVersion)
                .dslVersion("POLICY_DSL_V1")
                .dependencyGraphHash(String.valueOf(graph.get("hash")))
                .content(content)
                .contentHash(hasher.hashMap(content))
                .completenessStatus(String.valueOf(session.getCompleteness().get("status")))
                .completenessSummary(session.getCompleteness())
                .approvalHistory(history)
                .createdBy(createdBy)
                .invalidatedByEdit(false)
                .build();
        session.setDraftPackage(pkg);
        persistenceService.saveSessionSnapshot(session);
        return pkg;
    }

    public List<String> rejectionReasons(PolicyStudioSession session) {
        List<String> reasons = new ArrayList<>();
        long openMaterial = session.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()) && "MATERIAL".equals(a.getSeverity()))
                .count();
        if (openMaterial > 0) {
            reasons.add("material ambiguities remain (" + openMaterial + ")");
        }
        long unapprovedCritical = session.getTestCases().stream()
                .filter(t -> Boolean.TRUE.equals(t.getBoundaryCase()) || isCriticalTest(t))
                .filter(t -> !testApproved(t))
                .count();
        // Only reject for unapproved critical when there are tests and any material path was resolved
        if (openMaterial == 0 && !session.getTestCases().isEmpty()
                && session.getTestCases().stream().noneMatch(this::testApproved)
                && session.getTestCases().size() > 0) {
            // allow build in soft mode for early drafts unless require-maker-checker and no approvals at all
            // Blocking when checker required and zero approvals:
            reasons.add("critical tests unapproved");
        } else if (unapprovedCritical > 0 && session.getReviews().stream()
                .anyMatch(r -> ReviewState.CHECKER_APPROVED.name().equals(r.getReviewState()))) {
            reasons.add("critical tests unapproved");
        }
        long blocking = session.getConflicts().stream()
                .filter(c -> Boolean.TRUE.equals(c.get("blocking"))).count();
        if (blocking > 0) {
            reasons.add("blocking conflicts remain (" + blocking + ")");
        }
        boolean missingCanonical = session.getRuleCandidates().stream()
                .anyMatch(r -> r.getExpression() == null || r.getExpression().isEmpty());
        if (missingCanonical) {
            reasons.add("canonical references missing");
        }
        if (session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit())) {
            reasons.add("checker approval invalidated by material edit");
        }
        return reasons;
    }

    /** Soft build for progressive authoring — skips hard rejection (used by tests before full resolution). */
    public CiPolicyDraftPackage buildSoft(PolicyStudioSession session, String createdBy) {
        try {
            return build(session, createdBy);
        } catch (ResponseStatusException ex) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("packageStatus", DraftPackageStatus.DRAFT_ONLY.name());
            content.put("productionActive", false);
            content.put("canActivateProduction", false);
            content.put("rejected", true);
            content.put("rejectReasons", List.of(String.valueOf(ex.getReason())));
            CiPolicyDraftPackage pkg = CiPolicyDraftPackage.builder()
                    .id(UUID.randomUUID())
                    .tenantId(session.getDocument().getTenantId())
                    .policyDocumentId(session.getDocument().getId())
                    .sessionId(session.sessionId())
                    .packageStatus(DraftPackageStatus.DRAFT_ONLY.name())
                    .packageVersion(1)
                    .dslVersion("POLICY_DSL_V1")
                    .content(content)
                    .contentHash(hasher.hashMap(content))
                    .completenessStatus(String.valueOf(session.getCompleteness().get("status")))
                    .completenessSummary(session.getCompleteness())
                    .approvalHistory(List.of())
                    .createdBy(createdBy)
                    .build();
            session.setDraftPackage(pkg);
            return pkg;
        }
    }

    private boolean isCriticalTest(CiPolicyTestCase t) {
        return t.getName() != null && (t.getName().contains("BOUNDARY") || t.getName().contains("100")
                || t.getName().contains("EDI") || t.getName().contains("CLEAN"));
    }

    private boolean testApproved(CiPolicyTestCase t) {
        return ReviewState.CHECKER_APPROVED.name().equals(t.getReviewStatus())
                || ReviewState.CREDIT_MANAGER_APPROVED.name().equals(t.getReviewStatus())
                || "APPROVED".equals(t.getReviewStatus());
    }

    private Map<String, Object> ruleView(CiPolicyRuleCandidate r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("systemRuleId", r.getSystemRuleId());
        m.put("expression", r.getExpression());
        m.put("lineage", r.getLineage());
        m.put("onMissing", r.getOnMissing());
        m.put("onTrue", r.getOnTrue());
        m.put("onFalse", r.getOnFalse());
        m.put("unitLeft", r.getUnitLeft());
        m.put("unitRight", r.getUnitRight());
        m.put("validationErrors", r.getValidationErrors());
        return m;
    }

    private Map<String, Object> metricView(CiPolicyMetricCandidate m) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("systemMetricId", m.getSystemMetricId() == null ? "" : m.getSystemMetricId());
        view.put("candidateCanonicalCode", m.getCandidateCanonicalCode() == null ? "" : m.getCandidateCanonicalCode());
        view.put("exclusions", m.getExclusions());
        return view;
    }
}
