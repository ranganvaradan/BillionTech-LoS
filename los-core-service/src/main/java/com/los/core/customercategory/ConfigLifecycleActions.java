package com.los.core.customercategory;

import java.util.List;

/**
 * Backend-authoritative lifecycle actions for admin UI.
 * Roles still enforced on each mutating endpoint.
 */
public final class ConfigLifecycleActions {

    private ConfigLifecycleActions() {}

    public static List<String> forStatus(ConfigLifecycleStatus status) {
        if (status == null) {
            return List.of();
        }
        return switch (status) {
            case DRAFT -> List.of("EDIT", "DELETE", "SUBMIT", "COPY");
            case IN_REVIEW -> List.of("APPROVE", "RETURN", "COPY");
            case APPROVED -> List.of("ACTIVATE", "RETIRE", "COPY");
            case ACTIVE -> List.of("COPY", "RETIRE");
            case RETIRED -> List.of("COPY");
        };
    }
}
