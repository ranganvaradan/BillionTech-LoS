package com.los.core.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BorrowerGstAnalysisPrepareRequest {
    @NotBlank
    private String gstin;
    private boolean consent;
}
