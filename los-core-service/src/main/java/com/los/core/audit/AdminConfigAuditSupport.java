package com.los.core.audit;

import com.los.core.service.audit.RecordAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Records before/after snapshots for admin configuration changes (workflows, rules, scorecards, etc.).
 */
@Component
@RequiredArgsConstructor
public class AdminConfigAuditSupport {

    private final RecordAuditService recordAuditService;

    public void captureCreate(String entityType, String entityId, Object snapshot, String description) {
        recordAuditService.capture(
                entityType,
                entityId,
                "CREATE",
                activeFlag(snapshot),
                AdminAuditContext.userId(),
                AdminAuditContext.userRole(),
                null,
                null,
                snapshot,
                description);
    }

    public void captureUpdate(String entityType, String entityId, Object before, Object after, String description) {
        recordAuditService.capture(
                entityType,
                entityId,
                "UPDATE",
                activeFlag(after),
                AdminAuditContext.userId(),
                AdminAuditContext.userRole(),
                null,
                before,
                after,
                description);
    }

    public void captureDelete(String entityType, String entityId, Object before, String description) {
        recordAuditService.capture(
                entityType,
                entityId,
                "DELETE",
                activeFlag(before),
                AdminAuditContext.userId(),
                AdminAuditContext.userRole(),
                null,
                before,
                null,
                description);
    }

    public void captureAction(String entityType, String entityId, String action, Object before, Object after, String description) {
        recordAuditService.capture(
                entityType,
                entityId,
                action,
                activeFlag(after != null ? after : before),
                AdminAuditContext.userId(),
                AdminAuditContext.userRole(),
                null,
                before,
                after,
                description);
    }

    private static String activeFlag(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot instanceof java.util.Map<?, ?> map) {
            Object active = map.get("active");
            if (active instanceof Boolean b) {
                return b ? "ACTIVE" : "INACTIVE";
            }
        }
        try {
            var m = snapshot.getClass().getMethod("isActive");
            Object v = m.invoke(snapshot);
            if (v instanceof Boolean b) {
                return b ? "ACTIVE" : "INACTIVE";
            }
        } catch (ReflectiveOperationException ignored) {
            // not an active-flag DTO
        }
        return null;
    }
}
