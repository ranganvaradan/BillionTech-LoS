package com.los.core.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UnderwritingScorecardResponse {
    private UUID id;
    private String name;
    private String borrowerType;
    private String loanProduct;
    private int version;
    private int priority;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Map<String, Object> geography;
    private Map<String, Object> scorecardJson;
    private Map<String, Object> thresholdsJson;
    private Map<String, Object> hardRulesJson;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}
