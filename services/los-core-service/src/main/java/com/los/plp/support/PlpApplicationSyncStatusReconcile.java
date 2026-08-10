package com.los.plp.support;

import com.los.core.model.entity.LoanApplication;
import com.los.plp.model.enums.PlpSyncStatus;

/**
 * Repairs application-level PLP sync flags when PLP entity IDs exist but status columns
 * were never updated (e.g. idempotent skip after a partial run).
 */
public final class PlpApplicationSyncStatusReconcile {

    private PlpApplicationSyncStatusReconcile() {
    }

    /** @return true if any sync status or timestamp field was updated */
    public static boolean reconcile(LoanApplication app) {
        boolean changed = false;

        if (app.getPlpBorrowerId() != null && app.getPlpBorrowerSyncStatus() == PlpSyncStatus.NOT_SYNCED) {
            app.setPlpBorrowerSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            if (app.getPlpBorrowerSyncedAt() == null) {
                app.setPlpBorrowerSyncedAt(PlpSyncSupport.now());
            }
            changed = true;
        }

        if (app.getPlpSubProgramBorrowerId() != null && app.getPlpLinkSyncStatus() == PlpSyncStatus.NOT_SYNCED) {
            app.setPlpLinkSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            if (app.getPlpLinkSyncedAt() == null) {
                app.setPlpLinkSyncedAt(PlpSyncSupport.now());
            }
            changed = true;
        }

        if (app.getPlpBorrowerProgramMappingId() != null && app.getPlpMappingSyncStatus() == PlpSyncStatus.NOT_SYNCED) {
            app.setPlpMappingSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            if (app.getPlpMappingSyncedAt() == null) {
                app.setPlpMappingSyncedAt(PlpSyncSupport.now());
            }
            changed = true;
        }

        if (app.getPlpBorrowerProgramMappingId() != null && app.getPlpProgramSyncStatus() == PlpSyncStatus.NOT_SYNCED) {
            app.setPlpProgramSyncStatus(PlpSyncStatus.SYNC_SUCCESS);
            app.setPlpProgramSyncError(null);
            if (app.getPlpProgramSyncedAt() == null) {
                app.setPlpProgramSyncedAt(PlpSyncSupport.now());
            }
            changed = true;
        }

        return changed;
    }
}
