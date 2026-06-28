package com.los.core.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Borrower request to finance a single invoice (invoice discounting).
 */
@Data
public class BorrowerFinanceRequest {
    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be greater than zero")
    private BigDecimal amount;
}
