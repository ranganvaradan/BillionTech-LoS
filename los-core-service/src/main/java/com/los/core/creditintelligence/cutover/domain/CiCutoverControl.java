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
public class CiCutoverControl {
    private UUID id;
    private UUID cohortId;
    private String authorityMode;
    private Instant effectiveAt;
    private String changedBy;
    private String reason;
    @Builder.Default
    private Map<String, Object> audit = new LinkedHashMap<>();
    private Instant createdAt;
}
