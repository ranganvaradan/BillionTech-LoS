package com.los.core.creditintelligence.cutover.service;

import com.los.core.creditintelligence.cutover.domain.CiCutoverComparison;
import com.los.core.creditintelligence.cutover.domain.CiCutoverReview;
import com.los.core.creditintelligence.cutover.domain.ReviewDisposition;
import com.los.core.creditintelligence.cutover.store.CutoverStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Human review of dual-run mismatches — immutable audit via review rows.
 */
@Service
public class CutoverReviewService {

    private final CutoverStore store;

    public CutoverReviewService(CutoverStore store) {
        this.store = store;
    }

    public CiCutoverReview review(
            UUID comparisonId,
            ReviewDisposition disposition,
            String commentary,
            String reviewer) {
        CiCutoverComparison cmp = store.findComparison(comparisonId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "comparison not found"));

        Instant now = Instant.now();
        // Ensure chronological audit ordering even within the same millisecond
        List<CiCutoverReview> existing = store.listReviews(comparisonId);
        if (!existing.isEmpty()) {
            Instant last = existing.get(existing.size() - 1).getCreatedAt();
            if (last != null && !now.isAfter(last)) {
                now = last.plusMillis(1);
            }
        }

        CiCutoverReview review = CiCutoverReview.builder()
                .comparisonId(comparisonId)
                .disposition(disposition.name())
                .commentary(commentary)
                .reviewer(reviewer)
                .createdAt(now)
                .build();
        store.saveReview(review);

        // Update comparison review fields but keep prior review rows (immutable audit)
        cmp.setReviewStatus("REVIEWED");
        cmp.setReviewedBy(reviewer);
        cmp.setReviewDisposition(disposition.name());
        cmp.setReviewComment(commentary);
        store.saveComparison(cmp);
        return review;
    }

    public List<CiCutoverReview> auditTrail(UUID comparisonId) {
        return store.listReviews(comparisonId);
    }
}
