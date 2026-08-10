package com.los.plp.client;

import java.util.UUID;

/**
 * Optional request-scoped application id for PLP HTTP audits so timeline / api_audit_log
 * rows link back to the LOS loan application that triggered the call.
 */
public final class PlpApiAuditContext {

    private static final ThreadLocal<UUID> APPLICATION_ID = new ThreadLocal<>();

    private PlpApiAuditContext() {}

    public static void setApplicationId(UUID applicationId) {
        if (applicationId == null) {
            APPLICATION_ID.remove();
        } else {
            APPLICATION_ID.set(applicationId);
        }
    }

    public static UUID getApplicationId() {
        return APPLICATION_ID.get();
    }

    public static void clear() {
        APPLICATION_ID.remove();
    }

    /** Runs {@code action} with the given application id bound for PLP API audits. */
    public static void runWithApplication(UUID applicationId, Runnable action) {
        UUID previous = APPLICATION_ID.get();
        try {
            setApplicationId(applicationId);
            action.run();
        } finally {
            if (previous == null) {
                clear();
            } else {
                setApplicationId(previous);
            }
        }
    }

    public static <T> T callWithApplication(UUID applicationId, java.util.concurrent.Callable<T> action) {
        UUID previous = APPLICATION_ID.get();
        try {
            setApplicationId(applicationId);
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (previous == null) {
                clear();
            } else {
                setApplicationId(previous);
            }
        }
    }
}
