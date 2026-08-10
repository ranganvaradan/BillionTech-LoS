package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class SubProgramMasterRequest {
    @NotBlank
    private String subProgramCode;
    @NotBlank
    private String name;
    @NotNull
    private UUID programId;
    @NotNull
    private UUID anchorId;
    private String flowType;
    private String anchorRole;
    private String borrowerRole;
    private BigDecimal subProgramLimit;
}
