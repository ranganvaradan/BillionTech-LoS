package com.los.core.model.dto.request;

import com.los.core.model.enums.BorrowerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UnderwritingScorecardRequest {

    @NotBlank
    private String name;

    @NotNull
    private BorrowerType borrowerType;

    @NotBlank
    private String loanProduct;

    private int version = 1;
    private int priority;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;

    /** {@code { "rows": [ { id, parameter, source, condition, weight, score, attachment? } ] }} */
    private Map<String, Object> scorecardJson;
    /** {@code { "approveMinPercent": 70, "manualMinPercent": 40 }} */
    private Map<String, Object> thresholdsJson;
    /** {@code { "rules": [ { parameter, source, condition, decision, message? } ] }} */
    private Map<String, Object> hardRulesJson;
    private boolean active;
}
