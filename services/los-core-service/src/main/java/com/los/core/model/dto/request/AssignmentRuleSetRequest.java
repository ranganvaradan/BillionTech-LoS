package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
public class AssignmentRuleSetRequest {

    @NotBlank
    private String name;

    @NotNull
    private BorrowerType borrowerType;

    @NotBlank
    private String loanProduct;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;
    private Integer minTenureMonths;
    private Integer maxTenureMonths;

    @NotBlank
    private String assignedRole;

    private UUID assignedUserId;

    @NotNull
    private Integer priority;
}
