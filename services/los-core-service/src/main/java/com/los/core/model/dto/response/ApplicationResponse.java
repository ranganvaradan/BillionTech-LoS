package com.los.core.model.dto.response;

import com.los.core.model.enums.ApplicationStatus;
import com.los.core.model.enums.BorrowerType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ApplicationResponse {

    private UUID id;
    private String applicationNumber;
    private UUID customerId;
    private BorrowerType borrowerType;
    private String loanProduct;
    private BigDecimal requestedAmount;
    private BigDecimal interestRate;
    private Integer tenureMonths;
    private ApplicationStatus status;
    private Map<String, Object> personalInfo;
    private Map<String, Object> businessInfo;
    private Map<String, Object> financialInfo;
    private Map<String, Object> collateralInfo;
    private String remarks;
    private UUID assignedTo;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
}
