package com.los.core.service.underwriting;

import com.los.core.audit.AdminConfigAuditSupport;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import com.los.core.exception.ResourceNotFoundException;
import com.los.core.model.dto.response.UnderwritingScorecardResponse;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SCORECARD-GOVERNANCE-1 — maker-checker lifecycle for Live Scorecards.
 * Reuses {@link ScorecardSafetyValidator}; does not create a new scorecard engine.
 */
@Service
@RequiredArgsConstructor
public class ScorecardGovernanceService {

    public static final Set<String> MAKER_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN");
    public static final Set<String> CHECKER_ROLES = Set.of(
            "POLICY_CHECKER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN", "RISK_MANAGER");
    public static final Set<String> ACTIVATOR_ROLES = Set.of(
            "CREDIT_MANAGER", "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN");

    private final UnderwritingScorecardRepository repository;
    private final ScorecardConvergenceService convergenceService;
    private final AdminConfigAuditSupport adminConfigAuditSupport;
    private final UnderwritingScorecardAdminService adminService;

    public record Actor(String userId, String displayName, String role) {
        String identity() {
            return displayName != null && !displayName.isBlank() ? displayName : userId;
        }
    }

    public void requireMaker(Actor actor) {
        requireRole(actor, MAKER_ROLES, "Credit Manager / Administrator required for maker actions");
    }

    public void requireChecker(Actor actor) {
        requireRole(actor, CHECKER_ROLES, "Policy Checker / Approver role required");
    }

    public void requireActivator(Actor actor) {
        requireRole(actor, ACTIVATOR_ROLES, "Credit Manager / Administrator required to activate");
    }

    private static void requireRole(Actor actor, Set<String> allowed, String message) {
        if (actor == null || actor.role() == null || actor.role().isBlank()) {
            throw new ForbiddenException("Authentication required");
        }
        String role = actor.role().trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(role)) {
            throw new ForbiddenException(message);
        }
    }

    @Transactional
    public UnderwritingScorecardResponse recordPreview(UUID id, Map<String, Object> previewBody, Actor actor) {
        requireMaker(actor);
        UnderwritingScorecard e = load(id);
        assertEditableOrPreview(e);
        Map<String, Object> body = new LinkedHashMap<>(previewBody == null ? Map.of() : previewBody);
        body.put("scorecardId", id.toString());
        Map<String, Object> result = convergenceService.preview(body);
        Map<String, Object> gov = gov(e);
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("at", Instant.now().toString());
        last.put("by", actor.identity());
        last.put("byRole", actor.role());
        last.put("earnedPoints", result.get("earnedPoints"));
        last.put("maxPoints", result.get("maxPoints"));
        last.put("normalizedPercent", result.get("normalizedPercent"));
        last.put("policyDecision", result.get("policyDecision"));
        last.put("contentFingerprint", ScorecardContentFingerprint.of(e));
        last.put("applicationMutated", false);
        gov.put("lastPreview", last);
        e.setGovernanceJson(gov);
        e.setUpdatedAt(Instant.now());
        return adminService.toResponsePublic(repository.save(e));
    }

    @Transactional
    public UnderwritingScorecardResponse submitForReview(UUID id, Actor actor, String remarks) {
        requireMaker(actor);
        UnderwritingScorecard e = load(id);
        if (!ScorecardGovernanceStatuses.DRAFT.equalsIgnoreCase(e.getStatus())) {
            throw blocked("Only DRAFT scorecards can be submitted for review", "SCORECARD_NOT_DRAFT");
        }
        assertExecutionReady(e, "submit");
        assertPreviewValid(e);
        Map<String, Object> gov = gov(e);
        clearCheckerDecision(gov);
        gov.put("submittedBy", actor.identity());
        gov.put("submittedByUserId", actor.userId());
        gov.put("submittedByRole", actor.role());
        gov.put("submittedAt", Instant.now().toString());
        if (remarks != null && !remarks.isBlank()) {
            gov.put("submitRemarks", remarks.trim());
        }
        appendHistory(gov, "SUBMITTED", actor, remarks);
        e.setStatus(ScorecardGovernanceStatuses.IN_REVIEW);
        e.setActive(false);
        e.setGovernanceJson(gov);
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse after = adminService.toResponsePublic(repository.save(e));
        adminConfigAuditSupport.captureUpdate(
                "UNDERWRITING_SCORECARD", id.toString(), null, after, "Scorecard submitted for review");
        return after;
    }

    @Transactional
    public UnderwritingScorecardResponse approve(UUID id, Actor actor, String remarks) {
        requireChecker(actor);
        UnderwritingScorecard e = load(id);
        if (!ScorecardGovernanceStatuses.IN_REVIEW.equalsIgnoreCase(e.getStatus())) {
            throw blocked("Only IN_REVIEW scorecards can be approved", "SCORECARD_NOT_IN_REVIEW");
        }
        assertExecutionReady(e, "approve");
        assertPreviewValid(e);
        Map<String, Object> gov = gov(e);
        String maker = str(gov.get("submittedByUserId"));
        if (maker == null) maker = str(gov.get("submittedBy"));
        if (maker != null && (maker.equalsIgnoreCase(actor.userId())
                || maker.equalsIgnoreCase(actor.identity()))) {
            throw blocked(
                    "Maker-checker: submitter cannot be the checker approving this scorecard",
                    "SCORECARD_SELF_APPROVAL_FORBIDDEN");
        }
        gov.put("checkerDecision", ScorecardGovernanceStatuses.DECISION_APPROVED);
        gov.put("approvedBy", actor.identity());
        gov.put("approvedByUserId", actor.userId());
        gov.put("approvedByRole", actor.role());
        gov.put("approvedAt", Instant.now().toString());
        gov.put("remarks", remarks == null ? null : remarks.trim());
        gov.put("approvedContentFingerprint", ScorecardContentFingerprint.of(e));
        appendHistory(gov, "APPROVED", actor, remarks);
        e.setStatus(ScorecardGovernanceStatuses.APPROVED);
        e.setActive(false);
        e.setGovernanceJson(gov);
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse after = adminService.toResponsePublic(repository.save(e));
        adminConfigAuditSupport.captureUpdate(
                "UNDERWRITING_SCORECARD", id.toString(), null, after, "Scorecard checker approved");
        return after;
    }

    @Transactional
    public UnderwritingScorecardResponse returnForChanges(UUID id, Actor actor, String remarks) {
        requireChecker(actor);
        UnderwritingScorecard e = load(id);
        if (!ScorecardGovernanceStatuses.IN_REVIEW.equalsIgnoreCase(e.getStatus())
                && !ScorecardGovernanceStatuses.APPROVED.equalsIgnoreCase(e.getStatus())) {
            throw blocked("Only IN_REVIEW or APPROVED scorecards can be returned", "SCORECARD_NOT_RETURNABLE");
        }
        if (remarks == null || remarks.isBlank()) {
            throw blocked("Return remarks are required", "SCORECARD_RETURN_REMARKS_REQUIRED");
        }
        Map<String, Object> gov = gov(e);
        gov.put("checkerDecision", ScorecardGovernanceStatuses.DECISION_RETURNED);
        gov.put("returnedBy", actor.identity());
        gov.put("returnedByUserId", actor.userId());
        gov.put("returnedByRole", actor.role());
        gov.put("returnedAt", Instant.now().toString());
        gov.put("remarks", remarks.trim());
        gov.remove("approvedBy");
        gov.remove("approvedByUserId");
        gov.remove("approvedAt");
        gov.remove("approvedContentFingerprint");
        appendHistory(gov, "RETURNED", actor, remarks);
        e.setStatus(ScorecardGovernanceStatuses.DRAFT);
        e.setActive(false);
        e.setGovernanceJson(gov);
        e.setUpdatedAt(Instant.now());
        UnderwritingScorecardResponse after = adminService.toResponsePublic(repository.save(e));
        adminConfigAuditSupport.captureUpdate(
                "UNDERWRITING_SCORECARD", id.toString(), null, after, "Scorecard returned for changes");
        return after;
    }

    /**
     * Activate APPROVED scorecard: retire prior ACTIVE peers in same lineage transactionally.
     */
    @Transactional
    public UnderwritingScorecardResponse activate(UUID id, Actor actor) {
        requireActivator(actor);
        UnderwritingScorecard e = load(id);
        if (!ScorecardGovernanceStatuses.APPROVED.equalsIgnoreCase(e.getStatus())) {
            throw blocked("Only APPROVED scorecards can be activated", "SCORECARD_NOT_APPROVED");
        }
        assertExecutionReady(e, "activate");
        assertPreviewValid(e);
        Map<String, Object> gov = gov(e);
        if (!ScorecardGovernanceStatuses.DECISION_APPROVED.equalsIgnoreCase(str(gov.get("checkerDecision")))) {
            throw blocked("Checker APPROVED decision required before activation", "SCORECARD_CHECKER_REQUIRED");
        }
        String approvedFp = str(gov.get("approvedContentFingerprint"));
        if (!ScorecardContentFingerprint.matches(e, approvedFp)) {
            throw blocked(
                    "Approved content changed after approval — return to DRAFT and re-approve",
                    "SCORECARD_APPROVAL_STALE");
        }
        UUID lineage = e.getLineageId() != null ? e.getLineageId() : e.getId();
        Instant now = Instant.now();
        List<UnderwritingScorecard> peers = repository.findAll().stream()
                .filter(s -> lineage.equals(s.getLineageId() != null ? s.getLineageId() : s.getId()))
                .filter(s -> !s.getId().equals(e.getId()))
                .filter(s -> s.isActive() || ScorecardGovernanceStatuses.ACTIVE.equalsIgnoreCase(s.getStatus()))
                .toList();
        for (UnderwritingScorecard peer : peers) {
            Map<String, Object> peerGov = gov(peer);
            peerGov.put("supersededByScorecardId", e.getId().toString());
            peerGov.put("supersededAt", now.toString());
            appendHistory(peerGov, "SUPERSEDED", actor, "Superseded by v" + e.getVersion());
            peer.setGovernanceJson(peerGov);
            peer.setActive(false);
            peer.setStatus(ScorecardGovernanceStatuses.RETIRED);
            peer.setUpdatedAt(now);
            repository.save(peer);
        }
        gov.put("activatedBy", actor.identity());
        gov.put("activatedByUserId", actor.userId());
        gov.put("activatedByRole", actor.role());
        gov.put("activatedAt", now.toString());
        if (!peers.isEmpty()) {
            gov.put("supersedesScorecardId", peers.get(0).getId().toString());
        }
        appendHistory(gov, "ACTIVATED", actor, null);
        e.setGovernanceJson(gov);
        e.setActive(true);
        e.setStatus(ScorecardGovernanceStatuses.ACTIVE);
        e.setActivatedAt(now);
        e.setLineageId(lineage);
        e.setUpdatedAt(now);
        UnderwritingScorecardResponse after = adminService.toResponsePublic(repository.save(e));
        adminConfigAuditSupport.captureUpdate(
                "UNDERWRITING_SCORECARD", id.toString(), null, after, "Scorecard activated; prior lineage ACTIVE retired");
        return after;
    }

    public Map<String, Object> reviewPackage(UUID id) {
        UnderwritingScorecard e = load(id);
        UnderwritingScorecard parent = e.getParentScorecardId() == null ? null
                : repository.findById(e.getParentScorecardId()).orElse(null);
        ScorecardSafetyValidator.ValidationResult safety = ScorecardSafetyValidator.validateForActivation(e);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scorecard", adminService.toResponsePublic(e));
        out.put("status", e.getStatus());
        out.put("version", e.getVersion());
        out.put("scope", Map.of(
                "name", e.getName(),
                "borrowerType", e.getBorrowerType(),
                "loanProduct", e.getLoanProduct(),
                "priority", e.getPriority(),
                "minAmount", e.getMinAmount(),
                "maxAmount", e.getMaxAmount(),
                "geography", e.getGeography()));
        out.put("thresholds", e.getThresholdsJson());
        out.put("factors", businessFactors(e));
        out.put("hardRules", e.getHardRulesJson());
        out.put("diffVsPrevious", ScorecardVersionDiff.diff(parent, e));
        out.put("executionReadiness", Map.of(
                "ok", safety.ok(),
                "problems", safety.problems(),
                "missingPoliciesExplicit", safety.missingPoliciesExplicit(),
                "missingPoliciesConfirmed", safety.missingPoliciesConfirmed()));
        out.put("governance", e.getGovernanceJson() == null ? Map.of() : e.getGovernanceJson());
        out.put("primaryAction", primaryAction(e));
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public Map<String, Object> primaryAction(UnderwritingScorecard e) {
        String status = e.getStatus() == null ? ScorecardGovernanceStatuses.DRAFT : e.getStatus();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        if (ScorecardGovernanceStatuses.DRAFT.equalsIgnoreCase(status)) {
            m.put("action", "SUBMIT_FOR_REVIEW");
            m.put("label", "Submit for Review");
            m.put("secondary", "Save");
        } else if (ScorecardGovernanceStatuses.IN_REVIEW.equalsIgnoreCase(status)) {
            m.put("action", "CHECKER_DECIDE");
            m.put("label", "Approve / Return");
        } else if (ScorecardGovernanceStatuses.APPROVED.equalsIgnoreCase(status)) {
            m.put("action", "ACTIVATE");
            m.put("label", "Activate");
        } else if (ScorecardGovernanceStatuses.ACTIVE.equalsIgnoreCase(status) || e.isActive()) {
            m.put("action", "CREATE_NEW_VERSION");
            m.put("label", "Create New Version");
        } else {
            m.put("action", "NONE");
            m.put("label", status);
        }
        return m;
    }

    /** Strip approval evidence when cloning a new version. */
    public static Map<String, Object> emptyGovernanceForClone() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("requireMakerChecker", true);
        g.put("reviewHistory", List.of());
        return g;
    }

    private void assertEditableOrPreview(UnderwritingScorecard e) {
        if (ScorecardGovernanceStatuses.ACTIVE.equalsIgnoreCase(e.getStatus()) || e.isActive()) {
            throw blocked("ACTIVE scorecard cannot record a mutating preview association", "SCORECARD_ACTIVE_IMMUTABLE");
        }
    }

    private void assertExecutionReady(UnderwritingScorecard e, String gate) {
        // Ensure missing-data confirmed when possible
        ScorecardSafetyValidator.ValidationResult vr = ScorecardSafetyValidator.validateForActivation(e);
        if (!vr.ok()) {
            throw new BusinessRuleException(
                    "Scorecard execution readiness failed at " + gate + ": " + String.join("; ", vr.problems()),
                    "SCORECARD_EXECUTION_NOT_READY",
                    "FIX_SAFETY_BLOCKERS",
                    Map.of("gate", gate, "problems", vr.problems()));
        }
    }

    private void assertPreviewValid(UnderwritingScorecard e) {
        Map<String, Object> gov = gov(e);
        Object last = gov.get("lastPreview");
        if (!(last instanceof Map<?, ?> m)) {
            throw blocked(
                    "A valid scorecard Test/Preview is required before this governance step",
                    "SCORECARD_TEST_REQUIRED");
        }
        String fp = str(m.get("contentFingerprint"));
        if (!ScorecardContentFingerprint.matches(e, fp)) {
            throw blocked(
                    "Scorecard content changed after the last Test/Preview — run Test again",
                    "SCORECARD_TEST_STALE");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> businessFactors(UnderwritingScorecard e) {
        List<Map<String, Object>> out = new ArrayList<>();
        Object rows = e.getScorecardJson() == null ? null : e.getScorecardJson().get("rows");
        Map<String, Object> policies = Map.of();
        if (e.getSafetyJson() != null && e.getSafetyJson().get("factorPolicies") instanceof Map<?, ?> fp) {
            policies = (Map<String, Object>) fp;
        }
        if (!(rows instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> rm)) continue;
            Map<String, Object> row = (Map<String, Object>) rm;
            String param = str(row.get("parameter"));
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("parameter", param);
            one.put("factorLabel", row.get("factorLabel"));
            one.put("source", row.get("source"));
            one.put("canonicalParameterId", row.get("canonicalParameterId"));
            one.put("canonicalDefinitionVersion", row.get("canonicalDefinitionVersion"));
            one.put("condition", row.get("condition"));
            one.put("points", row.get("score"));
            Object pol = policies.get(param);
            if (pol instanceof Map<?, ?> pm) {
                one.put("missingData", pm.get("missingData"));
            } else {
                one.put("missingData", row.get("missingData"));
            }
            out.add(one);
        }
        return out;
    }

    private UnderwritingScorecard load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + id));
    }

    private static Map<String, Object> gov(UnderwritingScorecard e) {
        return new LinkedHashMap<>(e.getGovernanceJson() == null ? Map.of() : e.getGovernanceJson());
    }

    private static void clearCheckerDecision(Map<String, Object> gov) {
        gov.remove("checkerDecision");
        gov.remove("approvedBy");
        gov.remove("approvedByUserId");
        gov.remove("approvedAt");
        gov.remove("approvedContentFingerprint");
        gov.remove("returnedBy");
        gov.remove("returnedAt");
    }

    @SuppressWarnings("unchecked")
    private static void appendHistory(Map<String, Object> gov, String event, Actor actor, String remarks) {
        List<Map<String, Object>> hist = new ArrayList<>();
        Object raw = gov.get("reviewHistory");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) hist.add(new LinkedHashMap<>((Map<String, Object>) m));
            }
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("event", event);
        entry.put("at", Instant.now().toString());
        entry.put("by", actor.identity());
        entry.put("byUserId", actor.userId());
        entry.put("byRole", actor.role());
        if (remarks != null && !remarks.isBlank()) entry.put("remarks", remarks.trim());
        hist.add(entry);
        gov.put("reviewHistory", hist);
    }

    private static BusinessRuleException blocked(String message, String reason) {
        return new BusinessRuleException(message, reason, "RESOLVE_GOVERNANCE", null);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }
}
