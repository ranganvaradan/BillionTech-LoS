package com.los.core.model.dto.response;

import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.ProviderType;
import com.los.core.model.enums.StepOutcome;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class KycStepResultResponse {

    private UUID id;
    private UUID applicationId;
    private KycStepType stepType;
    private ProviderType provider;
    private StepOutcome outcome;
    private double confidenceScore;
    private Map<String, Object> parsedData;
    private String transactionId;
    private String errorMessage;
    private boolean overridden;
    private String overrideReason;
    private int attemptNumber;
    private Instant createdAt;
    private Instant completedAt;
}
