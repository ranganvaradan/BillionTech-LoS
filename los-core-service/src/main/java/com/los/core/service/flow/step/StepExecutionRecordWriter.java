package com.los.core.service.flow.step;

import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.model.enums.StepExecutionStatus;
import com.los.core.repository.StepExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Commits each recording phase in a new transaction so step execution history survives
 * rollbacks of the caller's business transaction.
 */
@Service
@RequiredArgsConstructor
public class StepExecutionRecordWriter {

    private final StepExecutionRecordRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public StepExecutionRecord createStarted(UUID applicationId, String stepType, String inputJson) {
        StepExecutionRecord r = StepExecutionRecord.builder()
                .applicationId(applicationId)
                .stepType(stepType)
                .status(StepExecutionStatus.STARTED)
                .inputJson(inputJson)
                .startedAt(Instant.now())
                .build();
        return repository.save(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void markSuccess(UUID recordId, String outputJson) {
        StepExecutionRecord r = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("StepExecutionRecord missing: " + recordId));
        r.setStatus(StepExecutionStatus.SUCCESS);
        r.setOutputJson(outputJson);
        r.setErrorCode(null);
        r.setErrorMessage(null);
        r.setCompletedAt(Instant.now());
        repository.save(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void markFailureUnsuccessfulResult(UUID recordId, String outputJson) {
        StepExecutionRecord r = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("StepExecutionRecord missing: " + recordId));
        r.setStatus(StepExecutionStatus.FAILED);
        r.setOutputJson(outputJson);
        r.setCompletedAt(Instant.now());
        repository.save(r);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Throwable.class)
    public void markFailureException(UUID recordId, String errorCode, String errorMessage) {
        StepExecutionRecord r = repository.findById(recordId)
                .orElseThrow(() -> new IllegalStateException("StepExecutionRecord missing: " + recordId));
        r.setStatus(StepExecutionStatus.FAILED);
        r.setErrorCode(errorCode);
        r.setErrorMessage(errorMessage);
        r.setCompletedAt(Instant.now());
        repository.save(r);
    }
}
