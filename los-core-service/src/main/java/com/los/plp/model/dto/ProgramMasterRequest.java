package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class ProgramMasterRequest {
    @NotBlank
    private String programCode;
    @NotBlank
    private String programName;
    @NotBlank
    private String productType;
    private BigDecimal programLimit;
    private BigDecimal maxBorrowerLimit;
    private UUID workflowConfigId;
}
