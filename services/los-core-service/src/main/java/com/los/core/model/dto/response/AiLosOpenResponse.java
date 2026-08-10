package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiLosOpenResponse {
    private String loanId;
    private String status;
    private String message;
    private String reviewUrl;
    private String finalRedirectUrl;
    private String mode;
}
