package com.los.core.creditintelligence.staging;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.catalogue.CatalogueCapabilityDraftService;
import com.los.core.creditintelligence.policystudio.catalogue.CreditCapabilityCatalogueService;
import com.los.core.creditintelligence.policystudio.catalogue.IngestionMatchClassification;
import com.los.core.creditintelligence.policystudio.parameters.CmRuleAuthoringService;
import com.los.core.creditintelligence.policystudio.parameters.PolicyExecutionReadiness;
import com.los.core.exception.BusinessRuleException;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyClause;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyInterpretation;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyRuleCandidate;
import com.los.core.creditintelligence.policystudio.domain.ClauseType;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.lifecycle.PolicyLifecycleService;
import com.los.core.creditintelligence.policystudio.lifecycle.StructuredPolicySessionCloner;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.policystudio.service.BusinessMeasureDesignerService;
import com.los.core.creditintelligence.policystudio.service.DeterministicGoldenInterpretationProvider;
import com.los.core.creditintelligence.policystudio.service.PolicyClauseExtractor;
import com.los.core.creditintelligence.policystudio.service.PolicyDocumentService;
import com.los.core.creditintelligence.policystudio.service.PolicyImplementabilityService;
import com.los.core.creditintelligence.policystudio.service.PolicyReviewService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioDurableLandingListService;
import com.los.core.creditintelligence.policystudio.service.PolicyStudioOrchestrator;
import com.los.core.creditintelligence.policystudio.scorecard.PolicyVersionScorecardLinkage;
import com.los.core.creditintelligence.policystudio.repository.CiPolicyDocumentRepository;
import com.los.core.model.entity.UnderwritingScorecard;
import com.los.core.repository.UnderwritingScorecardRepository;
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
    private final CmRuleAuthoringService cmRuleAuthoringService;
    private final PolicyStudioDurableLandingListService durableLandingListService;

    /** Optional — overlays durable ci_policy_document.scorecard_id at projection time. */
    private CiPolicyDocumentRepository documentRepository;
    /** Optional — resolve linked scorecard identity by canonical id only. */
    private UnderwritingScorecardRepository scorecardRepository;

    /** documentId → meta used to rebuild prospect view after resolve/review */
    private final ConcurrentHashMap<UUID, Map<String, Object>> sessionMeta = new ConcurrentHashMap<>();

    @Autowired
    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService,
            BusinessMeasureDesignerService measureDesigner,
            PolicyLifecycleService lifecycleService,
            CatalogueCapabilityDraftService catalogueDraftService,
            CmRuleAuthoringService cmRuleAuthoringService,
            PolicyStudioDurableLandingListService durableLandingListService) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.textExtractionService = textExtractionService;
        this.measureDesigner = measureDesigner != null ? measureDesigner : new BusinessMeasureDesignerService();
        this.lifecycleService = lifecycleService;
        this.catalogueDraftService = catalogueDraftService != null
                ? catalogueDraftService
                : new CatalogueCapabilityDraftService(new CreditCapabilityCatalogueService(properties));
        this.cmRuleAuthoringService = cmRuleAuthoringService != null
                ? cmRuleAuthoringService : new CmRuleAuthoringService();
        this.durableLandingListService = durableLandingListService != null
                ? durableLandingListService
                : new PolicyStudioDurableLandingListService();
    }

    /** Test / legacy convenience — Spring uses the @Autowired constructor. */
    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService) {
        this(properties, orchestrator, textExtractionService, new BusinessMeasureDesignerService(),
                null, null, new CmRuleAuthoringService(), new PolicyStudioDurableLandingListService());
    }

    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService,
            BusinessMeasureDesignerService measureDesigner) {
        this(properties, orchestrator, textExtractionService, measureDesigner, null, null,
                new CmRuleAuthoringService(), new PolicyStudioDurableLandingListService());
    }

    @Autowired(required = false)
    public void setDocumentRepository(CiPolicyDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired(required = false)
    public void setScorecardRepository(UnderwritingScorecardRepository scorecardRepository) {
        this.scorecardRepository = scorecardRepository;
    }

    /** Unit-test convenience with lifecycle + durable landing list. */
    public StagingPolicyStudioDemoService(
            CreditIntelligenceProperties properties,
            PolicyStudioOrchestrator orchestrator,
            PolicyTextExtractionService textExtractionService,
            BusinessMeasureDesignerService measureDesigner,
            PolicyLifecycleService lifecycleService,
            CatalogueCapabilityDraftService catalogueDraftService,
            CmRuleAuthoringService cmRuleAuthoringService) {
        this(properties, orchestrator, textExtractionService, measureDesigner, lifecycleService,
                catalogueDraftService, cmRuleAuthoringService, new PolicyStudioDurableLandingListService());
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
        List<Map<String, Object>> existing = listExistingPolicies();
        out.put("existingPolicies", existing);
        out.put("existingPolicyCount", existing.size());
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
        // Scratch stub text must not become fake underwriting CLASSIFICATION cards
        purgeStubClassificationRules(session);
        if (session.getDocument() != null && session.getDocument().getId() != null) {
            sessionMeta.put(session.getDocument().getId(), meta);
            orchestrator.persistence().saveSessionSnapshot(session);
        }
        Map<String, Object> view = toProspectView(session, meta);
        view.put("message", "Draft created. Continue with Scope, then Rules.");
        view.put("enterWorkspace", true);
        view.put("defaultTab", "scope");
        return view;
    }

    /** Remove non-executable CLASSIFICATION/AMBIGUOUS stubs produced from scratch boilerplate. */
    private void purgeStubClassificationRules(PolicyStudioSession session) {
        if (session == null || session.getRuleCandidates() == null) return;
        session.getRuleCandidates().removeIf(r -> {
            Map<String, Object> expr = r.getExpression();
            Map<String, Object> meta = r.getMetadata() == null ? Map.of() : r.getMetadata();
            if (Boolean.TRUE.equals(meta.get("cmAuthored"))) return false;
            if (expr != null && "CLASSIFICATION".equalsIgnoreCase(String.valueOf(expr.get("op")))) {
                return true;
            }
            String sys = r.getSystemRuleId() == null ? "" : r.getSystemRuleId().toUpperCase(Locale.ROOT);
            return sys.startsWith("CLASSIFICATION_");
        });
    }

    /**
     * POLICY-CREATION-1 / POLICY-VERSION-INTEGRITY-1 — Copy into a NEW draft (separate family).
     * Distinct from Create New Version (same family, next version). Both use structured clone — no NLP re-ingest.
     */
    public Map<String, Object> copyPolicy(UUID sourceDocumentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession source = requireSession(sourceDocumentId, tenantHeader);
        CiPolicyDocument doc = source.getDocument();
        if (doc == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Source policy is unavailable to copy.");
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
        String baseText = doc.getSourceText() == null || doc.getSourceText().isBlank()
                ? "STRUCTURED_POLICY_COPY provenance for " + sourceName
                : doc.getSourceText();
        String cloneText = baseText + "\n\n<!-- COPIED_FROM documentId="
                + sourceDocumentId + " name=" + sourceName + " version=" + sourceVersion
                + " structuredClone=true -->\n";
        String createdBy = body != null && body.get("createdBy") != null
                ? String.valueOf(body.get("createdBy")) : "credit_manager";
        CiPolicyDocument newDoc = new PolicyDocumentService().create(
                doc.getTenantId(),
                newName,
                doc.getDocumentType() == null ? "TXT" : doc.getDocumentType(),
                cloneText,
                createdBy,
                doc.getOriginalFileReference(),
                Map.of(
                        "structuredClone", true,
                        "reingested", false,
                        "copiedFromDocumentId", sourceDocumentId.toString()));
        newDoc.setProductScope(doc.getProductScope());
        PolicyStudioSession created = StructuredPolicySessionCloner.cloneSession(
                source, newDoc, StructuredPolicySessionCloner.Mode.COPY);
        Map<String, Object> srcMeta = sessionMeta.getOrDefault(sourceDocumentId, Map.of());
        if (Boolean.TRUE.equals(srcMeta.get("createdFromScratch"))
                || "scratch".equals(srcMeta.get("kind"))) {
            purgeStubClassificationRules(created);
        }
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
        meta.put("createdFromScratch", srcMeta.get("createdFromScratch"));
        meta.put("structuredClone", true);
        meta.put("reingested", false);
        if (created.getDocument() != null && created.getDocument().getId() != null) {
            sessionMeta.put(created.getDocument().getId(), meta);
            orchestrator.persistence().saveSessionSnapshot(created);
        }
        Map<String, Object> view = toProspectView(created, meta);
        view.put("message", "New draft created (structured copy). Source policy was not changed.");
        view.put("enterWorkspace", true);
        view.put("defaultTab", "scope");
        view.put("copiedFromLabel", meta.get("copiedFromLabel"));
        view.put("structuredClone", true);
        view.put("reingested", false);
        return view;
    }

    /**
     * Existing policies for landing — durable {@code ci_policy_document} membership
     * (POLICY-STUDIO-DURABLE-LANDING-LIST-1). In-memory sessions enrich only.
     */
    public List<Map<String, Object>> listExistingPolicies() {
        return durableLandingListService.list(
                properties.getDefaultTenantId(),
                orchestrator.persistence(),
                lifecycleService,
                sessionMeta);
    }

    public Map<String, Object> build(String kind) {
        return build(kind, false);
    }

    /**
     * Open demo policy. By default resumes the latest resolved Banking/Bureau/KYC demo
     * for this kind (POLICY-RESOLUTION-PERSISTENCE-P0). Pass forceFresh via resetDemo to reseed.
     */
    public Map<String, Object> build(String kind, boolean forceFresh) {
        DemoSpec spec = resolveDemoSpec(kind);
        String text = loadClasspathText(spec.resource());
        UUID tenantId = properties.getDefaultTenantId();

        if (!forceFresh) {
            Map<String, Object> resumed = tryResumeDemo(spec, text);
            if (resumed != null) {
                return resumed;
            }
        }

        PolicyStudioSession session = orchestrator.processUpload(
                tenantId, spec.name(), "TXT", text, "staging-demo", spec.fileName());

        // After restart, memory is empty but durable overlays remain — rebind by identity.
        if (!forceFresh) {
            UUID priorId = orchestrator.persistence().durableResolutionStore().latestDemoDocumentId(spec.kind());
            if (priorId != null) {
                Map<String, Object> bundle = orchestrator.persistence().durableResolutionStore().loadBundle(priorId);
                if (!bundle.isEmpty()) {
                    com.los.core.creditintelligence.policystudio.parameters.PolicyResolutionIdentity
                            .rebind(session, bundle);
                    Object lineage = bundle.get("lineageRootId");
                    if (lineage != null && session.getDocument() != null) {
                        Map<String, Object> dm = session.getDocument().getMetadata();
                        if (dm == null) {
                            dm = new LinkedHashMap<>();
                            session.getDocument().setMetadata(dm);
                        }
                        dm.put("lineageRootId", String.valueOf(lineage));
                        dm.put("reboundFromDocumentId", String.valueOf(priorId));
                    }
                    log.info("policy-resolution-persistence rebound demoKind={} fromPriorDoc={} onto={}",
                            spec.kind(), priorId, session.getDocument().getId());
                }
            }
        }

        Map<String, Object> meta = demoMeta(spec.kind(), spec.fileName(), spec.resource(), text);
        if (session.getDocument() != null) {
            Map<String, Object> docMeta = session.getDocument().getMetadata();
            if (docMeta == null) {
                docMeta = new LinkedHashMap<>();
                session.getDocument().setMetadata(docMeta);
            }
            docMeta.put("kind", spec.kind());
            docMeta.put("demo", true);
            // Preserve lineage from rebound durable bundle when present
            if (!docMeta.containsKey("lineageRootId") || docMeta.get("lineageRootId") == null) {
                if (session.getDocument().getId() != null) {
                    docMeta.put("lineageRootId", session.getDocument().getId().toString());
                }
            }
        }
        orchestrator.persistence().saveSessionSnapshot(session);
        return toProspectView(session, meta);
    }

    private Map<String, Object> tryResumeDemo(DemoSpec spec, String text) {
        PolicyStudioSession best = null;
        Instant bestUpdated = Instant.EPOCH;
        for (PolicyStudioSession s : orchestrator.persistence().listAllSessions()) {
            if (s.getDocument() == null || s.getDocument().getMetadata() == null) continue;
            Map<String, Object> m = s.getDocument().getMetadata();
            if (!spec.kind().equals(String.valueOf(m.getOrDefault("kind", "")))) continue;
            if (!Boolean.TRUE.equals(m.get("demo"))) continue;
            Instant updated = s.getAuthoringSession() != null && s.getAuthoringSession().getLastUpdatedAt() != null
                    ? s.getAuthoringSession().getLastUpdatedAt() : Instant.EPOCH;
            if (updated.isAfter(bestUpdated)) {
                bestUpdated = updated;
                best = s;
            }
        }
        if (best == null) {
            UUID latest = orchestrator.persistence().durableResolutionStore().latestDemoDocumentId(spec.kind());
            if (latest != null) {
                best = orchestrator.persistence().loadSession(latest);
            }
        } else if (best.getDocument() != null && best.getDocument().getId() != null) {
            // Prefer cache/live instance so mutations persist consistently
            PolicyStudioSession live = orchestrator.persistence().loadSession(best.getDocument().getId());
            if (live != null) best = live;
        }
        if (best == null) return null;
        Map<String, Object> meta = demoMeta(spec.kind(), spec.fileName(), spec.resource(), text);
        meta.put("resumed", true);
        meta.put("resumedDocumentId", best.getDocument().getId().toString());
        log.info("policy-resolution-persistence resume demoKind={} documentId={}",
                spec.kind(), best.getDocument().getId());
        return toProspectView(best, meta);
    }

    /** Staging-only: restore Banking/Bureau demo to fixture baseline (intentional wipe). */
    public Map<String, Object> resetDemo(String kind) {
        DemoSpec spec = resolveDemoSpec(kind);
        log.info("staging-demo reset demo policy kind={} (force fresh — clears durable resume index)", spec.kind());
        orchestrator.persistence().durableResolutionStore().clearDemoLatest(spec.kind());
        return build(spec.kind(), true);
    }

    /** Golden helper — wipe in-memory sessions; durable overlays remain. */
    public void simulateProcessRestart() {
        orchestrator.persistence().simulateProcessRestart();
        log.info("policy-resolution-persistence simulateProcessRestart — in-memory cleared");
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
        Map<String, Object> stored = sessionMeta.get(documentId);
        Map<String, Object> meta;
        if (stored != null) {
            meta = new LinkedHashMap<>(stored);
        } else {
            meta = new LinkedHashMap<>();
            meta.put("kind", "session");
            meta.put("demo", false);
            meta.put("canResetDemo", false);
            String fileName = session.getDocument() == null
                    ? null : session.getDocument().getOriginalFileReference();
            if (fileName != null) {
                meta.put("fileName", fileName);
            }
        }
        return toProspectView(session, meta);
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
            case "IGNORE", "IGNORE_FOR_NOW", "IGNORE_FOR_AUTOMATION" -> {
                reviewState = ReviewState.REJECTED.name();
                if (reason == null) {
                    reason = "IGNORE_FOR_AUTOMATION".equals(action)
                            ? "Ignored for automation — retained as policy evidence, excluded from Needs Input"
                            : "Ignored for now — retained for draft, excluded from activation";
                }
            }
            case "KEEP_AS_POLICY_REQUIREMENT" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Kept as policy requirement — not an executable underwriting parameter";
                }
            }
            case "RECLASSIFY" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Business classification updated on policy draft";
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
            // POLICY-DATA-RESOLUTION-UX-1 — typed Data & Calculations definitions (session/draft only)
            case "RESOLVE_DATA_THRESHOLD", "RESOLVE_DATA_CLASSIFICATION",
                 "RESOLVE_DATA_CALCULATION", "RESOLVE_DATA_ADJUSTMENT",
                 "RESOLVE_DATA_MANUAL" -> {
                reviewState = ReviewState.CREDIT_MANAGER_APPROVED.name();
                if (reason == null) {
                    reason = "Data & Calculations resolution recorded on policy draft (session only)";
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
                case "IGNORE", "IGNORE_FOR_NOW", "IGNORE_FOR_AUTOMATION" -> {
                    if ("IGNORE_FOR_AUTOMATION".equals(action)) {
                        assertNonExecutableDispositionAllowed(rule, action);
                    }
                    String prevDisp = String.valueOf(meta.getOrDefault("disposition", ""));
                    meta.put("previousDisposition", prevDisp);
                    meta.put("disposition", "IGNORE_FOR_AUTOMATION".equals(action)
                            ? "IGNORE_FOR_AUTOMATION" : "IGNORED");
                    meta.put("businessDisposition", "IGNORE_FOR_AUTOMATION".equals(action)
                            ? "IGNORE_FOR_AUTOMATION" : "IGNORED");
                    meta.put("excludedFromActivation", true);
                    meta.put("deleted", false);
                    meta.put("NEEDS_INPUT", false);
                    meta.put("blockedReason", null);
                    if (reason != null) {
                        meta.put("dispositionReason", reason);
                    }
                    meta.put("dispositionChangedAt", Instant.now().toString());
                    meta.put("dispositionChangedBy", str(body, "reviewer", "credit_manager"));
                }
                case "KEEP_AS_POLICY_REQUIREMENT" -> {
                    assertNonExecutableDispositionAllowed(rule, action);
                    String prevKeep = String.valueOf(meta.getOrDefault("disposition", ""));
                    meta.put("previousDisposition", prevKeep);
                    meta.put("disposition", "KEEP_AS_POLICY_REQUIREMENT");
                    meta.put("businessDisposition", "KEEP_AS_POLICY_REQUIREMENT");
                    meta.put("excludedFromActivation", true);
                    meta.put("deleted", false);
                    meta.put("NEEDS_INPUT", false);
                    meta.put("blockedReason", null);
                    if (reason != null) {
                        meta.put("dispositionReason", reason);
                    }
                    meta.put("dispositionChangedAt", Instant.now().toString());
                    meta.put("dispositionChangedBy", str(body, "reviewer", "credit_manager"));
                }
                case "RECLASSIFY" -> {
                    String cls = str(body, "classification", null);
                    if (cls == null || cls.isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "classification is required for RECLASSIFY");
                    }
                    try {
                        IngestionMatchClassification.valueOf(cls.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Unsupported classification: " + cls);
                    }
                    String prevCls = String.valueOf(meta.getOrDefault("classification", ""));
                    meta.put("previousClassification", prevCls);
                    meta.put("classification", cls.trim().toUpperCase(Locale.ROOT));
                    meta.put("reclassifiedAt", Instant.now().toString());
                    meta.put("reclassifiedBy", str(body, "reviewer", "credit_manager"));
                    IngestionMatchClassification parsed = IngestionMatchClassification.valueOf(
                            cls.trim().toUpperCase(Locale.ROOT));
                    meta.put("dataRequirementOnly", parsed == IngestionMatchClassification.DATA_REQUIREMENT
                            || parsed == IngestionMatchClassification.REPORT_FIELD
                            || parsed == IngestionMatchClassification.DOCUMENT_REQUIREMENT);
                    meta.put("metricAdjustment", parsed == IngestionMatchClassification.METRIC_ADJUSTMENT);
                    meta.put("classificationOnly", parsed == IngestionMatchClassification.NARRATIVE
                            || parsed == IngestionMatchClassification.AMBIGUOUS);
                }
                case "DELETE", "EXCLUDE" -> {
                    // Soft-delete session artefact only — never physical purge of protected history
                    meta.put("disposition", "DELETED");
                    meta.put("excludedFromActivation", true);
                    meta.put("deleted", true);
                    meta.put("NEEDS_INPUT", false);
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
                case "RESOLVE_DATA_THRESHOLD", "RESOLVE_DATA_CLASSIFICATION",
                     "RESOLVE_DATA_CALCULATION", "RESOLVE_DATA_ADJUSTMENT",
                     "RESOLVE_DATA_MANUAL" -> {
                    applyDataCalcResolution(session, rule, meta, action, body);
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
            // POLICY-RULE-AUTHORING-FIX-1 — plain-English / structured edit must rewrite the rule
            if ("EDIT".equals(action) && (body.get("businessRule") != null || body.get("parameterId") != null
                    || body.get("text") != null)) {
                Map<String, Object> authorBody = new LinkedHashMap<>(body);
                authorBody.put("replaceRuleId", ruleId.toString());
                if (body.get("businessRule") != null && body.get("text") == null) {
                    authorBody.put("text", body.get("businessRule"));
                    authorBody.putIfAbsent("mode", "DESCRIBE");
                }
                try {
                    Map<String, Object> confirmed = cmRuleAuthoringService.confirm(session, authorBody);
                    meta.putAll(rule.getMetadata() == null ? Map.of() : rule.getMetadata());
                    humanChanges.put("authoring", confirmed.get("message"));
                    humanChanges.put("preview", confirmed.get("preview"));
                } catch (ResponseStatusException ex) {
                    // Surface authoring failure — do not silently mark EDITED on CLASSIFICATION stub
                    throw ex;
                }
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
            // Token-safe EDI match — sys.contains("EDI") falsely matches MONTHLY_CREDITS
            boolean match = ("proposed_edi".equals(operandKey)
                    && (com.los.core.creditintelligence.policystudio.parameters.SystemRuleIdTokens
                    .hasProposedEdiToken(r.getSystemRuleId())
                    || String.valueOf(r.getExpression()).toLowerCase(java.util.Locale.ROOT)
                    .contains("proposed_edi")))
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

    /**
     * POLICY-DATA-CALC-FUNCTIONAL-COMPLETION-1 / BANKING-BRE-FINAL-CLOSURE-1 —
     * preview EMI Bounce Count or ADB bulk adjustment on staging fixture / supplied txns.
     * Uses the same calculator Policy Test / runtime binding uses.
     */
    public Map<String, Object> previewDataCalculation(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = requireSession(documentId, tenantHeader);
        String dataItemId = body == null ? "" : String.valueOf(body.getOrDefault("dataItemId",
                body.getOrDefault("parameterId", "banking.emi_bounce_count_3m")));
        java.time.LocalDate asOf = java.time.LocalDate.of(2026, 8, 1);
        if (body != null && body.get("asOf") != null) {
            try {
                asOf = java.time.LocalDate.parse(String.valueOf(body.get("asOf")));
            } catch (Exception ignored) {
                // keep fixture asOf
            }
        }

        boolean adbBulk = dataItemId.contains("adb") || dataItemId.contains("bulk")
                || dataItemId.contains("avg_daily")
                || "banking.adb_bulk_deposit_adjustment".equals(dataItemId);
        if (adbBulk) {
            var cfg = com.los.core.creditintelligence.policystudio.metrics
                    .AdbBulkDepositAdjustmentCalculator.Config.fromBody(body == null ? Map.of() : body);
            List<com.los.core.creditintelligence.policystudio.metrics.AdbBulkDepositAdjustmentCalculator.Txn> txns =
                    com.los.core.creditintelligence.policystudio.metrics
                            .AdbBulkDepositAdjustmentCalculator.stagingFixture();
            if (body != null && body.get("transactions") instanceof List<?> rawList) {
                List<com.los.core.creditintelligence.policystudio.metrics
                        .AdbBulkDepositAdjustmentCalculator.Txn> parsed = new ArrayList<>();
                for (Object o : rawList) {
                    if (!(o instanceof Map<?, ?> m)) continue;
                    try {
                        parsed.add(new com.los.core.creditintelligence.policystudio.metrics
                                .AdbBulkDepositAdjustmentCalculator.Txn(
                                java.time.LocalDate.parse(String.valueOf(m.get("date"))),
                                m.get("narration") == null ? null : String.valueOf(m.get("narration")),
                                m.get("direction") == null ? "CREDIT" : String.valueOf(m.get("direction")),
                                m.get("amount") == null ? null : new BigDecimal(String.valueOf(m.get("amount"))),
                                m.get("category") == null ? null : String.valueOf(m.get("category")),
                                Boolean.parseBoolean(String.valueOf(
                                        m.get("classified") == null ? Boolean.TRUE : m.get("classified"))),
                                m.get("balanceAfter") == null ? null
                                        : new BigDecimal(String.valueOf(m.get("balanceAfter"))),
                                m.get("duplicateStatus") == null ? null : String.valueOf(m.get("duplicateStatus"))));
                    } catch (Exception ignored) {
                        // skip malformed row
                    }
                }
                if (!parsed.isEmpty()) txns = parsed;
            }
            Map<String, Object> result = com.los.core.creditintelligence.policystudio.metrics
                    .AdbBulkDepositAdjustmentCalculator.evaluate(txns, cfg, asOf);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("documentId", documentId.toString());
            out.put("dataItemId", "banking.adb_bulk_deposit_adjustment");
            out.put("policyName", session.getDocument() == null ? null : session.getDocument().getName());
            out.put("preview", result);
            out.put("fixture", body == null || !(body.get("transactions") instanceof List<?>));
            out.put("binding", com.los.core.creditintelligence.policystudio.metrics
                    .AdbBulkDepositAdjustmentCalculator.BINDING);
            out.put("samePathAsPolicyTest", true);
            out.put("allowCanonicalAuthority", false);
            out.put("gacatMutated", false);
            out.put("message", "Preview uses AdbBulkDepositAdjustmentCalculator.V1 — same path as Policy Test");
            return out;
        }

        var cfg = com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.Config
                .fromBody(body == null ? Map.of() : body);
        List<com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.Txn> txns =
                com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.stagingFixture();
        // Optional override from body.transactions for advanced callers
        if (body != null && body.get("transactions") instanceof List<?> rawList) {
            List<com.los.core.creditintelligence.policystudio.metrics.EmiBounceCountCalculator.Txn> parsed =
                    new ArrayList<>();
            for (Object o : rawList) {
                if (!(o instanceof Map<?, ?> m)) continue;
                try {
                    parsed.add(new com.los.core.creditintelligence.policystudio.metrics
                            .EmiBounceCountCalculator.Txn(
                            java.time.LocalDate.parse(String.valueOf(m.get("date"))),
                            m.get("narration") == null ? null : String.valueOf(m.get("narration")),
                            m.get("direction") == null ? "DEBIT" : String.valueOf(m.get("direction")),
                            m.get("amount") == null ? null : new BigDecimal(String.valueOf(m.get("amount"))),
                            m.get("category") == null ? null : String.valueOf(m.get("category")),
                            Boolean.parseBoolean(String.valueOf(
                                    m.get("classified") == null ? Boolean.TRUE : m.get("classified"))),
                            Boolean.parseBoolean(String.valueOf(
                                    m.get("emiFlag") == null ? Boolean.FALSE : m.get("emiFlag"))),
                            Boolean.parseBoolean(String.valueOf(
                                    m.get("bounceFlag") == null ? Boolean.FALSE : m.get("bounceFlag"))),
                            Boolean.parseBoolean(String.valueOf(
                                    m.get("returnFlag") == null ? Boolean.FALSE : m.get("returnFlag"))),
                            m.get("duplicateStatus") == null ? null : String.valueOf(m.get("duplicateStatus"))));
                } catch (Exception ignored) {
                    // skip malformed row
                }
            }
            if (!parsed.isEmpty()) txns = parsed;
        }
        Map<String, Object> result = com.los.core.creditintelligence.policystudio.metrics
                .EmiBounceCountCalculator.evaluate(txns, cfg, asOf);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("documentId", documentId.toString());
        out.put("dataItemId", dataItemId);
        out.put("policyName", session.getDocument() == null ? null : session.getDocument().getName());
        out.put("preview", result);
        out.put("fixture", body == null || !(body.get("transactions") instanceof List<?>));
        out.put("binding", com.los.core.creditintelligence.policystudio.metrics
                .EmiBounceCountCalculator.BINDING);
        out.put("samePathAsPolicyTest", true);
        out.put("allowCanonicalAuthority", false);
        out.put("gacatMutated", false);
        out.put("message", "Preview uses EmiBounceCountCalculator.V1 — same path as Policy Test overlays");
        return out;
    }

    /**
     * POLICY-DATA-RESOLUTION-UX-1 — persist typed Data & Calculations definition on policy document.
     * Does not mutate GACAT. Does not invent executable metrics.
     */
    private void applyDataCalcResolution(
            PolicyStudioSession session,
            CiPolicyRuleCandidate rule,
            Map<String, Object> meta,
            String action,
            Map<String, Object> body) {
        String dataItemId = body.get("dataItemId") == null
                ? String.valueOf(body.getOrDefault("parameterId",
                body.getOrDefault("targetParameterId", "")))
                : String.valueOf(body.get("dataItemId"));
        if (dataItemId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dataItemId required");
        }
        String actor = body.get("actor") == null
                ? str(body, "reviewer", "credit_manager") : String.valueOf(body.get("actor"));
        String documentId = session.getDocument() == null || session.getDocument().getId() == null
                ? null : session.getDocument().getId().toString();
        Map<String, Object> resolution;
        try {
            resolution = switch (action) {
                case "RESOLVE_DATA_THRESHOLD" -> {
                    java.math.BigDecimal amt = body.get("amountInr") == null
                            ? null : new java.math.BigDecimal(String.valueOf(body.get("amountInr"))
                            .replace(",", "").replace("₹", "").trim());
                    yield com.los.core.creditintelligence.policystudio.parameters
                            .PolicyDataResolutionSupport.thresholdDefinition(
                                    dataItemId, amt,
                                    body.get("notes") == null ? null : String.valueOf(body.get("notes")),
                                    actor, documentId);
                }
                case "RESOLVE_DATA_CLASSIFICATION" -> com.los.core.creditintelligence.policystudio.parameters
                        .PolicyDataResolutionSupport.classificationDefinition(
                                dataItemId,
                                body.get("identificationMethod") == null ? null
                                        : String.valueOf(body.get("identificationMethod")),
                                body.get("notes") == null ? null : String.valueOf(body.get("notes")),
                                actor, documentId);
                case "RESOLVE_DATA_CALCULATION" -> com.los.core.creditintelligence.policystudio.parameters
                        .PolicyDataResolutionSupport.calculationConfiguration(
                                dataItemId, actor, documentId, body == null ? Map.of() : body);
                case "RESOLVE_DATA_ADJUSTMENT" -> {
                    java.math.BigDecimal mult = body.get("multiple") == null
                            ? new java.math.BigDecimal("10")
                            : new java.math.BigDecimal(String.valueOf(body.get("multiple")).trim());
                    String adjKey = dataItemId.contains("avg_daily") || dataItemId.contains("adb")
                            || dataItemId.contains("bulk")
                            ? "banking.adb_bulk_deposit_adjustment" : dataItemId;
                    yield com.los.core.creditintelligence.policystudio.parameters
                            .PolicyDataResolutionSupport.policyAdjustment(
                                    adjKey, mult, actor, documentId, body == null ? Map.of() : body);
                }
                case "RESOLVE_DATA_MANUAL" -> com.los.core.creditintelligence.policystudio.parameters
                        .PolicyDataResolutionSupport.manualInput(
                                dataItemId,
                                body.get("manualInputLabel") == null ? null
                                        : String.valueOf(body.get("manualInputLabel")),
                                body.get("manualInputType") == null ? null
                                        : String.valueOf(body.get("manualInputType")),
                                body.get("requiredActor") == null ? actor
                                        : String.valueOf(body.get("requiredActor")),
                                body.get("captureStage") == null ? null
                                        : String.valueOf(body.get("captureStage")),
                                documentId);
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported data resolve");
            };
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        String stampKey = String.valueOf(resolution.getOrDefault("dataItemId", dataItemId));
        meta.put(com.los.core.creditintelligence.policystudio.parameters
                .PolicyDataResolutionSupport.RULE_META_KEY, resolution);
        meta.put("disposition", "EDITED");
        meta.put("excludedFromActivation", false);
        boolean execReady = "READY".equals(String.valueOf(resolution.get("executionStatus")))
                || "INFORMATION_ONLY".equals(String.valueOf(resolution.get("executionStatus")))
                || "MANUAL_INPUT".equals(String.valueOf(resolution.get("executionStatus")));
        meta.put("NEEDS_INPUT", !execReady
                && !"INFORMATION_ONLY".equals(String.valueOf(resolution.get("executionStatus"))));
        com.los.core.creditintelligence.policystudio.parameters
                .PolicyDataResolutionSupport.stampOnDocument(session, stampKey, resolution);
        // Also stamp under canonical parameter id when adjustment key differs
        if (!stampKey.equals(dataItemId)) {
            com.los.core.creditintelligence.policystudio.parameters
                    .PolicyDataResolutionSupport.stampOnDocument(session, dataItemId, resolution);
        }
        rule.setMetadata(meta);
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
     * Credit Manager "+ Add rule" — CM authoring (preview/confirm) over registry + PolicyDsl.
     * Does not introduce a second rule engine.
     */
    public Map<String, Object> addPlainEnglishRule(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        UUID tenantId = resolveTenant(tenantHeader);
        PolicyStudioSession session = orchestrator.requireSession(documentId, tenantId);
        Map<String, Object> payload = body == null ? Map.of() : body;
        boolean confirm = Boolean.TRUE.equals(payload.get("confirm"))
                || "CONFIRM".equalsIgnoreCase(str(payload, "action", ""));
        if (!confirm) {
            Map<String, Object> preview = cmRuleAuthoringService.preview(payload);
            Map<String, Object> view = sessionView(documentId, tenantHeader);
            view.put("preview", preview);
            view.put("previewOnly", true);
            view.put("message", preview.get("message"));
            if (!Boolean.TRUE.equals(preview.get("complete"))) {
                view.put("authoringStatus", preview.get("needsResolver") != null
                        && Boolean.TRUE.equals(preview.get("needsResolver"))
                        ? "NEEDS_RESOLVER" : "INCOMPLETE");
            } else {
                view.put("authoringStatus", "READY_TO_CONFIRM");
            }
            return view;
        }
        Map<String, Object> confirmed = cmRuleAuthoringService.confirm(session, payload);
        orchestrator.reviewService().invalidateCheckerApproval(session, "cm-rule-authoring-add");
        orchestrator.persistence().saveSessionSnapshot(session);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.putAll(confirmed);
        view.put("addedRuleCount", 1);
        return view;
    }

    public Map<String, Object> authoringSources(String tenantHeader) {
        Map<String, Object> out = cmRuleAuthoringService.sources();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    public Map<String, Object> previewAuthoredRule(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        requireSession(documentId, tenantHeader);
        Map<String, Object> preview = cmRuleAuthoringService.preview(body == null ? Map.of() : body);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        out.put("preview", preview);
        out.put("message", preview.get("message"));
        out.put("allowCanonicalAuthority", false);
        return out;
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

    /**
     * POLICY-STUDIO-UX-CLOSURE-1 — hard-delete never-activated DRAFT by documentId.
     */
    public Map<String, Object> deleteDraftPolicy(UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession session = requireSession(documentId, tenantHeader);
        Map<String, Object> result = requireLifecycle().deleteDraftPolicy(session, body == null ? Map.of() : body);
        sessionMeta.remove(documentId);
        Map<String, Object> out = new LinkedHashMap<>(result);
        out.put("existingPolicies", listExistingPolicies());
        out.put("allowCanonicalAuthority", false);
        return out;
    }

    /**
     * IGNORE_FOR_AUTOMATION / KEEP_AS_POLICY_REQUIREMENT must not silently clear blockers on
     * genuine executable underwriting dependencies. Documentary / narrative / non-UW taxonomy may proceed.
     */
    private static void assertNonExecutableDispositionAllowed(CiPolicyRuleCandidate rule, String action) {
        if (rule == null) {
            return;
        }
        Map<String, Object> meta = rule.getMetadata() == null ? Map.of() : rule.getMetadata();
        if (Boolean.TRUE.equals(meta.get("dataRequirementOnly"))
                || Boolean.TRUE.equals(meta.get("metricAdjustment"))
                || Boolean.TRUE.equals(meta.get("classificationOnly"))) {
            return;
        }
        String cls = String.valueOf(meta.getOrDefault("classification", ""));
        try {
            IngestionMatchClassification parsed = IngestionMatchClassification.valueOf(cls);
            if (!parsed.underwritingExecutable()) {
                return;
            }
        } catch (Exception ignored) {
            // unknown classification — fall through to readiness check
        }
        if (PolicyExecutionReadiness.isIncludedExecutableRule(rule)
                && !PolicyExecutionReadiness.isExecutionReady(rule)) {
            throw new BusinessRuleException(
                    "This item is a required executable dependency and cannot be set to " + action
                            + " to bypass readiness. Resolve / Configure it, or reclassify if it is "
                            + "documentary / narrative content.",
                    "POLICY_DISPOSITION_NOT_ALLOWED",
                    "Use RESOLVE / CONFIGURE for executable dependencies, or RECLASSIFY if mis-typed",
                    Map.of(
                            "ruleId", rule.getId() == null ? "" : rule.getId().toString(),
                            "uiAction", action,
                            "executionBlockers", PolicyExecutionReadiness.executionBlockersForRule(rule)));
        }
    }

    /**
     * Staging-only: stamp ACTIVE so Create New Version can be exercised without full maker-checker.
     * Does not grant production authority. Not a lifecycle redesign.
     */
    public Map<String, Object> stampActiveForVersioning(
            UUID documentId, Map<String, Object> body, String tenantHeader) {
        if (!properties.getStagingDemo().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staging demo disabled");
        }
        PolicyStudioSession session = requireSession(documentId, tenantHeader);
        Map<String, Object> result = requireLifecycle().stampActiveForVersioningDemo(session, body == null ? Map.of() : body);
        orchestrator.persistence().saveSessionSnapshot(session);
        Map<String, Object> view = sessionView(documentId, tenantHeader);
        view.put("lifecycle", result);
        view.put("message", result.get("message"));
        view.put("allowCanonicalAuthority", false);
        return view;
    }

    public Map<String, Object> createLifecycleVersion(UUID documentId, Map<String, Object> body, String tenantHeader) {
        PolicyStudioSession source = requireSession(documentId, tenantHeader);
        Map<String, Object> result = requireLifecycle().createNewVersion(source, body);
        Object newId = result.get("documentId");
        if (newId != null) {
            Map<String, Object> srcMeta = sessionMeta.getOrDefault(documentId, Map.of());
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("kind", "version");
            meta.put("clonedFrom", documentId.toString());
            meta.put("demo", false);
            meta.put("structuredClone", true);
            meta.put("reingested", false);
            meta.put("createdFromScratch", srcMeta.get("createdFromScratch"));
            meta.put("previousVersionLabel", result.get("lineage") instanceof Map<?, ?> lin
                    ? lin.get("from") : null);
            sessionMeta.put(UUID.fromString(String.valueOf(newId)), meta);
            Map<String, Object> view = sessionView(UUID.fromString(String.valueOf(newId)), tenantHeader);
            view.put("lifecycle", result);
            view.put("message", result.get("message"));
            view.put("lineage", result.get("lineage"));
            view.put("structuredClone", true);
            view.put("reingested", false);
            view.put("underwritingRuleCount", result.get("underwritingRuleCount"));
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
        overlayAuthoritativeScorecardLinkage(session);
        Map<String, Object> out = new LinkedHashMap<>();
        StagingDemoWorkspaceService.stampSafety(out);
        ProspectPolicyViewBuilder.enrich(out, session, meta);
        enrichLinkedScorecardIdentity(out, session);
        if (lifecycleService != null && session.getDocument() != null) {
            try {
                Map<String, Object> life = lifecycleService.settingsView(session);
                out.put("lifecycle", life);
                out.put("policySettings", life.get("policySettings"));
                out.put("implementationStatus", life.get("implementationStatus"));
                out.put("readyToSchedule", life.get("readyToSchedule"));
                out.put("businessLifecycleStatus", life.get("businessStatus"));
                out.put("currentExecutionReadiness", life.get("currentExecutionReadiness"));
                out.put("contentEditable", life.get("contentEditable"));
                out.put("lifecycleAuthority", life.get("lifecycleAuthority"));
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

    /**
     * Durable {@code ci_policy_document.scorecard_id} wins over stale session/snapshot values.
     */
    private void overlayAuthoritativeScorecardLinkage(PolicyStudioSession session) {
        if (session == null || session.getDocument() == null || session.getDocument().getId() == null
                || documentRepository == null) {
            return;
        }
        try {
            documentRepository.findById(session.getDocument().getId()).ifPresent(durable ->
                    PolicyVersionScorecardLinkage.overlayFromDurableDocument(session.getDocument(), durable));
        } catch (Exception e) {
            log.debug("scorecard linkage overlay skipped: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Resolve scorecard identity by the canonical document FK only — never by
     * {@code underwriting_scorecards.policy_document_id}.
     */
    @SuppressWarnings("unchecked")
    private void enrichLinkedScorecardIdentity(Map<String, Object> out, PolicyStudioSession session) {
        Object headerObj = out.get("policyHeader");
        if (!(headerObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> header = (Map<String, Object>) headerObj;
        CiPolicyDocument doc = session == null ? null : session.getDocument();
        UUID canonical = doc == null ? null : doc.getScorecardId();
        PolicyVersionScorecardLinkage.ScorecardIdentity identity = null;
        if (canonical != null && scorecardRepository != null) {
            try {
                UnderwritingScorecard found = scorecardRepository.findById(canonical).orElse(null);
                if (found != null) {
                    identity = new PolicyVersionScorecardLinkage.ScorecardIdentity(
                            found.getId(),
                            found.getName(),
                            found.getStatus(),
                            found.getScoringMode());
                }
            } catch (Exception e) {
                log.debug("scorecard identity lookup skipped: {}", e.getClass().getSimpleName());
            }
        }
        PolicyVersionScorecardLinkage.applyProjection(header, doc, identity);
        out.put("scorecardId", header.get("scorecardId"));
        out.put("scorecardName", header.get("scorecardName"));
        out.put("scorecardStatus", header.get("scorecardStatus"));
        out.put("scorecardScoringMode", header.get("scorecardScoringMode"));
        out.put("scorecardLinked", header.get("scorecardLinked"));
        out.put("scorecardLinkageKnown", header.get("scorecardLinkageKnown"));
        out.put("scorecardLinkageAuthority", header.get("scorecardLinkageAuthority"));
        out.put("scorecardLinkOwnerType", header.get("scorecardLinkOwnerType"));
        out.put("scorecardLinkOwnerId", header.get("scorecardLinkOwnerId"));
        out.put("scorecardLinkOwnerVersion", header.get("scorecardLinkOwnerVersion"));
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
