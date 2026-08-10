package com.los.core.service.flow.step;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.exception.BusinessRuleException;
import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.model.enums.StepExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepExecutionRecordingServiceTest {

    @Mock
    private StepExecutorRegistry stepExecutorRegistry;
    @Mock
    private StepExecutionRecordWriter recordWriter;
    @Mock
    private IStepExecutor executor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StepExecutionRecordingService recordingService;

    @BeforeEach
    void setUp() {
        recordingService = new StepExecutionRecordingService(stepExecutorRegistry, recordWriter, objectMapper);
    }

    @Test
    void successfulStep_insertsStartedThenFinishesWithSuccess() throws Exception {
        UUID appId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Map<String, Object> ctx = Map.of("a", 1);
        Map<String, Object> out = Map.of("k", "v");
        StepResult ok = StepResult.ok(out);

        when(stepExecutorRegistry.require("KYC")).thenReturn(executor);
        when(executor.execute(appId, ctx)).thenReturn(ok);
        when(recordWriter.createStarted(eq(appId), eq("KYC"), anyString())).thenReturn(recordWithId(recordId));

        StepResult result = recordingService.executeWithRecording("KYC", appId, ctx);

        assertEquals(ok, result);
        assertEquals(out, result.output());
        InOrder order = inOrder(recordWriter);
        order.verify(recordWriter).createStarted(eq(appId), eq("KYC"), anyString());
        order.verify(recordWriter).markSuccess(eq(recordId), anyString());
        verify(recordWriter, never()).markFailureException(any(), any(), any());
        verify(recordWriter, never()).markFailureUnsuccessfulResult(any(), anyString());
    }

    @Test
    void successfulStep_persistsOutputJson() {
        UUID appId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Map<String, Object> out = Map.of("x", 42);
        when(stepExecutorRegistry.require("X")).thenReturn(executor);
        when(executor.execute(appId, Map.of())).thenReturn(StepResult.ok(out));
        when(recordWriter.createStarted(eq(appId), eq("X"), anyString())).thenReturn(recordWithId(recordId));

        recordingService.executeWithRecording("X", appId, Map.of());

        ArgumentCaptor<String> outputCaptor = ArgumentCaptor.forClass(String.class);
        verify(recordWriter).markSuccess(eq(recordId), outputCaptor.capture());
        assertTrue(outputCaptor.getValue().contains("42"), () -> "expected output JSON, got: " + outputCaptor.getValue());
    }

    @Test
    void failedStep_businessException_recordsFailureAndRethrows() {
        UUID appId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        BusinessRuleException ex = new BusinessRuleException("no", "REASON", "ACT", null);
        when(stepExecutorRegistry.require("BUREAU")).thenReturn(executor);
        when(executor.execute(appId, Map.of())).thenThrow(ex);
        when(recordWriter.createStarted(eq(appId), eq("BUREAU"), anyString())).thenReturn(recordWithId(recordId));

        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> recordingService.executeWithRecording("BUREAU", appId, Map.of()));
        assertSame(ex, thrown);
        verify(recordWriter).markFailureException(recordId, "REASON", "no");
        verify(recordWriter, never()).markSuccess(any(), anyString());
    }

    @Test
    void unsuccessfulStepResult_persistsFailedWithOutput() {
        UUID appId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        Map<String, Object> out = Map.of("f", 1);
        when(stepExecutorRegistry.require("Y")).thenReturn(executor);
        when(executor.execute(appId, Map.of())).thenReturn(StepResult.fail(out));
        when(recordWriter.createStarted(eq(appId), eq("Y"), anyString())).thenReturn(recordWithId(recordId));

        StepResult r = recordingService.executeWithRecording("Y", appId, Map.of());

        assertFalse(r.success());
        verify(recordWriter).markFailureUnsuccessfulResult(eq(recordId), anyString());
    }

    @Test
    void flowOutputMapUnchangedForSuccess() {
        UUID appId = UUID.randomUUID();
        Map<String, Object> ctx = Map.of("kycPayload", Map.of("pan", "ABCDE1234F"));
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("applicationId", appId);
        out.put("allPassed", true);
        out.put("results", java.util.List.of());
        when(stepExecutorRegistry.require("KYC_WORKFLOW")).thenReturn(executor);
        when(executor.execute(appId, ctx)).thenReturn(StepResult.ok(out));
        when(recordWriter.createStarted(any(), anyString(), anyString())).thenReturn(recordWithId(UUID.randomUUID()));

        Map<String, Object> direct = executor.execute(appId, ctx).output();
        Map<String, Object> throughRecording = recordingService.executeWithRecording("KYC_WORKFLOW", appId, ctx).output();
        assertEquals(direct, throughRecording);
    }

    private static StepExecutionRecord recordWithId(UUID id) {
        return StepExecutionRecord.builder()
                .id(id)
                .applicationId(UUID.randomUUID())
                .stepType("—")
                .status(StepExecutionStatus.STARTED)
                .startedAt(java.time.Instant.now())
                .build();
    }
}
