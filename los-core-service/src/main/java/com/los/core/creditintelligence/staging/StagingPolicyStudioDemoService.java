package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.catalogue.CatalogueCapabilityDraftService;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.BusinessMeasureDesignerService;
import com.los.core.creditintelligence.policystudio.service.DeterministicGoldenInterpretationProvider;
import com.los.core.creditintelligence.policystudio.service.PolicyClauseExtractor;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.service.PolicyTextExtractionService;
import com.los.core.creditintelligence.policystudio.service.RuleCandidateFactory;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Policy Studio prospect views — Day 1 upload/structure + Day 2 ambiguity/rule review.
 */
@Slf4j
@Service
public class StagingPolicyStudioDemoService {

    public static final String DEMO_BANNER = "DEMO POLICY — CUSTOMER-SUPPLIED SAMPLE";

    private final CreditIntelligenceProperties properties;
    private final PolicyStudioOrchestrator orchestrator;
    private final PolicyTextExtractionService textExtractionService;
    private final BusinessMeasureDesignerService measureDesigner;
    private final PolicyLifecycleService lifecycleService;
    private final CatalogueCapabilityDraftService catalogueDraftService;

    /** documentId → meta used to rebuild prospect view after resolve/review */
    private final ConcurrentHashMap<UUID, Map<String, Object>> sessionMeta = new ConcurrentHashMap<>();

    @Autowired
    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService,
            BusinessMeasureDesignerService measureDesigner,
            PolicyLifecycleService lifecycleService,
            CatalogueCapabilityDraftService catalogueDraftService) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.textExtractionService = textExtractionService;
        this.measureDesigner = measureDesigner != null ? measureDesigner : new BusinessMeasureDesignerService();
        this.lifecycleService = lifecycleService;
        this.catalogueDraftService = catalogueDraftService != null
                ? catalogueDraftService
                : new CatalogueCapabilityDraftService(new CreditCapabilityCatalogueService(properties));
    }

    /** Test / legacy convenience — Spring uses the @Autowired constructor. */
    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService) {
        this(properties, orchestrator, textExtractionService, new BusinessMeasureDesignerService(), null, null);
    }

    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService,
            BusinessMeasureDesignerService measureDesigner) {
        this(properties, orchestrator, textExtractionService, measureDesigner, null, null);
    }

    public Map<String, Object> landing() {
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        // POLICY-CREATION-1 — Credit Manager workspace, not upload/AI demo first
        out.put("title", "Credit Policies");
        out.put("subtitle",
                "Create and manage underwriting policies for your lending products and customer segments.");
        out.put("supportedFormats", List.of("PDF", "DOCX", "TXT"));
        out.put("maxUploadBytes", PolicyTextExtractionService.MAX_BYTES);
        out.put("primaryAction", "CREATE_POLICY");
        out.put("createPaths", List.of("START_FROM_SCRATCH", "UPLOAD_EXISTING", "COPY_EXISTING"));
        out.put("journey", List.of(
                "Create Policy", "Scope", "Rules", "Resolve parameters", "Test", "Versions", "Approved", "Scorecard"));
        out.put("allowCanonicalAuthority", false);
        out.put("existingPolicies", listExistingPolicies());
        out.put("demoPolicies", List.of(
                Map.of(
                        "kind", "kyc",
                        "name", "KYC & Eligibility (validation sample)",
                        "label", "DEMO POLICY / VALIDATION SAMPLE",
                        "description", "Sample KYC & Eligibility clauses for Decision Policy authoring demonstration."),
                Map.of(
                        "kind", "banking",
                        "name", "Banking BRE",
                        "label", DEMO_BANNER,
                        "description", "Sample banking policy used for product demonstration."),
                Map.of(
                        "kind", "bureau",
                        "name", "Bureau BRE",
                        "label", DEMO_BANNER,
                        "description", "Sample bureau policy used for product demonstration.")));
        out.put("examplesNote", "Examples & templates are for staging/development only.");
        return out;
    }

    /**
     * POLICY-CREATION-1 — Start from scratch: empty draft session (no document upload required).
     * Reuses processUpload with minimal stub text (document service requires non-blank source).
     */
    public Map<String, Object> createFromScratch(Map<String, Object> body, String uploadedBy, String tenantHeader) {
        String name = body == null || body.get("policyName") == null
                ? "" : String.valueOf(body.get("policyName")).trim();
        if (name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Policy name is required");
        }
        String description = body == null || body.get("description") == null
                ? "" : String.valueOf(body.get("description")).trim();
        UUID tenantId = resolveTenant(tenantHeader);
        String text = "# " + name + "\n\n"
                + (description.isBlank() ? "" : description + "\n\n")
                + "Draft policy created from scratch in Policy Studio.\n"
                + "Define Scope, then add underwriting rules. Save Draft anytime.\n";
        PolicyStudioSession session = orchestrator.processUpload(
                tenantId,
                name,
                "TXT",
                text,
                uploadedBy == null || uploadedBy.isBlank() ? "credit_manager" : uploadedBy,
                name.replaceAll("[^a-zA-Z0-9._-]+", "_") + ".txt");
        if (lifecycleService != null) {
            requireLifecycle().saveDraft(session, Map.of(
                    "reasonForChange", "Created from scratch",
                    "policyName", name));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "scratch");
        meta.put("demo", false);
        meta.put("canResetDemo", false);
        meta.put("createdFromScratch", true);
        meta.put("fileName", name + ".txt");
        meta.put("description", description);
        if (session.getDocument() != null && session.getDocument().getId() != null) {
            sessionMeta.put(session.getDocument().getId(), meta);
        }
        Map<String, Object> view = toProspectView(session, meta);
        view.put("message", "Draft created. Continue with Scope, then Rules.");
        view.put("enterWorkspace", true);
        view.put("defaultTab", "scope");
        return view;
    }

    /**
     * POLICY-CREATION-1 — Copy existing policy into a NEW draft without mutating the source.
     * Reuses clone-via-processUpload pattern (same as lifecycle new-version) but allows any source status.
     */
    public Map<String, Object> copyPolicy(UUID sourceDocumentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession source = requireSession(sourceDocumentId, tenantHeader);
        CiPolicyDocument doc = source.getDocument();
        if (doc == null || doc.getSourceText() == null || doc.getSourceText().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Source policy text is unavailable to copy.");
        }
        String sourceName = doc.getName() == null ? "Policy" : doc.getName();
        String newName = body != null && body.get("policyName") != null
                && !String.valueOf(body.get("policyName")).isBlank()
                ? String.valueOf(body.get("policyName")).trim()
                : sourceName + " (Copy)";
        Map<String, Object> srcLife = lifecycleService == null
                ? Map.of() : requireLifecycle().settingsView(source);
        String sourceVersion = String.valueOf(srcLife.getOrDefault("policyVersion",
                "v" + (doc.getDocumentVersion() == null ? 1 : doc.getDocumentVersion())));
        String cloneText = doc.getSourceText() + "\n\n<!-- COPIED_FROM documentId="
                + sourceDocumentId + " name=" + sourceName + " version=" + sourceVersion + " -->\n";
        PolicyStudioSession created = orchestrator.processUpload(
                doc.getTenantId(),
                newName,
                doc.getDocumentType() == null ? "TXT" : doc.getDocumentType(),
                cloneText,
                body != null && body.get("createdBy") != null
                        ? String.valueOf(body.get("createdBy")) : "credit_manager",
                doc.getOriginalFileReference());
        if (created.getDocument() != null) {
            created.getDocument().setProductScope(doc.getProductScope());
        }
        // Preserve CM dispositions / parameter resolutions where systemRuleId matches
        copyRuleDraftMetadata(source, created);
        if (lifecycleService != null) {
            requireLifecycle().saveDraft(created, Map.of(
                    "reasonForChange", "Copied from " + sourceName + " · " + sourceVersion,
                    "policyName", newName));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "copy");
        meta.put("demo", false);
        meta.put("canResetDemo", false);
        meta.put("copiedFrom", sourceDocumentId.toString());
        meta.put("copiedFromName", sourceName);
        meta.put("copiedFromVersion", sourceVersion);
        meta.put("copiedFromLabel", "Copied from: " + sourceName + " · Version " + sourceVersion);
        if (created.getDocument() != null && created.getDocument().getId() != null) {
            sessionMeta.put(created.getDocument().getId(), meta);
            orchestrator.persistence().saveSessionSnapshot(created);
        }
        Map<String, Object> view = toProspectView(created, meta);
        view.put("message", "New draft created. Source policy was not changed.");
        view.put("enterWorkspace", true);
        view.put("defaultTab", "scope");
        view.put("copiedFromLabel", meta.get("copiedFromLabel"));
        return view;
    }

    private void copyRuleDraftMetadata(PolicyStudioSession source, PolicyStudioSession target) {
        if (source == null || target == null) return;
        Map<String, CiPolicyRuleCandidate> bySys = new LinkedHashMap<>();
        for (CiPolicyRuleCandidate r : source.getRuleCandidates()) {
            if (r.getSystemRuleId() != null && !r.getSystemRuleId().isBlank()) {
                bySys.put(r.getSystemRuleId().toUpperCase(Locale.ROOT), r);
            }
        }
        for (CiPolicyRuleCandidate t : target.getRuleCandidates()) {
            if (t.getSystemRuleId() == null) continue;
            CiPolicyRuleCandidate s = bySys.get(t.getSystemRuleId().toUpperCase(Locale.ROOT));
            if (s == null || s.getMetadata() == null || s.getMetadata().isEmpty()) continue;
            Map<String, Object> meta = t.getMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(t.getMetadata());
            meta.putAll(s.getMetadata());
            t.setMetadata(meta);
            if (s.getReviewStatus() != null) t.setReviewStatus(s.getReviewStatus());
        }
    }

    /** Existing policies for landing — session store (+ rule counts from session). */
    public List<Map<String, Object>> listExistingPolicies() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PolicyStudioSession session : orchestrator.persistence().listAllSessions()) {
            if (session.getDocument() == null || session.getDocument().getId() == null) continue;
            UUID docId = session.getDocument().getId();
            Map<String, Object> meta = sessionMeta.getOrDefault(docId, Map.of());
            // Skip pure demo fixtures unless they were saved as working drafts
            if (Boolean.TRUE.equals(meta.get("demo")) && !"scratch".equals(meta.get("kind"))
                    && !"copy".equals(meta.get("kind")) && !"upload".equals(meta.get("kind"))) {
                // still show demos that are open sessions — Credit Manager may want them; mark as example
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("documentId", docId.toString());
            row.put("policyName", session.getDocument().getName());
            Map<String, Object> life = Map.of();
            try {
                if (lifecycleService != null) {
                    life = requireLifecycle().settingsView(session);
                }
            } catch (Exception ignored) {
                life = Map.of();
            }
            row.put("status", life.getOrDefault("businessStatus",
                    session.getDocument().getStatus() == null ? "DRAFT" : session.getDocument().getStatus()));
            row.put("policyVersion", life.getOrDefault("policyVersion",
                    "v" + (session.getDocument().getDocumentVersion() == null
                            ? 1 : session.getDocument().getDocumentVersion())));
            Map<String, Object> app = life.get("applicability") instanceof Map<?, ?> m
                    ? castMap(m) : Map.of();
            row.put("products", app.getOrDefault("products", List.of()));
            row.put("scopeSummary", life.get("scopeSummary"));
            row.put("effectiveFrom", app.get("effectiveFrom"));
            long uw = session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().stream()
                    .filter(r -> r.getSystemRuleId() == null
                            || !r.getSystemRuleId().toUpperCase(Locale.ROOT).contains("OVERDUE_CHILD"))
                    .count();
            long needs = session.getRuleCandidates() == null ? 0 : session.getRuleCandidates().stream()
                    .filter(r -> {
                        Map<String, Object> rm = r.getMetadata() == null ? Map.of() : r.getMetadata();
                        return Boolean.TRUE.equals(rm.get("NEEDS_INPUT"))
                                || rm.get("blockedReason") != null;
                    })
                    .count();
            row.put("underwritingRuleCount", uw);
            row.put("needsInputCount", needs);
            row.put("kind", meta.getOrDefault("kind", Boolean.TRUE.equals(meta.get("demo")) ? "demo" : "session"));
            row.put("demo", Boolean.TRUE.equals(meta.get("demo")));
            row.put("copiedFromLabel", meta.get("copiedFromLabel"));
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(b.get("policyName")).compareToIgnoreCase(String.valueOf(a.get("policyName"))));
        return rows;
    }

    public Map<String, Object> build(String kind) {
        DemoSpec spec = resolveDemoSpec(kind);
        String text = loadClasspathText(spec.resource());
        UUID tenantId = properties.getDefaultTenantId();
        PolicyStudioSession session = orchestrator.processUpload(
                tenantId, spec.name(), "TXT", text, "staging-demo", spec.fileName());

        Map<String, Object> meta = demoMeta(spec.kind(), spec.fileName(), spec.resource(), text);
        return toProspectView(session, meta);
    }

    /** Staging-only: restore Banking/Bureau demo to fixture baseline. */
    public Map<String, Object> resetDemo(String kind) {
        DemoSpec spec = resolveDemoSpec(kind);
        log.info("staging-demo reset demo policy kind={}", spec.kind());
        return build(spec.kind());
    }

    public Map<String, Object> upload(MultipartFile file, String uploadedBy, String tenantHeader) {
        PolicyTextExtractionService.ExtractedText extracted = textExtractionService.extract(file);
        UUID tenantId = resolveTenant(tenantHeader);
        String name = stripExtension(extracted.originalFileName());
        if (name.isBlank()) {
            name = "Uploaded credit policy";
        }

        PolicyStudioSession session;
        try {
            session = orchestrator.processUpload(
                    tenantId,
                    name,
                    extracted.documentType(),
                    extracted.text(),
                    uploadedBy == null || uploadedBy.isBlank() ? "staging-upload" : uploadedBy,
                    extracted.originalFileName());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("policy-studio upload pipeline failed type={} reason={}",
                    extracted.documentType(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "We could not interpret this policy document. Please try again or upload a clearer text version.");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "upload");
        meta.put("demo", false);
        meta.put("canResetDemo", false);
        meta.put("fileName", extracted.originalFileName());
        meta.put("sourceTextLength", extracted.text().length());
        meta.put("sourceTextPreview",
                extracted.text().length() > 2000 ? extracted.text().substring(0, 2000) + "…" : extracted.text());
        if (session.getDocument() != null && session.getDocument().getId() != null) {
            sessionMeta.put(session.getDocument().getId(), meta);
        }
        Map<String, Object> view = toProspectView(session, meta);
        view.put("enterWorkspace", true);
        view.put("defaultTab", "scope");
        return view;
    }

    public Map<String, Object> sessionView(UUID documentId, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        Map<String, Object> meta = sessionMeta.getOrDefault(documentId, Map.of(
                "kind", "session",
                "demo", false,
                "canResetDemo", false,
                "fileName", session.getDocument() == null ? null : session.getDocument().getOriginalFileReference()));
        return toProspectView(session, new LinkedHashMap<>(meta));
    }

    public Map<String, Object> resolveAmbiguity(
            UUID documentId, UUID ambiguityId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        orchestrator.requireSession(documentId, tenantId);

        AmbiguityResolutionAction action = AmbiguityResolutionAction.SELECT_CANDIDATE;
        if (body.get("action") != null) {
            try {
                action = AmbiguityResolutionAction.valueOf(String.valueOf(body.get("action")));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resolution action");
            }
        }

        // UI actions → engine actions
        String uiAction = body.get("uiAction") == null ? null : String.valueOf(body.get("uiAction"));
        if (uiAction != null) {
            action = switch (uiAction) {
                case "ACCEPT_RECOMMENDATION", "SELECT_DIFFERENT_MEANING" -> AmbiguityResolutionAction.SELECT_CANDIDATE;
                case "CREATE_NEW_METRIC" -> AmbiguityResolutionAction.CREATE_NEW_METRIC;
                case "CREATE_POLICY_PARAMETER" -> AmbiguityResolutionAction.CREATE_POLICY_PARAMETER;
                case "ASK_CUSTOMER", "KEEP_UNRESOLVED" -> AmbiguityResolutionAction.REQUEST_CLARIFICATION;
                default -> action;
            };
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = body.get("payload") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) body.get("payload"))
                : new LinkedHashMap<>();

        boolean remember = Boolean.TRUE.equals(body.get("rememberDefinition"))
                || Boolean.TRUE.equals(payload.get("rememberDefinition"));
        if (remember && action == AmbiguityResolutionAction.SELECT_CANDIDATE) {
            action = AmbiguityResolutionAction.CREATE_VOCABULARY_TERM;
            payload.putIfAbsent("term", body.getOrDefault("term", payload.get("term")));
            payload.putIfAbsent("canonicalPath", body.getOrDefault("resolvedOption", body.get("resolvedOption")));
            String scope = String.valueOf(body.getOrDefault("rememberScope",
                    payload.getOrDefault("scopeLevel", "TENANT")));
            // DOCUMENT scope stays session-only suggestion via TENANT note; PRODUCT/TENANT go to vocabulary
            if ("DOCUMENT".equalsIgnoreCase(scope) || "THIS_POLICY".equalsIgnoreCase(scope)) {
                payload.put("scopeLevel", "TENANT");
                payload.put("previouslyApprovedNote",
                        "Suggested for this policy only — not automatically authoritative on future policies.");
            } else if ("PRODUCT".equalsIgnoreCase(scope)) {
                payload.put("scopeLevel", "PRODUCT");
                payload.put("previouslyApprovedNote",
                        "Previously approved suggestion for this product — not automatically authoritative.");
            } else {
                payload.put("scopeLevel", "TENANT");
                payload.put("previouslyApprovedNote",
                        "Previously approved suggestion for this lender — not automatically authoritative.");
            }
            if (payload.get("term") == null && body.get("unclearTerm") != null) {
                payload.put("term", body.get("unclearTerm"));
            }
            if (payload.get("canonicalPath") == null) {
                payload.put("canonicalPath", body.get("resolvedOption"));
            }
            if (payload.get("canonicalMeaning") == null) {
                payload.put("canonicalMeaning", body.getOrDefault("notes", body.get("resolvedOption")));
            }
        }

        String resolvedOption = str(body, "resolvedOption", null);
        String resolvedBy = str(body, "resolvedBy", "credit_manager");
        String notes = str(body, "notes", null);

        Map<String, Object> resolveResult = orchestrator.resolveAmbiguity(
                documentId, ambiguityId, action, resolvedOption, resolvedBy, notes, payload);

        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lastResolution", resolveResult);
        view.put("message", "Resolution saved. Policy readiness and rule status have been recalculated.");
        return view;
    }

    public Map<String, Object> reviewRule(
            UUID documentId, UUID ruleId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);

        String uiAction = str(body, "uiAction", "APPROVE");
        String reviewState;
        String reason = str(body, "reason", null);
        String action = uiAction == null ? "APPROVE" : uiAction.toUpperCase(Locale.ROOT);
        switch (action) {
            case "REJECT", "REJECT_RULE", "DELETE", "EXCLUDE" -> {
                reviewState = ReviewState.REJECTED.name();
                if (reason == null) {
                    reason = "DELETE".equals(action) || "EXCLUDE".equals(action)
                            ? "Excluded from intended policy by Credit Manager"
                            : "Rejected during Credit Head review";
                }
            }
            case "IGNORE", "IGNORE_FOR_NOW" -> {
                reviewState = ReviewState.REJECTED.name();
                if (reason == null) {
                    reason = "Ignored for now — retained for draft, excluded from activation";
                }
            }
            case "MANUAL_INPUT", "MANUAL_VERIFICATION" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Designated as Manual Input by Credit Manager";
                }
            }
            case "APPROVE", "APPROVE_RULE", "ACCEPT", "EDIT" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "EDIT".equals(action)
                            ? "Edited and accepted by Credit Manager"
                            : "Accepted during Credit Manager review";
                }
            }
            // POLICY-CONVERGENCE-1 — CLEAN definition (session/draft only; never invent DPD=0)
            case "DEFINE_CLEAN", "USE_EXISTING_CLEAN_DEFINITION", "MANUAL_CLEAN_INPUT" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Clean history definition recorded on draft (session only)";
                }
            }
            // POLICY-PARAMETER-RESOLVER-1 — generic parameter resolution (session/draft only)
            case "RESOLVE_PARAMETER_MAP", "RESOLVE_PARAMETER_MANUAL",
                 "RESOLVE_PARAMETER_USE_PROPOSAL", "RESOLVE_PARAMETER_UNAVAILABLE" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Parameter resolution recorded on draft (session only)";
                }
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported rule review action");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> humanChanges = body.get("humanChanges") instanceof Map<?, ?>
                ? new LinkedHashMap<>((Map<String, Object>) body.get("humanChanges"))
                : new LinkedHashMap<>();

        // Persist Credit Manager disposition in existing rule metadata (no schema migration).
        CiPolicyRuleCandidate rule = session.getRuleCandidates().stream()
                .filter(r -> ruleId.equals(r.getId()))
                .findFirst()
                .orElse(null);
        if (rule != null) {
            Map<String, Object> meta = rule.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(rule.getMetadata());
            switch (action) {
                case "IGNORE", "IGNORE_FOR_NOW" -> {
                    meta.put("disposition", "IGNORED");
                    meta.put("excludedFromActivation", true);
                    meta.put("deleted", false);
                }
                case "DELETE", "EXCLUDE" -> {
                    meta.put("disposition", "DELETED");
                    meta.put("excludedFromActivation", true);
                    meta.put("deleted", true);
                }
                case "MANUAL_INPUT", "MANUAL_VERIFICATION" -> {
                    meta.put("disposition", "MANUAL_INPUT");
                    meta.put("verificationMode", "MANUAL");
                    meta.put("dataGapDisposition", "MANUAL_VERIFICATION");
                    meta.put("excludedFromActivation", false);
                    meta.put("deleted", false);
                    if (body.get("manualInputLabel") != null) {
                        meta.put("manualInputLabel", String.valueOf(body.get("manualInputLabel")));
                    }
                    if (body.get("manualInputType") != null) {
                        meta.put("manualInputType", String.valueOf(body.get("manualInputType")));
                    }
                    if (body.get("requiredActor") != null) {
                        meta.put("requiredActor", String.valueOf(body.get("requiredActor")));
                    } else {
                        meta.putIfAbsent("requiredActor", "Credit Manager");
                    }
                    meta.putIfAbsent("requiredEvidence", "Application review capture");
                    meta.putIfAbsent("manualOutcome", "PASS / FAIL / REFER");
                }
                case "APPROVE", "APPROVE_RULE", "ACCEPT", "EDIT" -> {
                    meta.put("disposition", "EDIT".equals(action) ? "EDITED" : "ACCEPTED");
                    meta.put("excludedFromActivation", false);
                    meta.put("deleted", false);
                }
                case "DEFINE_CLEAN" -> {
                    Map<String, Object> def = com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.buildDraftDefinition(body);
                    meta.put(com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.META_KEY, def);
                    meta.put("NEEDS_INPUT", Boolean.TRUE.equals(def.get("incomplete")));
                    meta.put("blockedReason", Boolean.TRUE.equals(def.get("incomplete"))
                            ? "Clean credit history needs a definition."
                            : null);
                    meta.put("evaluatedFrom", "Bureau");
                    meta.put("disposition", "EDITED");
                    meta.put("excludedFromActivation", false);
                    // Also stamp parent siblings sharing overdue exception
                    stampCleanOnRelatedRules(session, def);
                }
                case "USE_EXISTING_CLEAN_DEFINITION" -> {
                    String pid = body.get("parameterId") == null
                            ? "bureau.credit_after_overdue.clean_history_months"
                            : String.valueOf(body.get("parameterId"));
                    Map<String, Object> def = com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.linkExisting(pid);
                    meta.put(com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.META_KEY, def);
                    meta.put("NEEDS_INPUT", false);
                    meta.put("blockedReason", null);
                    meta.put("evaluatedFrom", "Bureau");
                    meta.put("disposition", "EDITED");
                    stampCleanOnRelatedRules(session, def);
                }
                case "MANUAL_CLEAN_INPUT" -> {
                    Map<String, Object> def = com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.manualInput(
                                    body.get("manualInputLabel") == null ? null
                                            : String.valueOf(body.get("manualInputLabel")));
                    meta.put(com.los.core.creditintelligence.policystudio.parameters
                            .CleanHistoryDefinitionSupport.META_KEY, def);
                    meta.put("disposition", "MANUAL_INPUT");
                    meta.put("verificationMode", "MANUAL");
                    meta.put("NEEDS_INPUT", false);
                    meta.put("evaluatedFrom", "Manual Input");
                    stampCleanOnRelatedRules(session, def);
                }
                case "RESOLVE_PARAMETER_MAP", "RESOLVE_PARAMETER_MANUAL",
                     "RESOLVE_PARAMETER_USE_PROPOSAL", "RESOLVE_PARAMETER_UNAVAILABLE" -> {
                    applyParameterResolution(session, rule, meta, action, body);
                }
                default -> {
                    /* reject path keeps prior metadata */
                }
            }
            meta.put("lastUiAction", action);
            meta.put("lastUiActionAt", Instant.now().toString());
            rule.setMetadata(meta);
            if ("EDIT".equals(action) && body.get("businessRule") != null) {
                humanChanges.putIfAbsent("businessRule", body.get("businessRule"));
            }
            if ("EDIT".equals(action) && body.get("threshold") != null) {
                humanChanges.putIfAbsent("threshold", body.get("threshold"));
            }
            // POLICY-UX-2C — catalogue-backed edit rewrites parameters + expression in place
            if ("EDIT".equals(action)
                    && (body.get("parameters") != null || body.get("failureTreatment") != null)
                    && meta.get("businessCapabilityId") != null) {
                Map<String, Object> catalogueEdit = new LinkedHashMap<>(body);
                catalogueEdit.put("ruleId", ruleId.toString());
                catalogueEdit.putIfAbsent("businessCapabilityId", meta.get("businessCapabilityId"));
                catalogueDraftService.addOrUpdate(session, catalogueEdit);
            }
        }

        var review = orchestrator.reviewService().review(
                session,
                "RULE",
                ruleId,
                str(body, "reviewer", "credit_manager"),
                str(body, "reviewerRole", PolicyReviewService.ROLE_CREDIT_MANAGER),
                reviewState,
                humanChanges,
                reason);

        if (rule != null) {
            orchestrator.persistence().saveSessionSnapshot(session);
        }

        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lastReview", Map.of(
                "reviewId", review.getId(),
                "reviewState", review.getReviewState(),
                "reviewer", review.getReviewer(),
                "reviewerRole", review.getReviewerRole(),
                "uiAction", action));
        view.put("message", "Rule review recorded.");
        return view;
    }

    /**
     * POLICY-PARAMETER-RESOLVER-1 — persist policy-scoped operand resolution (session draft only).
     * Does not invent CLEAN=DPD0. Does not grant production authority.
     */
    private void applyParameterResolution(
            PolicyStudioSession session,
            CiPolicyRuleCandidate rule,
            Map<String, Object> meta,
            String action,
            Map<String, Object> body) {
        String operandKey = body.get("operandKey") == null
                ? com.los.core.creditintelligence.policystudio.parameters.ParameterResolutionSupport
                .normalizeOperandKey(String.valueOf(body.getOrDefault("originalTerm", "parameter")))
                : String.valueOf(body.get("operandKey"));
        String originalTerm = body.get("originalTerm") == null
                ? operandKey : String.valueOf(body.get("originalTerm"));
        var registry = com.los.core.creditintelligence.policystudio.parameters
                .PolicyStudioConvergencePresenter.registry();
        Map<String, Object> resolution;
        switch (action) {
            case "RESOLVE_PARAMETER_MAP" -> {
                String parameterId = String.valueOf(body.getOrDefault("parameterId", ""));
                var def = registry.findById(parameterId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Unknown parameter — search/browse only returns registry entries."));
                resolution = com.los.core.creditintelligence.policystudio.parameters
                        .ParameterResolutionSupport.mapToExisting(def, originalTerm);
            }
            case "RESOLVE_PARAMETER_MANUAL" -> resolution = com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.manual(
                            body.get("manualInputLabel") == null ? null
                                    : String.valueOf(body.get("manualInputLabel")),
                            body.get("manualInputType") == null ? null
                                    : String.valueOf(body.get("manualInputType")),
                            body.get("unit") == null ? null : String.valueOf(body.get("unit")),
                            body.get("requiredActor") == null ? null
                                    : String.valueOf(body.get("requiredActor")),
                            body.get("guidance") == null ? null : String.valueOf(body.get("guidance")),
                            originalTerm);
            case "RESOLVE_PARAMETER_USE_PROPOSAL" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> proposal = body.get("proposal") instanceof Map<?, ?>
                        ? new LinkedHashMap<>((Map<String, Object>) body.get("proposal"))
                        : Map.of();
                if (proposal.isEmpty()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Proposal required — describe-meaning must be confirmed explicitly.");
                }
                resolution = com.los.core.creditintelligence.policystudio.parameters
                        .ParameterResolutionSupport.acceptProposal(proposal, originalTerm);
            }
            case "RESOLVE_PARAMETER_UNAVAILABLE" -> resolution = com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.unavailable(
                            body.get("parameterId") == null ? null : String.valueOf(body.get("parameterId")),
                            body.get("reason") == null ? null : String.valueOf(body.get("reason")),
                            originalTerm);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported resolve action");
        }
        Map<String, Object> all = com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.resolutionsOf(meta);
        all.put(operandKey, resolution);
        meta.put(com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.META_KEY, all);
        meta.put("disposition", "EDITED");
        meta.put("excludedFromActivation", false);
        meta.put("NEEDS_INPUT", !com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.isResolved(resolution)
                || com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.STATUS_UNAVAILABLE.equals(
                        String.valueOf(resolution.get("status"))));
        if (com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.isResolved(resolution)) {
            meta.remove("blockedReason");
        }
        // CLEAN bridge — preserve compound ALL parent relationship
        if ("clean_history".equals(operandKey)) {
            Map<String, Object> bridge = com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.toCleanHistoryBridge(resolution);
            if (bridge != null) {
                meta.put(com.los.core.creditintelligence.policystudio.parameters
                        .CleanHistoryDefinitionSupport.META_KEY, bridge);
                stampCleanOnRelatedRules(session, bridge);
            }
        }
        // Share EDI (and other) draft mappings across sibling rules in this session
        if ("proposed_edi".equals(operandKey) || "clean_history".equals(operandKey)) {
            stampParameterResolutionOnRelatedRules(session, operandKey, resolution);
        }
        stampParameterResolutionOnDocument(session, operandKey, resolution);
        rule.setMetadata(meta);
    }

    private static void stampParameterResolutionOnRelatedRules(
            PolicyStudioSession session, String operandKey, Map<String, Object> resolution) {
        if (session == null || resolution == null || operandKey == null) return;
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
            boolean match = ("proposed_edi".equals(operandKey) && sys.contains("EDI"))
                    || ("clean_history".equals(operandKey)
                    && (sys.contains("OVERDUE_EXCEPTION") || sys.contains("CLEAN")
                    || sys.contains("OVERDUE_CHILD_3") || sys.contains("NO_OVERDUE_EXCEPT")));
            if (!match) continue;
            Map<String, Object> m = r.getMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(r.getMetadata());
            Map<String, Object> all = com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.resolutionsOf(m);
            all.put(operandKey, resolution);
            m.put(com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.META_KEY, all);
            if (com.los.core.creditintelligence.policystudio.parameters
                    .ParameterResolutionSupport.isResolved(resolution)) {
                m.remove("blockedReason");
            }
            r.setMetadata(m);
        }
    }

    private static void stampParameterResolutionOnDocument(
            PolicyStudioSession session, String operandKey, Map<String, Object> resolution) {
        if (session == null || session.getDocument() == null || resolution == null) return;
        Map<String, Object> docMeta = session.getDocument().getMetadata() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(session.getDocument().getMetadata());
        @SuppressWarnings("unchecked")
        Map<String, Object> maps = docMeta.get(com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.DOC_META_KEY) instanceof Map<?, ?> m
                ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
        maps.put(operandKey, resolution);
        docMeta.put(com.los.core.creditintelligence.policystudio.parameters
                .ParameterResolutionSupport.DOC_META_KEY, maps);
        session.getDocument().setMetadata(docMeta);
    }

    /** Session/draft only — stamp CLEAN definition onto overdue-exception related rules. */
    private static void stampCleanOnRelatedRules(PolicyStudioSession session, Map<String, Object> def) {
        if (session == null || def == null) return;
        for (CiPolicyRuleCandidate r : session.getRuleCandidates()) {
            String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
            if (!(sys.contains("OVERDUE_EXCEPTION") || sys.contains("NO_OVERDUE_EXCEPT")
                    || sys.contains("OVERDUE_CHILD_3") || sys.contains("CLEAN"))) {
                continue;
            }
            Map<String, Object> m = r.getMetadata() == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(r.getMetadata());
            m.put(com.los.core.creditintelligence.policystudio.parameters
                    .CleanHistoryDefinitionSupport.META_KEY, def);
            m.put("evaluatedFrom", "Bureau");
            if (!com.los.core.creditintelligence.policystudio.parameters
                    .CleanHistoryDefinitionSupport.STATUS_UNRESOLVED
                    .equals(String.valueOf(def.get("status")))) {
                m.put("NEEDS_INPUT", Boolean.TRUE.equals(def.get("incomplete")));
                if (!Boolean.TRUE.equals(def.get("incomplete"))) {
                    m.remove("blockedReason");
                }
            }
            r.setMetadata(m);
        }
        if (session.getDocument() != null) {
            Map<String, Object> docMeta = session.getDocument().getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(session.getDocument().getMetadata());
            docMeta.put(com.los.core.creditintelligence.policystudio.parameters
                    .CleanHistoryDefinitionSupport.META_KEY, def);
            session.getDocument().setMetadata(docMeta);
        }
    }

    /**
     * Credit Manager "+ Add rule" — reuses existing clause → interpretation → rule factory path.
     * Does not introduce a second rule engine.
     */
    public Map<String, Object> addPlainEnglishRule(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        String text = str(body, "text", null);
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule text is required");
        }
        String group = str(body, "group", "Credit Rules");
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        CiPolicyDocument doc = session.getDocument();
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Policy document not found");
        }
        var kind = new PolicyClauseExtractor().detectKind(
                doc.getSourceText() == null ? text : doc.getSourceText());
        CiPolicyClause clause = CiPolicyClause.builder()
                .id(UUID.randomUUID())
                .policyDocumentId(doc.getId())
                .section(group == null || group.isBlank() ? "Credit Rules" : group)
                .sourceText(text.trim())
                .normalizedText(text.trim().replaceAll("\\s+", " "))
                .clauseType(ClauseType.HARD_RULE.name())
                .extractionConfidence(new BigDecimal("0.9200"))
                .sortOrder(session.getClauses().size())
                .sourceLocation("manual:" + session.getClauses().size())
                .status("EXTRACTED")
                .metadata(Map.of("plainEnglishAdded", true, "businessGroup", group == null ? "Credit Rules" : group))
                .effectiveScope(Map.of())
                .build();
        List<CiPolicyInterpretation> interps = new DeterministicGoldenInterpretationProvider()
                .interpret(List.of(clause), new PolicyAuthoringRegistry(), kind);
        List<CiPolicyRuleCandidate> rules = new RuleCandidateFactory()
                .create(doc, List.of(clause), interps, kind);
        for (CiPolicyRuleCandidate r : rules) {
            Map<String, Object> meta = r.getMetadata() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(r.getMetadata());
            meta.put("disposition", "EXTRACTED");
            meta.put("plainEnglishAdded", true);
            meta.putIfAbsent("businessTitle", text.trim());
            r.setMetadata(meta);
            r.setReviewStatus(ReviewState.AI_DRAFTED.name());
        }
        session.getClauses().add(clause);
        session.getInterpretations().addAll(interps);
        session.getRuleCandidates().addAll(rules);
        orchestrator.reviewService().invalidateCheckerApproval(session, "plain-english-add");
        orchestrator.persistence().saveSessionSnapshot(session);

        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("addedClauseId", clause.getId().toString());
        view.put("addedRuleCount", rules.size());
        view.put("message", rules.isEmpty()
                ? "Clause stored for review — interpretation did not yet produce an executable rule."
                : "Plain-English rule added — review before Accept.");
        return view;
    }

    /**
     * POLICY-UX-2C — add or update a catalogue capability as a draft rule (no new engine / tables).
     */
    public Map<String, Object> addCatalogueCapabilityRule(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        CatalogueCapabilityDraftService.DraftMutation mutation =
                catalogueDraftService.addOrUpdate(session, body == null ? Map.of() : body);
        orchestrator.reviewService().invalidateCheckerApproval(session,
                mutation.created() ? "catalogue-capability-add" : "catalogue-capability-edit");
        orchestrator.persistence().saveSessionSnapshot(session);

        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("addedRuleId", mutation.rule().getId().toString());
        view.put("created", mutation.created());
        view.put("businessCapabilityId", mutation.rule().getMetadata() == null
                ? null : mutation.rule().getMetadata().get("businessCapabilityId"));
        view.put("message", mutation.created()
                ? "Capability added to draft policy."
                : "Capability parameters updated in draft.");
        view.put("allowCanonicalAuthority", false);
        return view;
    }

    public Map<String, Object> openBusinessMeasureDesigner(
            UUID documentId, String dataElementCode, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        Map<String, Object> out = measureDesigner.openDesigner(session, dataElementCode);
        out.put("documentId", documentId.toString());
        return out;
    }

    public Map<String, Object> confirmBusinessMeasure(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        Map<String, Object> result = measureDesigner.confirmDefinition(session, body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("measureDesignerResult", result);
        view.put("implementability", new PolicyImplementabilityService().assess(session));
        view.put("implementabilitySummary",
                ((Map<?, ?>) view.get("implementability")).get("summary"));
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> approveBusinessMeasure(
            UUID documentId, String measureId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        Map<String, Object> result = measureDesigner.approveMeasure(
                session, measureId, str(body, "resolvedBy", "credit_manager"));
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("measureDesignerResult", result);
        view.put("implementability", new PolicyImplementabilityService().assess(session));
        view.put("implementabilitySummary",
                ((Map<?, ?>) view.get("implementability")).get("summary"));
        view.put("message", "Business measure approved — readiness recalculated.");
        return view;
    }

    // ─── Day 6.2 Policy lifecycle / applicability ───────────────────

    public Map<String, Object> lifecycleSettings(UUID documentId, String tenantHeader) {
        return requireLifecycle().settingsView(requireSession(documentId, tenantHeader));
    }

    public Map<String, Object> saveLifecycleDraft(UUID documentId, Map<String, Object> body, String tenantHeader) {
        Map<String, Object> result = requireLifecycle().saveDraft(requireSession(documentId, tenantHeader), body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> submitLifecycleReview(UUID documentId, Map<String, Object> body, String tenantHeader) {
        Map<String, Object> result = requireLifecycle().submitForReview(requireSession(documentId, tenantHeader), body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> approveLifecyclePolicy(UUID documentId, Map<String, Object> body, String tenantHeader) {
        Map<String, Object> result = requireLifecycle().approvePolicy(requireSession(documentId, tenantHeader), body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> scheduleLifecyclePolicy(UUID documentId, Map<String, Object> body, String tenantHeader) {
        Map<String, Object> result = requireLifecycle().schedulePolicy(requireSession(documentId, tenantHeader), body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> retireLifecyclePolicy(UUID documentId, Map<String, Object> body, String tenantHeader) {
        Map<String, Object> result = requireLifecycle().retirePolicy(requireSession(documentId, tenantHeader), body);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        return view;
    }

    public Map<String, Object> createLifecycleVersion(UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession source = requireSession(documentId, tenantHeader);
        Map<String, Object> result = requireLifecycle().createNewVersion(source, body);
        Object newId = result.get("documentId");
        if (newId != null) {
            Map<String, Object> meta = new LinkedHashMap<>(sessionMeta.getOrDefault(documentId, Map.of()));
            meta.put("kind", meta.getOrDefault("kind", "version"));
            meta.put("clonedFrom", documentId.toString());
            meta.put("demo", false);
            sessionMeta.put(UUID.fromString(String.valueOf(newId)), meta);
            Map<String, Object> view = sessionView(UUID.fromString(String.valueOf(newId)), tenantHeader);
            view.put("lifecycle", result);
            view.put("message", result.get("message"));
            view.put("lineage", result.get("lineage"));
            return view;
        }
        return result;
    }

    public Map<String, Object> resolveShadowApplication(Map<String, Object> body, String tenantHeader) {
        return requireLifecycle().resolveShadowApplication(resolveTenant(tenantHeader), body == null ? Map.of() : body);
    }

    public Map<String, Object> lifecycleHistory(UUID documentId, String tenantHeader) {
        Map<String, Object> settings = requireLifecycle().settingsView(requireSession(documentId, tenantHeader));
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("history", settings.get("history"));
        out.put("policySettings", settings.get("policySettings"));
        out.put("compareVersionsNote",
                "Compare Versions is analytical — use Portfolio Intelligence → Policy Impact Lab later. "
                        + "Not part of normal application processing.");
        return out;
    }

    public Map<String, Object> lifecycleStatusMapping() {
        return requireLifecycle().statusMapping();
    }

    private PolicyStudioSession requireSession(UUID documentId, String tenantHeader) {
        return orchestrator.requireSession(documentId, resolveTenant(tenantHeader));
    }

    private PolicyLifecycleService requireLifecycle() {
        if (lifecycleService == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Policy lifecycle service unavailable");
        }
        return lifecycleService;
    }

    private Map<String, Object> toProspectView(PolicyStudioSession session, Map<String, Object> meta) {
        if (session.getDocument() != null && session.getDocument().getId() != null) {
            sessionMeta.put(session.getDocument().getId(), meta);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        ProspectPolicyViewBuilder.enrich(out, session, meta);
        if (lifecycleService != null && session.getDocument() != null) {
            try {
                Map<String, Object> life = lifecycleService.settingsView(session);
                out.put("lifecycle", life);
                out.put("policySettings", life.get("policySettings"));
                out.put("implementationStatus", life.get("implementationStatus"));
                out.put("readyToSchedule", life.get("readyToSchedule"));
                out.put("businessLifecycleStatus", life.get("businessStatus"));
            } catch (Exception e) {
                log.debug("lifecycle enrich skipped: {}", e.getClass().getSimpleName());
            }
        }
        out.put("columns", List.of("SOURCE", "INTERPRETATION", "EXECUTABLE"));
        out.put("canResetDemo", Boolean.TRUE.equals(meta.get("demo")));
        if (meta.get("copiedFromLabel") != null) {
            out.put("copiedFromLabel", meta.get("copiedFromLabel"));
        }
        if (Boolean.TRUE.equals(meta.get("createdFromScratch"))) {
            out.put("createdFromScratch", true);
        }
        out.put("portfolioIntelligencePlaceholder", Map.of(
                "path", "Portfolio Intelligence → Policy Impact Lab",
                "enabled", false));
        return out;
    }

    private Map<String, Object> demoMeta(String kind, String fileName, String resource, String text) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", kind);
        meta.put("demo", true);
        meta.put("demoLabel", "kyc".equalsIgnoreCase(kind)
                ? "DEMO POLICY / VALIDATION SAMPLE"
                : DEMO_BANNER);
        meta.put("canResetDemo", true);
        meta.put("fileName", fileName);
        meta.put("sourcePath", resource);
        meta.put("sourceTextLength", text.length());
        meta.put("sourceTextPreview", text.length() > 2000 ? text.substring(0, 2000) + "…" : text);
        return meta;
    }

    private DemoSpec resolveDemoSpec(String kind) {
        String k = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if ("kyc".equals(k) || "kyc-bre".equals(k) || "kyc_bre".equals(k)
                || "decision-kyc".equals(k) || "eligibility".equals(k)) {
            return new DemoSpec("kyc", "KYC & Eligibility Validation Sample",
                    "policy-fixtures/kyc-bre/Kyc_Eligibility_Validation_Sample.txt",
                    "Kyc_Eligibility_Validation_Sample.txt");
        }
        if ("banking".equals(k) || "banking-bre".equals(k) || "banking_bre".equals(k)) {
            return new DemoSpec("banking", "Banking BRE",
                    "policy-fixtures/banking-bre/Banking_BRE.txt", "Banking_BRE.txt");
        }
        if ("bureau".equals(k) || "bureau-bre".equals(k) || "bureau_bre".equals(k)) {
            return new DemoSpec("bureau", "Bureau BRE",
                    "policy-fixtures/bureau-bre/Bureau_BRE.txt", "Bureau_BRE.txt");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind must be kyc, banking, or bureau");
    }

    private UUID resolveTenant(String tenantHeader) {
        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                return UUID.fromString(tenantHeader.trim());
            } catch (IllegalArgumentException ignored) {
                log.info("staging-demo ignored invalid tenant header");
            }
        }
        return properties.getDefaultTenantId();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> m) {
        if (m == null) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    private static String str(Map<String, Object> body, String key, String defaultValue) {
        if (body == null || body.get(key) == null) {
            return defaultValue;
        }
        String v = String.valueOf(body.get(key));
        return v.isBlank() ? defaultValue : v;
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int i = fileName.lastIndexOf('.');
        return i > 0 ? fileName.substring(0, i) : fileName;
    }

    private static String loadClasspathText(String path) {
        try (InputStream in = classLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo policy not found");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load demo policy");
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : StagingPolicyStudioDemoService.class.getClassLoader();
    }

    private record DemoSpec(String kind, String name, String resource, String fileName) {}
}
