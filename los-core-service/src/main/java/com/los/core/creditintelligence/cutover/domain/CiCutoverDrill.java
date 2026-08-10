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
public class CiCutoverDrill {
    private UUID id;
    private UUID cohortId;
    private String drillType;
    private String result;
    @Builder.Default
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private String performedBy;
    private Instant performedAt;
}
