package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class LosLoanPaymentInProgressResponse {
    UUID id;
    UUID applicationId;
    String applicationNumber;
    String loanProduct;
    UUID borrowerUserId;
    BigDecimal principalAmount;
    String pipStatus;
    Instant createdAt;
}
