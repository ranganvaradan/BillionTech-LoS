package com.los.core.service.loan;

import java.util.UUID;

/**
 * Optional ThreadLocal context so status-history writes can capture actor and remarks
 * when {@link LoanApplication} status changes via entity listener.
 */
public final class StatusChangeContext {

    private static final ThreadLocal<Holder> HOLDER = new ThreadLocal<>();

    private StatusChangeContext() {
    }

    public static void set(UUID changedBy, String remarks) {
        HOLDER.set(new Holder(changedBy, remarks));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static UUID changedBy() {
        Holder h = HOLDER.get();
        return h == null ? null : h.changedBy;
    }

    public static String remarks() {
        Holder h = HOLDER.get();
        return h == null ? null : h.remarks;
    }

    /** Run body with context, always clearing afterwards. */
    public static void run(UUID changedBy, String remarks, Runnable body) {
        set(changedBy, remarks);
        try {
            body.run();
        } finally {
            clear();
        }
    }

    private record Holder(UUID changedBy, String remarks) {
    }
}
