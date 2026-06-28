package com.los.core.service.audit;

import com.los.core.model.entity.AuditEvent;
import com.los.core.repository.AuditEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

/**
 * Audit logger. Writes are async so they do not extend the caller's transaction.
 *
 * Important: the entity carries a FK on {@code application_id → loan_applications.id}.
 * If the caller is still inside a transaction that is creating/updating the application,
 * the async thread can race ahead of the COMMIT and hit a FK violation. To prevent that,
 * each public {@code logEvent} delegates to an internal {@code @Async doLog(...)} that is
 * only triggered after the caller's transaction commits when one is active. When no
 * transaction is active, we dispatch the async call immediately (e.g. from controllers
 * that don't open a tx, or from already-committed code paths).
 */
@Slf4j
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Self-injected proxy so calls to {@code doLog*} go through Spring's {@code @Async}
     * interceptor (calls on {@code this} bypass the proxy and would run synchronously).
     */
    private final AuditService self;

    public AuditService(AuditEventRepository auditEventRepository,
                        @Lazy AuditService self) {
        this.auditEventRepository = auditEventRepository;
        this.self = self;
    }

    public void logEvent(UUID applicationId, String eventType, String action,
                         UUID performedBy, Map<String, Object> previousState,
                         Map<String, Object> newState, String description) {
        Runnable dispatch = () -> self.doLog(applicationId, eventType, action,
                performedBy, previousState, newState, description);
        runAfterCommitOrNow(dispatch);
    }

    public void logEvent(UUID applicationId, String eventType, Map<String, Object> details) {
        Runnable dispatch = () -> self.doLogSimple(applicationId, eventType, details);
        runAfterCommitOrNow(dispatch);
    }

    public Page<AuditEvent> getAuditTrail(UUID applicationId, Pageable pageable) {
        return auditEventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable);
    }

    /**
     * Defer the supplied dispatcher until the active transaction has committed, so any
     * referenced FK rows (e.g. {@code loan_applications}) are visible to the async writer.
     * If no transaction is active, dispatch immediately.
     */
    private void runAfterCommitOrNow(Runnable dispatcher) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        dispatcher.run();
                    } catch (Exception e) {
                        log.warn("Audit dispatch after commit failed: {}", e.getMessage());
                    }
                }
            });
        } else {
            try {
                dispatcher.run();
            } catch (Exception e) {
                log.warn("Audit dispatch failed: {}", e.getMessage());
            }
        }
    }

    @Async
    public void doLog(UUID applicationId, String eventType, String action,
                      UUID performedBy, Map<String, Object> previousState,
                      Map<String, Object> newState, String description) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .applicationId(applicationId)
                    .eventType(eventType)
                    .action(action)
                    .performedBy(performedBy)
                    .previousState(previousState)
                    .newState(newState)
                    .description(description)
                    .build();
            auditEventRepository.save(event);
            log.debug("Audit event logged: {} - {} for application {}", eventType, action, applicationId);
        } catch (Exception e) {
            log.warn("Audit event save failed ({} - {} for application {}): {}",
                    eventType, action, applicationId, e.getMessage());
        }
    }

    @Async
    public void doLogSimple(UUID applicationId, String eventType, Map<String, Object> details) {
        try {
            AuditEvent event = AuditEvent.builder()
                    .applicationId(applicationId)
                    .eventType(eventType)
                    .action(eventType)
                    .newState(details)
                    .description(details != null ? details.toString() : null)
                    .build();
            auditEventRepository.save(event);
            log.debug("Audit event logged: {} for application {}", eventType, applicationId);
        } catch (Exception e) {
            log.warn("Audit event save failed ({} for application {}): {}",
                    eventType, applicationId, e.getMessage());
        }
    }
}
