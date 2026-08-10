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
public class CiCutoverException {
    private UUID id;
    private UUID cohortId;
    private String exceptionCode;
    private String risk;
    private String approver;
    private String mitigation;
    private Instant expiry;
    private Instant createdAt;
}
