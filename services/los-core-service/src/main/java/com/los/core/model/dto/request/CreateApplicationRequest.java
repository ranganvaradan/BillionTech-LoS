package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CreateApplicationRequest {

    @NotNull(message = "Borrower type is required")
    private BorrowerType borrowerType;

    @NotBlank(message = "Loan product is required")
    private String loanProduct;

    @Positive(message = "Requested amount must be positive")
    private BigDecimal requestedAmount;

    private Integer tenureMonths;

    private Map<String, Object> personalInfo;

    private Map<String, Object> businessInfo;

    private Map<String, Object> financialInfo;

    private Map<String, Object> collateralInfo;
}
