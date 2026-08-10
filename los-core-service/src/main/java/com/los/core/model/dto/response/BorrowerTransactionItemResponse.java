package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class BorrowerTransactionItemResponse {
    Instant postedAt;
    String description;
    String reference;
    BigDecimal amount;
    String type;
}
