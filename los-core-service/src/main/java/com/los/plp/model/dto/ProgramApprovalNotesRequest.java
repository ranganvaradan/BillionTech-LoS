package com.los.plp.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProgramApprovalNotesRequest {

    @NotBlank
    private String notes;
}
