package com.los.lms.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class LoanHandoverRequest {

    private UUID applicationId;
    private String applicationNumber;
    private String borrowerName;
    private String borrowerType;
    private String loanProduct;
    private BigDecimal sanctionedAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private BigDecimal emiAmount;
    private Map<String, Object> borrowerDetails;
    private Map<String, Object> collateralDetails;
    private String productCode; // Encore loan product code
}
