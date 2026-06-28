package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class CamResponse {
    private UUID applicationId;
    private String applicationStatus;
    private Map<String, Object> section1ApplicantSummary;
    private Map<String, Object> section2KycSummary;
    private Map<String, Object> section3CreditSummary;
    private Map<String, Object> section4UnderwritingSummary;
    /** Auto sections: executive summary, income, collateral, risk flags — may be empty */
    private Map<String, Object> sectionExtended;
    private String section5Observations;
    private String section5RiskAssessment;
    private String section5Mitigants;
    private String section6RecommendedDecision;
    private boolean camReviewed;
    private Integer camVersion;
    private String camStatus;
    private BigDecimal recommendedAmount;
    private Integer recommendedTenureMonths;
    private BigDecimal recommendedRate;
    private List<String> conditionsPrecedent;
    private List<String> conditionsSubsequent;
    private String creditOfficerRemarks;
    private String creditManagerRemarks;
    private String submittedAt;
    private String approvedAt;
    private String updatedAt;
    private String createdAt;
    private UUID approvedByUserId;
    private Map<String, Object> editableSections;
}
