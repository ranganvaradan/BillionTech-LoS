package com.los.core.creditintelligence.policystudio.service;

import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import com.los.core.creditintelligence.policystudio.catalogue.CapabilityIngestionBindingService;
import com.los.core.creditintelligence.policystudio.domain.AmbiguityResolutionAction;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyDocument;
import com.los.core.creditintelligence.policystudio.domain.CiPolicyMetricCandidate;
import com.los.core.creditintelligence.policystudio.domain.DocumentStatus;
import com.los.core.creditintelligence.policystudio.domain.ReviewState;
import com.los.core.creditintelligence.policystudio.dsl.PolicyDslSchemaValidator;
import com.los.core.creditintelligence.policystudio.model.PolicyStudioSession;
import com.los.core.creditintelligence.validation.service.PolicyAuthoringRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * document → extract → interpret → map → ambiguities → rules/metrics → tests → completeness → persist
 */
@Service
public class PolicyStudioOrchestrator {

    private final CreditIntelligenceProperties properties;
    private final PolicyDocumentService documentService;
    private final PolicyClauseExtractor clauseExtractor;
    private final PolicyInterpretationProvider interpretationProvider;
    private final PolicyAuthoringRegistry registry;
    private final CanonicalMappingService mappingService;
    private final AmbiguityDetector ambiguityDetector;
    private final MetricCandidateFactory metricCandidateFactory;
    private final RuleCandidateFactory ruleCandidateFactory;
    private final PolicyTestCaseGenerator testCaseGenerator;
    private final PolicyConflictDetector conflictDetector;
    private final PolicyCompletenessAnalyzer completenessAnalyzer;
    private final DraftPolicyPackageBuilder draftPackageBuilder;
    private final DraftPolicySimulator simulator;
    private final PolicyReviewService reviewService;
    private final PolicyStudioPersistenceService persistenceService;
    private final PolicyVocabularyService vocabularyService;
    private final PolicyAuthoringProgressScorer progressScorer;
    private final PolicyDependencyGraphBuilder dependencyGraphBuilder;
    private final PolicyPreviewService previewService;
    private final DraftPolicyDiffService diffService;
    private final PolicyDslSchemaValidator dslValidator;
    private final PolicyStudioDashboardReadModel dashboardReadModel;
    private CapabilityIngestionBindingService ingestionBindingService;

    @Autowired(required = false)
    public void setIngestionBindingService(CapabilityIngestionBindingService ingestionBindingService) {
        this.ingestionBindingService = ingestionBindingService;
    }

    public PolicyStudioOrchestrator(
            CreditIntelligenceProperties properties,
            PolicyDocumentService documentService,
            PolicyClauseExtractor clauseExtractor,
            DeterministicGoldenInterpretationProvider interpretationProvider,
            PolicyAuthoringRegistry registry,
            CanonicalMappingService mappingService,
            AmbiguityDetector ambiguityDetector,
            MetricCandidateFactory metricCandidateFactory,
            RuleCandidateFactory ruleCandidateFactory,
            PolicyTestCaseGenerator testCaseGenerator,
            PolicyConflictDetector conflictDetector,
            PolicyCompletenessAnalyzer completenessAnalyzer,
            DraftPolicyPackageBuilder draftPackageBuilder,
            DraftPolicySimulator simulator,
            PolicyReviewService reviewService) {
        this(properties, documentService, clauseExtractor, interpretationProvider, registry, mappingService,
                ambiguityDetector, metricCandidateFactory, ruleCandidateFactory, testCaseGenerator,
                conflictDetector, completenessAnalyzer, draftPackageBuilder, simulator, reviewService,
                null, null, null, null, null, null, null, null);
    }

    public PolicyStudioOrchestrator(
            CreditIntelligenceProperties properties,
            PolicyDocumentService documentService,
            PolicyClauseExtractor clauseExtractor,
            DeterministicGoldenInterpretationProvider interpretationProvider,
            PolicyAuthoringRegistry registry,
            CanonicalMappingService mappingService,
            AmbiguityDetector ambiguityDetector,
            MetricCandidateFactory metricCandidateFactory,
            RuleCandidateFactory ruleCandidateFactory,
            PolicyTestCaseGenerator testCaseGenerator,
            PolicyConflictDetector conflictDetector,
            PolicyCompletenessAnalyzer completenessAnalyzer,
            DraftPolicyPackageBuilder draftPackageBuilder,
            DraftPolicySimulator simulator,
            PolicyReviewService reviewService,
            PolicyStudioPersistenceService persistenceService,
            PolicyVocabularyService vocabularyService,
            PolicyAuthoringProgressScorer progressScorer,
            PolicyDependencyGraphBuilder dependencyGraphBuilder,
            PolicyPreviewService previewService,
            DraftPolicyDiffService diffService,
            PolicyDslSchemaValidator dslValidator,
            PolicyStudioDashboardReadModel dashboardReadModel) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.documentService = documentService != null ? documentService : new PolicyDocumentService();
        this.clauseExtractor = clauseExtractor != null ? clauseExtractor : new PolicyClauseExtractor();
        this.interpretationProvider = interpretationProvider != null
                ? interpretationProvider : new DeterministicGoldenInterpretationProvider();
        this.registry = registry != null ? registry : new PolicyAuthoringRegistry();
        this.mappingService = mappingService != null ? mappingService : new CanonicalMappingService();
        this.ambiguityDetector = ambiguityDetector != null ? ambiguityDetector : new AmbiguityDetector();
        this.metricCandidateFactory = metricCandidateFactory != null ? metricCandidateFactory : new MetricCandidateFactory();
        this.ruleCandidateFactory = ruleCandidateFactory != null ? ruleCandidateFactory : new RuleCandidateFactory();
        this.testCaseGenerator = testCaseGenerator != null ? testCaseGenerator : new PolicyTestCaseGenerator();
        this.conflictDetector = conflictDetector != null ? conflictDetector : new PolicyConflictDetector();
        this.completenessAnalyzer = completenessAnalyzer != null ? completenessAnalyzer : new PolicyCompletenessAnalyzer();
        this.persistenceService = persistenceService != null ? persistenceService : new PolicyStudioPersistenceService();
        this.vocabularyService = vocabularyService != null ? vocabularyService : new PolicyVocabularyService();
        this.progressScorer = progressScorer != null ? progressScorer : new PolicyAuthoringProgressScorer();
        this.dependencyGraphBuilder = dependencyGraphBuilder != null ? dependencyGraphBuilder : new PolicyDependencyGraphBuilder();
        this.previewService = previewService != null ? previewService : new PolicyPreviewService();
        this.diffService = diffService != null ? diffService : new DraftPolicyDiffService(this.persistenceService);
        this.dslValidator = dslValidator != null ? dslValidator : new PolicyDslSchemaValidator(this.registry);
        this.draftPackageBuilder = draftPackageBuilder != null
                ? draftPackageBuilder
                : new DraftPolicyPackageBuilder(this.dependencyGraphBuilder, this.dslValidator, this.persistenceService);
        this.simulator = simulator != null ? simulator : new DraftPolicySimulator(this.persistenceService);
        this.reviewService = reviewService != null
                ? reviewService
                : new PolicyReviewService(this.properties, this.vocabularyService, this.persistenceService, this.testCaseGenerator);
        this.dashboardReadModel = dashboardReadModel != null
                ? dashboardReadModel
                : new PolicyStudioDashboardReadModel(new PolicyStudioReadModel(), this.progressScorer);
    }

    public PolicyStudioOrchestrator() {
        this(new CreditIntelligenceProperties(), null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public void assertEnabled() {
        if (!properties.getPolicyStudio().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Policy Studio disabled");
        }
    }

    public PolicyStudioSession processUpload(
            UUID tenantId,
            String name,
            String documentType,
            String sourceText,
            String uploadedBy,
            String originalFileReference) {
        CiPolicyDocument doc = documentService.create(
                tenantId, name, documentType, sourceText, uploadedBy, originalFileReference, Map.of());
        return processDocument(doc);
    }

    public PolicyStudioSession processDocument(CiPolicyDocument doc) {
        PolicyStudioSession session = new PolicyStudioSession();
        session.setDocument(doc);
        doc.setStatus(DocumentStatus.PARSING.name());

        var kind = clauseExtractor.detectKind(doc.getSourceText());
        var clauses = clauseExtractor.extract(doc.getId(), doc.getSourceText());
        session.getClauses().addAll(clauses);
        doc.setStatus(DocumentStatus.PARSED.name());

        doc.setStatus(DocumentStatus.INTERPRETING.name());
        var interpretations = interpretationProvider.interpret(clauses, registry, kind);
        session.getInterpretations().addAll(interpretations);

        session.getMappings().addAll(mappingService.map(clauses, interpretations));
        session.getAmbiguities().addAll(ambiguityDetector.detect(clauses, kind));
        session.getMetricCandidates().addAll(metricCandidateFactory.create(clauses, kind));
        session.getRuleCandidates().addAll(ruleCandidateFactory.create(doc, clauses, interpretations, kind));
        // POLICY-UX-2D — bind clauses to existing catalogue capabilities; golden becomes fallback hint
        CapabilityIngestionBindingService binder = ingestionBindingService != null
                ? ingestionBindingService
                : new CapabilityIngestionBindingService();
        Map<String, Object> ingestionSummary = binder.bind(session);
        for (var rule : session.getRuleCandidates()) {
            if (rule.getExpression() != null
                    && "CLASSIFICATION".equalsIgnoreCase(String.valueOf(rule.getExpression().get("op")))) {
                continue; // non-executable classification cards
            }
            var vr = dslValidator.validateRuleExpression(rule.getExpression(), rule.getRuleType(), rule.getOnMissing());
            rule.setValidationErrors(new java.util.ArrayList<>(vr.errors()));
        }
        session.getTestCases().addAll(testCaseGenerator.generate(session.getRuleCandidates().stream()
                .filter(r -> r.getExpression() == null
                        || !"CLASSIFICATION".equalsIgnoreCase(String.valueOf(r.getExpression().get("op"))))
                .toList()));
        session.getConflicts().clear();
        session.getConflicts().addAll(conflictDetector.detect(session.getRuleCandidates()));

        var completeness = completenessAnalyzer.analyze(
                clauses, clauses, session.getMappings(), session.getAmbiguities(),
                session.getMetricCandidates(), session.getRuleCandidates(),
                session.getTestCases(), session.getConflicts());
        session.setCompleteness(completeness);
        session.setDependencyGraph(dependencyGraphBuilder.build(
                session.getMetricCandidates(), session.getRuleCandidates()));
        session.setReadiness(progressScorer.score(session));
        Map<String, Object> preview = new LinkedHashMap<>(previewService.preview(session));
        if (ingestionSummary != null && !ingestionSummary.isEmpty()) {
            preview.put("ingestionBinding", ingestionSummary);
        }
        session.setPreview(preview);

        long open = session.getAmbiguities().stream().filter(a -> "OPEN".equals(a.getResolutionStatus())).count();
        doc.setStatus(open > 0 ? DocumentStatus.REVIEW_REQUIRED.name() : DocumentStatus.DRAFT_READY.name());

        persistenceService.saveSessionSnapshot(session);
        return session;
    }

    public PolicyStudioSession requireSession(UUID documentId) {
        return persistenceService.requireSession(documentId);
    }

    public PolicyStudioSession requireSession(UUID documentId, UUID tenantId) {
        return persistenceService.requireSessionForTenant(documentId, tenantId);
    }

    public PolicyStudioPersistenceService persistence() {
        return persistenceService;
    }

    public Map<String, Object> buildDraft(UUID documentId, String createdBy) {
        PolicyStudioSession session = requireSession(documentId);
        // Soft build preserves DRAFT_ONLY handoff even while BLOCKED; strictBuildDraft enforces gates.
        var pkg = draftPackageBuilder.buildSoft(session, createdBy);
        simulator.simulate(session);
        session.setReadiness(progressScorer.score(session));
        persistenceService.saveSessionSnapshot(session);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draftPackageId", pkg.getId());
        result.put("packageStatus", pkg.getPackageStatus());
        result.put("packageVersion", pkg.getPackageVersion());
        result.put("productionActive", false);
        result.put("contentHash", pkg.getContentHash());
        result.put("completeness", session.getCompleteness());
        result.put("readiness", session.getReadiness());
        result.put("simulation", session.getSimulation());
        result.put("rejectReasons", draftPackageBuilder.rejectionReasons(session));
        return result;
    }

    public Map<String, Object> strictBuildDraft(UUID documentId, String createdBy) {
        PolicyStudioSession session = requireSession(documentId);
        var pkg = draftPackageBuilder.build(session, createdBy);
        simulator.simulate(session);
        persistenceService.saveSessionSnapshot(session);
        return Map.of(
                "draftPackageId", pkg.getId(),
                "packageStatus", pkg.getPackageStatus(),
                "productionActive", false,
                "contentHash", pkg.getContentHash(),
                "readiness", session.getReadiness()
        );
    }

    public Map<String, Object> reInterpret(UUID documentId) {
        PolicyStudioSession session = requireSession(documentId);
        var kind = clauseExtractor.detectKind(session.getDocument().getSourceText());
        session.getInterpretations().clear();
        session.getInterpretations().addAll(
                interpretationProvider.interpret(session.getClauses(), registry, kind));
        // Propose prior vocabulary mappings first — do not auto-finalize
        for (var amb : session.getAmbiguities()) {
            if (amb.getPhrase() != null) {
                var prior = vocabularyService.proposePriorMappings(
                        session.getDocument().getTenantId(),
                        session.getDocument().getProductScope(),
                        amb.getPhrase());
                if (!prior.isEmpty() && "OPEN".equals(amb.getResolutionStatus())) {
                    Map<String, Object> meta = new LinkedHashMap<>(
                            amb.getMetadata() == null ? Map.of() : amb.getMetadata());
                    meta.put("proposedVocabulary", prior.get(0).getCanonicalPath());
                    meta.put("autoFinalize", false);
                    amb.setMetadata(meta);
                    if (amb.getRecommendedOption() == null) {
                        amb.setRecommendedOption(prior.get(0).getCanonicalPath());
                    }
                }
            }
        }
        session.setReadiness(progressScorer.score(session));
        persistenceService.saveSessionSnapshot(session);
        return Map.of("documentId", documentId, "interpretations", session.getInterpretations().size(),
                "readiness", session.getReadiness());
    }

    @SuppressWarnings("unchecked")
    public CiPolicyMetricCandidate createCustomMetric(UUID documentId, Map<String, Object> body) {
        PolicyStudioSession session = requireSession(documentId);
        Map<String, Object> expression = body.get("expression") instanceof Map<?, ?>
                ? (Map<String, Object>) body.get("expression") : Map.of();
        if (!BusinessMeasureDesignerService.isExecutableExpression(expression)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Executable calculation required — empty or NEW_METRIC_CANDIDATE placeholders are not measures");
        }
        String governance = String.valueOf(body.getOrDefault("governanceStatus", "PROPOSED"));
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("custom", true);
        meta.put("businessMeasure", true);
        meta.put("governanceStatus", governance);
        meta.put("executable", true);
        if (body.get("businessDefinition") instanceof Map<?, ?> bd) {
            meta.put("businessDefinition", bd);
        }
        if (body.get("classification") != null) {
            meta.put("classification", String.valueOf(body.get("classification")));
        }
        // Only APPROVED measures count as AVAILABLE for implementability
        meta.put("availability", "APPROVED".equalsIgnoreCase(governance) ? "AVAILABLE" : "CANDIDATE");
        if (body.get("missingDataBehaviour") != null) {
            meta.put("missingDataBehaviour", String.valueOf(body.get("missingDataBehaviour")));
        }

        CiPolicyMetricCandidate m = CiPolicyMetricCandidate.builder()
                .id(UUID.randomUUID())
                .clauseId(body.get("clauseId") == null ? session.getClauses().get(0).getId()
                        : UUID.fromString(String.valueOf(body.get("clauseId"))))
                .metricName(String.valueOf(body.getOrDefault("metricName", "CUSTOM_METRIC")))
                .candidateCanonicalCode(String.valueOf(body.getOrDefault("code", "CUSTOM_METRIC")))
                .systemMetricId(String.valueOf(body.getOrDefault("systemMetricId",
                        body.getOrDefault("code", "CUSTOM_METRIC"))))
                .expression(expression)
                .exclusions(body.get("exclusions") instanceof List<?> l ? (List<Object>) l : List.of())
                .inclusions(body.get("inclusions") instanceof List<?> l ? (List<Object>) l : List.of())
                .period(body.get("period") == null ? null : String.valueOf(body.get("period")))
                .aggregation(body.get("aggregation") == null ? null : String.valueOf(body.get("aggregation")))
                .missingDataPolicy(String.valueOf(body.getOrDefault("missingDataPolicy", "DATA_INSUFFICIENT")))
                .reviewStatus("APPROVED".equalsIgnoreCase(governance)
                        ? ReviewState.CREDIT_MANAGER_APPROVED.name()
                        : ReviewState.AI_DRAFTED.name())
                .baseMetric(body.get("baseMetric") == null ? null : String.valueOf(body.get("baseMetric")))
                .dependencies(body.get("dependencies") instanceof List<?> l ? (List<Object>) l : List.of())
                .metadata(meta)
                .build();
        // Replace stub candidates for the same code
        String code = m.getCandidateCanonicalCode();
        session.getMetricCandidates().removeIf(c ->
                code != null && code.equals(c.getCandidateCanonicalCode())
                        && c.getMetadata() != null
                        && Boolean.TRUE.equals(c.getMetadata().get("NEW_METRIC_CANDIDATE")));
        session.getMetricCandidates().add(m);
        reviewService.invalidateCheckerApproval(session, String.valueOf(body.getOrDefault("createdBy", "author")));
        persistenceService.saveSessionSnapshot(session);
        return m;
    }

    public PolicyReviewService reviewService() {
        return reviewService;
    }

    public DraftPolicySimulator simulator() {
        return simulator;
    }

    public DraftPolicyDiffService diffService() {
        return diffService;
    }

    public PolicyVocabularyService vocabularyService() {
        return vocabularyService;
    }

    public PolicyStudioDashboardReadModel dashboard() {
        return dashboardReadModel;
    }

    public PolicyPreviewService previewService() {
        return previewService;
    }

    public List<String> vocabularyTerms() {
        return vocabularyService.globalSeed().stream().map(v -> v.getTerm()).toList();
    }

    public Map<String, Object> resolveAmbiguity(
            UUID documentId, UUID ambiguityId, AmbiguityResolutionAction action,
            String resolvedOption, String resolvedBy, String notes, Map<String, Object> payload) {
        PolicyStudioSession session = requireSession(documentId);
        var result = reviewService.resolveAmbiguity(
                session, ambiguityId, action, resolvedOption, resolvedBy, notes, payload);
        session.setCompleteness(completenessAnalyzer.analyze(
                session.getClauses(), session.getClauses(), session.getMappings(), session.getAmbiguities(),
                session.getMetricCandidates(), session.getRuleCandidates(),
                session.getTestCases(), session.getConflicts()));
        session.setReadiness(progressScorer.score(session));
        persistenceService.saveSessionSnapshot(session);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", result.id());
        out.put("status", result.status());
        out.put("resolvedOption", result.resolvedOption());
        out.put("action", result.action());
        out.put("extras", result.extras());
        out.put("readiness", session.getReadiness());
        return out;
    }
}
