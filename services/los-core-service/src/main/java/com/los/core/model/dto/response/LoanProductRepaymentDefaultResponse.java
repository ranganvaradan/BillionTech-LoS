package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LoanProductRepaymentDefaultResponse {

    private String loanProduct;
    private String repaymentMechanism;
    private String pgProviderCode;
    private Boolean enabled;
    private Instant updatedAt;
    private String updatedBy;
    /** When true, UI should show PLP platform admin link instead of editing. */
    private Boolean managedByPlp;
}
