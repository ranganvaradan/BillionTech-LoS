package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

@Value
@Builder
public class BorrowerStatementLineResponse {
    LocalDate valueDate;
    String description;
    BigDecimal credit;
    BigDecimal debit;
    BigDecimal balance;
}
