package com.los.core.audit;

import java.util.UUID;

/**
 * Request-scoped admin actor from {@code X-User-Id} / {@code X-User-Role} headers.
 */
public final class AdminAuditContext {

    private static final ThreadLocal<UUID> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ROLE = new ThreadLocal<>();

    private AdminAuditContext() {
    }

    public static void set(String userIdHeader, String userRoleHeader) {
        USER_ID.set(parseUuid(userIdHeader));
        USER_ROLE.set(blankToNull(userRoleHeader));
    }

    public static UUID userId() {
        return USER_ID.get();
    }

    public static String userRole() {
        return USER_ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        USER_ROLE.remove();
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
