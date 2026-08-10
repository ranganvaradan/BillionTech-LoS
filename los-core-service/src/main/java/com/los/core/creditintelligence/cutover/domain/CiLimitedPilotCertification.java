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
public class CiLimitedPilotCertification {
    private UUID id;
    private UUID cohortId;
    private UUID policyCertificationId;
    private UUID decisionCertificationId;
    @Builder.Default
    private Map<String, Object> validationDatasetSummary = new LinkedHashMap<>();
    @Builder.Default
    private int realStoredCaseCount = 0;
    private BigDecimal weightedEvidenceScore;
    private BigDecimal criticalBindingCoverage;
    private BigDecimal replayPassRate;
    private BigDecimal canonicalSuccessRate;
    private BigDecimal canonicalDiRate;
    private BigDecimal materialMismatchRate;
    @Builder.Default
    private int unresolvedMismatchCount = 0;
    @Builder.Default
    private boolean securityGate = false;
    @Builder.Default
    private boolean rollbackGate = false;
    @Builder.Default
    private boolean operationsGate = false;
    @Builder.Default
    private String status = LimitedPilotCertificationStatus.NOT_READY.name();
    private String certifiedBy;
    private Instant certifiedAt;
    private String notes;
    @Builder.Default
    private Map<String, Object> gateResults = new LinkedHashMap<>();
    @Builder.Default
    private List<String> blockers = new ArrayList<>();
    private Instant createdAt;
}
