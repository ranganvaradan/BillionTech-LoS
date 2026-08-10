package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiCutoverCohort {
    private UUID id;
    private UUID tenantId;
    private UUID lenderId;
    private String productCode;
    private String segment;
    private Instant effectiveFrom;
    @Builder.Default
    private String status = CohortStatus.DRAFT.name();
    private UUID canonicalPolicyPackageId;
    private UUID decisionStrategyId;
    private String bindingSetVersion;
    @Builder.Default
    private Map<String, Object> rollbackPolicy = new LinkedHashMap<>();
    private String createdBy;
    private String approvedBy;
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Instant createdAt;
}
