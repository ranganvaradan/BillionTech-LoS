package com.los.core.service.flow.step;

import com.los.core.model.dto.response.StepExecutionRecordView;
import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.model.enums.StepExecutionStatus;
import com.los.core.repository.StepExecutionRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StepExecutionReadServiceTest {

    @Mock
    private StepExecutionRecordRepository repository;

    @InjectMocks
    private StepExecutionReadService stepExecutionReadService;

    @Test
    void listStepExecutionRecords_usesOrderByFromRepository() {
        UUID app = UUID.randomUUID();
        when(repository.findByApplicationIdOrderByStartedAtDesc(app)).thenReturn(List.of());

        assertTrue(stepExecutionReadService.listStepExecutionRecords(app).isEmpty());
        verify(repository).findByApplicationIdOrderByStartedAtDesc(app);
    }

    @Test
    void listStepExecutionRecords_mapsEntityToView() {
        UUID id = UUID.randomUUID();
        UUID app = UUID.randomUUID();
        Instant t0 = Instant.parse("2025-01-01T00:00:00Z");
        StepExecutionRecord e = StepExecutionRecord.builder()
                .id(id)
                .applicationId(app)
                .stepType("ESIGN")
                .status(StepExecutionStatus.FAILED)
                .inputJson("{\"a\":1}")
                .outputJson("{\"b\":2}")
                .errorCode("E1")
                .errorMessage("m")
                .startedAt(t0)
                .completedAt(t0)
                .build();
        when(repository.findByApplicationIdOrderByStartedAtDesc(app)).thenReturn(List.of(e));

        List<StepExecutionRecordView> views = stepExecutionReadService.listStepExecutionRecords(app);
        assertEquals(1, views.size());
        StepExecutionRecordView v = views.get(0);
        assertEquals(id, v.id());
        assertEquals(app, v.applicationId());
        assertEquals("ESIGN", v.stepType());
        assertEquals("FAILED", v.status());
        assertEquals("{\"a\":1}", v.inputJson());
        assertEquals("{\"b\":2}", v.outputJson());
        assertEquals("E1", v.errorCode());
        assertEquals("m", v.errorMessage());
    }
}
