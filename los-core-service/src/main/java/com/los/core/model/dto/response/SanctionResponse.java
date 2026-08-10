package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SanctionResponse {
    private UUID id;
    private UUID applicationId;
    private BigDecimal approvedAmount;
    private Integer approvedTenure;
    private BigDecimal interestRate;
    private BigDecimal processingFee;
    private String conditionsText;
    private String remarks;
    private String approvedBy;
    private String sanctionPdfPath;
    private Instant createdAt;
}
