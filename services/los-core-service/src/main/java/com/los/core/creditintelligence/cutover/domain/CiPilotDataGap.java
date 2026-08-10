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
public class CiPilotDataGap {
    private UUID id;
    private UUID applicationId;
    private UUID cohortId;
    private String canonicalPath;
    private String cause;
    @Builder.Default
    private boolean remediable = true;
    private String requiredAction;
    private Instant createdAt;
}
