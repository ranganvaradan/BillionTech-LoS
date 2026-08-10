package com.los.core.service.flow.step;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.StepExecutionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Wraps every {@link IStepExecutor#execute} in persistent {@link com.los.core.model.entity.StepExecutionRecord} rows.
 * The registry (dynamic sequencing) is unchanged: callers pass an explicit step type, same as before.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepExecutionRecordingService {

    private final StepExecutorRegistry stepExecutorRegistry;
    private final StepExecutionRecordWriter recordWriter;
    private final ObjectMapper objectMapper;

    public StepResult executeWithRecording(String stepType, UUID applicationId, Map<String, Object> context) {
        IStepExecutor executor = stepExecutorRegistry.require(stepType);
        String inputJson = safeWriteJson(context);
        StepExecutionRecord started = recordWriter.createStarted(applicationId, stepType, inputJson);
        UUID recordId = started.getId();
        try {
            StepResult result = executor.execute(applicationId, context);
            if (result.success()) {
                recordWriter.markSuccess(recordId, safeWriteJson(result.output()));
            } else {
                recordWriter.markFailureUnsuccessfulResult(recordId, safeWriteJson(result.output()));
            }
            return result;
        } catch (RuntimeException e) {
            try {
                String code = e instanceof BusinessRuleException bre ? bre.getReason() : null;
                String message = e.getMessage();
                recordWriter.markFailureException(recordId, code, message);
            } catch (Exception recordEx) {
                log.warn("Failed to persist step execution failure for app {} type {}: {}",
                        applicationId, stepType, recordEx.getMessage());
            }
            throw e;
        }
    }

    private String safeWriteJson(Object value) {
        try {
            if (value == null) {
                return objectMapper.writeValueAsString(Map.of());
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage().replace("\\", "\\\\").replace("\"", "'") : "";
            return "{\"_serializationError\":true,\"message\":\"" + msg + "\"}";
        }
    }
}
