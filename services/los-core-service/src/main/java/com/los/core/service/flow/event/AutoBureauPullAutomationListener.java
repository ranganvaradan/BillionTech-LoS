package com.los.core.service.flow.event;

import com.los.core.model.entity.KycStepResult;
import com.los.core.model.enums.KycStepType;
import com.los.core.model.enums.StepExecutionStatus;
import com.los.core.repository.KycStepResultRepository;
import com.los.core.repository.StepExecutionRecordRepository;
import com.los.core.service.audit.AuditService;
import com.los.core.service.flow.step.FlowStepType;
import com.los.core.service.workflow.coordinator.WorkflowExecutionCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.UUID;

/**
 * Triggers bureau pull once when KYC reaches PASS. Runs after the KYC transaction commits so KYC flow
 * is never blocked by bureau provider latency/failure.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoBureauPullAutomationListener {

    private final WorkflowExecutionCoordinator workflowExecutionCoordinator;
    private final KycStepResultRepository kycStepResultRepository;
    private final StepExecutionRecordRepository stepExecutionRecordRepository;
    private final AuditService auditService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAutoBureauPullRequested(AutoBureauPullRequestedEvent event) {
        UUID applicationId = event.applicationId();
        String triggerSource = event.triggerSource() != null ? event.triggerSource() : "UNKNOWN";

        try {
            if (shouldSkipAutomation(applicationId)) {
                log.debug("Skipping auto bureau pull for {}: existing bureau state present", applicationId);
                return;
            }

            auditService.logEvent(
                    applicationId,
                    "AUTO_BUREAU_PULL",
                    "AUTO_BUREAU_PULL_TRIGGERED",
                    null,
                    null,
                    Map.of("triggerSource", triggerSource, "flowStep", FlowStepType.BUREAU_PULL),
                    "Auto bureau pull initiated after KYC success"
            );

            Map<String, Object> output = workflowExecutionCoordinator
                    .executeFlowStepForApplication(
                            applicationId,
                            FlowStepType.BUREAU_PULL,
                            Map.of("autoTriggered", true, "triggerSource", triggerSource))
                    .output();

            auditService.logEvent(
                    applicationId,
                    "AUTO_BUREAU_PULL",
                    "AUTO_BUREAU_PULL_COMPLETED",
                    null,
                    Map.of("triggerSource", triggerSource),
                    Map.of(
                            "success", String.valueOf(output.getOrDefault("success", false)),
                            "creditScore", String.valueOf(output.getOrDefault("creditScore", 0)),
                            "transactionId", String.valueOf(output.getOrDefault("transactionId", ""))),
                    "Auto bureau pull completed"
            );
        } catch (Exception ex) {
            log.warn("Auto bureau pull failed for app {} from {}: {}", applicationId, triggerSource, ex.getMessage());
            auditService.logEvent(
                    applicationId,
                    "AUTO_BUREAU_PULL",
                    "AUTO_BUREAU_PULL_FAILED",
                    null,
                    Map.of("triggerSource", triggerSource),
                    Map.of("error", ex.getMessage() != null ? ex.getMessage() : "UNKNOWN"),
                    "Auto bureau pull failed; manual retry remains available"
            );
        }
    }

    private boolean shouldSkipAutomation(UUID applicationId) {
        if (stepExecutionRecordRepository.existsByApplicationIdAndStepTypeAndStatus(
                applicationId, FlowStepType.BUREAU_PULL, StepExecutionStatus.STARTED)) {
            return true;
        }

        KycStepResult latest = kycStepResultRepository
                .findTopByApplicationIdAndStepTypeOrderByCreatedAtDesc(applicationId, KycStepType.BUREAU_PULL)
                .orElse(null);
        return latest != null;
    }
}
