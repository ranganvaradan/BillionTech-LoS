package com.los.core.creditintelligence.aiunderwriter.store;

import com.los.core.creditintelligence.aiunderwriter.domain.CiAiAnalysisRequest;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiPromptTemplate;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiReview;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiScenario;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory durable store for unit tests and when JPA is unavailable.
 */
@Component
public class AiUnderwritingStore {

    private final ConcurrentHashMap<UUID, CiAiPromptTemplate> templates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CiAiPromptTemplate> templatesByCodeVer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiAiAnalysisRequest> requests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> requestByIdempotency = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiAiUnderwritingSuggestion> suggestions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiAiReview> reviews = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CiAiScenario> scenarios = new ConcurrentHashMap<>();

    public AiUnderwritingStore() {
        seedDefaultTemplates();
    }

    private void seedDefaultTemplates() {
        seed("UNDERWRITING_SUMMARY_V1", "1",
                "Summarize using only supplied evidence. Never invent metrics or approve/reject.");
        seed("POLICY_EXPLANATION_V1", "1",
                "Explain policy outcomes using only deterministic rule IDs in context.");
        seed("CAM_DRAFT_V1", "1",
                "Draft CAM narrative from evidence/policy/decision views. Human review required.");
        seed("INVESTIGATION_QUESTIONS_V1", "1",
                "Refine deterministic investigation questions; preserve discrepancy and evidence.");
        seed("ALTERNATE_STRUCTURE_V1", "1",
                "Suggest alternate structures vs canonical recommendation; never overwrite.");
    }

    private void seed(String code, String version, String content) {
        CiAiPromptTemplate t = CiAiPromptTemplate.builder()
                .id(UUID.randomUUID())
                .templateCode(code)
                .version(version)
                .content(content)
                .modelProvider("stub")
                .modelName("stub-grounded-v1")
                .temperature(BigDecimal.ZERO)
                .settings(new LinkedHashMap<>(Map.of("outputMarker", "AI_SUGGESTION", "authoritative", false)))
                .effectiveFrom(Instant.parse("2024-01-01T00:00:00Z"))
                .createdAt(Instant.parse("2024-01-01T00:00:00Z"))
                .build();
        saveTemplate(t);
    }

    public void saveTemplate(CiAiPromptTemplate t) {
        if (t.getId() == null) {
            t.setId(UUID.randomUUID());
        }
        if (t.getCreatedAt() == null) {
            t.setCreatedAt(Instant.now());
        }
        templates.put(t.getId(), t);
        templatesByCodeVer.put(key(t.getTemplateCode(), t.getVersion()), t);
    }

    public Optional<CiAiPromptTemplate> findTemplate(String code, String version) {
        return Optional.ofNullable(templatesByCodeVer.get(key(code, version)));
    }

    public Optional<CiAiPromptTemplate> findLatestTemplate(String code) {
        return templatesByCodeVer.values().stream()
                .filter(t -> code.equals(t.getTemplateCode()))
                .findFirst();
    }

    public List<CiAiPromptTemplate> listTemplates() {
        return new ArrayList<>(templates.values());
    }

    public CiAiAnalysisRequest saveRequest(CiAiAnalysisRequest r) {
        if (r.getId() == null) {
            r.setId(UUID.randomUUID());
        }
        if (r.getCreatedAt() == null) {
            r.setCreatedAt(Instant.now());
        }
        requests.put(r.getId(), r);
        if (r.getIdempotencyKey() != null) {
            requestByIdempotency.put(r.getIdempotencyKey(), r.getId());
        }
        return r;
    }

    public Optional<CiAiAnalysisRequest> findRequest(UUID id) {
        return Optional.ofNullable(requests.get(id));
    }

    public Optional<CiAiAnalysisRequest> findRequestByIdempotency(String key) {
        UUID id = requestByIdempotency.get(key);
        return id == null ? Optional.empty() : findRequest(id);
    }

    public List<CiAiAnalysisRequest> findRequestsByTenantAndApp(UUID tenantId, UUID applicationId) {
        return requests.values().stream()
                .filter(r -> tenantId.equals(r.getTenantId()) && applicationId.equals(r.getApplicationId()))
                .collect(Collectors.toList());
    }

    public CiAiUnderwritingSuggestion saveSuggestion(CiAiUnderwritingSuggestion s) {
        if (s.getId() == null) {
            s.setId(UUID.randomUUID());
        }
        if (s.getCreatedAt() == null) {
            s.setCreatedAt(Instant.now());
        }
        if (s.getAuthoritative() == null) {
            s.setAuthoritative(false);
        }
        if (s.getHumanReviewRequired() == null) {
            s.setHumanReviewRequired(true);
        }
        if (s.getOutputMarker() == null) {
            s.setOutputMarker("AI_SUGGESTION");
        }
        suggestions.put(s.getId(), s);
        return s;
    }

    public Optional<CiAiUnderwritingSuggestion> findSuggestion(UUID id) {
        return Optional.ofNullable(suggestions.get(id));
    }

    public List<CiAiUnderwritingSuggestion> findSuggestionsByRequest(UUID requestId) {
        return suggestions.values().stream()
                .filter(s -> requestId.equals(s.getAnalysisRequestId()))
                .collect(Collectors.toList());
    }

    public List<CiAiUnderwritingSuggestion> findSuggestionsByTenantAndApp(UUID tenantId, UUID applicationId) {
        return suggestions.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId()) && applicationId.equals(s.getApplicationId()))
                .collect(Collectors.toList());
    }

    public CiAiReview saveReview(CiAiReview r) {
        if (r.getId() == null) {
            r.setId(UUID.randomUUID());
        }
        if (r.getCreatedAt() == null) {
            r.setCreatedAt(Instant.now());
        }
        reviews.put(r.getId(), r);
        return r;
    }

    public List<CiAiReview> findReviewsBySuggestion(UUID suggestionId) {
        return reviews.values().stream()
                .filter(r -> suggestionId.equals(r.getSuggestionId()))
                .collect(Collectors.toList());
    }

    public CiAiScenario saveScenario(CiAiScenario s) {
        if (s.getId() == null) {
            s.setId(UUID.randomUUID());
        }
        if (s.getCreatedAt() == null) {
            s.setCreatedAt(Instant.now());
        }
        scenarios.put(s.getId(), s);
        return s;
    }

    public Optional<CiAiScenario> findScenario(UUID id) {
        return Optional.ofNullable(scenarios.get(id));
    }

    public List<CiAiScenario> findScenariosByApp(UUID tenantId, UUID applicationId) {
        return scenarios.values().stream()
                .filter(s -> tenantId.equals(s.getTenantId()) && applicationId.equals(s.getApplicationId()))
                .collect(Collectors.toList());
    }

    public void clearAll() {
        requests.clear();
        requestByIdempotency.clear();
        suggestions.clear();
        reviews.clear();
        scenarios.clear();
        // keep seeded templates
    }

    private static String key(String code, String version) {
        return code + "::" + version;
    }
}
