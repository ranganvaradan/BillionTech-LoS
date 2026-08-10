package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiPilotCandidateScore {
    private UUID id;
    private String productCode;
    private BigDecimal score;
    @Builder.Default
    private Map<String, Object> rationale = new LinkedHashMap<>();
    private Instant rankedAt;
}
