package com.los.core.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/** Borrower-initiated repayment from the borrower portal. */
@Data
public class BorrowerRepaymentRequest {

    @NotNull
    @Positive
    private BigDecimal amount;
}
