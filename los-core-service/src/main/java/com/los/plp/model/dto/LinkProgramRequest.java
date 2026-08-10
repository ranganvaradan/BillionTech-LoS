package com.los.plp.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LinkProgramRequest {

    @NotNull
    private UUID subProgramId;
}
