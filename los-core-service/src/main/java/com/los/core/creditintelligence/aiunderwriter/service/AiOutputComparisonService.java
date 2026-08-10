package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Compare two suggestion generations without overwriting history.
 */
@Service
public class AiOutputComparisonService {

    private final AiUnderwritingStore store;

    public AiOutputComparisonService() {
        this(new AiUnderwritingStore());
    }

    public AiOutputComparisonService(AiUnderwritingStore store) {
        this.store = store != null ? store : new AiUnderwritingStore();
    }

    public Map<String, Object> compare(UUID tenantId, UUID oldSuggestionId, UUID newSuggestionId) {
        CiAiUnderwritingSuggestion oldS = store.findSuggestion(oldSuggestionId).orElse(null);
        CiAiUnderwritingSuggestion newS = store.findSuggestion(newSuggestionId).orElse(null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authoritative", false);
        out.put("banner", AiUnderwriterViewBuilder.BANNER);
        if (oldS == null || newS == null) {
            out.put("status", "NOT_FOUND");
            return out;
        }
        if (!tenantId.equals(oldS.getTenantId()) || !tenantId.equals(newS.getTenantId())) {
            out.put("status", "TENANT_ISOLATION");
            return out;
        }
        // Do not overwrite — mark older as SUPERSEDED only if still PENDING_REVIEW / GENERATED
        if (SuggestionStatus.PENDING_REVIEW.name().equals(oldS.getStatus())
                || SuggestionStatus.GENERATED.name().equals(oldS.getStatus())) {
            oldS.setStatus(SuggestionStatus.SUPERSEDED.name());
            store.saveSuggestion(oldS);
        }
        out.put("old", brief(oldS));
        out.put("new", brief(newS));
        out.put("overwroteHistorical", false);
        out.put("status", "COMPARED");
        return out;
    }

    private Map<String, Object> brief(CiAiUnderwritingSuggestion s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("type", s.getType());
        m.put("promptVersion", s.getPromptVersion());
        m.put("content", s.getContent());
        m.put("status", s.getStatus());
        m.put("modelConfidence", s.getModelConfidence());
        m.put("groundingCoverage", s.getGroundingCoverage());
        m.put("evidenceCompleteness", s.getEvidenceCompleteness());
        m.put("outputMarker", s.getOutputMarker());
        m.put("authoritative", false);
        return m;
    }
}
