package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UnderwritingRuleSetRequest {

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

    @NotNull
    private Integer priority;

    private Map<String, Object> rulesJson;
}
