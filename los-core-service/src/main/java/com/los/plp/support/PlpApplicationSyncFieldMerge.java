package com.los.plp.support;

import com.los.core.model.entity.LoanApplication;

/**
 * Merges PLP sync columns from a freshly synced application onto a managed entity in an outer
 * transaction. Without this, a {@code REQUIRES_NEW} PLP sync can succeed and commit, then the outer
 * transaction flush overwrites those columns with stale null / NOT_SYNCED values.
 */
public final class PlpApplicationSyncFieldMerge {

    private PlpApplicationSyncFieldMerge() {
    }

    public static void mergeInto(LoanApplication target, LoanApplication source) {
        if (target == null || source == null) {
            return;
        }
        target.setPlpBorrowerId(source.getPlpBorrowerId());
        target.setPlpSubProgramBorrowerId(source.getPlpSubProgramBorrowerId());
        target.setPlpBorrowerProgramMappingId(source.getPlpBorrowerProgramMappingId());
        target.setPlpProgramSyncStatus(source.getPlpProgramSyncStatus());
        target.setPlpProgramSyncError(source.getPlpProgramSyncError());
        target.setPlpProgramSyncedAt(source.getPlpProgramSyncedAt());
        target.setPlpBorrowerSyncStatus(source.getPlpBorrowerSyncStatus());
        target.setPlpBorrowerSyncedAt(source.getPlpBorrowerSyncedAt());
        target.setPlpLinkSyncStatus(source.getPlpLinkSyncStatus());
        target.setPlpLinkSyncedAt(source.getPlpLinkSyncedAt());
        target.setPlpMappingSyncStatus(source.getPlpMappingSyncStatus());
        target.setPlpMappingSyncedAt(source.getPlpMappingSyncedAt());
    }
}
