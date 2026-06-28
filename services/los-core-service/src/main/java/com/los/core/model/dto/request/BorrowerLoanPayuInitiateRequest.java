package com.los.core.model.dto.request;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class BorrowerLoanPayuInitiateRequest {
    private BigDecimal amount;
}
