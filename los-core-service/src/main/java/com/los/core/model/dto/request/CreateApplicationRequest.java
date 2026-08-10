package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import com.los.core.model.enums.IntakeSegment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateApplicationRequest {

    @NotNull(message = "Borrower type is required")
    private BorrowerType borrowerType;

    @NotBlank(message = "Loan product is required")
    private String loanProduct;

    /** Defaults to {@link IntakeSegment#BORROWER} when omitted. */
    private IntakeSegment intakeSegment;

    @Positive(message = "Requested amount must be positive")
    private BigDecimal requestedAmount;

    private Integer tenureMonths;

    /** Encore LMS product code (optional; falls back to active workflow default). */
    private String lmsProductCode;

    /** Encore tenure unit (optional; falls back to active workflow default). */
    private String lmsTenureUnit;

    /** Optional explicit workflow binding for new multi-workflow products. */
    private UUID workflowId;

    private Map<String, Object> personalInfo;

    private Map<String, Object> businessInfo;

    private Map<String, Object> financialInfo;

    private Map<String, Object> collateralInfo;
}
