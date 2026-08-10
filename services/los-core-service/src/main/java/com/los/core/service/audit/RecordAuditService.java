package com.los.core.service.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.los.core.model.entity.EntityRecordAudit;
import com.los.core.repository.EntityRecordAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAuditService {

    private final EntityRecordAuditRepository entityRecordAuditRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void capture(
            String entityType,
            String entityId,
            String action,
            String statusAtChange,
            UUID performedBy,
            String performedByRole,
            UUID applicationId,
            Object oldEntity,
            Object newEntity,
            String description) {
        Map<String, Object> oldMap = toMap(oldEntity);
        Map<String, Object> newMap = toMap(newEntity);
        String changed = diffKeys(oldMap, newMap);
        try {
            entityRecordAuditRepository.save(EntityRecordAudit.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(action)
                    .statusAtChange(statusAtChange)
                    .performedBy(performedBy)
                    .performedByRole(performedByRole)
                    .oldRow(oldMap)
                    .newRow(newMap)
                    .changedFields(changed)
                    .applicationId(applicationId)
                    .build());
        } catch (Exception e) {
            log.warn("Entity record audit skipped: {}", e.getMessage());
        }
        if (applicationId != null) {
            try {
                auditService.logEvent(
                        applicationId,
                        entityType,
                        action,
                        performedBy,
                        oldMap,
                        newMap,
                        description != null ? description : (action + " " + entityType));
            } catch (Exception e) {
                log.warn("Application audit index skipped: {}", e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public Page<EntityRecordAudit> search(
            String entityType,
            String entityId,
            UUID applicationId,
            Pageable pageable) {
        Specification<EntityRecordAudit> spec = (root, query, cb) -> cb.conjunction();
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType.trim()));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId.trim()));
        }
        if (applicationId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("applicationId"), applicationId));
        }
        return entityRecordAuditRepository.findAll(spec, pageable);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Map<?, ?> m) {
            return new LinkedHashMap<>((Map<String, Object>) m);
        }
        try {
            return objectMapper.convertValue(entity, Map.class);
        } catch (Exception e) {
            return Map.of("toString", String.valueOf(entity));
        }
    }

    private static String diffKeys(Map<String, Object> oldMap, Map<String, Object> newMap) {
        if (oldMap == null && newMap == null) {
            return null;
        }
        if (oldMap == null) {
            return String.join(",", newMap.keySet());
        }
        if (newMap == null) {
            return String.join(",", oldMap.keySet());
        }
        return oldMap.keySet().stream()
                .filter(k -> !Objects.equals(oldMap.get(k), newMap.get(k)))
                .collect(Collectors.joining(","));
    }
}
