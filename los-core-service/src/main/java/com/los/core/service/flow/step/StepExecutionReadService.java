package com.los.core.service.flow.step;

import com.los.core.model.dto.response.StepExecutionRecordView;
import com.los.core.model.entity.StepExecutionRecord;
import com.los.core.repository.StepExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to persisted step execution history for a loan application.
 */
@Service
@RequiredArgsConstructor
public class StepExecutionReadService {

    private final StepExecutionRecordRepository stepExecutionRecordRepository;

    @Transactional(readOnly = true)
    public List<StepExecutionRecordView> listStepExecutionRecords(UUID applicationId) {
        return stepExecutionRecordRepository
                .findByApplicationIdOrderByStartedAtDesc(applicationId)
                .stream()
                .map(StepExecutionReadService::toView)
                .toList();
    }

    private static StepExecutionRecordView toView(StepExecutionRecord e) {
        return new StepExecutionRecordView(
                e.getId(),
                e.getApplicationId(),
                e.getStepType(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getInputJson(),
                e.getOutputJson(),
                e.getErrorCode(),
                e.getErrorMessage(),
                e.getStartedAt(),
                e.getCompletedAt());
    }
}
