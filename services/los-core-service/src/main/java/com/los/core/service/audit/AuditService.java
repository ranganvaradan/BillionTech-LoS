package com.los.core.service.audit;

import com.los.core.model.entity.AuditEvent;
import com.los.core.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    @Async
    public void logEvent(UUID applicationId, String eventType, String action,
                         UUID performedBy, Map<String, Object> previousState,
                         Map<String, Object> newState, String description) {
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
    }

    public Page<AuditEvent> getAuditTrail(UUID applicationId, Pageable pageable) {
        return auditEventRepository.findByApplicationIdOrderByCreatedAtDesc(applicationId, pageable);
    }

    /**
     * Simplified audit log — for events that don't need full previous/new state tracking.
     */
    @Async
    public void logEvent(UUID applicationId, String eventType, Map<String, Object> details) {
        AuditEvent event = AuditEvent.builder()
                .applicationId(applicationId)
                .eventType(eventType)
                .action(eventType)
                .newState(details)
                .description(details.toString())
                .build();
        auditEventRepository.save(event);
        log.debug("Audit event logged: {} for application {}", eventType, applicationId);
    }
}
