package com.los.core.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BorrowerItrReturnFormsRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    /** Must be true to authorize Karza ITR pull. */
    private boolean consent;

    @AssertTrue(message = "Consent is required to pull ITR data")
    public boolean isConsentGiven() {
        return consent;
    }
}
