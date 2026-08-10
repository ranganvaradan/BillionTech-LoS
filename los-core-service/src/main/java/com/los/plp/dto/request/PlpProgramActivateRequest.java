package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpProgramActivateRequest {
    private String sourceSystem;
    private String losProgramId;
}
