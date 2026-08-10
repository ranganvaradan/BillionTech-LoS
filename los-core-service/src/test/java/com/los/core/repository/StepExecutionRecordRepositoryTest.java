package com.los.core.repository;

import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.model.enums.StepExecutionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = Replace.ANY)
@TestPropertySource(properties = "spring.flyway.enabled=false")
class StepExecutionRecordRepositoryTest {

    @Autowired
    private StepExecutionRecordRepository repository;

    @Test
    void findByApplicationIdOrderByStartedAtDesc_returnsNewestFirst() {
        UUID appId = UUID.randomUUID();
        Instant older = Instant.parse("2024-01-01T00:00:00Z");
        Instant newer = Instant.parse("2024-06-15T12:00:00Z");

        StepExecutionRecord first = StepExecutionRecord.builder()
                .applicationId(appId)
                .stepType("BUREAU_PULL")
                .status(StepExecutionStatus.SUCCESS)
                .startedAt(older)
                .completedAt(older)
                .build();
        StepExecutionRecord second = StepExecutionRecord.builder()
                .applicationId(appId)
                .stepType("KYC_WORKFLOW")
                .status(StepExecutionStatus.SUCCESS)
                .startedAt(newer)
                .completedAt(newer)
                .build();
        repository.save(first);
        repository.save(second);

        List<StepExecutionRecord> list = repository.findByApplicationIdOrderByStartedAtDesc(appId);
        assertEquals(2, list.size());
        assertEquals("KYC_WORKFLOW", list.get(0).getStepType());
        assertEquals("BUREAU_PULL", list.get(1).getStepType());
    }
}
