package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class AssignmentRuleSetResponse {
    private UUID id;
    private String name;
    private String borrowerType;
    private String loanProduct;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;
    private String assignedRole;
    private UUID assignedUserId;
    private int priority;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
