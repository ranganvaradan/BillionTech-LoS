package com.los.core.creditintelligence.cutover.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CiCutoverDimension {
    private UUID id;
    private UUID cohortId;
    private String dimensionCode;
    @Builder.Default
    private String readiness = DimensionReadiness.NOT_READY.name();
    @Builder.Default
    private String authoritySource = "LEGACY";
    private String notes;
}
