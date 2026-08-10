package com.los.core.model.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class CamUpdateRequest {
    private String observations;
    private String riskAssessment;
    private String mitigants;
    /** APPROVE | REJECT | MANUAL_REVIEW */
    private String recommendedDecision;
    private BigDecimal recommendedAmount;
    private Integer recommendedTenureMonths;
    private BigDecimal recommendedRate;
    /** UPFRONT | REDUCING */
    private String interestType;
    private List<String> conditionsPrecedent;
    private List<String> conditionsSubsequent;
    private String creditOfficerRemarks;
    private String creditManagerRemarks;
    /**
     * Shallow-merged into {@code credit_appraisal_memos.editable_sections_json} (section narratives and structured
     * overrides shown on CAM; PDF prefers these over auto-generated text).
     */
    private Map<String, Object> editableSectionsPatch;
}
