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
        out.put("title", "AI Policy Studio");
        out.put("subtitle",
                "Upload your Decision Policy. BillionTech will identify KYC & Eligibility requirements, credit rules, "
                        + "definitions, exceptions and ambiguities, then convert them into an executable draft for review.");
        out.put("supportedFormats", List.of("PDF", "DOCX", "TXT"));
        out.put("maxUploadBytes", PolicyTextExtractionService.MAX_BYTES);
        out.put("capabilities", List.of(
                "Read and structure the document",
                "Identify KYC & Eligibility requirements",
                "Identify credit underwriting rules",
                "Map rules to available data and verification facts",
                "Flag ambiguous terms for review",
                "Generate executable rule candidates",
                "Generate boundary and missing-data tests"));
        out.put("humanReviewBanner",
                "AI-generated interpretations require human review. Nothing is published automatically.");
        out.put("allowCanonicalAuthority", false);
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
        return out;
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
        return toProspectView(session, meta);
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
