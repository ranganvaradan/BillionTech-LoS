package com.los.core.service.loan;

import com.los.core.config.ApplicationContextHolder;
import com.los.core.model.entity.LoanApplication;
import com.los.core.model.enums.ApplicationStatus;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Records {@code application_status_history} whenever a loan application's status changes.
 * <p>
 * Writes are deferred until after the current Hibernate flush / transaction commit.
 * Calling {@code repository.save} from {@code @PreUpdate} during flush causes nested-flush
 * failures (e.g. on "Save draft & notify borrower").
 */
@Slf4j
public class LoanApplicationStatusHistoryListener {

    @PostLoad
    public void onLoad(LoanApplication app) {
        app.setLoadedStatus(app.getStatus());
    }

    @PostPersist
    public void onCreate(LoanApplication app) {
        if (app.getId() == null || app.getStatus() == null) {
            return;
        }
        scheduleWrite(
                app.getId(),
                null,
                app.getStatus(),
                StatusChangeContext.changedBy(),
                firstNonBlank(StatusChangeContext.remarks(), "Application created"));
        app.setLoadedStatus(app.getStatus());
    }

    @PreUpdate
    public void onUpdate(LoanApplication app) {
        ApplicationStatus previous = app.getLoadedStatus();
        ApplicationStatus current = app.getStatus();
        if (current == null || previous == current) {
            return;
        }
        String remarks = StatusChangeContext.remarks();
        if (remarks == null || remarks.isBlank()) {
            remarks = "Status changed to " + current.name();
        }
        scheduleWrite(app.getId(), previous, current, StatusChangeContext.changedBy(), remarks);
        app.setLoadedStatus(current);
    }

    private static void scheduleWrite(
            UUID applicationId,
            ApplicationStatus from,
            ApplicationStatus to,
            UUID changedBy,
            String remarks) {
        if (!ApplicationContextHolder.isReady()) {
            log.warn("Skipping status history write — ApplicationContext not ready");
            return;
        }
        Runnable task = () -> writeNow(applicationId, from, to, changedBy, remarks);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.warn("Deferred status history write failed for {}: {}", applicationId, e.getMessage());
                    }
                }
            });
        } else {
            task.run();
        }
    }

    private static void writeNow(
            UUID applicationId,
            ApplicationStatus from,
            ApplicationStatus to,
            UUID changedBy,
            String remarks) {
        try {
            ApplicationContextHolder.getBean(ApplicationStatusHistoryWriter.class)
                    .record(applicationId, from, to, changedBy, remarks);
        } catch (Exception e) {
            log.warn("Failed to write application status history for {}: {}", applicationId, e.getMessage());
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
