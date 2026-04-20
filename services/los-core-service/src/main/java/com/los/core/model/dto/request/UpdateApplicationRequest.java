package com.los.core.model.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class UpdateApplicationRequest {

    private BigDecimal requestedAmount;

    private Integer tenureMonths;

    private Map<String, Object> personalInfo;

    private Map<String, Object> businessInfo;

    private Map<String, Object> financialInfo;

    private Map<String, Object> collateralInfo;

    private String remarks;
}
