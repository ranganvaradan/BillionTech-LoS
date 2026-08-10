package com.los.core.creditintelligence.aiunderwriter.service;

import com.los.core.creditintelligence.aiunderwriter.domain.CiAiReview;
import com.los.core.creditintelligence.aiunderwriter.domain.CiAiUnderwritingSuggestion;
import com.los.core.creditintelligence.aiunderwriter.domain.FactCandidateDesign;
import com.los.core.creditintelligence.aiunderwriter.domain.FeedbackCode;
import com.los.core.creditintelligence.aiunderwriter.domain.ReviewAction;
import com.los.core.creditintelligence.aiunderwriter.domain.SuggestionStatus;
import com.los.core.creditintelligence.aiunderwriter.store.AiUnderwritingStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACCEPT_AS_NOTE / EDIT / REJECT — never ACCEPT_AS_FACT; never promotes facts.
 */
@Service
public class AiReviewService {

    private final AiUnderwritingStore store;

    public AiReviewService() {
        this(new AiUnderwritingStore());
    }

    public AiReviewService(AiUnderwritingStore store) {
        this.store = store != null ? store : new AiUnderwritingStore();
    }

    public Map<String, Object> review(
            UUID tenantId,
            UUID suggestionId,
            String actionRaw,
            String feedbackCodeRaw,
            String editedContent,
            String reviewer,
            String reason) {

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authoritative", false);
        out.put("factPromoted", false);
        out.put("factCandidateActivated", FactCandidateDesign.ACTIVATED_IN_A1);

        if (actionRaw != null && actionRaw.toUpperCase().contains("ACCEPT_AS_FACT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "ACCEPT_AS_FACT is not allowed in A1");
        }

        ReviewAction action;
        try {
            action = ReviewAction.valueOf(actionRaw == null ? "" : actionRaw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review action");
        }

        CiAiUnderwritingSuggestion suggestion = store.findSuggestion(suggestionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Suggestion not found"));
        if (!tenantId.equals(suggestion.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-tenant access rejected");
        }

        String newStatus = switch (action) {
            case ACCEPT_AS_NOTE -> SuggestionStatus.ACCEPTED_AS_NOTE.name();
            case EDIT -> SuggestionStatus.EDITED.name();
            case REJECT -> SuggestionStatus.REJECTED.name();
        };
        suggestion.setStatus(newStatus);
        if (action == ReviewAction.EDIT && editedContent != null) {
            suggestion.setContent(editedContent);
        }
        // Never set authoritative true
        suggestion.setAuthoritative(false);
        suggestion.setHumanReviewRequired(true);
        store.saveSuggestion(suggestion);

        String feedback = null;
        if (feedbackCodeRaw != null && !feedbackCodeRaw.isBlank()) {
            try {
                feedback = FeedbackCode.valueOf(feedbackCodeRaw.trim().toUpperCase()).name();
            } catch (Exception ignored) {
                feedback = feedbackCodeRaw;
            }
        }

        CiAiReview review = CiAiReview.builder()
                .suggestionId(suggestionId)
                .tenantId(tenantId)
                .action(action.name())
                .feedbackCode(feedback)
                .editedContent(editedContent)
                .reviewer(reviewer)
                .reason(reason)
                .build();
        store.saveReview(review);

        out.put("suggestionId", suggestionId);
        out.put("action", action.name());
        out.put("status", suggestion.getStatus());
        out.put("reviewId", review.getId());
        out.put("outputMarker", suggestion.getOutputMarker());
        out.put("acceptedAsNoteDoesNotPromoteFacts", action == ReviewAction.ACCEPT_AS_NOTE);
        return out;
    }
}
