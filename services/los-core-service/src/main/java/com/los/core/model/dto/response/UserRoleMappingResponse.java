package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserRoleMappingResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private String role;
    private String loanProduct;
    private String borrowerType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;
    private int priority;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
