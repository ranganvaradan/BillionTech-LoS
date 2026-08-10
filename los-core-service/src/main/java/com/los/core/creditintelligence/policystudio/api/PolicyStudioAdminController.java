package com.los.core.creditintelligence.policystudio.api;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioReadModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Internal Policy Studio APIs — never activates production policy.
 */
@RestController
@RequestMapping("/api/v1/internal/credit-intelligence/policy-studio")
@RequiredArgsConstructor
public class PolicyStudioAdminController {

    private final PolicyStudioOrchestrator orchestrator;
    private final PolicyStudioReadModel readModel;
    private final CreditIntelligenceProperties properties;

    @Value("${credit-intelligence.internal-token:}")
    private String internalToken;

    @PostMapping("/documents")
    public Map<String, Object> upload(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.processUpload(
                tenantId,
                str(body, "name", "policy"),
                str(body, "documentType", "TXT"),
                str(body, "sourceText", null),
                str(body, "uploadedBy", "system"),
                str(body, "originalFileReference", null));
        return documentSummary(session);
    }

    @GetMapping("/documents/{id}")
    public Map<String, Object> getDocument(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        return documentSummary(orchestrator.requireSession(id, resolveTenant(tenantHeader)));
    }

    @GetMapping("/documents/{id}/session-summary")
    public Map<String, Object> sessionSummary(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return orchestrator.dashboard().dashboard(s);
    }

    @GetMapping("/documents/{id}/dashboard")
    public Map<String, Object> dashboard(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        return sessionSummary(id, token, tenantHeader);
    }

    @GetMapping("/documents/{id}/next-ambiguity")
    public Map<String, Object> nextAmbiguity(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        var next = s.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus()))
                .findFirst()
                .orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentId", id);
        out.put("ambiguity", next);
        out.put("remaining", s.getAmbiguities().stream()
                .filter(a -> "OPEN".equals(a.getResolutionStatus())).count());
        return out;
    }

    @GetMapping("/documents/{id}/clauses")
    public Map<String, Object> clauses(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "clauses", s.getClauses(), "count", s.getClauses().size());
    }

    @GetMapping("/documents/{id}/interpretation")
    public Map<String, Object> interpretation(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of(
                "documentId", id,
                "interpretations", s.getInterpretations(),
                "readModel", readModel.threeColumn(s));
    }

    @GetMapping("/documents/{id}/ambiguities")
    public Map<String, Object> ambiguities(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "ambiguities", s.getAmbiguities(), "count", s.getAmbiguities().size());
    }

    @PostMapping("/ambiguities/{id}/resolve")
    public Map<String, Object> resolveAmbiguity(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestParam UUID documentId,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(documentId, resolveTenant(tenantHeader));
        AmbiguityResolutionAction action = AmbiguityResolutionAction.SELECT_CANDIDATE;
        if (body.get("action") != null) {
            action = AmbiguityResolutionAction.valueOf(String.valueOf(body.get("action")));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("payload") : body;
        return orchestrator.resolveAmbiguity(
                documentId, id, action,
                str(body, "resolvedOption", null),
                str(body, "resolvedBy", "credit_manager"),
                str(body, "notes", null),
                payload);
    }

    @PostMapping("/documents/{id}/metrics/custom")
    public Map<String, Object> createCustomMetric(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(id, resolveTenant(tenantHeader));
        var m = orchestrator.createCustomMetric(id, body);
        return Map.of("id", m.getId(), "systemMetricId", m.getSystemMetricId(),
                "reviewStatus", m.getReviewStatus());
    }

    @PostMapping("/documents/{id}/re-interpret")
    public Map<String, Object> reInterpret(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return orchestrator.reInterpret(id);
    }

    @GetMapping("/documents/{id}/preview")
    public Map<String, Object> preview(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return orchestrator.previewService().preview(s);
    }

    @GetMapping("/documents/{id}/rule-candidates")
    public Map<String, Object> rules(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "rules", s.getRuleCandidates(), "count", s.getRuleCandidates().size());
    }

    @GetMapping("/documents/{id}/metric-candidates")
    public Map<String, Object> metrics(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "metrics", s.getMetricCandidates(), "count", s.getMetricCandidates().size());
    }

    @PostMapping("/rules/{id}/review")
    public Map<String, Object> reviewRule(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestParam UUID documentId,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(documentId, resolveTenant(tenantHeader));
        return review(documentId, "RULE", id, body);
    }

    @PostMapping("/metrics/{id}/review")
    public Map<String, Object> reviewMetric(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestParam UUID documentId,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(documentId, resolveTenant(tenantHeader));
        return review(documentId, "METRIC", id, body);
    }

    @GetMapping("/documents/{id}/test-cases")
    public Map<String, Object> tests(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "testCases", s.getTestCases(), "count", s.getTestCases().size());
    }

    @PostMapping("/test-cases/{id}/review")
    public Map<String, Object> reviewTest(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestParam UUID documentId,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(documentId, resolveTenant(tenantHeader));
        return review(documentId, "TEST", id, body);
    }

    @PostMapping("/documents/{id}/submit-credit-manager-review")
    public Map<String, Object> submitCm(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        var review = orchestrator.reviewService().review(
                s, "DOCUMENT", id,
                str(body, "reviewer", "cm"),
                PolicyReviewService.ROLE_CREDIT_MANAGER,
                ReviewState.CREDIT_MANAGER_APPROVED.name(),
                Map.of(),
                str(body, "reason", "CM submit"));
        return Map.of("reviewId", review.getId(), "reviewState", review.getReviewState());
    }

    @PostMapping("/documents/{id}/submit-checker-review")
    public Map<String, Object> submitChecker(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        var review = orchestrator.reviewService().review(
                s, "DOCUMENT", id,
                str(body, "reviewer", "checker"),
                PolicyReviewService.ROLE_POLICY_CHECKER,
                ReviewState.CHECKER_APPROVED.name(),
                Map.of(),
                str(body, "reason", "Checker submit"));
        return Map.of("reviewId", review.getId(), "reviewState", review.getReviewState());
    }

    @PostMapping("/documents/{id}/build-draft-policy")
    public Map<String, Object> buildDraft(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody(required = false) Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        orchestrator.requireSession(id, resolveTenant(tenantHeader));
        String createdBy = body == null ? "system" : str(body, "createdBy", "system");
        boolean strict = body != null && Boolean.TRUE.equals(body.get("strict"));
        Map<String, Object> result = strict
                ? orchestrator.strictBuildDraft(id, createdBy)
                : orchestrator.buildDraft(id, createdBy);
        result = new LinkedHashMap<>(result);
        result.put("productionActive", false);
        result.put("activationForbidden", true);
        return result;
    }

    @PostMapping("/documents/{id}/simulate")
    public Map<String, Object> simulate(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return orchestrator.simulator().simulate(s);
    }

    @PostMapping("/documents/{id}/diff-drafts")
    public Map<String, Object> diffDrafts(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader,
            @RequestBody Map<String, Object> body) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        // Rebuild two versions if needed: compare current draft to previous simulation package content
        orchestrator.buildDraft(id, str(body, "createdBy", "system"));
        var to = s.getDraftPackage();
        var from = to;
        if (s.getDraftDiffs().isEmpty() && body.get("fromContentHash") == null) {
            // create a second package after a no-op material note
            orchestrator.reviewService().invalidateCheckerApproval(s, "diff");
            orchestrator.buildDraft(id, str(body, "createdBy", "system"));
            from = to;
            to = s.getDraftPackage();
        }
        return orchestrator.diffService().diff(s, from, to);
    }

    @GetMapping("/documents/{id}/completeness")
    public Map<String, Object> completeness(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantHeader) {
        assertInternalToken(token);
        orchestrator.assertEnabled();
        var s = orchestrator.requireSession(id, resolveTenant(tenantHeader));
        return Map.of("documentId", id, "completeness", s.getCompleteness(),
                "conflicts", s.getConflicts(), "readiness", s.getReadiness());
    }

    private Map<String, Object> review(UUID documentId, String subjectType, UUID subjectId, Map<String, Object> body) {
        var s = orchestrator.requireSession(documentId);
        var review = orchestrator.reviewService().review(
                s,
                subjectType,
                subjectId,
                str(body, "reviewer", "user"),
                str(body, "reviewerRole", PolicyReviewService.ROLE_CREDIT_MANAGER),
                str(body, "reviewState", ReviewState.CREDIT_MANAGER_APPROVED.name()),
                body.containsKey("humanChanges") ? (Map<String, Object>) body.get("humanChanges") : Map.of(),
                str(body, "reason", null));
        return Map.of("reviewId", review.getId(), "reviewState", review.getReviewState(),
                "reviewer", review.getReviewer(), "reviewerRole", review.getReviewerRole());
    }

    private Map<String, Object> documentSummary(PolicyStudioSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", session.getDocument().getId());
        m.put("sessionId", session.sessionId());
        m.put("name", session.getDocument().getName());
        m.put("status", session.getDocument().getStatus());
        m.put("contentHash", session.getDocument().getContentHash());
        m.put("documentVersion", session.getDocument().getDocumentVersion());
        m.put("clauseCount", session.getClauses().size());
        m.put("ambiguityCount", session.getAmbiguities().size());
        m.put("ruleCount", session.getRuleCandidates().size());
        m.put("completeness", session.getCompleteness().get("status"));
        m.put("readiness", session.getReadiness());
        return m;
    }

    private UUID resolveTenant(String tenantHeader) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            return UUID.fromString(tenantHeader.trim());
        }
        return properties.getDefaultTenantId();
    }

    private String str(Map<String, Object> body, String key, String def) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            if (def == null && "sourceText".equals(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceText required");
            }
            return def;
        }
        return String.valueOf(body.get(key));
    }

    private void assertInternalToken(String token) {
        if (internalToken == null || internalToken.isBlank()) {
            return;
        }
        if (token == null || !internalToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing X-Internal-Token");
        }
    }
}
