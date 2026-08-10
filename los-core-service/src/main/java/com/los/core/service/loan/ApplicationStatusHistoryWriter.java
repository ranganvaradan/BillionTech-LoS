package com.los.core.service.loan;

import com.los.core.model.entity.ApplicationStatusHistory;
import com.los.core.model.enums.ApplicationStatus;
import com.los.core.repository.ApplicationStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists rows to {@code application_status_history} for TAT / timeline.
 * Does not change application status itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApplicationStatusHistoryWriter {

    private final ApplicationStatusHistoryRepository repository;

    /**
     * Uses {@code REQUIRES_NEW} so deferred after-commit writes from the entity listener
     * run in their own transaction (the caller's TX is already committed).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            UUID applicationId,
            ApplicationStatus fromStatus,
            ApplicationStatus toStatus,
            UUID changedBy,
            String remarks) {
        if (applicationId == null || toStatus == null) {
            return;
        }
        String trimmed = remarks == null ? null : remarks.trim();
        if (trimmed != null && trimmed.length() > 500) {
            trimmed = trimmed.substring(0, 500);
        }
        repository.save(ApplicationStatusHistory.builder()
                .applicationId(applicationId)
                .fromStatus(fromStatus == null ? null : fromStatus.name())
                .toStatus(toStatus.name())
                .changedBy(changedBy)
                .remarks(trimmed)
                .build());
        log.debug("Status history {} -> {} for application {}", fromStatus, toStatus, applicationId);
    }
}
