package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class BorrowerRepaymentScheduleItemResponse {
    int installmentNo;
    LocalDate dueDate;
    BigDecimal emi;
    BigDecimal principal;
    BigDecimal interest;
    BigDecimal outstandingPrincipal;
}
