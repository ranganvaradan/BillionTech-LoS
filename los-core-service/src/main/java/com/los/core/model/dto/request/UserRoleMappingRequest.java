package com.los.core.model.dto.request;

import com.los.core.model.enums.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
public class UserRoleMappingRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private AssignmentRole role;

    /** Optional null/blank = any product */
    private String loanProduct;
    /** Optional null/blank = any borrower type (e.g. INDIVIDUAL) */
    private String borrowerType;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;
    private int priority;
    private boolean active = true;
}
