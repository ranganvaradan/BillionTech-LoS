package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.AnalysisRequestStatus;
import com.los.core.creditintelligence.aiunderwriter.domain.AiUnderwritingContext;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiAnalysisRequest;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import com.los.core.creditintelligence.config.CreditIntelligenceProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates assistive AI underwriting. Never mutates recommendations/facts/policy.
 */
@Service
public class AiUnderwritingService {

    private final CreditIntelligenceProperties properties;
    private final AiUnderwritingStore store;
    private final AiUnderwritingContextBuilder contextBuilder;
    private final AiPromptTemplateService promptTemplateService;
    private final IdempotencyKeyFactory idempotencyKeyFactory;
    private final AiGroundingValidator groundingValidator;
    private final StubAiUnderwritingProvider stubProvider;
    private final HttpAiUnderwritingProvider httpProvider;
    private AiUnderwritingProvider providerOverride;

    public AiUnderwritingService() {
        this(new CreditIntelligenceProperties(), new AiUnderwritingStore(),
                new AiUnderwritingContextBuilder(), new AiPromptTemplateService(),
                new IdempotencyKeyFactory(), new AiGroundingValidator(),
                new StubAiUnderwritingProvider(), new HttpAiUnderwritingProvider());
    }

    public AiUnderwritingService(
            CreditIntelligenceProperties properties,
            AiUnderwritingStore store,
            AiUnderwritingContextBuilder contextBuilder,
            AiPromptTemplateService promptTemplateService,
            IdempotencyKeyFactory idempotencyKeyFactory,
            AiGroundingValidator groundingValidator,
            StubAiUnderwritingProvider stubProvider,
            HttpAiUnderwritingProvider httpProvider) {
        this.properties = properties != null ? properties : new CreditIntelligenceProperties();
        this.store = store != null ? store : new AiUnderwritingStore();
        this.contextBuilder = contextBuilder != null ? contextBuilder : new AiUnderwritingContextBuilder();
        this.promptTemplateService = promptTemplateService != null
                ? promptTemplateService : new AiPromptTemplateService(this.store);
        this.idempotencyKeyFactory = idempotencyKeyFactory != null
                ? idempotencyKeyFactory : new IdempotencyKeyFactory();
        this.groundingValidator = groundingValidator != null ? groundingValidator : new AiGroundingValidator();
        this.stubProvider = stubProvider != null ? stubProvider : new StubAiUnderwritingProvider();
        this.httpProvider = httpProvider != null ? httpProvider : new HttpAiUnderwritingProvider();
    }

    /** Test/hook: force a provider (e.g. unavailable or adversarial). */
    public void setProviderOverride(AiUnderwritingProvider providerOverride) {
        this.providerOverride = providerOverride;
    }

    public Map<String, Object> analyze(
            UUID tenantId,
            UUID applicationId,
            UUID evaluationContextId,
            UUID policyEvaluationId,
            UUID recommendationId,
            List<String> requestedOutputTypes,
            Map<String, Object> creditEvidenceView,
            Map<String, Object> policyDecisionExplanation,
            Map<String, Object> creditDecisionView,
            Map<String, Object> shadowRecommendation,
            List<Map<String, Object>> investigationQuestions,
            String createdBy,
            Map<String, Object> recommendationSnapshotForMutationCheck) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("authoritative", false);
        result.put("humanReviewRequired", true);
        result.put("outputMarker", "AI_SUGGESTION");
        result.put("demoUrlUsed", false);

        if (!isEnabledForTenant(tenantId)) {
            result.put("status", AnalysisRequestStatus.AI_ASSISTANCE_UNAVAILABLE.name());
            result.put("failureCode", "AI_ASSISTANCE_UNAVAILABLE");
            result.put("suggestions", List.of());
            return result;
        }

        List<String> types = requestedOutputTypes == null || requestedOutputTypes.isEmpty()
                ? List.of("NARRATIVE", "EXPLANATION", "QUESTION")
                : new ArrayList<>(requestedOutputTypes);
        if (!properties.getAiUnderwriter().isCamDraftEnabled()) {
            types.removeIf(t -> "CAM_DRAFT".equals(t) || "CREDIT_NOTE_DRAFT".equals(t));
        }

        Map<String, Object> promptVersions = promptTemplateService.resolveVersions(types);
        String idempotencyKey = idempotencyKeyFactory.build(
                applicationId, evaluationContextId, recommendationId, promptVersions, types);

        Optional<CiAiAnalysisRequest> existing = store.findRequestByIdempotency(idempotencyKey);
        if (existing.isPresent()) {
            CiAiAnalysisRequest req = existing.get();
            if (!tenantId.equals(req.getTenantId())) {
                result.put("status", AnalysisRequestStatus.FAILED.name());
                result.put("failureCode", "TENANT_ISOLATION");
                result.put("suggestions", List.of());
                return result;
            }
            List<CiAiUnderwritingSuggestion> prior = store.findSuggestionsByRequest(req.getId());
            result.put("status", req.getStatus());
            result.put("failureCode", req.getFailureCode());
            result.put("analysisRequestId", req.getId());
            result.put("idempotencyKey", idempotencyKey);
            result.put("idempotentReplay", true);
            result.put("suggestions", prior.stream().map(this::toMap).toList());
            result.put("recommendationUnchanged", true);
            return result;
        }

        AiUnderwritingContext context = contextBuilder.build(
                tenantId, applicationId, evaluationContextId, policyEvaluationId, recommendationId,
                creditEvidenceView, policyDecisionExplanation, creditDecisionView,
                shadowRecommendation, investigationQuestions);

        CiAiAnalysisRequest request = CiAiAnalysisRequest.builder()
                .tenantId(tenantId)
                .applicationId(applicationId)
                .evaluationContextId(evaluationContextId)
                .policyEvaluationId(policyEvaluationId)
                .recommendationId(recommendationId)
                .idempotencyKey(idempotencyKey)
                .contextVersion(context.contextVersion())
                .requestedOutputTypes(new ArrayList<>(types))
                .contextPayload(new LinkedHashMap<>(context.toSanitizedMap()))
                .promptVersions(new LinkedHashMap<>(promptVersions))
                .status(AnalysisRequestStatus.PENDING.name())
                .createdBy(createdBy == null ? "system" : createdBy)
                .build();
        store.saveRequest(request);

        AiUnderwritingProvider provider = resolveProvider();
        AiUnderwritingProvider.ProviderResponse response =
                provider.analyze(context, types, promptVersions);

        // Never mutate caller recommendation snapshot
        Map<String, Object> snapshotCopy = recommendationSnapshotForMutationCheck == null
                ? null : new LinkedHashMap<>(recommendationSnapshotForMutationCheck);

        if (!response.available()) {
            request.setStatus(AnalysisRequestStatus.AI_ASSISTANCE_UNAVAILABLE.name());
            request.setFailureCode("AI_ASSISTANCE_UNAVAILABLE");
            request.setCompletedAt(Instant.now());
            store.saveRequest(request);
            result.put("status", AnalysisRequestStatus.AI_ASSISTANCE_UNAVAILABLE.name());
            result.put("failureCode", "AI_ASSISTANCE_UNAVAILABLE");
            result.put("analysisRequestId", request.getId());
            result.put("idempotencyKey", idempotencyKey);
            result.put("suggestions", List.of());
            result.put("recommendationUnchanged", snapshotEquals(snapshotCopy, recommendationSnapshotForMutationCheck));
            return result;
        }

        request.setModelProvider(response.modelProvider());
        request.setModelName(response.modelName());
        request.setModelVersion(response.modelVersion());

        List<Map<String, Object>> suggestionMaps = new ArrayList<>();
        for (AiUnderwritingProvider.RawSuggestion raw : response.suggestions()) {
            AiGroundingValidator.GroundingResult grounding = groundingValidator.validate(raw, context);
            CiAiUnderwritingSuggestion suggestion = CiAiUnderwritingSuggestion.builder()
                    .tenantId(tenantId)
                    .applicationId(applicationId)
                    .analysisRequestId(request.getId())
                    .evaluationContextId(evaluationContextId)
                    .policyEvaluationId(policyEvaluationId)
                    .recommendationId(recommendationId)
                    .type(raw.type())
                    .title(raw.title())
                    .content(raw.content())
                    .structuredPayload(raw.structuredPayload() == null
                            ? new LinkedHashMap<>() : new LinkedHashMap<>(raw.structuredPayload()))
                    .modelConfidence(raw.modelConfidence() == null
                            ? null : BigDecimal.valueOf(raw.modelConfidence()))
                    .groundingCoverage(grounding.groundingCoverage())
                    .evidenceCompleteness(grounding.evidenceCompleteness())
                    .confidence(null) // never a single arbitrary authority confidence
                    .limitations(raw.limitations() == null ? new ArrayList<>() : new ArrayList<>(raw.limitations()))
                    .evidenceRefs(raw.evidenceRefs() == null ? new ArrayList<>() : new ArrayList<>(raw.evidenceRefs()))
                    .sourceRefs(raw.sourceRefs() == null ? new ArrayList<>() : new ArrayList<>(raw.sourceRefs()))
                    .modelMetadata(new LinkedHashMap<>(Map.of(
                            "provider", response.modelProvider() == null ? "" : response.modelProvider(),
                            "modelName", response.modelName() == null ? "" : response.modelName(),
                            "modelVersion", response.modelVersion() == null ? "" : response.modelVersion()
                    )))
                    .promptVersion(raw.promptVersion())
                    .outputMarker("AI_SUGGESTION")
                    .authoritative(false)
                    .humanReviewRequired(true)
                    .status(grounding.grounded()
                            ? SuggestionStatus.PENDING_REVIEW.name()
                            : SuggestionStatus.REJECTED_GROUNDING_FAILURE.name())
                    .groundingStatus(grounding.status())
                    .build();
            if (!grounding.grounded()) {
                suggestion.getStructuredPayload().put("groundingFailures", grounding.failures());
            }
            store.saveSuggestion(suggestion);
            suggestionMaps.add(toMap(suggestion));
        }

        request.setStatus(AnalysisRequestStatus.COMPLETED.name());
        request.setCompletedAt(Instant.now());
        store.saveRequest(request);

        result.put("status", AnalysisRequestStatus.COMPLETED.name());
        result.put("analysisRequestId", request.getId());
        result.put("idempotencyKey", idempotencyKey);
        result.put("idempotentReplay", false);
        result.put("suggestions", suggestionMaps);
        result.put("promptVersions", promptVersions);
        result.put("recommendationUnchanged", snapshotEquals(snapshotCopy, recommendationSnapshotForMutationCheck));
        result.put("factCandidateActivated", false);
        return result;
    }

    public Map<String, Object> getAnalysis(UUID tenantId, UUID analysisId) {
        Optional<CiAiAnalysisRequest> req = store.findRequest(analysisId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authoritative", false);
        if (req.isEmpty()) {
            out.put("status", "NOT_FOUND");
            return out;
        }
        CiAiAnalysisRequest r = req.get();
        if (!tenantId.equals(r.getTenantId())) {
            out.put("status", "TENANT_ISOLATION");
            out.put("failureCode", "TENANT_ISOLATION");
            return out;
        }
        out.put("analysisRequestId", r.getId());
        out.put("status", r.getStatus());
        out.put("failureCode", r.getFailureCode());
        out.put("idempotencyKey", r.getIdempotencyKey());
        out.put("suggestions", store.findSuggestionsByRequest(r.getId()).stream().map(this::toMap).toList());
        out.put("demoUrlUsed", false);
        return out;
    }

    public Map<String, Object> listForApplication(UUID tenantId, UUID applicationId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applicationId", applicationId);
        out.put("authoritative", false);
        if (!isEnabledForTenant(tenantId)) {
            out.put("status", AnalysisRequestStatus.AI_ASSISTANCE_UNAVAILABLE.name());
            out.put("analyses", List.of());
            return out;
        }
        List<Map<String, Object>> analyses = new ArrayList<>();
        for (CiAiAnalysisRequest r : store.findRequestsByTenantAndApp(tenantId, applicationId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("analysisRequestId", r.getId());
            row.put("status", r.getStatus());
            row.put("failureCode", r.getFailureCode());
            row.put("createdAt", r.getCreatedAt());
            row.put("suggestionCount", store.findSuggestionsByRequest(r.getId()).size());
            analyses.add(row);
        }
        out.put("analyses", analyses);
        out.put("suggestions", store.findSuggestionsByTenantAndApp(tenantId, applicationId)
                .stream().map(this::toMap).toList());
        out.put("demoUrlUsed", false);
        return out;
    }

    public boolean isEnabledForTenant(UUID tenantId) {
        CreditIntelligenceProperties.AiUnderwriter cfg = properties.getAiUnderwriter();
        if (cfg == null || !cfg.isEnabled()) {
            return false;
        }
        List<String> tenants = cfg.getTenantIds();
        if (tenants == null || tenants.isEmpty()) {
            return true;
        }
        return tenantId != null && tenants.contains(tenantId.toString());
    }

    private AiUnderwritingProvider resolveProvider() {
        if (providerOverride != null) {
            return providerOverride;
        }
        String provider = properties.getAiUnderwriter().getProvider();
        if ("http".equalsIgnoreCase(provider)) {
            String url = properties.getAiUnderwriter().getHttpBaseUrl();
            HttpAiUnderwritingProvider http = httpProvider != null && httpProvider.isConfigured()
                    ? httpProvider
                    : new HttpAiUnderwritingProvider(url == null ? "" : url);
            if (http.isConfigured()) {
                return http;
            }
            return ctxUnavailableProvider();
        }
        return stubProvider;
    }

    private AiUnderwritingProvider ctxUnavailableProvider() {
        return (context, types, promptVersions) ->
                AiUnderwritingProvider.ProviderResponse.unavailable("AI_ASSISTANCE_UNAVAILABLE");
    }

    private Map<String, Object> toMap(CiAiUnderwritingSuggestion s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("outputId", s.getId());
        m.put("requestId", s.getAnalysisRequestId());
        m.put("applicationId", s.getApplicationId());
        m.put("type", s.getType());
        m.put("title", s.getTitle());
        m.put("content", s.getContent());
        m.put("structuredPayload", s.getStructuredPayload());
        m.put("modelConfidence", s.getModelConfidence());
        m.put("groundingCoverage", s.getGroundingCoverage());
        m.put("evidenceCompleteness", s.getEvidenceCompleteness());
        m.put("confidence", s.getConfidence());
        m.put("limitations", s.getLimitations());
        m.put("evidenceRefs", s.getEvidenceRefs());
        m.put("sourceRefs", s.getSourceRefs());
        m.put("modelMetadata", s.getModelMetadata());
        m.put("promptVersion", s.getPromptVersion());
        m.put("outputMarker", s.getOutputMarker());
        m.put("authoritative", Boolean.FALSE.equals(s.getAuthoritative()) ? false : false);
        m.put("humanReviewRequired", true);
        m.put("status", s.getStatus());
        m.put("groundingStatus", s.getGroundingStatus());
        m.put("createdAt", s.getCreatedAt());
        return m;
    }

    private boolean snapshotEquals(Map<String, Object> a, Map<String, Object> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return true; // no snapshot provided to mutate
        }
        return a.equals(b);
    }

    public AiUnderwritingStore store() {
        return store;
    }
}
