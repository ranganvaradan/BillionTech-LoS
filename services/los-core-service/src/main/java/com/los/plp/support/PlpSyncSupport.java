package com.los.plp.support;

import java.time.Instant;
import java.util.UUID;

public final class PlpSyncSupport {

    private PlpSyncSupport() {
    }

    public static String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    public static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public static Instant now() {
        return Instant.now();
    }
}
