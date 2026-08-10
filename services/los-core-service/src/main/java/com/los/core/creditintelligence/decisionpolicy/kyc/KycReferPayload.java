package com.los.core.creditintelligence.decisionpolicy.kyc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Carries REFER context for reuse with existing manual KYC review.
 * Does not create a second review workflow.
 */
public record KycReferPayload(
        String reason,
        String requirementId,
        String evidenceSummary,
        String recommendedReviewerRole,
        String sourceStep,
        String policyVersionId,
        String reviewStatus
) {
    public static KycReferPayload fromStep(
            String reason,
            String requirementId,
            String sourceStep,
            String evidenceSummary
    ) {
        return new KycReferPayload(
                reason,
                requirementId,
                evidenceSummary,
                "KYC_REVIEWER",
                sourceStep,
                null,
                "OPEN");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reason", reason);
        m.put("requirementId", requirementId);
        m.put("evidenceSummary", evidenceSummary);
        m.put("recommendedReviewerRole", recommendedReviewerRole);
        m.put("sourceStep", sourceStep);
        m.put("policyVersionId", policyVersionId);
        m.put("reviewStatus", reviewStatus == null ? "OPEN" : reviewStatus);
        m.put("businessOutcome", KycBusinessOutcome.REFER.name());
        return m;
    }

    public KycReferPayload withPolicyVersion(UUID policyVersionId) {
        return new KycReferPayload(
                reason,
                requirementId,
                evidenceSummary,
                recommendedReviewerRole,
                sourceStep,
                policyVersionId == null ? null : policyVersionId.toString(),
                reviewStatus);
    }
}
