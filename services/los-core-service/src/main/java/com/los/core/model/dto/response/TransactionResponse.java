package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID applicationId;
    private String transactionType;
    private BigDecimal amount;
    private String status;
    private String referenceNumber;
    private String utrNumber;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant completedAt;
}
