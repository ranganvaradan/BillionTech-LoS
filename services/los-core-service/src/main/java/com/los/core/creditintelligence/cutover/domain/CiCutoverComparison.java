package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiCutoverComparison {
    private UUID id;
    private UUID cohortId;
    private UUID applicationId;
    private UUID evaluationContextId;
    private String legacyPolicyOutcome;
    private String canonicalPolicyOutcome;
    private BigDecimal legacyAmount;
    private BigDecimal canonicalAmount;
    private Integer legacyTenure;
    private Integer canonicalTenure;
    private BigDecimal legacyPricing;
    private BigDecimal canonicalPricing;
    private String legacyAuthority;
    private String canonicalAuthority;
    @Builder.Default
    private List<Object> legacyConditions = new ArrayList<>();
    @Builder.Default
    private List<Object> canonicalConditions = new ArrayList<>();
    private String comparisonClass;
    private String materiality;
    private String rootCause;
    @Builder.Default
    private List<String> reasonCodes = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> decisionTrace = new LinkedHashMap<>();
    @Builder.Default
    private String reviewStatus = "PENDING";
    private String reviewedBy;
    private String reviewDisposition;
    private String reviewComment;
    private Instant createdAt;
}
