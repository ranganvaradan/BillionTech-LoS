package com.los.core.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * Read model for {@link com.los.core.model.entity.StepExecutionRecord} (ops / history UI).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StepExecutionRecordView(
        UUID id,
        UUID applicationId,
        String stepType,
        String status,
        String inputJson,
        String outputJson,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant completedAt) {
}
