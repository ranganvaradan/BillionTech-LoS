package com.los.plp.dto.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlpSubProgramActivateRequest {
    private String sourceSystem;
    private String losSubProgramId;
}
