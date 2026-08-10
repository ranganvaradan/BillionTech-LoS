package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiCutoverReview {
    private UUID id;
    private UUID comparisonId;
    private String disposition;
    private String commentary;
    private String reviewer;
    private Instant createdAt;
}
