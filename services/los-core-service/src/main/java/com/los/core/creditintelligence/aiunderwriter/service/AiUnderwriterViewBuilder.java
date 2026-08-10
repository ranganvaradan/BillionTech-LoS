package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Underwriter AI panel read model (§23).
 */
@Service
public class AiUnderwriterViewBuilder {

    public static final String BANNER = "AI GENERATED — NON-AUTHORITATIVE";

    private final AiUnderwritingStore store;

    public AiUnderwriterViewBuilder() {
        this(new AiUnderwritingStore());
    }

    public AiUnderwriterViewBuilder(AiUnderwritingStore store) {
        this.store = store != null ? store : new AiUnderwritingStore();
    }

    public Map<String, Object> build(UUID tenantId, UUID applicationId) {
        List<CiAiUnderwritingSuggestion> all = store.findSuggestionsByTenantAndApp(tenantId, applicationId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("banner", BANNER);
        view.put("authoritative", false);
        view.put("humanReviewRequired", true);
        view.put("outputMarker", "AI_SUGGESTION");
        view.put("AI Summary", filterByType(all, "NARRATIVE"));
        view.put("Key Risks", extractSection(all, "risk"));
        view.put("Key Strengths", extractSection(all, "strength"));
        view.put("Questions", filterByType(all, "QUESTION"));
        view.put("Policy Explanations", filterByType(all, "EXPLANATION"));
        view.put("Alternate Structures", filterByType(all, "ALTERNATE_STRUCTURE_SUGGESTION"));
        view.put("CAM Draft", filterByType(all, "CAM_DRAFT"));
        view.put("AI Limitations", collectLimitations(all));
        view.put("Evidence Used", collectEvidence(all));
        view.put("demoUrlUsed", false);
        return view;
    }

    public Map<String, Object> buildFromSuggestions(List<Map<String, Object>> suggestions) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("banner", BANNER);
        view.put("authoritative", false);
        view.put("humanReviewRequired", true);
        view.put("outputMarker", "AI_SUGGESTION");
        view.put("AI Summary", filterMaps(suggestions, "NARRATIVE"));
        view.put("Key Risks", List.of());
        view.put("Key Strengths", List.of());
        view.put("Questions", filterMaps(suggestions, "QUESTION"));
        view.put("Policy Explanations", filterMaps(suggestions, "EXPLANATION"));
        view.put("Alternate Structures", filterMaps(suggestions, "ALTERNATE_STRUCTURE_SUGGESTION"));
        view.put("CAM Draft", filterMaps(suggestions, "CAM_DRAFT"));
        view.put("AI Limitations", List.of("AI_SUGGESTION — non-authoritative"));
        view.put("Evidence Used", List.of());
        view.put("demoUrlUsed", false);
        return view;
    }

    private List<Map<String, Object>> filterByType(List<CiAiUnderwritingSuggestion> all, String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiAiUnderwritingSuggestion s : all) {
            if (type.equals(s.getType())) {
                out.add(toBrief(s));
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterMaps(List<Map<String, Object>> suggestions, String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (suggestions == null) {
            return out;
        }
        for (Map<String, Object> s : suggestions) {
            if (type.equals(String.valueOf(s.get("type")))) {
                out.add(s);
            }
        }
        return out;
    }

    private List<Map<String, Object>> extractSection(List<CiAiUnderwritingSuggestion> all, String keyword) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CiAiUnderwritingSuggestion s : all) {
            String content = s.getContent() == null ? "" : s.getContent().toLowerCase();
            if (content.contains(keyword)) {
                out.add(toBrief(s));
            }
        }
        return out;
    }

    private List<Object> collectLimitations(List<CiAiUnderwritingSuggestion> all) {
        List<Object> out = new ArrayList<>();
        out.add(BANNER);
        for (CiAiUnderwritingSuggestion s : all) {
            if (s.getLimitations() != null) {
                out.addAll(s.getLimitations());
            }
        }
        return out;
    }

    private List<Object> collectEvidence(List<CiAiUnderwritingSuggestion> all) {
        List<Object> out = new ArrayList<>();
        for (CiAiUnderwritingSuggestion s : all) {
            if (s.getEvidenceRefs() != null) {
                out.addAll(s.getEvidenceRefs());
            }
        }
        return out;
    }

    private Map<String, Object> toBrief(CiAiUnderwritingSuggestion s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("type", s.getType());
        m.put("title", s.getTitle());
        m.put("content", s.getContent());
        m.put("status", s.getStatus());
        m.put("modelConfidence", s.getModelConfidence());
        m.put("groundingCoverage", s.getGroundingCoverage());
        m.put("evidenceCompleteness", s.getEvidenceCompleteness());
        m.put("authoritative", false);
        m.put("outputMarker", "AI_SUGGESTION");
        return m;
    }
}
