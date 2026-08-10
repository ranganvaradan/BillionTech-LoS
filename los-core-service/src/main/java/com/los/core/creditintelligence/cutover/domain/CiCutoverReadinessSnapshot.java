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
public class CiCutoverReadinessSnapshot {
    private UUID id;
    private UUID cohortId;
    private String overallOutcome;
    private BigDecimal score;
    @Builder.Default
    private Map<String, Object> dimensions = new LinkedHashMap<>();
    @Builder.Default
    private List<String> blockers = new ArrayList<>();
    @Builder.Default
    private boolean limitedPilotReady = false;
    private Instant createdAt;
}
