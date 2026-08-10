package com.los.core.model.dto.request;

import lombok.Data;

@Data
public class LoanProductRepaymentDefaultUpdateRequest {

    private String repaymentMechanism;
    private String pgProviderCode;
    private Boolean enabled;
}
