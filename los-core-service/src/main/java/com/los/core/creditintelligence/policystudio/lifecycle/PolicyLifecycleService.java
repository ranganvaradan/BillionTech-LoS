package com.los.core.creditintelligence.policystudio.lifecycle;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyAmbiguity;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.PolicyDocumentService;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.exception.BusinessRuleException;
import com.los.core.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Business policy lifecycle for single-NBFC staging. Maps onto existing Studio
 * sessions without renaming backend enums. Never enables production authority.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyLifecycleService {

    public static final String META_KEY = "policyLifecycle";
    public static final String LINEAGE_KEY = "policyLineage";

    /** Destructive draft delete — administrator / policy-admin roles. */
    public static final Set<String> DELETE_ROLES = Set.of(
            "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN", "CREDIT_MANAGER");
    /** Retire — governance roles. */
    public static final Set<String> RETIRE_ROLES = Set.of(
            "ADMINISTRATOR", "ADMIN", "PLATFORM_ADMIN", "CREDIT_MANAGER", "POLICY_CHECKER", "RISK_MANAGER");

    private final PolicyApplicabilityResolver resolver;
    private final PolicyStudioOrchestrator orchestrator;
    private final CreditIntelligenceProperties properties;
    private final PolicyCatalogueService catalogueService;
    private final PolicyImplementabilityService implementabilityService = new PolicyImplementabilityService();

    /** Fallback in-memory catalogue when durable store unavailable (unit tests). */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, PolicyApplicabilityRecord>> catalogue =
            new ConcurrentHashMap<>();

    /** documentId → frozen content fingerprint at approval (immutability). */
    private final ConcurrentHashMap<UUID, String> approvedContentFingerprints = new ConcurrentHashMap<>();

    /** documentId → historical evaluation pins for replay demos. */
    private final ConcurrentHashMap<UUID, Map<String, Object>> evaluationPins = new ConcurrentHashMap<>();

    /** documentId → version history rows (business view). */
    private final ConcurrentHashMap<UUID, List<Map<String, Object>>> historyByLineage =
            new ConcurrentHashMap<>();

    public Map<String, Object> statusMapping() {
        Map<String, Object> out = new LinkedHashMap<>(PolicyBusinessLifecycleStatus.statusMapping());
        out.put("allowCanonicalAuthority", false);
        out.put("productionAuthority", "DISABLED");
        out.put("portfolioComparisonBoundary",
                "Portfolio Intelligence → Policy Impact Lab (not application processing)");
        return out;
    }

    public Map<String, Object> settingsView(PolicyStudioSession session) {
        Map<String, Object> life = ensureLifecycle(session);
        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        out.put("policySettings", businessHeader(session, life));
        Map<String, Object> applicability = castMap(life.get("applicability"));
        out.put("applicability", applicability);
        out.put("scopeSummary", PolicyScopeSupport.summarize(applicability));
        out.put("scopeOptions", PolicyScopeSupport.scopeOptions());
        out.put("mappingReliability", PolicyScopeSupport.mappingReliability());
        out.put("overlapPreview", overlapPreview(session, life));
        Map<String, Object> impl = implementationStatus(session, life);
        out.put("implementationStatus", impl);
        List<String> approveBlockers = scheduleReadinessBlockers(session, life);
        List<String> submitBlockers = submitReadinessBlockers(session, life);
        boolean readyToSchedule = Boolean.TRUE.equals(life.get("readyToSchedule"))
                || (PolicyBusinessLifecycleStatus.APPROVED.equals(str(life, "businessStatus", ""))
                && approveBlockers.isEmpty());
        out.put("readyToSchedule", readyToSchedule);
        out.put("readyToScheduleBlockers", approveBlockers);
        out.put("submitBlockers", submitBlockers);
        out.put("approveBlockers", approveBlockers);
        out.put("businessStatus", life.get("businessStatus"));
        out.put("lifecycleAuthority", PolicyCanonicalLifecycleAuthority.NAME);
        out.put("lifecycleOwnerType", PolicyCanonicalLifecycleAuthority.OWNER_TYPE);
        out.put("lifecycleOwnerId", life.get("durableApplicabilityId"));
        boolean contentEditable = PolicyCanonicalLifecycleAuthority.contentEditable(
                str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT))
                && !Boolean.TRUE.equals(life.get("contentImmutable"));
        out.put("contentEditable", contentEditable);
        out.put("statusMapping", PolicyBusinessLifecycleStatus.statusMapping());
        List<String> actions = availableActions(session, life);
        if (!contentEditable) {
            actions.remove("SAVE_DRAFT");
        }
        out.put("actions", actions);
        out.put("history", historyFor(session));
        Map<String, Object> cmView = creditManagerLifecycleView(session, life, impl, submitBlockers, approveBlockers, actions);
        out.putAll(cmView);
        out.put("authoritySeparation", Map.of(
                "policyBusinessStatus", life.get("businessStatus"),
                "creditIntelligenceProductionAuthority", "DISABLED",
                "allowCanonicalAuthority", false,
                "note", "Business ACTIVE does not enable production underwriting."));
        out.put("portfolioIntelligencePlaceholder", Map.of(
                "path", "Portfolio Intelligence → Policy Impact Lab",
                "enabled", false,
                "note", "Alternate-policy comparison stays outside normal application processing."));
        return out;
    }

    public Map<String, Object> saveDraft(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        String status = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        if (isImmutable(status) || Boolean.TRUE.equals(life.get("contentImmutable"))
                || !PolicyCanonicalLifecycleAuthority.contentEditable(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approved / active policy content is immutable. Use Create New Version.");
        }
        assertNotMutatingApprovedContent(session);
        try {
            mergeApplicability(life, body);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        if (body.get("policyName") != null && session.getDocument() != null) {
            session.getDocument().setName(String.valueOf(body.get("policyName")));
        }
        if (body.get("reasonForChange") != null) {
            life.put("reasonForChange", String.valueOf(body.get("reasonForChange")));
        }
        if (body.get("policyType") != null) {
            life.put("policyType", String.valueOf(body.get("policyType")));
        }
        if (body.get("replacesDocumentId") != null && !String.valueOf(body.get("replacesDocumentId")).isBlank()) {
            life.put("replacesDocumentId", String.valueOf(body.get("replacesDocumentId")).trim());
        }
        if (body.get("replacesApplicabilityId") != null && !String.valueOf(body.get("replacesApplicabilityId")).isBlank()) {
            life.put("replacesApplicabilityId", String.valueOf(body.get("replacesApplicabilityId")).trim());
        }
        life.put("businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        life.put("updatedAt", Instant.now().toString());
        persistLifecycle(session, life);
        Map<String, Object> out = settingsView(session);
        out.put("message", "Draft policy settings saved.");
        out.put("rulesUnchanged", true);
        return out;
    }

    public Map<String, Object> submitForReview(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        if (isImmutable(str(life, "businessStatus", ""))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot submit an immutable policy for review. Create a new version.");
        }
        mergeApplicability(life, body);
        List<String> blockers = submitReadinessBlockers(session, life);
        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot submit for review. Resolve: " + String.join("; ", blockers));
        }
        life.put("businessStatus", PolicyBusinessLifecycleStatus.IN_REVIEW);
        life.put("submittedAt", Instant.now().toString());
        life.put("submittedBy", str(body, "reviewer", "credit_manager"));
        persistLifecycle(session, life);
        Map<String, Object> out = settingsView(session);
        out.put("message", "Policy submitted for review.");
        return out;
    }

    /**
     * Business Approve Policy — requires Day 4/6/6.1 gates + Credit Manager + Checker.
     * Does NOT schedule and does NOT enable production authority.
     */
    public Map<String, Object> approvePolicy(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        List<String> blockers = scheduleReadinessBlockers(session, life);
        if (!blockers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Policy cannot become APPROVED until: " + String.join("; ", blockers));
        }
        if (!hasCheckerApproval(session)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Checker approval is required before Approve Policy.");
        }
        life.put("businessStatus", PolicyBusinessLifecycleStatus.APPROVED);
        life.put("approvedAt", Instant.now().toString());
        life.put("approvedBy", str(body, "reviewer",
                str(life, "approvedBy", "credit_manager")));
        life.put("checker", str(body, "checker",
                str(life, "checker", "policy_checker")));
        approvedContentFingerprints.put(session.documentId(), contentFingerprint(session));
        life.put("contentImmutable", true);
        life.put("readyToSchedule", true);
        persistLifecycle(session, life);
        appendHistory(session, life, "APPROVED");
        Map<String, Object> out = settingsView(session);
        out.put("message", "Policy APPROVED. Ready to schedule. Production authority remains DISABLED.");
        return out;
    }

    public Map<String, Object> schedulePolicy(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        String status = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        if (!PolicyBusinessLifecycleStatus.APPROVED.equals(status)
                && !PolicyBusinessLifecycleStatus.SCHEDULED.equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only APPROVED policies can be scheduled. Current status: " + status);
        }
        mergeApplicability(life, body);
        Map<String, Object> app = castMap(life.get("applicability"));
        // Gates are enforced at Approve Policy; schedule requires products + effective dates + no overlap.
        if (!(app.get("products") instanceof List<?> pl) || pl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot schedule: Applicable product(s) must be defined");
        }
        if (app.get("effectiveFrom") == null || String.valueOf(app.get("effectiveFrom")).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Effective From is required to schedule a policy.");
        }
        if (!PolicyBusinessLifecycleStatus.APPROVED.equals(status)) {
            List<String> blockers = scheduleReadinessBlockers(session, life);
            if (!blockers.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot schedule: " + String.join("; ", blockers));
            }
        }
        PolicyApplicabilityRecord candidate = toRecord(session, life, PolicyBusinessLifecycleStatus.SCHEDULED);
        Map<String, Object> overlap = resolver.detectOverlap(candidate, catalogueList(session.getDocument().getTenantId()));
        if (Boolean.TRUE.equals(overlap.get("blocked"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conflicts = overlap.get("conflicts") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            String msg = conflicts.isEmpty()
                    ? "Effective-date / applicability conflict — change dates or scope."
                    : String.valueOf(conflicts.get(0).get("message"));
            throw new ResponseStatusException(HttpStatus.CONFLICT, msg);
        }

        LocalDate from = LocalDate.parse(String.valueOf(app.get("effectiveFrom")).substring(0, 10));
        LocalDate businessAsOf = body.get("businessDate") != null
                ? LocalDate.parse(String.valueOf(body.get("businessDate")).substring(0, 10))
                : from;
        boolean alreadyActive = !businessAsOf.isBefore(from);
        String next = alreadyActive ? PolicyBusinessLifecycleStatus.ACTIVE : PolicyBusinessLifecycleStatus.SCHEDULED;
        life.put("businessStatus", next);
        life.put("scheduledAt", Instant.now().toString());
        life.put("scheduledBy", str(body, "reviewer", "credit_manager"));
        life.put("contentImmutable", true);
        approvedContentFingerprints.putIfAbsent(session.documentId(), contentFingerprint(session));

        // Supersede prior version in same lineage when activating
        if (alreadyActive && life.get("replacesDocumentId") != null) {
            supersedePrior(session.getDocument().getTenantId(),
                    UUID.fromString(String.valueOf(life.get("replacesDocumentId"))),
                    str(life, "policyVersion", "v?"));
        }

        PolicyApplicabilityRecord registered = toRecord(session, life, next);
        catalogue
                .computeIfAbsent(session.getDocument().getTenantId(), t -> new ConcurrentHashMap<>())
                .put(session.documentId(), registered);
        persistLifecycle(session, life);
        appendHistory(session, life, next);

        Map<String, Object> out = settingsView(session);
        // Durable catalogue handoff (production-shaped persistence)
        if (catalogueService != null) {
            try {
                Map<String, Object> bodyDurable = new LinkedHashMap<>();
                bodyDurable.put("policyDocumentId", session.documentId().toString());
                bodyDurable.put("policyName", session.getDocument().getName());
                bodyDurable.put("policyVersion", life.get("policyVersion"));
                bodyDurable.put("policyType", life.get("policyType"));
                bodyDurable.putAll(castMap(life.get("applicability")));
                bodyDurable.put("approvedBy", life.get("approvedBy"));
                bodyDurable.put("checker", life.get("checker"));
                bodyDurable.put("createdBy", life.get("createdBy"));
                bodyDurable.put("reasonForChange", life.get("reasonForChange"));
                bodyDurable.put("replacesVersion", life.get("replacesVersion"));
                if (life.get("replacesDocumentId") != null) {
                    bodyDurable.put("replacesDocumentId", life.get("replacesDocumentId"));
                }
                if (life.get("replacesApplicabilityId") != null) {
                    bodyDurable.put("replacesApplicabilityId", life.get("replacesApplicabilityId"));
                }
                // Session draft-package ids are in-memory / studio-only. They are not
                // ci_policy_draft_package rows, so attaching them here trips
                // ci_policy_applicability_draft_package_id_fkey and the whole durable
                // catalogue handoff is deferred. Omit unless a future path persists the package first.
                var draft = catalogueService.upsertDraft(session.getDocument().getTenantId(), bodyDurable);
                catalogueService.transition(draft.getId(), PolicyBusinessLifecycleStatus.APPROVED,
                        PolicyCatalogueService.EVT_APPROVED,
                        str(body, "reviewer", "credit_manager"), "Pre-schedule approve");
                Map<String, Object> scheduleBody = new LinkedHashMap<>();
                scheduleBody.put("effectiveFrom", String.valueOf(app.get("effectiveFrom")));
                if (app.get("effectiveUntil") != null && !String.valueOf(app.get("effectiveUntil")).isBlank()) {
                    scheduleBody.put("effectiveUntil", String.valueOf(app.get("effectiveUntil")));
                }
                scheduleBody.put("products", app.get("products"));
                scheduleBody.put("businessDate", String.valueOf(body.getOrDefault("businessDate", app.get("effectiveFrom"))));
                scheduleBody.put("actor", str(body, "reviewer", "credit_manager"));
                Map<String, Object> scheduledDurable = catalogueService.schedule(draft.getId(), scheduleBody);
                life.put("durableApplicabilityId", draft.getId().toString());
                persistLifecycle(session, life);
                out.put("durableCatalogue", scheduledDurable);
                out.put("durableApplicabilityId", draft.getId().toString());
            } catch (Exception e) {
                // Scratch / in-memory Studio sessions may lack a durable ci_policy_document row.
                // Business schedule already succeeded above — surface a warning, do not roll back
                // SCHEDULED/ACTIVE or fake production authority.
                log.warn("Durable catalogue schedule deferred after business {}: {}",
                        next, e.getMessage());
                out.put("durableCatalogueWarning",
                        "Business schedule applied. Durable catalogue sync deferred for this session draft.");
            }
        }

        String durableWarn = out.get("durableCatalogueWarning") == null
                ? "" : (" " + out.get("durableCatalogueWarning"));
        out.put("message", (alreadyActive
                ? "Policy business status ACTIVE for scheduling window. Credit Intelligence production authority remains DISABLED."
                : "Policy SCHEDULED. Applicability uses evaluation date. Production authority remains DISABLED.")
                + durableWarn);
        out.put("scheduledRecord", registered.toBusinessView());
        out.put("overlapCheck", overlap);
        return out;
    }

    public Map<String, Object> retirePolicy(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        String status = PolicyBusinessLifecycleStatus.fromStored(
                str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT));
        if (!(PolicyBusinessLifecycleStatus.ACTIVE.equals(status)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(status)
                || PolicyBusinessLifecycleStatus.APPROVED.equals(status))) {
            throw new BusinessRuleException(
                    "Only APPROVED / SCHEDULED / ACTIVE policies can be retired.",
                    "POLICY_RETIRE_NOT_ALLOWED",
                    "Use Open / Copy for this status",
                    Map.of("businessStatus", status, "documentId", session.documentId().toString()));
        }
        requireRole(body, RETIRE_ROLES, "Policy governance role required to retire");
        String reason = firstNonBlank(str(body, "retirementReason", null),
                str(body, "reason", null), str(body, "remarks", null));
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException(
                    "Retirement reason / remarks are required.",
                    "POLICY_RETIRE_REASON_REQUIRED",
                    "Provide retirementReason",
                    Map.of("documentId", session.documentId().toString()));
        }
        // Shadow/governance: do not invent false Live Rule Set / Scorecard runtime dependencies.
        String actor = firstNonBlank(str(body, "reviewer", null), str(body, "actor", null), "credit_manager");
        life.put("businessStatus", PolicyBusinessLifecycleStatus.RETIRED);
        life.put("retiredAt", Instant.now().toString());
        life.put("retiredBy", actor);
        life.put("retirementReason", reason.trim());
        if (body.get("effectiveUntil") != null) {
            Map<String, Object> app = castMap(life.get("applicability"));
            app.put("effectiveUntil", String.valueOf(body.get("effectiveUntil")));
            life.put("applicability", app);
        }
        catalogue.getOrDefault(session.getDocument().getTenantId(), new ConcurrentHashMap<>())
                .remove(session.documentId());
        if (catalogueService != null && life.get("durableApplicabilityId") != null) {
            try {
                UUID appId = UUID.fromString(String.valueOf(life.get("durableApplicabilityId")));
                catalogueService.retire(appId, Map.of(
                        "actor", actor,
                        "reason", reason.trim(),
                        "effectiveUntil", body.getOrDefault("effectiveUntil", "")));
            } catch (Exception e) {
                log.warn("durable catalogue retire skipped documentId={} reason={}",
                        session.documentId(), e.getMessage());
            }
        }
        persistLifecycle(session, life);
        appendHistory(session, life, PolicyBusinessLifecycleStatus.RETIRED);
        if (orchestrator != null) {
            orchestrator.persistence().saveSessionSnapshot(session);
        }
        Map<String, Object> out = settingsView(session);
        out.put("message", "Policy RETIRED. Historical evaluations retain their pinned version. "
                + "Production underwriting authority unchanged (allowCanonicalAuthority=false).");
        out.put("retirementReason", reason.trim());
        out.put("retiredBy", actor);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /**
     * POLICY-STUDIO-UX-CLOSURE-1 — hard-delete a never-activated DRAFT with no protected dependency.
     * Identity is by documentId/version — never by display name.
     */
    public Map<String, Object> deleteDraftPolicy(PolicyStudioSession session, Map<String, Object> body) {
        requireRole(body, DELETE_ROLES, "Administrator / policy-admin role required to delete drafts");
        Map<String, Object> life = ensureLifecycle(session);
        String status = PolicyBusinessLifecycleStatus.fromStored(
                str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT));
        String deny = deleteDenialReason(session, life, status);
        if (deny != null) {
            throw new BusinessRuleException(
                    deny,
                    "POLICY_DELETE_NOT_ALLOWED",
                    "Use Retire for ACTIVE policies, or Open/Copy for historical versions",
                    Map.of(
                            "documentId", session.documentId().toString(),
                            "policyName", session.getDocument() == null ? "" : String.valueOf(session.getDocument().getName()),
                            "policyVersion", str(life, "policyVersion", "v1"),
                            "businessStatus", status));
        }
        UUID docId = session.documentId();
        String policyName = session.getDocument() == null ? "" : String.valueOf(session.getDocument().getName());
        String version = str(life, "policyVersion", "v1");
        String actor = firstNonBlank(str(body, "reviewer", null), str(body, "actor", null), "administrator");
        // Audit evidence before removal (lineage history retains the event without the session body)
        life.put("deletedAt", Instant.now().toString());
        life.put("deletedBy", actor);
        life.put("deleteReason", firstNonBlank(str(body, "reason", null), "Draft deleted by administrator"));
        appendHistory(session, life, "DRAFT_DELETED");
        catalogue.getOrDefault(session.getDocument().getTenantId(), new ConcurrentHashMap<>())
                .remove(docId);
        approvedContentFingerprints.remove(docId);
        evaluationPins.entrySet().removeIf(e -> {
            Object pinDoc = e.getValue() == null ? null : e.getValue().get("documentId");
            return docId.toString().equals(String.valueOf(pinDoc));
        });
        if (orchestrator == null || !orchestrator.persistence().deleteSession(docId)) {
            throw new BusinessRuleException(
                    "Draft session could not be removed from persistence.",
                    "POLICY_DELETE_NOT_ALLOWED",
                    "Retry or contact platform admin",
                    Map.of("documentId", docId.toString()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        out.put("deleted", true);
        out.put("documentId", docId.toString());
        out.put("policyName", policyName);
        out.put("policyVersion", version);
        out.put("message", "Draft policy permanently deleted. Unrelated versions and Live Rule Sets / "
                + "Scorecards / Product Config were not modified.");
        out.put("auditEvent", "DRAFT_DELETED");
        out.put("deletedBy", actor);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /** Landing-row actions for Existing Policies (Open/Copy always; Delete/Retire context-valid). */
    public List<String> landingActions(PolicyStudioSession session) {
        Map<String, Object> life = ensureLifecycle(session);
        return resolveLandingActions(session, life);
    }

    /**
     * Same landing action matrix for catalogue-backed rows (no in-memory session).
     * Catalogue governance rows carry durableApplicabilityId → DELETE never exposed.
     */
    public List<String> landingActionsForCatalogueRow(Map<String, Object> catalogueRow) {
        Map<String, Object> life = new LinkedHashMap<>();
        Object status = catalogueRow == null ? null : catalogueRow.get("status");
        if (status == null && catalogueRow != null) {
            status = catalogueRow.get("businessStatus");
        }
        life.put("businessStatus", status != null ? status : PolicyBusinessLifecycleStatus.DRAFT);
        if (catalogueRow != null) {
            if (catalogueRow.get("contentImmutable") != null) {
                life.put("contentImmutable", catalogueRow.get("contentImmutable"));
            }
            Object applicabilityId = catalogueRow.get("applicabilityId");
            if (applicabilityId != null && !String.valueOf(applicabilityId).isBlank()) {
                life.put("durableApplicabilityId", applicabilityId);
            }
            if (catalogueRow.get("approvedAt") != null) {
                life.put("approvedAt", catalogueRow.get("approvedAt"));
            }
            if (catalogueRow.get("scheduledAt") != null) {
                life.put("scheduledAt", catalogueRow.get("scheduledAt"));
            }
            if (catalogueRow.get("retiredAt") != null) {
                life.put("retiredAt", catalogueRow.get("retiredAt"));
            }
        }
        return resolveLandingActions(null, life);
    }

    /** Single resolver used by session and catalogue landing paths. */
    List<String> resolveLandingActions(PolicyStudioSession session, Map<String, Object> life) {
        String status = PolicyBusinessLifecycleStatus.fromStored(
                str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT));
        List<String> actions = new ArrayList<>();
        actions.add("OPEN");
        actions.add("COPY");
        if ((PolicyBusinessLifecycleStatus.DRAFT.equals(status)
                || PolicyBusinessLifecycleStatus.IN_REVIEW.equals(status))
                && deleteDenialReason(session, life, status) == null) {
            actions.add("DELETE");
        }
        if (PolicyBusinessLifecycleStatus.ACTIVE.equals(status)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(status)
                || PolicyBusinessLifecycleStatus.APPROVED.equals(status)) {
            actions.add("RETIRE");
        }
        return actions;
    }

    private String deleteDenialReason(PolicyStudioSession session, Map<String, Object> life, String status) {
        if (!(PolicyBusinessLifecycleStatus.DRAFT.equals(status)
                || PolicyBusinessLifecycleStatus.IN_REVIEW.equals(status))) {
            return "Hard delete is only allowed for never-activated DRAFT policies. Status=" + status;
        }
        if (Boolean.TRUE.equals(life.get("contentImmutable"))) {
            return "Policy content is immutable (historically protected). Hard delete is not allowed.";
        }
        if (life.get("approvedAt") != null || life.get("scheduledAt") != null
                || life.get("retiredAt") != null) {
            return "Policy has protected lifecycle history (approved/scheduled/retired). Hard delete is not allowed.";
        }
        if (life.get("durableApplicabilityId") != null) {
            return "Policy is registered in the durable governance catalogue. Hard delete is not allowed.";
        }
        // Catalogue / status-only landing path — never invent DELETE without session evidence.
        if (session == null || session.documentId() == null) {
            return "Hard delete requires an editable studio session; catalogue-backed rows cannot authorize DELETE.";
        }
        String lineage = str(life, "lineageId", session.documentId().toString());
        UUID key;
        try {
            key = UUID.fromString(lineage);
        } catch (Exception e) {
            key = session.documentId();
        }
        List<Map<String, Object>> hist = historyByLineage.getOrDefault(key, List.of());
        for (Map<String, Object> row : hist) {
            String ev = String.valueOf(row.getOrDefault("event", ""));
            String st = String.valueOf(row.getOrDefault("businessStatus", ""));
            if (ev.contains("ACTIVE") || PolicyBusinessLifecycleStatus.ACTIVE.equals(st)
                    || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(st)
                    || PolicyBusinessLifecycleStatus.RETIRED.equals(st)
                    || "APPROVED".equals(ev) || PolicyBusinessLifecycleStatus.APPROVED.equals(st)) {
                return "Policy has historical ACTIVE/APPROVED lifecycle evidence. Hard delete is not allowed.";
            }
        }
        for (Map<String, Object> pin : evaluationPins.values()) {
            if (pin != null && session.documentId().toString().equals(String.valueOf(pin.get("documentId")))) {
                return "Policy is referenced by historical evaluation pins. Hard delete is not allowed.";
            }
        }
        return null;
    }

    private static void requireRole(Map<String, Object> body, Set<String> allowed, String message) {
        String role = firstNonBlank(
                body == null ? null : str(body, "reviewerRole", null),
                body == null ? null : str(body, "actorRole", null),
                body == null ? null : str(body, "role", null));
        if (role == null || role.isBlank()) {
            throw new ForbiddenException("Authentication required — " + message);
        }
        if (!allowed.contains(role.trim().toUpperCase(Locale.ROOT))) {
            throw new ForbiddenException(message);
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    /**
     * Staging-demo helper: mark a draft ACTIVE so Create New Version can be exercised.
     * Skips maker-checker / test gates. Never enables production authority.
     */
    public Map<String, Object> stampActiveForVersioningDemo(PolicyStudioSession session, Map<String, Object> body) {
        Map<String, Object> life = ensureLifecycle(session);
        if (body != null && !body.isEmpty()) {
            try {
                mergeApplicability(life, body);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
        life.put("businessStatus", PolicyBusinessLifecycleStatus.ACTIVE);
        life.put("contentImmutable", true);
        life.put("approvedBy", str(body, "reviewer", "staging_versioning_demo"));
        life.put("checker", str(body, "checker", "staging_versioning_demo"));
        life.put("approvedAt", Instant.now().toString());
        life.put("reasonForChange", str(body, "reasonForChange",
                "Staging stamp ACTIVE for version-integrity walkthrough"));
        if (str(life, "policyVersion", null) == null || str(life, "policyVersion", "").isBlank()) {
            life.put("policyVersion", "v1");
        }
        if (str(life, "lineageId", null) == null || str(life, "lineageId", "").isBlank()) {
            life.put("lineageId", session.documentId().toString());
        }
        persistLifecycle(session, life);
        appendHistory(session, life, "STAGED_ACTIVE_FOR_VERSIONING");
        Map<String, Object> out = settingsView(session);
        out.put("message", "Stamped ACTIVE for Create New Version (staging demo only). "
                + "Production authority remains DISABLED.");
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /**
     * POLICY-VERSION-INTEGRITY-1 — clone approved/active policy into a new DRAFT version.
     * Structured session clone (rules, typed values, resolutions, measures). No NLP re-ingestion.
     * Material edits happen only on the draft; source version remains immutable.
     */
    public Map<String, Object> createNewVersion(PolicyStudioSession source, Map<String, Object> body) {
        Map<String, Object> srcLife = ensureLifecycle(source);
        String srcStatus = str(srcLife, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        if (!(PolicyBusinessLifecycleStatus.APPROVED.equals(srcStatus)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(srcStatus)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(srcStatus)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(srcStatus))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Create New Version is available from APPROVED / SCHEDULED / ACTIVE policies.");
        }
        if (orchestrator == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Policy Studio orchestrator required for structured version clone.");
        }
        CiPolicyDocument doc = source.getDocument();
        String text = doc.getSourceText();
        if (text == null || text.isBlank()) {
            // Scratch drafts still need a non-blank provenance string for document hash.
            text = "STRUCTURED_POLICY_CLONE provenance for " + doc.getName();
        }
        int nextVer = parseVersionNumber(str(srcLife, "policyVersion", "v" + doc.getDocumentVersion())) + 1;
        String versionLabel = "v" + nextVer;
        String name = str(body, "policyName", doc.getName());

        // Footer is lineage / hash uniqueness only — not fed into NLP (no processUpload).
        String cloneText = text + "\n\n<!-- POLICY_VERSION " + versionLabel
                + " replaces=" + doc.getId() + " structuredClone=true -->\n";

        PolicyDocumentService documents = new PolicyDocumentService();
        CiPolicyDocument newDoc = documents.create(
                doc.getTenantId(),
                name,
                doc.getDocumentType() == null ? "TXT" : doc.getDocumentType(),
                cloneText,
                str(body, "createdBy", "credit_manager"),
                doc.getOriginalFileReference(),
                Map.of(
                        "structuredClone", true,
                        "reingested", false,
                        "clonedFromDocumentId", doc.getId().toString(),
                        "previousVersion", str(srcLife, "policyVersion", "v1")));
        newDoc.setDocumentVersion(nextVer);
        newDoc.setProductScope(doc.getProductScope());
        newDoc.setLenderId(doc.getLenderId());
        newDoc.setLanguage(doc.getLanguage() == null ? "en" : doc.getLanguage());
        newDoc.setScorecardId(doc.getScorecardId());

        PolicyStudioSession created = StructuredPolicySessionCloner.cloneSession(
                source, newDoc, StructuredPolicySessionCloner.Mode.NEW_VERSION);
        orchestrator.persistence().saveSessionSnapshot(created);

        Map<String, Object> life = ensureLifecycle(created);
        life.put("policyVersion", versionLabel);
        life.put("policyType", str(srcLife, "policyType", "CREDIT_POLICY"));
        life.put("businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        life.put("replacesVersion", str(srcLife, "policyVersion", "v" + doc.getDocumentVersion()));
        life.put("replacesDocumentId", doc.getId().toString());
        life.put("reasonForChange", str(body, "reasonForChange", "Material policy change — new version"));
        life.put("lineageId", str(srcLife, "lineageId", doc.getId().toString()));
        life.put("contentImmutable", false);
        life.put("createdBy", str(body, "createdBy", "credit_manager"));
        // Governance reset — do not inherit approvals / schedule / activation
        life.remove("approvedBy");
        life.remove("approvedAt");
        life.remove("checker");
        life.remove("checkerApprovedAt");
        life.remove("scheduledAt");
        life.remove("activatedAt");
        life.remove("readyToSchedule");
        life.put("applicability", governedApplicabilityForClone(srcLife));
        persistLifecycle(created, life);
        orchestrator.persistence().saveSessionSnapshot(created);
        appendHistory(created, life, "DRAFT_CREATED_FROM_" + str(srcLife, "policyVersion", "?"));
        // Also record on source lineage history (does not mutate source status)
        appendHistory(source, srcLife, "SPAWNED_" + versionLabel);

        long uwCount = StructuredPolicySessionCloner.countUnderwritingRules(created);
        Map<String, Object> out = settingsView(created);
        out.put("documentId", created.documentId().toString());
        out.put("structuredClone", true);
        out.put("reingested", false);
        out.put("underwritingRuleCount", uwCount);
        out.put("message", "New DRAFT version " + versionLabel + " structured-cloned from "
                + str(srcLife, "policyVersion", "?")
                + " (" + uwCount + " underwriting rules). "
                + "Approvals and test evidence reset — Tests, Simulation and maker-checker must run again.");
        out.put("lineage", Map.of(
                "from", str(srcLife, "policyVersion", "?"),
                "to", versionLabel,
                "fromDocumentId", doc.getId().toString(),
                "toDocumentId", created.documentId().toString(),
                "structuredClone", true,
                "reingested", false));
        return out;
    }

    public Map<String, Object> resolveShadowApplication(UUID tenantId, Map<String, Object> body) {
        ApplicationPolicyQuery query = new ApplicationPolicyQuery(
                str(body, "applicationCode", "APP-X"),
                str(body, "productCode", null),
                blankToNull(str(body, "facilityType", null)),
                blankToNull(str(body, "customerSegment", null)),
                blankToNull(str(body, "borrowerType", null)),
                blankToNull(str(body, "securedUnsecured", null)),
                blankToNull(str(body, "programScheme", null)),
                decimal(body.get("loanAmount")),
                body.get("evaluationDate") == null ? null
                        : LocalDate.parse(String.valueOf(body.get("evaluationDate")).substring(0, 10))
        );
        Map<String, Object> result;
        if (catalogueService != null) {
            result = catalogueService.resolve(tenantId, query);
        } else {
            result = resolver.resolve(query, catalogueList(tenantId));
        }
        stampSafety(result);
        result.put("mode", "SHADOW");
        result.put("note", "Shadow resolution only — production applications are not routed through this engine.");
        if (PolicyApplicabilityResolver.EXACTLY_ONE.equals(result.get("outcome"))) {
            result.put("banner", "ONE POLICY SELECTED");
        }
        return result;
    }

    /** Pin policy version on an evaluation context (historical replay). */
    public Map<String, Object> pinHistoricalEvaluation(
            UUID evaluationId, UUID policyVersionId, String policyVersionLabel, LocalDate evaluationAsOf) {
        Map<String, Object> pin = new LinkedHashMap<>();
        pin.put("evaluationId", evaluationId.toString());
        pin.put("policyVersionId", policyVersionId.toString());
        pin.put("policyVersion", policyVersionLabel);
        pin.put("evaluationAsOf", evaluationAsOf == null ? null : evaluationAsOf.toString());
        pin.put("pinnedAt", Instant.now().toString());
        evaluationPins.put(evaluationId, pin);
        Map<String, Object> out = new LinkedHashMap<>(pin);
        stampSafety(out);
        out.put("message", "Historical evaluation pinned to original policy version.");
        return out;
    }

    public Map<String, Object> replayHistorical(UUID evaluationId, UUID newlyActivePolicyVersionId) {
        Map<String, Object> pin = evaluationPins.get(evaluationId);
        if (pin == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No pinned historical evaluation");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        out.put("pinnedPolicyVersionId", pin.get("policyVersionId"));
        out.put("pinnedPolicyVersion", pin.get("policyVersion"));
        out.put("newlyActivePolicyVersionId",
                newlyActivePolicyVersionId == null ? null : newlyActivePolicyVersionId.toString());
        out.put("usesPinnedVersion", true);
        out.put("changedByNewActivePolicy", false);
        out.put("message", "Replay resolves the originally pinned policy version — new ACTIVE policy does not rewrite history.");
        return out;
    }

    public Map<String, Object> assertImmutability(PolicyStudioSession session, Map<String, Object> attemptedEdit) {
        Map<String, Object> life = ensureLifecycle(session);
        String status = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        Map<String, Object> out = new LinkedHashMap<>();
        stampSafety(out);
        if (isImmutable(status) || Boolean.TRUE.equals(life.get("contentImmutable"))) {
            out.put("allowed", false);
            out.put("requiresNewVersion", true);
            out.put("message", "Material edit of APPROVED/ACTIVE policy is blocked. Create New Version.");
            out.put("attemptedEdit", attemptedEdit == null ? Map.of() : attemptedEdit);
            return out;
        }
        out.put("allowed", true);
        out.put("requiresNewVersion", false);
        return out;
    }

    public List<PolicyApplicabilityRecord> catalogueList(UUID tenantId) {
        ConcurrentHashMap<UUID, PolicyApplicabilityRecord> map =
                catalogue.getOrDefault(tenantId, new ConcurrentHashMap<>());
        return new ArrayList<>(map.values());
    }

    public void registerForTests(UUID tenantId, PolicyApplicabilityRecord record) {
        catalogue.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>())
                .put(record.policyVersionId(), record);
    }

    public void clearCatalogueForTests(UUID tenantId) {
        catalogue.remove(tenantId);
        approvedContentFingerprints.clear();
        evaluationPins.clear();
        historyByLineage.clear();
    }

    // ─── internals ─────────────────────────────────────────────────

    private Map<String, Object> ensureLifecycle(PolicyStudioSession session) {
        CiPolicyDocument doc = session.getDocument();
        Map<String, Object> meta = doc.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(doc.getMetadata());
        Object existing = meta.get(META_KEY);
        Map<String, Object> life;
        if (existing instanceof Map<?, ?> m) {
            life = new LinkedHashMap<>(castMap(m));
        } else {
            life = defaultLifecycle(session);
            meta.put(META_KEY, life);
            doc.setMetadata(meta);
        }
        overlayCanonicalLifecycle(session, life);
        // Refresh readiness flags each read
        List<String> blockers = scheduleReadinessBlockers(session, life);
        refreshReadyFlag(session, life);
        return life;
    }

    /**
     * Read-path overlay: durable catalogue status for this document/version is reconciled
     * with session metadata. Does not write catalogue rows or invent ACTIVE from dates.
     */
    private void overlayCanonicalLifecycle(PolicyStudioSession session, Map<String, Object> life) {
        if (session == null || session.getDocument() == null || life == null) {
            return;
        }
        String sessionStatus = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        String catalogueStatus = null;
        UUID applicabilityId = null;
        Boolean catalogueImmutable = null;
        try {
            if (catalogueService != null && session.getDocument().getId() != null) {
                var row = catalogueService.findLatestByDocument(
                        session.getDocument().getTenantId(), session.getDocument().getId());
                if (row.isPresent()) {
                    var a = row.get();
                    catalogueStatus = a.getBusinessStatus();
                    applicabilityId = a.getId();
                    catalogueImmutable = a.getContentImmutable();
                    if (a.getPolicyVersionLabel() != null && !a.getPolicyVersionLabel().isBlank()
                            && life.get("policyVersion") == null) {
                        life.put("policyVersion", a.getPolicyVersionLabel());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("canonical lifecycle catalogue overlay skipped: {}", e.getClass().getSimpleName());
        }
        PolicyCanonicalLifecycleAuthority.Projection projection =
                PolicyCanonicalLifecycleAuthority.project(
                        sessionStatus, catalogueStatus, applicabilityId, catalogueImmutable);
        life.put("businessStatus", projection.businessStatus());
        life.put("lifecycleAuthority", projection.lifecycleAuthority());
        life.put("lifecycleOwnerType", projection.ownerType());
        if (projection.ownerId() != null) {
            life.put("durableApplicabilityId", projection.ownerId());
        }
        if (Boolean.TRUE.equals(catalogueImmutable) || !projection.contentEditable()) {
            if (PolicyCanonicalLifecycleAuthority.eligibleForCustomerCategoryLinkage(projection.businessStatus())
                    || !PolicyCanonicalLifecycleAuthority.contentEditable(projection.businessStatus())) {
                life.put("contentImmutable", !projection.contentEditable());
            }
        }
        CiPolicyDocument doc = session.getDocument();
        Map<String, Object> meta = doc.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(doc.getMetadata());
        meta.put(META_KEY, life);
        doc.setMetadata(meta);
    }

    private void refreshReadyFlag(PolicyStudioSession session, Map<String, Object> life) {
        List<String> blockers = scheduleReadinessBlockers(session, life);
        life.put("readyToScheduleBlockers", blockers);
        String st = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        boolean approvedish = PolicyBusinessLifecycleStatus.APPROVED.equals(st)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(st)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(st);
        life.put("readyToSchedule", blockers.isEmpty() && (approvedish || hasCheckerApproval(session)));
    }

    private Map<String, Object> defaultLifecycle(PolicyStudioSession session) {
        CiPolicyDocument doc = session.getDocument();
        Map<String, Object> life = new LinkedHashMap<>();
        life.put("policyVersion", "v" + (doc.getDocumentVersion() == null ? 1 : doc.getDocumentVersion()));
        life.put("policyType", inferPolicyType(session));
        life.put("businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        life.put("createdBy", doc.getUploadedBy() == null ? "staging" : doc.getUploadedBy());
        life.put("lineageId", doc.getId() == null ? UUID.randomUUID().toString() : doc.getId().toString());
        life.put("contentImmutable", false);
        life.put("reasonForChange", null);
        life.put("replacesVersion", null);
        Map<String, Object> app = new LinkedHashMap<>();
        List<String> products = new ArrayList<>();
        if (doc.getProductScope() != null && !doc.getProductScope().isBlank()) {
            products.add(doc.getProductScope().trim().toUpperCase(Locale.ROOT));
        } else {
            products.addAll(inferProducts(session));
        }
        app.put("products", products);
        app.put("facilityType", null);
        app.put("customerSegment", null);
        app.put("borrowerType", null);
        app.put("borrowerTypes", List.of()); // empty = ALL
        app.put("securedUnsecured", null);
        app.put("programScheme", null);
        app.put("minLoanAmount", null);
        app.put("maxLoanAmount", null);
        app.put("effectiveFrom", null);
        app.put("effectiveUntil", null);
        life.put("applicability", app);
        return life;
    }

    private List<String> inferProducts(PolicyStudioSession session) {
        String name = session.getDocument() == null ? "" : String.valueOf(session.getDocument().getName()).toUpperCase(Locale.ROOT);
        String text = session.getDocument() == null || session.getDocument().getSourceText() == null
                ? "" : session.getDocument().getSourceText().toUpperCase(Locale.ROOT);
        List<String> products = new ArrayList<>();
        // Explicit demo product names only — do not invent DigiLeap from "BANK" / empty defaults.
        if (name.contains("DIGILEAP") || text.contains("DIGILEAP")) {
            products.add("DIGILEAP");
        }
        if (name.contains("SMART SWITCH") || text.contains("SMART SWITCH") || text.contains("SMART_SWITCH")) {
            products.add("SMART_SWITCH");
        }
        if (name.contains("INVOICE")) {
            products.add("BUSINESS_WC_INVOICE_DISCOUNTING");
        } else if (name.contains("PERSONAL")) {
            products.add("PERSONAL_LOAN");
        } else if (name.contains("STARTER") || name.contains("TERM") || name.contains("BUSINESS")
                || name.contains("CLEAN") || name.contains("BANK")) {
            // Clean-client / term-loan propositions — not DigiLeap demo product codes
            products.add("BUSINESS_TERM_LOAN");
        }
        // Empty list = All products until Credit Manager sets scope (never default DIGILEAP)
        return products;
    }

    private String inferPolicyType(PolicyStudioSession session) {
        String name = session.getDocument() == null ? "" : String.valueOf(session.getDocument().getName()).toUpperCase(Locale.ROOT);
        if (name.contains("BUREAU")) {
            return "BUREAU_POLICY";
        }
        if (name.contains("BANK")) {
            return "BANKING_POLICY";
        }
        return "CREDIT_POLICY";
    }

    /**
     * Copy / new-version: keep the source policy's governed applicability dimensions.
     * Effective dates are not copied — the successor must set its own non-overlapping window.
     * Products are never re-inferred from name, source text, latest, or UI defaults.
     */
    public static Map<String, Object> governedApplicabilityForClone(Map<String, Object> srcLife) {
        Map<String, Object> app = new LinkedHashMap<>();
        Object raw = srcLife == null ? null : srcLife.get("applicability");
        if (raw instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() != null) {
                    app.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
        }
        app.remove("effectiveFrom");
        app.remove("effectiveUntil");
        return app;
    }

    private void mergeApplicability(Map<String, Object> life, Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        Map<String, Object> app = new LinkedHashMap<>(castMap(life.get("applicability")));
        Object nested = body.get("applicability");
        if (nested instanceof Map<?, ?> n) {
            app.putAll(castMap(n));
        }
        for (String k : List.of("products", "facilityType", "customerSegment", "borrowerType", "borrowerTypes",
                "securedUnsecured", "programScheme", "minLoanAmount", "maxLoanAmount",
                "effectiveFrom", "effectiveUntil", "productCode")) {
            if (body.containsKey(k)) {
                if ("productCode".equals(k) && body.get(k) != null) {
                    app.put("products", List.of(String.valueOf(body.get(k)).toUpperCase(Locale.ROOT)));
                } else {
                    app.put(k, body.get(k));
                }
            }
        }
        PolicyScopeSupport.normalizeAndValidate(app);
        if (body.get("replacesVersion") != null) {
            life.put("replacesVersion", String.valueOf(body.get("replacesVersion")));
        }
        if (body.get("policyVersion") != null) {
            life.put("policyVersion", String.valueOf(body.get("policyVersion")));
        }
        life.put("applicability", app);
    }

    /**
     * Pre-activation overlap preview using catalogue applicability.
     * Does not rank specificity — only surfaces potential EXACTLY_ONE conflicts.
     */
    private Map<String, Object> overlapPreview(PolicyStudioSession session, Map<String, Object> life) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("allowCanonicalAuthority", false);
        try {
            PolicyApplicabilityRecord candidate = toRecord(session, life,
                    str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT));
            UUID tenantId = session.getDocument() == null ? null : session.getDocument().getTenantId();
            Map<String, Object> overlap = resolver.detectOverlap(candidate, catalogueList(tenantId));
            out.put("potentialOverlap", Boolean.TRUE.equals(overlap.get("blocked")));
            out.put("message", Boolean.TRUE.equals(overlap.get("blocked"))
                    ? "Another scheduled/active policy may also match this scope."
                    : "No overlapping scheduled/active policy detected for this scope.");
            out.put("conflicts", overlap.getOrDefault("conflicts", List.of()));
            out.put("exactlyOneSafety", "EXACTLY_ONE preserved — overlaps are not silently resolved");
        } catch (Exception e) {
            out.put("potentialOverlap", false);
            out.put("message", "Overlap preview unavailable until effective dates are set.");
            out.put("conflicts", List.of());
        }
        return out;
    }

    private void persistLifecycle(PolicyStudioSession session, Map<String, Object> life) {
        CiPolicyDocument doc = session.getDocument();
        Map<String, Object> meta = doc.getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(doc.getMetadata());
        meta.put(META_KEY, life);
        doc.setMetadata(meta);
        Map<String, Object> app = castMap(life.get("applicability"));
        Object products = app.get("products");
        if (products instanceof List<?> list && !list.isEmpty()) {
            doc.setProductScope(String.valueOf(list.get(0)));
        }
        if (session.getAuthoringSession() != null) {
            Map<String, Object> am = session.getAuthoringSession().getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(session.getAuthoringSession().getMetadata());
            am.put(META_KEY, life);
            session.getAuthoringSession().setMetadata(am);
            session.getAuthoringSession().setStatus(toAuthoringStatus(str(life, "businessStatus", "DRAFT")));
        }
    }

    private String toAuthoringStatus(String business) {
        return switch (PolicyBusinessLifecycleStatus.fromStored(business)) {
            case PolicyBusinessLifecycleStatus.IN_REVIEW -> "IN_REVIEW";
            case PolicyBusinessLifecycleStatus.APPROVED -> "APPROVED";
            case PolicyBusinessLifecycleStatus.SCHEDULED -> "SCHEDULED";
            case PolicyBusinessLifecycleStatus.ACTIVE -> "ACTIVE";
            case PolicyBusinessLifecycleStatus.SUPERSEDED -> "SUPERSEDED";
            case PolicyBusinessLifecycleStatus.RETIRED -> "RETIRED";
            default -> "DRAFT";
        };
    }

    private Map<String, Object> businessHeader(PolicyStudioSession session, Map<String, Object> life) {
        Map<String, Object> app = castMap(life.get("applicability"));
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("policyName", session.getDocument().getName());
        h.put("policyVersion", life.get("policyVersion"));
        h.put("policyType", life.get("policyType"));
        h.put("products", app.get("products"));
        h.put("customerSegment", app.get("customerSegment"));
        h.put("facilityType", app.get("facilityType"));
        h.put("borrowerType", app.get("borrowerType"));
        h.put("borrowerTypes", app.get("borrowerTypes"));
        h.put("borrowerTypeSummary", BorrowerTypeScope.summaryLabel(
                BorrowerTypeScope.normalize(
                        app.get("borrowerTypes") instanceof List<?> list
                                ? list.stream().map(String::valueOf).toList() : List.of(),
                        app.get("borrowerType") == null ? null : String.valueOf(app.get("borrowerType")))));
        h.put("securedUnsecured", app.get("securedUnsecured"));
        h.put("programScheme", app.get("programScheme"));
        h.put("minLoanAmount", app.get("minLoanAmount"));
        h.put("maxLoanAmount", app.get("maxLoanAmount"));
        h.put("effectiveFrom", app.get("effectiveFrom"));
        h.put("effectiveUntil", app.get("effectiveUntil"));
        h.put("createdBy", life.get("createdBy"));
        h.put("approvedBy", life.get("approvedBy"));
        h.put("checker", life.get("checker"));
        h.put("status", life.get("businessStatus"));
        h.put("replaces", life.get("replacesVersion"));
        h.put("reasonForChange", life.get("reasonForChange"));
        if (session.getDocument() != null && session.getDocument().getId() != null) {
            h.put("documentId", session.getDocument().getId().toString());
        }
        return h;
    }

    private Map<String, Object> implementationStatus(PolicyStudioSession session, Map<String, Object> life) {
        Map<String, Object> impl = implementabilityService.assess(session);
        Map<String, Object> summary = impl.get("summary") instanceof Map<?, ?> m ? castMap(m) : Map.of();
        boolean understanding = policyUnderstandingReady(session);
        // POLICY-READINESS-CONVERGENCE-1 — execution readiness from shared helper (not implementability %)
        Map<String, Object> execStats = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.executionReadinessStats(session);
        List<Map<String, Object>> execBlockers = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.sessionExecutionBlockers(session);
        boolean dataReady = execBlockers.isEmpty();
        boolean tests = testsReady(session);
        // POLICY-LIFECYCLE-FIX-1 — Policy Test stamps both; CM sees one "Test" concept
        boolean simulation = simulationReady(session) || tests;
        boolean cm = hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name());
        boolean checker = hasCheckerApproval(session);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("understanding", understanding);
        out.put("dataReadiness", dataReady);
        out.put("executionReadiness", dataReady);
        out.put("tests", tests);
        out.put("simulation", simulation);
        out.put("policyTest", tests && simulation);
        out.put("creditManager", cm);
        out.put("checker", checker);
        out.put("allGatesPassed", understanding && dataReady && tests && simulation && cm && checker);
        out.put("dataReadinessPercent", execStats.get("dataReadinessPercent"));
        out.put("executionBlockers", execBlockers);
        out.put("executionBlockerCount", execBlockers.size());
        out.put("criticalDataGap", !execBlockers.isEmpty());
        out.put("legacyImplementabilityPercent", summary.get("implementationReadinessPercent"));
        out.put("legacyCriticalDataGap", summary.get("draftBlockedByCriticalDataGap"));
        return out;
    }

    private boolean policyUnderstandingReady(PolicyStudioSession session) {
        if (session.getInterpretations() != null && !session.getInterpretations().isEmpty()) {
            return true;
        }
        // CM-authored / catalogue rules are the understanding for scratch/manual policies
        return session.getRuleCandidates().stream().anyMatch(r -> {
            Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
            return Boolean.TRUE.equals(m.get("cmAuthored"))
                    || Boolean.TRUE.equals(m.get("catalogueBacked"));
        });
    }

    private boolean testsReady(PolicyStudioSession session) {
        long approvedTests = session.getTestCases().stream()
                .filter(t -> ReviewState.CREDIT_MANAGER_APPROVED.name().equals(t.getReviewStatus())
                        || ReviewState.CHECKER_APPROVED.name().equals(t.getReviewStatus())
                        || "APPROVED".equalsIgnoreCase(t.getReviewStatus()))
                .count();
        return !session.getTestCases().isEmpty() && approvedTests > 0;
    }

    private boolean simulationReady(PolicyStudioSession session) {
        return session.getSimulation() != null
                && (Boolean.TRUE.equals(session.getSimulation().get("simulationReviewed"))
                || session.getSimulation().containsKey("runId"));
    }

    private List<String> availableActions(PolicyStudioSession session, Map<String, Object> life) {
        String st = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        List<String> actions = new ArrayList<>();
        if (!isImmutable(st)) {
            actions.add("SAVE_DRAFT");
            actions.add("SUBMIT_FOR_REVIEW");
        }
        if (hasCheckerApproval(session)
                && (PolicyBusinessLifecycleStatus.DRAFT.equals(st)
                || PolicyBusinessLifecycleStatus.IN_REVIEW.equals(st)
                || PolicyBusinessLifecycleStatus.APPROVED.equals(st))) {
            if (!PolicyBusinessLifecycleStatus.APPROVED.equals(st)
                    && !PolicyBusinessLifecycleStatus.SCHEDULED.equals(st)
                    && !PolicyBusinessLifecycleStatus.ACTIVE.equals(st)) {
                actions.add("APPROVE_POLICY");
            }
        }
        actions.add("SEND_FOR_CHECKER"); // existing maker-checker path
        if (PolicyBusinessLifecycleStatus.APPROVED.equals(st)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(st)) {
            actions.add("SCHEDULE_POLICY");
        }
        if (PolicyBusinessLifecycleStatus.ACTIVE.equals(st)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(st)
                || PolicyBusinessLifecycleStatus.APPROVED.equals(st)) {
            actions.add("RETIRE_POLICY");
            actions.add("CREATE_NEW_VERSION");
        }
        if ((PolicyBusinessLifecycleStatus.DRAFT.equals(st)
                || PolicyBusinessLifecycleStatus.IN_REVIEW.equals(st))
                && deleteDenialReason(session, life, st) == null) {
            actions.add("DELETE_DRAFT");
        }
        // Never expose activate canonical authority
        return actions;
    }

    private List<String> submitReadinessBlockers(PolicyStudioSession session, Map<String, Object> life) {
        List<String> blockers = new ArrayList<>();
        Map<String, Object> app = castMap(life.get("applicability"));
        Object products = app.get("products");
        if (!(products instanceof List<?> list) || list.isEmpty()) {
            blockers.add("Scope missing product");
        }
        long uw = underwritingRuleCount(session);
        if (uw == 0) {
            blockers.add("Add at least one underwriting rule");
        }
        // Structured execution blockers (same keys as Rules)
        for (Map<String, Object> b : com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.sessionExecutionBlockers(session)) {
            blockers.add(formatExecutionBlockerMessage(b));
        }
        return blockers;
    }

    private List<String> scheduleReadinessBlockers(PolicyStudioSession session, Map<String, Object> life) {
        List<String> blockers = new ArrayList<>();
        if (!policyUnderstandingReady(session)) {
            blockers.add("Policy content not confirmed — review Rules");
        }
        if (underwritingRuleCount(session) == 0) {
            blockers.add("No underwriting rules confirmed");
        }
        // POLICY-READINESS-CONVERGENCE-1 — execution blockers from canonical helper only
        // (do not independently gate on legacy materialAmbiguities / implementability matrix)
        for (Map<String, Object> b : com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.sessionExecutionBlockers(session)) {
            blockers.add(formatExecutionBlockerMessage(b));
        }
        for (Map<String, Object> b : com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.currentParameterBlockers(session)) {
            String name = String.valueOf(b.getOrDefault("businessName", b.get("canonicalParameterId")));
            String reason = String.valueOf(b.getOrDefault("reason", "Parameter not ready"));
            String msg = name + " — " + reason;
            if (!blockers.contains(msg)) {
                blockers.add(msg);
            }
        }
        // GOVERNANCE blockers — separate from execution readiness
        if (!testsReady(session) && !simulationReady(session)) {
            blockers.add("Test not completed — run Policy Test");
        } else if (!testsReady(session)) {
            blockers.add("Test not completed — run Policy Test");
        } else if (!simulationReady(session)) {
            // Should be rare once Policy Test stamps both; keep as soft alias
            blockers.add("Test not completed — run Policy Test");
        }
        if (!hasDocumentApproval(session, ReviewState.CREDIT_MANAGER_APPROVED.name())) {
            blockers.add("Credit Manager approval required");
        }
        if (!hasCheckerApproval(session)) {
            blockers.add("Checker approval required");
        }
        Map<String, Object> app = castMap(life.get("applicability"));
        Object products = app.get("products");
        if (!(products instanceof List<?> list) || list.isEmpty()) {
            blockers.add("Scope missing product");
        }
        return blockers;
    }

    private static String formatExecutionBlockerMessage(Map<String, Object> b) {
        String rule = String.valueOf(b.getOrDefault("ruleName", "Rule"));
        String reason = String.valueOf(b.getOrDefault("reason", "Unresolved requirement"));
        return rule + " — " + reason;
    }

    private long underwritingRuleCount(PolicyStudioSession session) {
        return session.getRuleCandidates().stream()
                .filter(r -> !com.los.core.creditintelligence.policystudio.parameters.PolicyStudioConvergencePresenter
                        .isCompoundChild(r.getSystemRuleId()))
                .filter(r -> {
                    Map<String, Object> m = r.getMetadata() == null ? Map.of() : r.getMetadata();
                    return !Boolean.TRUE.equals(m.get("classificationOnly"))
                            && !Boolean.TRUE.equals(m.get("dataRequirementOnly"))
                            && !Boolean.TRUE.equals(m.get("metricAdjustment"))
                            && !Boolean.TRUE.equals(m.get("deleted"));
                })
                .count();
    }

    private long rulesNeedingInput(PolicyStudioSession session) {
        Map<String, Object> eval = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.evaluate(session);
        long included = ((Number) eval.getOrDefault("includedExecutableRules", 0L)).longValue();
        long ready = ((Number) eval.getOrDefault("executionReadyRules", 0L)).longValue();
        return Math.max(0, included - ready);
    }

    private boolean underwritingRulesAuthoringComplete(PolicyStudioSession session) {
        return Boolean.TRUE.equals(com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.evaluate(session).get("executionReady"));
    }

    private long unresolvedAuthoringParameters(PolicyStudioSession session) {
        Map<String, Object> eval = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.evaluate(session);
        if (!Boolean.TRUE.equals(eval.get("requiredParametersResolved"))
                || !Boolean.TRUE.equals(eval.get("boundaryAmbiguitiesResolved"))) {
            return 1L;
        }
        return 0L;
    }

    /**
     * CM-facing progress / primary action / readiness (presentation over existing lifecycle).
     */
    private Map<String, Object> creditManagerLifecycleView(
            PolicyStudioSession session,
            Map<String, Object> life,
            Map<String, Object> impl,
            List<String> submitBlockers,
            List<String> approveBlockers,
            List<String> actions) {
        String status = str(life, "businessStatus", PolicyBusinessLifecycleStatus.DRAFT);
        boolean cm = Boolean.TRUE.equals(impl.get("creditManager"));
        boolean checker = Boolean.TRUE.equals(impl.get("checker"));
        boolean policyTest = Boolean.TRUE.equals(impl.get("policyTest"))
                || (Boolean.TRUE.equals(impl.get("tests")) && Boolean.TRUE.equals(impl.get("simulation")));
        // POLICY-READINESS-SINGLE-SOURCE-OF-TRUTH-1 — checklist + submit share evaluate()
        Map<String, Object> execEval = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.evaluate(session);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> executionBlockers = execEval.get("executionBlockers") instanceof List<?> l
                ? (List<Map<String, Object>>) l : List.of();
        long uw = ((Number) execEval.getOrDefault("includedExecutableRules", 0L)).longValue();
        long readyRules = ((Number) execEval.getOrDefault("executionReadyRules", 0L)).longValue();
        long needs = Math.max(0, uw - readyRules);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> currentParameterBlockers = execEval.get("currentParameterBlockers") instanceof List<?> cpl
                ? (List<Map<String, Object>>) cpl : List.of();
        String currentExecution = String.valueOf(execEval.getOrDefault("currentExecutionReadiness",
                currentParameterBlockers.isEmpty() && executionBlockers.isEmpty() ? "READY" : "BLOCKED"));
        boolean paramsResolved = Boolean.TRUE.equals(execEval.get("requiredParametersResolved"))
                && Boolean.TRUE.equals(execEval.get("boundaryAmbiguitiesResolved"));
        boolean scopeOk = castMap(life.get("applicability")).get("products") instanceof List<?> p && !p.isEmpty();
        List<Map<String, Object>> governanceBlockers = com.los.core.creditintelligence.policystudio.parameters
                .PolicyExecutionReadiness.sessionGovernanceBlockers(scopeOk, policyTest, cm, checker);

        List<Map<String, Object>> readinessItems = new ArrayList<>();
        readinessItems.add(readyItem("Scope complete",
                scopeOk,
                "scope", "Scope missing product"));
        readinessItems.add(readyItem(
                readyRules + " underwriting rule" + (readyRules == 1 ? "" : "s") + " execution-ready",
                uw > 0 && "READY".equals(currentExecution),
                "rules",
                !currentParameterBlockers.isEmpty()
                        ? currentParameterBlockers.size() + " required parameter"
                        + (currentParameterBlockers.size() == 1 ? "" : "s") + " need setup"
                        : (needs > 0
                        ? needs + " rule(s) need confirmation"
                        : (!executionBlockers.isEmpty()
                        ? "Resolve execution blockers before continuing"
                        : "Add underwriting rules"))));
        readinessItems.add(readyItem("Required parameters resolved",
                paramsResolved,
                "rules",
                currentParameterBlockers.isEmpty()
                        ? "Resolve required parameters / boundary ambiguities"
                        : currentParameterBlockers.size() + " parameter"
                        + (currentParameterBlockers.size() == 1 ? "" : "s") + " require setup"));
        readinessItems.add(readyItem("Last test completed", policyTest, "tests", "Run Policy Test"));

        List<Map<String, Object>> blockerDetails = new ArrayList<>();
        for (Map<String, Object> b : executionBlockers) {
            Map<String, Object> row = new LinkedHashMap<>(b);
            row.put("message", formatExecutionBlockerMessage(b));
            row.put("tab", "rules");
            row.put("category", "EXECUTION");
            blockerDetails.add(row);
        }
        for (Map<String, Object> b : currentParameterBlockers) {
            String msg = String.valueOf(b.getOrDefault("businessName", b.get("canonicalParameterId")))
                    + " — " + b.getOrDefault("reason", "Parameter not ready");
            boolean already = blockerDetails.stream()
                    .anyMatch(row -> msg.equals(String.valueOf(row.get("message"))));
            if (already) continue;
            Map<String, Object> row = new LinkedHashMap<>(b);
            row.put("message", msg);
            row.put("tab", "rules");
            row.put("category", "EXECUTION");
            blockerDetails.add(row);
        }
        String stUpper = status == null ? "DRAFT" : status.toUpperCase(Locale.ROOT).replace('_', ' ');
        List<String> relevant = stUpper.contains("REVIEW") || stUpper.equals("IN REVIEW")
                ? approveBlockers : (stUpper.equals("DRAFT") ? submitBlockers : approveBlockers);
        for (String b : relevant) {
            boolean already = blockerDetails.stream()
                    .anyMatch(row -> b.equals(String.valueOf(row.get("message"))));
            if (already) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("message", b);
            row.put("tab", tabForBlocker(b));
            row.put("category", isGovernanceMessage(b) ? "GOVERNANCE" : "EXECUTION");
            blockerDetails.add(row);
        }

        Map<String, Object> primary = primaryAction(status, actions, cm, checker, submitBlockers, approveBlockers);
        // Ready-for-next-step cannot be true while any execution blocker remains (invariant).
        boolean checklistOk = readinessItems.stream().allMatch(i -> Boolean.TRUE.equals(i.get("ok")));
        boolean readyForNext = checklistOk && executionBlockers.isEmpty() && currentParameterBlockers.isEmpty()
                && Boolean.TRUE.equals(primary.get("enabled"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("progressSteps", List.of("DRAFT", "IN REVIEW", "APPROVED", "SCHEDULED", "ACTIVE"));
        out.put("progressCurrent", normalizeProgressStatus(status));
        out.put("readinessItems", readinessItems);
        out.put("readyForNextStep", readyForNext);
        out.put("blockerDetails", blockerDetails);
        out.put("executionBlockers", executionBlockers);
        out.put("currentParameterBlockers", currentParameterBlockers);
        out.put("currentExecutionReadiness", currentExecution);
        out.put("lifecycleStatus", status);
        out.put("lifecycleAxis", "POLICY_VERSION_LIFECYCLE");
        out.put("executionReadinessAxis", "POLICY_EXECUTION_READINESS");
        out.put("historicalLifecycleUnchanged", true);
        out.put("governanceBlockers", governanceBlockers);
        out.put("executionReadinessOk", executionBlockers.isEmpty());
        out.put("governanceReadinessOk", governanceBlockers.isEmpty());
        out.put("authoritativeReadiness", execEval);
        out.put("readySubmitContradictionImpossible", true);
        out.put("approvals", Map.of(
                "creditManager", Map.of(
                        "label", "Credit Manager",
                        "status", cm ? "Approved" : "Pending",
                        "approved", cm),
                "checker", Map.of(
                        "label", "Checker",
                        "status", checker ? "Approved" : "Pending",
                        "approved", checker)));
        out.put("nextActorMessage", nextActorMessage(status, cm, checker, submitBlockers, approveBlockers));
        out.put("primaryAction", primary);
        out.put("secondaryActions", secondaryActions(status, actions));
        out.put("shadowBoundary", Map.of(
                "businessActiveReachable", true,
                "productionAuthority", "DISABLED",
                "allowCanonicalAuthority", false,
                "message", "Scheduled/Active here is business lifecycle only — production underwriting stays disabled."));
        return out;
    }

    private static Map<String, Object> readyItem(String label, boolean ok, String tab, String whenMissing) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("ok", ok);
        m.put("tab", tab);
        m.put("whenMissing", whenMissing);
        return m;
    }

    private static boolean isGovernanceMessage(String message) {
        if (message == null) return false;
        String l = message.toLowerCase(Locale.ROOT);
        return l.contains("test not completed")
                || l.contains("credit manager approval")
                || l.contains("checker approval")
                || l.contains("scope missing")
                || l.contains("effective date");
    }

    private static String tabForBlocker(String b) {
        String l = b == null ? "" : b.toLowerCase(Locale.ROOT);
        if (l.contains("scope") || l.contains("product")) return "scope";
        if (l.contains("test") || l.contains("simulation")) return "tests";
        if (l.contains("credit manager") || l.contains("checker") || l.contains("approval")) {
            return "approvals";
        }
        return "rules";
    }

    private static String normalizeProgressStatus(String status) {
        if (status == null) return "DRAFT";
        String u = status.toUpperCase(Locale.ROOT).replace('_', ' ').trim();
        if (u.equals("IN REVIEW") || u.equals("IN_REVIEW")) return "IN REVIEW";
        return u;
    }

    private Map<String, Object> primaryAction(
            String status,
            List<String> actions,
            boolean cm,
            boolean checker,
            List<String> submitBlockers,
            List<String> approveBlockers) {
        String st = normalizeProgressStatus(status);
        Map<String, Object> a = new LinkedHashMap<>();
        switch (st) {
            case "DRAFT" -> {
                a.put("code", "SUBMIT_FOR_REVIEW");
                a.put("label", "Submit for Review");
                a.put("enabled", submitBlockers.isEmpty() && actions.contains("SUBMIT_FOR_REVIEW"));
                a.put("disabledReason", submitBlockers.isEmpty() ? null
                        : "Cannot submit for review. Resolve: " + String.join("; ", submitBlockers));
            }
            case "IN REVIEW" -> {
                if (!cm) {
                    a.put("code", "GO_APPROVALS_CM");
                    a.put("label", "Complete Credit Manager approval");
                    a.put("enabled", true);
                    a.put("tab", "approvals");
                    a.put("disabledReason", null);
                } else if (!checker) {
                    a.put("code", "GO_APPROVALS_CHECKER");
                    a.put("label", "Go to Approvals (Checker)");
                    a.put("enabled", true);
                    a.put("tab", "approvals");
                    a.put("disabledReason", null);
                    a.put("hint", "Awaiting Checker approval — Approve Policy unlocks after Checker acts.");
                } else {
                    boolean nonApprovalLeft = approveBlockers.stream().anyMatch(b -> {
                        String l = b.toLowerCase(Locale.ROOT);
                        return !l.contains("credit manager") && !l.contains("checker");
                    });
                    a.put("code", "APPROVE_POLICY");
                    a.put("label", "Approve Policy");
                    a.put("enabled", actions.contains("APPROVE_POLICY") && !nonApprovalLeft);
                    a.put("disabledReason", nonApprovalLeft
                            ? "Cannot approve yet. Resolve: " + String.join("; ", approveBlockers) : null);
                    a.put("confirm", true);
                }
            }
            case "APPROVED" -> {
                a.put("code", "SCHEDULE_POLICY");
                a.put("label", "Schedule Policy");
                a.put("enabled", actions.contains("SCHEDULE_POLICY"));
                a.put("disabledReason", actions.contains("SCHEDULE_POLICY") ? null
                        : "Scheduling is only available for APPROVED policies.");
            }
            case "SCHEDULED" -> {
                a.put("code", "SCHEDULE_POLICY");
                a.put("label", "Update schedule / activate when effective");
                a.put("enabled", actions.contains("SCHEDULE_POLICY"));
                a.put("hint", "Set business date ≥ effective from to reach Active (business status only).");
            }
            case "ACTIVE" -> {
                a.put("code", "CREATE_NEW_VERSION");
                a.put("label", "Create New Version");
                a.put("enabled", actions.contains("CREATE_NEW_VERSION"));
            }
            case "RETIRED", "SUPERSEDED" -> {
                a.put("code", "NONE");
                a.put("label", "No further action");
                a.put("enabled", false);
                a.put("disabledReason", "This policy version is read-only.");
            }
            default -> {
                a.put("code", "SUBMIT_FOR_REVIEW");
                a.put("label", "Submit for Review");
                a.put("enabled", false);
            }
        }
        return a;
    }

    private List<Map<String, Object>> secondaryActions(String status, List<String> actions) {
        List<Map<String, Object>> out = new ArrayList<>();
        String st = normalizeProgressStatus(status);
        if (actions.contains("CREATE_NEW_VERSION")
                && List.of("APPROVED", "SCHEDULED").contains(st)) {
            // ACTIVE: Create New Version is already the primary action — do not duplicate.
            out.add(Map.of("code", "CREATE_NEW_VERSION", "label", "Create New Version", "more", false));
        }
        if (actions.contains("RETIRE_POLICY")) {
            out.add(Map.of("code", "RETIRE_POLICY", "label", "Retire Policy", "more", true));
        }
        return out;
    }

    private String nextActorMessage(
            String status, boolean cm, boolean checker,
            List<String> submitBlockers, List<String> approveBlockers) {
        String st = normalizeProgressStatus(status);
        if ("DRAFT".equals(st)) {
            return submitBlockers.isEmpty()
                    ? "Next step: Submit for Review"
                    : "Not ready for review — resolve items below";
        }
        if ("IN REVIEW".equals(st)) {
            if (!cm) return "Next step: Credit Manager approval (Approvals)";
            if (!checker) return "Next step: Awaiting Checker approval";
            return approveBlockers.stream().anyMatch(b -> {
                String l = b.toLowerCase(Locale.ROOT);
                return !l.contains("credit manager") && !l.contains("checker");
            }) ? "Next step: Resolve remaining items, then Approve Policy"
                    : "Next step: Approve Policy";
        }
        if ("APPROVED".equals(st)) return "Next step: Schedule Policy";
        if ("SCHEDULED".equals(st)) return "Scheduled — becomes Active on the effective date (business status)";
        if ("ACTIVE".equals(st)) return "Next step: Create New Version when changing this policy";
        if ("RETIRED".equals(st)) return "Retired — read-only";
        return "Review policy status";
    }


    private PolicyApplicabilityRecord toRecord(PolicyStudioSession session, Map<String, Object> life, String status) {
        Map<String, Object> app = castMap(life.get("applicability"));
        Map<String, Object> flat = new LinkedHashMap<>(app);
        flat.put("policyVersionId", session.documentId().toString());
        flat.put("policyName", session.getDocument().getName());
        flat.put("policyVersion", life.get("policyVersion"));
        flat.put("policyType", life.get("policyType"));
        flat.put("businessStatus", status);
        flat.put("replacesVersion", life.get("replacesVersion"));
        flat.put("reasonForChange", life.get("reasonForChange"));
        flat.put("approvedBy", life.get("approvedBy"));
        flat.put("checker", life.get("checker"));
        flat.put("createdBy", life.get("createdBy"));
        flat.put("productionAuthorityEnabled", false);
        return PolicyApplicabilityRecord.fromMap(flat);
    }

    private void supersedePrior(UUID tenantId, UUID priorDocumentId, String replacedBy) {
        ConcurrentHashMap<UUID, PolicyApplicabilityRecord> map = catalogue.get(tenantId);
        if (map == null) {
            return;
        }
        PolicyApplicabilityRecord prior = map.get(priorDocumentId);
        if (prior == null) {
            return;
        }
        PolicyApplicabilityRecord superseded = new PolicyApplicabilityRecord(
                prior.policyVersionId(), prior.policyName(), prior.policyVersion(), prior.policyType(),
                PolicyBusinessLifecycleStatus.SUPERSEDED, prior.products(), prior.facilityType(),
                prior.customerSegment(), prior.borrowerType(), prior.borrowerTypes(), prior.securedUnsecured(),
                prior.programScheme(), prior.minLoanAmount(), prior.maxLoanAmount(),
                prior.effectiveFrom(), prior.effectiveUntil() != null ? prior.effectiveUntil()
                        : LocalDate.now().minusDays(1),
                prior.replacesVersion(), "Superseded by " + replacedBy,
                prior.approvedBy(), prior.checker(), prior.createdBy(), false, prior.metadata());
        map.put(priorDocumentId, superseded);
    }

    private void appendHistory(PolicyStudioSession session, Map<String, Object> life, String event) {
        String lineage = str(life, "lineageId", session.documentId().toString());
        UUID key = UUID.fromString(lineage.contains("-") && lineage.length() == 36
                ? lineage : session.documentId().toString());
        try {
            key = UUID.fromString(lineage);
        } catch (Exception ignored) {
            key = session.documentId();
        }
        Map<String, Object> row = new LinkedHashMap<>(businessHeader(session, life));
        row.put("event", event);
        row.put("at", Instant.now().toString());
        row.put("applicationsEvaluated", 0);
        historyByLineage.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
    }

    private List<Map<String, Object>> historyFor(PolicyStudioSession session) {
        Map<String, Object> life = ensureLifecycle(session);
        String lineage = str(life, "lineageId", session.documentId().toString());
        UUID key;
        try {
            key = UUID.fromString(lineage);
        } catch (Exception e) {
            key = session.documentId();
        }
        List<Map<String, Object>> list = historyByLineage.getOrDefault(key, List.of());
        List<Map<String, Object>> copy = new ArrayList<>(list);
        // Always include current
        Map<String, Object> current = new LinkedHashMap<>(businessHeader(session, life));
        current.put("event", "CURRENT");
        current.put("applicationsEvaluated", 0);
        copy.add(current);
        return copy;
    }

    private boolean isImmutable(String status) {
        String s = PolicyBusinessLifecycleStatus.fromStored(status);
        return PolicyBusinessLifecycleStatus.APPROVED.equals(s)
                || PolicyBusinessLifecycleStatus.SCHEDULED.equals(s)
                || PolicyBusinessLifecycleStatus.ACTIVE.equals(s)
                || PolicyBusinessLifecycleStatus.SUPERSEDED.equals(s)
                || PolicyBusinessLifecycleStatus.RETIRED.equals(s);
    }

    private void assertNotMutatingApprovedContent(PolicyStudioSession session) {
        String fp = approvedContentFingerprints.get(session.documentId());
        if (fp == null) {
            return;
        }
        if (!fp.equals(contentFingerprint(session))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Approved policy content was modified in place. Create New Version instead.");
        }
    }

    private String contentFingerprint(PolicyStudioSession session) {
        int rules = session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().size();
        int params = session.getParameters() == null ? 0 : session.getParameters().size();
        String textHash = session.getDocument() == null ? "" : String.valueOf(session.getDocument().getContentHash());
        return textHash + "|rules=" + rules + "|params=" + params;
    }

    /**
     * Ambiguities tied only to Ignored/Deleted rules are not activation blockers.
     * Unresolved ambiguities on included executable rules remain blockers.
     */
    private static boolean ambiguityOnlyAffectsExcludedRules(PolicyStudioSession session, CiPolicyAmbiguity a) {
        if (a == null || a.getClauseId() == null) {
            return false;
        }
        List<CiPolicyRuleCandidate> related = session.getRuleCandidates().stream()
                .filter(r -> a.getClauseId().equals(r.getClauseId()))
                .toList();
        if (related.isEmpty()) {
            return false;
        }
        return related.stream().allMatch(PolicyImplementabilityService::isExcludedFromActivation);
    }

    private boolean hasCheckerApproval(PolicyStudioSession session) {
        return hasDocumentApproval(session, ReviewState.CHECKER_APPROVED.name())
                && !(session.getDraftPackage() != null
                && Boolean.TRUE.equals(session.getDraftPackage().getInvalidatedByEdit()));
    }

    private boolean hasDocumentApproval(PolicyStudioSession session, String state) {
        return session.getReviews().stream().anyMatch(r ->
                "DOCUMENT".equalsIgnoreCase(r.getSubjectType())
                        && state.equals(r.getReviewState()));
    }

    private int parseVersionNumber(String v) {
        if (v == null) {
            return 1;
        }
        String digits = v.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(digits);
        } catch (Exception e) {
            return 1;
        }
    }

    private void stampSafety(Map<String, Object> out) {
        out.put("allowCanonicalAuthority", false);
        out.put("productionActive", false);
        out.put("authoritative", false);
        out.put("productionAuthority", "DISABLED");
        boolean canon = properties.getCutover() != null && properties.getCutover().isAllowCanonicalAuthority();
        if (canon) {
            // Defensive — staging must never run with this true
            out.put("configurationError", "allowCanonicalAuthority unexpectedly true");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> m, String k, String def) {
        if (m == null || m.get(k) == null) {
            return def;
        }
        String s = String.valueOf(m.get(k));
        return s.isBlank() ? def : s;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
