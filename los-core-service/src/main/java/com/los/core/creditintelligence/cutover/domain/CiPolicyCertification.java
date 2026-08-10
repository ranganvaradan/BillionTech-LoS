package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class CiPolicyCertification {
    private UUID id;
    private UUID tenantId;
    private UUID policyPackageId;
    @Builder.Default
    private String status = "PENDING";
    private String certifiedBy;
    private Instant certifiedAt;
    @Builder.Default
    private List<String> evidenceRefs = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> checklist = new LinkedHashMap<>();
    private Instant createdAt;
}
