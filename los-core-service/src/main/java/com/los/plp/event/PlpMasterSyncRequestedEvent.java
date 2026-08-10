package com.los.plp.event;

import java.util.UUID;

/**
 * Published when a PLP master entity should be synced after the LOS transaction commits.
 * Carries id only — sync services reload committed state and own mapping/idempotency.
 */
public record PlpMasterSyncRequestedEvent(PlpMasterSyncType type, UUID entityId) {
    public PlpMasterSyncRequestedEvent {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId is required");
        }
    }
}
